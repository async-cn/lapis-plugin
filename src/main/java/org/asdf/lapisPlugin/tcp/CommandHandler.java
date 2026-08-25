package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.asdf.lapisPlugin.LapisPlugin;

import java.util.UUID;

public class CommandHandler {

    public static JsonObject handleAndReturn(JsonObject cmd) {
        String type = cmd.get("command_type").getAsString();
        JsonObject data = cmd.has("data") ? cmd.getAsJsonObject("data") : new JsonObject();
        String requestId = cmd.get("request_id").getAsString();

        JsonObject response = new JsonObject();
        response.addProperty("request_id", requestId);
        response.addProperty("message_type", "response");

        try {
            switch (type) {
                case "register_event_listener" -> {
                    String eventType = data.get("event_type").getAsString();
                    UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
                    String packageName = data.has("package_name") ? data.get("package_name").getAsString() : "unknown";
                    JsonObject filter = data.has("filter") ? data.getAsJsonObject("filter") : new JsonObject();
                    var subscription = data.has("subscription") ? data.getAsJsonArray("subscription") : new com.google.gson.JsonArray();

                    LapisPlugin.getInstance().getEventBridge().register(eventType, packageName, uuid, filter, subscription);
                    response.addProperty("status", "ok");
                }
                case "unregister_event_listener" -> {
                    UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
                    LapisPlugin.getInstance().getEventBridge().unregister(uuid);
                    response.addProperty("status", "ok");
                }
                case "send_message" -> {
                    UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
                    String message = data.get("message").getAsString();

                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                        response.addProperty("status", "ok");
                    } else {
                        response.addProperty("status", "error");
                        response.addProperty("error", "Player not found or offline");
                    }
                }
                default -> {
                    response.addProperty("status", "error");
                    response.addProperty("error", "Unknown command: " + type);
                }
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("error", e.getMessage());
        }

        return response;
    }
}