package org.asdf.lapisPlugin.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.asdf.lapisPlugin.LapisPlugin;
import org.asdf.lapisPlugin.config.EventConfig;
import org.asdf.lapisPlugin.filter.FilterEngine;
import org.asdf.lapisPlugin.filter.SubscriptionExtractor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EventBridge {

    private final JavaPlugin plugin;
    private final EventConfig eventConfig;
    private final Map<String, List<ListenerInfo>> registry = new HashMap<>();
    private final Map<String, Listener> activeListeners = new HashMap<>();
    private final Map<String, CompletableFuture<JsonObject>> proxyFutures = new ConcurrentHashMap<>();
    private static final long PROXY_TIMEOUT_MS = 3000;

    public EventBridge(JavaPlugin plugin, EventConfig eventConfig) {
        this.plugin = plugin;
        this.eventConfig = eventConfig;
    }

    public String register(String eventType, String packageName, UUID uuid, JsonObject filter, JsonArray subscription, boolean proxy) {
        if (!eventConfig.isEnabled(eventType)) {
            plugin.getLogger().warning("Event '" + eventType + "' rejected: not in event.yml whitelist (package: " + packageName + ")");
            return "Event type '" + eventType + "' is not enabled in event.yml";
        }

        if (!EventTypeMap.isSupported(eventType)) {
            return "Unsupported event type: " + eventType;
        }

        registry.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new ListenerInfo(uuid, packageName, filter, subscription, proxy));

        if (registry.get(eventType).size() == 1) {
            registerBukkitListener(eventType);
        }
        return null;
    }

    public void unregister(UUID uuid) {
        for (Iterator<Map.Entry<String, List<ListenerInfo>>> it = registry.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<ListenerInfo>> entry = it.next();
            entry.getValue().removeIf(info -> info.uuid.equals(uuid));

            if (entry.getValue().isEmpty()) {
                unregisterBukkitListener(entry.getKey());
                it.remove();
            }
        }
    }

    public void unregisterAllByPackage(String packageName) {
        for (Iterator<Map.Entry<String, List<ListenerInfo>>> it = registry.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<ListenerInfo>> entry = it.next();
            entry.getValue().removeIf(info -> info.packageName.equals(packageName));

            if (entry.getValue().isEmpty()) {
                unregisterBukkitListener(entry.getKey());
                it.remove();
            }
        }
    }

    // 被 TcpManager 调用，处理 Python 回传的 message_response
    public void onProxyResponse(String eventId, JsonObject responseData) {
        CompletableFuture<JsonObject> future = proxyFutures.remove(eventId);
        if (future != null) {
            future.complete(responseData);
        }
    }

    private void registerBukkitListener(String eventType) {
        Class<? extends Event> eventClass = EventTypeMap.get(eventType);
        if (eventClass == null) return;

        Listener listener = new Listener() {};
        EventExecutor executor = (l, event) -> handleEvent(eventType, event);

        Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.NORMAL,
                executor,
                plugin
        );

        activeListeners.put(eventType, listener);
    }

    private void unregisterBukkitListener(String eventType) {
        Listener listener = activeListeners.remove(eventType);
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
    }

    private void handleEvent(String eventType, Event event) {
        List<ListenerInfo> listeners = registry.get(eventType);
        if (listeners == null || listeners.isEmpty()) return;

        JsonObject innerData = EventSerializer.serialize(eventType, event);

        for (ListenerInfo info : listeners) {
            if (!FilterEngine.evaluate(info.filter, innerData)) continue;

            JsonObject payload = SubscriptionExtractor.apply(innerData, info.subscription);

            JsonObject outerData = new JsonObject();
            outerData.addProperty("event_type", eventType);
            outerData.addProperty("listener_uuid", info.uuid.toString());
            outerData.add("data", payload);

            if (info.proxy) {
                String eventId = UUID.randomUUID().toString();
                outerData.addProperty("event_id", eventId);
                outerData.addProperty("proxy", true);

                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                proxyFutures.put(eventId, future);

                JsonObject message = new JsonObject();
                message.addProperty("message_type", "event");
                message.add("data", outerData);
                LapisPlugin.getInstance().getTcpManager().sendEvent(info.packageName, message);

                try {
                    JsonObject responseData = future.get(PROXY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    boolean continueEvent = responseData.has("continue") && responseData.get("continue").getAsBoolean();
                    if (!continueEvent && event instanceof Cancellable cancellable) {
                        cancellable.setCancelled(true);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Proxy event timeout or error for " + eventType + ": " + e.getMessage());
                } finally {
                    proxyFutures.remove(eventId);
                }
            } else {
                JsonObject message = new JsonObject();
                message.addProperty("message_type", "event");
                message.add("data", outerData);
                LapisPlugin.getInstance().getTcpManager().sendEvent(info.packageName, message);
            }
        }
    }

    public int getTotalListenerCount() {
        return registry.values().stream().mapToInt(List::size).sum();
    }

    public int getActiveEventTypeCount() {
        return activeListeners.size();
    }

    public Map<String, List<ListenerInfo>> getRegistrySnapshot() {
        return new HashMap<>(registry);
    }

    public record ListenerInfo(UUID uuid, String packageName, JsonObject filter, JsonArray subscription, boolean proxy) {}
}