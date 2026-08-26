package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.asdf.lapisPlugin.LapisPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CommandHandler {

    public static JsonObject handleHandshake(JsonObject cmd) {
        int id = cmd.get("id").getAsInt();
        JsonObject data = cmd.getAsJsonObject("data");
        String packageName = data.get("package_name").getAsString();
        String password = data.has("password") ? data.get("password").getAsString() : "";

        String expected = LapisPlugin.getInstance().getConfig().getString("password", "");
        boolean ok = expected.isEmpty() || expected.equals(password);

        JsonObject resp = new JsonObject();
        resp.addProperty("response_type", "handshake_ack");
        resp.addProperty("id", id);
        resp.addProperty("ok", ok);
        resp.add("data", new JsonObject());
        return resp;
    }

    public static JsonObject handle(JsonObject cmd, String packageName) {
        String type = cmd.get("command_type").getAsString();
        int id = cmd.get("id").getAsInt();
        JsonObject data = cmd.has("data") ? cmd.getAsJsonObject("data") : new JsonObject();

        JsonObject response = new JsonObject();
        response.addProperty("id", id);

        try {
            switch (type) {
                case "register_event_listener" -> {
                    String eventType = data.get("event_type").getAsString();
                    UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
                    String pkg = data.has("package_name") ? data.get("package_name").getAsString() : packageName;
                    com.google.gson.JsonObject filter = data.has("filter") ? data.getAsJsonObject("filter") : new com.google.gson.JsonObject();
                    com.google.gson.JsonArray subscription = data.has("subscription") ? data.getAsJsonArray("subscription") : new com.google.gson.JsonArray();

                    LapisPlugin.getInstance().getEventBridge().register(eventType, pkg, uuid, filter, subscription);

                    response.addProperty("response_type", "register_event_listener_response");
                    response.addProperty("ok", true);
                    JsonObject respData = new JsonObject();
                    respData.addProperty("listener_uuid", uuid.toString());
                    respData.addProperty("state", "ok");
                    response.add("data", respData);
                }
                case "unregister_event_listener" -> {
                    UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
                    LapisPlugin.getInstance().getEventBridge().unregister(uuid);

                    response.addProperty("response_type", "unregister_event_listener_response");
                    response.addProperty("ok", true);
                    response.add("data", new JsonObject());
                }
                case "send_message" -> {
                    UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
                    String message = data.get("message").getAsString();

                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                        response.addProperty("response_type", "send_message_response");
                        response.addProperty("ok", true);
                        response.add("data", new JsonObject());
                    } else {
                        response.addProperty("response_type", "send_message_response");
                        response.addProperty("ok", false);
                        JsonObject errData = new JsonObject();
                        errData.addProperty("error", "Player not found or offline");
                        response.add("data", errData);
                    }
                }
                case "give_item" -> handleGiveItem(data, response);
                case "take_item" -> handleTakeItem(data, response);
                default -> {
                    response.addProperty("response_type", type + "_response");
                    response.addProperty("ok", false);
                    JsonObject errData = new JsonObject();
                    errData.addProperty("error", "Unknown command: " + type);
                    response.add("data", errData);
                }
            }
        } catch (Exception e) {
            response.addProperty("response_type", type + "_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", e.getMessage());
            response.add("data", errData);
        }

        return response;
    }

    private static void handleGiveItem(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String itemId = data.get("item_id").getAsString();
        int count = data.has("count") ? data.get("count").getAsInt() : 1;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            response.addProperty("response_type", "give_item_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", "Player not found or offline");
            response.add("data", errData);
            return;
        }

        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            response.addProperty("response_type", "give_item_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", "Unknown item: " + itemId);
            response.add("data", errData);
            return;
        }

        ItemStack item = new ItemStack(material, count);
        player.getInventory().addItem(item);
        response.addProperty("response_type", "give_item_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleTakeItem(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String itemId = data.get("item_id").getAsString();
        int count = data.has("count") ? data.get("count").getAsInt() : 1;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            response.addProperty("response_type", "take_item_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", "Player not found or offline");
            response.add("data", errData);
            return;
        }

        Material material = Material.matchMaterial(itemId);
        boolean success = false;

        if (material != null) {
            int removed = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item == null || item.getType() != material) continue;

                int amount = item.getAmount();
                int need = count - removed;

                if (amount <= need) {
                    removed += amount;
                    player.getInventory().remove(item);
                } else {
                    item.setAmount(amount - need);
                    removed = count;
                    break;
                }

                if (removed >= count) break;
            }
            success = removed >= count;
        }

        response.addProperty("response_type", "take_item_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("is_success", success);
        response.add("data", respData);
    }

    private static void handleRegister(JsonObject data, JsonObject response) {
        String eventType = data.get("event_type").getAsString();
        UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
        String packageName = data.has("package_name") ? data.get("package_name").getAsString() : "unknown";
        JsonObject filter = data.has("filter") ? data.getAsJsonObject("filter") : new JsonObject();
        var subscription = data.has("subscription") ? data.getAsJsonArray("subscription") : new com.google.gson.JsonArray();

        String error = LapisPlugin.getInstance().getEventBridge().register(eventType, packageName, uuid, filter, subscription);

        if (error != null) {
            response.addProperty("response_type", "register_event_listener_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", error);
            response.add("data", errData);
            return;
        }

        response.addProperty("response_type", "register_event_listener_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("listener_uuid", uuid.toString());
        respData.addProperty("state", "ok");
        response.add("data", respData);
    }
}