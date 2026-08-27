package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.asdf.lapisPlugin.LapisPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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
                case "register_event_listener" -> handleRegister(data, response);
                case "unregister_event_listener" -> handleUnregister(data, response);
                case "send_message" -> handleSendMessage(data, response);
                case "give_item" -> handleGiveItem(data, response);
                case "take_item" -> handleTakeItem(data, response);
                case "set_custom_tag" -> handleSetCustomTag(data, response);
                case "remove_custom_tag" -> handleRemoveCustomTag(data, response);
                case "set_custom_data" -> handleSetCustomData(data, response);
                case "remove_custom_data" -> handleRemoveCustomData(data, response);
                case "execute_command" -> handleExecuteCommand(data, response);
                case "set_block" -> handleSetBlock(data, response);
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

    private static void handleExecuteCommand(JsonObject data, JsonObject response) {
        String command = data.get("command").getAsString();

        StringBuilder output = new StringBuilder();
        org.bukkit.command.CommandSender sender = Bukkit.createCommandSender(component -> {
            String text = PlainTextComponentSerializer.plainText().serialize(component);
            output.append(text).append("\n");
        });

        boolean success = Bukkit.dispatchCommand(sender, command);

        response.addProperty("response_type", "execute_command_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        String result = output.toString().trim();
        respData.addProperty("result", result.isEmpty() ? (success ? "Command executed successfully" : "Command execution failed") : result);
        response.add("data", respData);
    }

    private static void handleSetBlock(JsonObject data, JsonObject response) {
        String blockId = data.get("block_id").getAsString();
        com.google.gson.JsonArray pos = data.getAsJsonArray("pos");
        int x = pos.get(0).getAsInt();
        int y = pos.get(1).getAsInt();
        int z = pos.get(2).getAsInt();

        org.bukkit.Material material = org.bukkit.Material.matchMaterial(blockId);
        if (material == null) {
            response.addProperty("response_type", "set_block_response");
            response.addProperty("ok", false);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Unknown block: " + blockId);
            response.add("data", err);
            return;
        }

        org.bukkit.World world = Bukkit.getWorlds().get(0);
        org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
        loc.getBlock().setType(material);

        // TODO: 应用 block_state 和 nbt

        response.addProperty("response_type", "set_block_response");
        response.add("data", new JsonObject());
    }

    private static void handleRegister(JsonObject data, JsonObject response) {
        String eventType = data.get("event_type").getAsString();
        UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
        String pkg = data.has("package_name") ? data.get("package_name").getAsString() : "unknown";
        JsonObject filter = data.has("filter") ? data.getAsJsonObject("filter") : new JsonObject();
        var subscription = data.has("subscription") ? data.getAsJsonArray("subscription") : new com.google.gson.JsonArray();
        boolean proxy = data.has("proxy") && data.get("proxy").getAsBoolean();

        String error = LapisPlugin.getInstance().getEventBridge().register(eventType, pkg, uuid, filter, subscription, proxy);

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

    private static void handleUnregister(JsonObject data, JsonObject response) {
        UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
        LapisPlugin.getInstance().getEventBridge().unregister(uuid);

        response.addProperty("response_type", "unregister_event_listener_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleSendMessage(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String messageType = data.has("message_type") ? data.get("message_type").getAsString() : "pure_text";
        String messageContent = data.get("message_content").getAsString();

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            response.addProperty("response_type", "send_message_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", "Player not found or offline");
            response.add("data", errData);
            return;
        }

        try {
            if ("text_component".equals(messageType)) {
                Component component = GsonComponentSerializer.gson().deserialize(messageContent);
                player.sendMessage(component);
            } else {
                player.sendMessage(Component.text(messageContent));
            }

            response.addProperty("response_type", "send_message_response");
            response.addProperty("ok", true);
            response.add("data", new JsonObject());
        } catch (Exception e) {
            response.addProperty("response_type", "send_message_response");
            response.addProperty("ok", false);
            JsonObject errData = new JsonObject();
            errData.addProperty("error", "Invalid message format: " + e.getMessage());
            response.add("data", errData);
        }
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

    private static void handleSetCustomTag(JsonObject data, JsonObject response) {
        String targetType = data.get("target_type").getAsString();
        UUID targetUuid = UUID.fromString(data.get("target_uuid").getAsString());
        String packageName = data.get("package_name").getAsString();
        String tagName = data.get("tag_name").getAsString();
        String tagValue = data.get("tag_value").getAsString();

        var pdcManager = LapisPlugin.getInstance().getPdcManager();

        if ("player".equals(targetType)) {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player == null || !player.isOnline()) {
                response.addProperty("response_type", "set_custom_tag_response");
                response.addProperty("ok", false);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Player not found or offline");
                response.add("data", err);
                return;
            }
            pdcManager.setTag(player.getPersistentDataContainer(), packageName, tagName, tagValue);
        }

        response.addProperty("response_type", "set_custom_tag_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleRemoveCustomTag(JsonObject data, JsonObject response) {
        String targetType = data.get("target_type").getAsString();
        UUID targetUuid = UUID.fromString(data.get("target_uuid").getAsString());
        String packageName = data.get("package_name").getAsString();
        String tagName = data.get("tag_name").getAsString();

        var pdcManager = LapisPlugin.getInstance().getPdcManager();

        if ("player".equals(targetType)) {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player != null && player.isOnline()) {
                pdcManager.removeTag(player.getPersistentDataContainer(), packageName, tagName);
            }
        }

        response.addProperty("response_type", "remove_custom_tag_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleSetCustomData(JsonObject data, JsonObject response) {
        String targetType = data.get("target_type").getAsString();
        UUID targetUuid = UUID.fromString(data.get("target_uuid").getAsString());
        String packageName = data.get("package_name").getAsString();
        String dataName = data.get("data_name").getAsString();
        String dataValue = data.get("data_value").getAsString();

        var pdcManager = LapisPlugin.getInstance().getPdcManager();

        if ("player".equals(targetType)) {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player == null || !player.isOnline()) {
                response.addProperty("response_type", "set_custom_data_response");
                response.addProperty("ok", false);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Player not found or offline");
                response.add("data", err);
                return;
            }
            pdcManager.setData(player.getPersistentDataContainer(), packageName, dataName, dataValue);
        }

        response.addProperty("response_type", "set_custom_data_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleRemoveCustomData(JsonObject data, JsonObject response) {
        String targetType = data.get("target_type").getAsString();
        UUID targetUuid = UUID.fromString(data.get("target_uuid").getAsString());
        String packageName = data.get("package_name").getAsString();
        String dataName = data.get("data_name").getAsString();

        var pdcManager = LapisPlugin.getInstance().getPdcManager();

        if ("player".equals(targetType)) {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player != null && player.isOnline()) {
                pdcManager.removeData(player.getPersistentDataContainer(), packageName, dataName);
            }
        }

        response.addProperty("response_type", "remove_custom_data_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }
}