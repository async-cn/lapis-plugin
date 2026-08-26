package org.asdf.lapisPlugin.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.asdf.lapisPlugin.LapisPlugin;
import org.asdf.lapisPlugin.filter.FilterEngine;
import org.asdf.lapisPlugin.filter.SubscriptionExtractor;

import java.util.*;

public class EventBridge {

    private final JavaPlugin plugin;
    private final Map<String, List<ListenerInfo>> registry = new HashMap<>();
    private final Map<String, Listener> activeListeners = new HashMap<>();

    public EventBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(String eventType, String packageName, UUID uuid, JsonObject filter, JsonArray subscription) {
        if (!EventTypeMap.isSupported(eventType)) {
            plugin.getLogger().warning("Unsupported event type: " + eventType);
            return;
        }
        registry.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new ListenerInfo(uuid, packageName, filter, subscription));

        if (registry.get(eventType).size() == 1) {
            registerBukkitListener(eventType);
        }
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

    private void registerBukkitListener(String eventType) {
        Class<? extends Event> eventClass = EventTypeMap.get(eventType);
        if (eventClass == null) return;

        // 用 EventExecutor 注册，不需要 @EventHandler
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

            JsonObject message = new JsonObject();
            message.addProperty("message_type", "event");
            message.add("data", outerData);

            LapisPlugin.getInstance().getTcpManager().sendEvent(info.packageName, message);
        }
    }

    public record ListenerInfo(UUID uuid, String packageName, JsonObject filter, JsonArray subscription) {}
}