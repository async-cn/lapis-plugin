package org.asdf.lapisPlugin.askInput;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AskInputManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public AskInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public JsonObject ask(UUID playerUuid, boolean prompt, String messageType, String messageContent, double timeout) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            JsonObject data = new JsonObject();
            data.addProperty("result_type", "timeout");
            data.addProperty("result_content", "");
            return data;
        }

        if (prompt) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if ("text_component".equals(messageType)) {
                        Component component = GsonComponentSerializer.gson().deserialize(messageContent);
                        player.sendMessage(component);
                    } else {
                        player.sendMessage(Component.text(messageContent));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to send ask_input prompt: " + e.getMessage());
                }
            });
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(playerUuid, future);

        if (timeout >= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pending.remove(playerUuid, future)) {
                    future.complete(null); // timeout
                }
            }, (long) (timeout * 20));
        }

        try {
            String result = future.get();
            JsonObject data = new JsonObject();
            if (result == null) {
                data.addProperty("result_type", "timeout");
                data.addProperty("result_content", "");
            } else {
                data.addProperty("result_type", "success");
                data.addProperty("result_content", result);
            }
            return data;
        } catch (Exception e) {
            JsonObject data = new JsonObject();
            data.addProperty("result_type", "timeout");
            data.addProperty("result_content", "");
            return data;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        CompletableFuture<String> future = pending.remove(uuid);
        if (future != null) {
            future.complete(event.getMessage());
            event.setCancelled(true);
        }
    }
}