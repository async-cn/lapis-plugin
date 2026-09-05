package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.asdf.lapisPlugin.LapisPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandHandler {

    public static JsonObject handleHandshake(JsonObject cmd) {
        int id = cmd.get("id").getAsInt();
        JsonObject data = cmd.getAsJsonObject("data");
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
                case "give_item" -> handleGiveItem(data, response, packageName);
                case "take_item" -> handleTakeItem(data, response);
                case "set_custom_data" -> handleSetCustomData(data, response);
                case "remove_custom_data" -> handleRemoveCustomData(data, response);
                case "execute_command" -> handleExecuteCommand(data, response);
                case "set_block" -> handleSetBlock(data, response, packageName);
                case "money_query" -> handleMoneyQuery(data, response);
                case "money_give" -> handleMoneyGive(data, response);
                case "money_set" -> handleMoneySet(data, response);
                case "money_take" -> handleMoneyTake(data, response);
                case "get_block" -> handleGetBlock(data, response);
                case "get_entity" -> handleGetEntity(data, response);
                case "show_title" -> handleShowTitle(data, response);
                case "play_sound" -> handlePlaySound(data, response);
                case "play_sound_private" -> handlePlaySoundPrivate(data, response);
                case "actionbar_set" -> handleActionBarSet(data, response);
                case "actionbar_clear" -> handleActionBarClear(data, response);
                case "ask_input" -> handleAskInput(data, response);
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


    private record PdcTarget(PersistentDataContainer pdc, TileState tileState) {
        void update() {
            if (tileState != null) tileState.update();
        }
    }

    private static void errorResponse(JsonObject response, String responseType, String error) {
        response.addProperty("response_type", responseType);
        response.addProperty("ok", false);
        JsonObject err = new JsonObject();
        err.addProperty("error", error);
        response.add("data", err);
    }

    private static PdcTarget resolveTarget(JsonObject data, JsonObject response, String responseType) {
        String targetType = data.get("target_type").getAsString();
        UUID targetUuid = UUID.fromString(data.get("target_uuid").getAsString());

        switch (targetType) {
            case "player" -> {
                Player player = Bukkit.getPlayer(targetUuid);
                if (player == null || !player.isOnline()) {
                    errorResponse(response, responseType, "Player not found or offline");
                    return null;
                }
                return new PdcTarget(player.getPersistentDataContainer(), null);
            }
            case "entity" -> {
                Entity entity = Bukkit.getEntity(targetUuid);
                if (entity == null) {
                    errorResponse(response, responseType, "Entity not found");
                    return null;
                }
                return new PdcTarget(entity.getPersistentDataContainer(), null);
            }
            case "block" -> {
                String worldName = data.has("world") ? data.get("world").getAsString() : null;
                JsonArray pos = data.has("pos") ? data.getAsJsonArray("pos") : null;
                if (pos == null || pos.size() < 3) {
                    errorResponse(response, responseType, "Block position required [x,y,z]");
                    return null;
                }
                World world = worldName != null ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);
                if (world == null) {
                    errorResponse(response, responseType, "Unknown world");
                    return null;
                }
                Block block = world.getBlockAt(
                        pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()
                );
                BlockState state = block.getState();
                if (!(state instanceof TileState tileState)) {
                    errorResponse(response, responseType, "Block does not support data (not a tile entity)");
                    return null;
                }
                return new PdcTarget(tileState.getPersistentDataContainer(), tileState);
            }
            default -> {
                errorResponse(response, responseType, "Unknown target_type: " + targetType);
                return null;
            }
        }
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

    private static void handleSetBlock(JsonObject data, JsonObject response, String packageName) {
        String blockId = data.get("block_id").getAsString();
        JsonObject posObj = data.getAsJsonObject("pos");
        int x = posObj.get("x").getAsInt();
        int y = posObj.get("y").getAsInt();
        int z = posObj.get("z").getAsInt();
        String worldName = data.has("world") ? data.get("world").getAsString() : null;

        String blockStateStr = null;
        if (data.has("block_state")) {
            JsonElement bsElem = data.get("block_state");
            if (bsElem.isJsonObject()) {
                JsonObject bsObj = bsElem.getAsJsonObject();
                if (bsObj.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (var entry : bsObj.entrySet()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(entry.getKey()).append("=").append(entry.getValue().getAsString());
                    }
                    blockStateStr = sb.toString();
                }
            } else if (!bsElem.isJsonNull()) {
                blockStateStr = bsElem.getAsString();
            }
        }

        JsonObject nbt = null;
        if (data.has("nbt")) {
            JsonElement nbtElem = data.get("nbt");
            if (nbtElem.isJsonObject() && !nbtElem.getAsJsonObject().isEmpty()) {
                nbt = nbtElem.getAsJsonObject();
            }
        }

        World world = worldName != null ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);
        if (world == null) {
            errorResponse(response, "set_block_response", "Unknown world: " + worldName);
            return;
        }

        Material material = Material.matchMaterial(blockId);
        if (material == null) {
            errorResponse(response, "set_block_response", "Unknown block: " + blockId);
            return;
        }

        Block block = world.getBlockAt(x, y, z);

        if (blockStateStr != null && !blockStateStr.isEmpty()) {
            try {
                org.bukkit.block.data.BlockData bd = Bukkit.createBlockData(blockId + "[" + blockStateStr + "]");
                block.setBlockData(bd);
            } catch (IllegalArgumentException e) {
                errorResponse(response, "set_block_response", "Invalid block_state: " + e.getMessage());
                return;
            }
        } else {
            block.setType(material);
        }

        if (nbt != null && !nbt.isEmpty()) {
            BlockState state = block.getState();
            if (state instanceof TileState tileState) {
                var pdc = tileState.getPersistentDataContainer();
                var pdcManager = LapisPlugin.getInstance().getPdcManager();
                for (var entry : nbt.entrySet()) {
                    pdcManager.setData(pdc, packageName, entry.getKey(), entry.getValue().toString());
                }
                tileState.update();
            }
        }

        response.addProperty("response_type", "set_block_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleRegister(JsonObject data, JsonObject response) {
        String eventType = data.get("event_type").getAsString();
        UUID uuid = UUID.fromString(data.get("listener_uuid").getAsString());
        String pkg = data.has("package_name") ? data.get("package_name").getAsString() : "unknown";
        JsonObject filter = data.has("filter") ? data.getAsJsonObject("filter") : new JsonObject();
        var subscription = data.has("subscription") ? data.getAsJsonArray("subscription") : new JsonArray();
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
            errorResponse(response, "send_message_response", "Player not found or offline");
            return;
        }

        try {
            if ("text_component".equals(messageType)) {
                Component component = GsonComponentSerializer.gson().deserialize(messageContent);
                player.sendMessage(component);
            } else if ("mini_message".equals(messageType)) {
                // 解析 MiniMessage 并发送
                Component component = MiniMessage.miniMessage().deserialize(messageContent);
                player.sendMessage(component);
            } else {
                player.sendMessage(Component.text(messageContent));
            }

            response.addProperty("response_type", "send_message_response");
            response.addProperty("ok", true);
            response.add("data", new JsonObject());
        } catch (Exception e) {
            errorResponse(response, "send_message_response", "Invalid message format: " + e.getMessage());
        }
    }

    private static void handleGiveItem(JsonObject data, JsonObject response, String packageName) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String itemId = data.get("item_id").getAsString();
        int count = data.has("count") ? data.get("count").getAsInt() : 1;
        String displayName = data.has("display_name") ? data.get("display_name").getAsString() : null;
        JsonArray loreArray = data.has("lore") ? data.getAsJsonArray("lore") : null;
        JsonObject nbt = data.has("nbt") ? data.getAsJsonObject("nbt") : null;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            errorResponse(response, "give_item_response", "Player not found or offline");
            return;
        }

        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            errorResponse(response, "give_item_response", "Unknown item: " + itemId);
            return;
        }

        ItemStack item = new ItemStack(material, count);

        item.editMeta(meta -> {
            if (displayName != null) {
                meta.displayName(GsonComponentSerializer.gson().deserialize(displayName));
            }
            if (loreArray != null) {
                List<Component> lore = new ArrayList<>();
                for (var elem : loreArray) {
                    lore.add(GsonComponentSerializer.gson().deserialize(elem.getAsString()));
                }
                meta.lore(lore);
            }
            if (nbt != null) {
                var pdc = meta.getPersistentDataContainer();
                var pdcManager = LapisPlugin.getInstance().getPdcManager();
                for (var entry : nbt.entrySet()) {
                    pdcManager.setData(pdc, packageName, entry.getKey(), entry.getValue().toString());
                }
            }
        });

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
            errorResponse(response, "take_item_response", "Player not found or offline");
            return;
        }

        Material material = Material.matchMaterial(itemId);
        boolean success = false;

        if (material != null) {
            int remaining = count;
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item == null || item.getType() != material) continue;

                int amount = item.getAmount();
                if (amount <= remaining) {
                    remaining -= amount;
                    contents[i] = null;
                } else {
                    item.setAmount(amount - remaining);
                    remaining = 0;
                }
            }
            player.getInventory().setStorageContents(contents);
            success = remaining == 0;
        }

        response.addProperty("response_type", "take_item_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("is_success", success);
        response.add("data", respData);
    }

    private static void handleSetCustomData(JsonObject data, JsonObject response) {
        String packageName = data.get("package_name").getAsString();
        String dataKey = data.get("data_key").getAsString();
        JsonElement dataValue = data.get("data_value");

        PdcTarget target = resolveTarget(data, response, "set_custom_data_response");
        if (target == null) return;

        LapisPlugin.getInstance().getPdcManager().setData(target.pdc(), packageName, dataKey, dataValue.toString());
        target.update();

        response.addProperty("response_type", "set_custom_data_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleRemoveCustomData(JsonObject data, JsonObject response) {
        String packageName = data.get("package_name").getAsString();
        String dataKey = data.get("data_key").getAsString();

        PdcTarget target = resolveTarget(data, response, "remove_custom_data_response");
        if (target == null) return;

        LapisPlugin.getInstance().getPdcManager().removeData(target.pdc(), packageName, dataKey);
        target.update();

        response.addProperty("response_type", "remove_custom_data_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }


    private static void handleMoneyQuery(JsonObject data, JsonObject response) {
        if (!LapisPlugin.getInstance().hasEconomy()) {
            errorResponse(response, "money_query_response", "Economy system not available");
            return;
        }

        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

        double balance = LapisPlugin.getInstance().getEconomy().getBalance(offlinePlayer);

        response.addProperty("response_type", "money_query_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("balance", balance);
        response.add("data", respData);
    }

    private static void handleMoneyGive(JsonObject data, JsonObject response) {
        if (!LapisPlugin.getInstance().hasEconomy()) {
            errorResponse(response, "money_give_response", "Economy system not available");
            return;
        }

        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        double amount = data.get("amount").getAsDouble();
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

        var economy = LapisPlugin.getInstance().getEconomy();
        economy.depositPlayer(offlinePlayer, amount);

        response.addProperty("response_type", "money_give_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("new_balance", economy.getBalance(offlinePlayer));
        response.add("data", respData);
    }

    private static void handleMoneySet(JsonObject data, JsonObject response) {
        if (!LapisPlugin.getInstance().hasEconomy()) {
            errorResponse(response, "money_set_response", "Economy system not available");
            return;
        }

        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        double amount = data.get("amount").getAsDouble();
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

        var economy = LapisPlugin.getInstance().getEconomy();
        double current = economy.getBalance(offlinePlayer);
        if (amount > current) {
            economy.depositPlayer(offlinePlayer, amount - current);
        } else if (amount < current) {
            economy.withdrawPlayer(offlinePlayer, current - amount);
        }

        response.addProperty("response_type", "money_set_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("new_balance", economy.getBalance(offlinePlayer));
        response.add("data", respData);
    }

    private static void handleMoneyTake(JsonObject data, JsonObject response) {
        if (!LapisPlugin.getInstance().hasEconomy()) {
            errorResponse(response, "money_take_response", "Economy system not available");
            return;
        }

        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        double amount = data.get("amount").getAsDouble();
        boolean force = data.has("force") && data.get("force").getAsBoolean();
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

        var economy = LapisPlugin.getInstance().getEconomy();
        double current = economy.getBalance(offlinePlayer);
        boolean success;

        if (force) {
            economy.withdrawPlayer(offlinePlayer, amount);
            success = true;
        } else {
            if (current >= amount) {
                economy.withdrawPlayer(offlinePlayer, amount);
                success = true;
            } else {
                success = false;
            }
        }

        response.addProperty("response_type", "money_take_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.addProperty("is_success", success);
        respData.addProperty("new_balance", economy.getBalance(offlinePlayer));
        response.add("data", respData);
    }

    private static void handleAskInput(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        boolean prompt = data.has("prompt") && data.get("prompt").getAsBoolean();
        String messageType = data.has("message_type") ? data.get("message_type").getAsString() : "pure_text";
        String messageContent = data.has("message_content") ? data.get("message_content").getAsString() : "";
        double timeout = data.has("timeout") ? data.get("timeout").getAsDouble() : -1;

        JsonObject result = LapisPlugin.getInstance().getAskInputManager().ask(
                playerUuid, prompt, messageType, messageContent, timeout
        );

        response.addProperty("response_type", "ask_input_response");
        response.addProperty("ok", true);
        response.add("data", result);
    }

    private static void handleGetBlock(JsonObject data, JsonObject response) {
        String worldName = data.has("world") ? data.get("world").getAsString() : null;
        JsonObject posObj = data.getAsJsonObject("pos");
        int x = posObj.get("x").getAsInt();
        int y = posObj.get("y").getAsInt();
        int z = posObj.get("z").getAsInt();


        World world = worldName != null ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);
        if (world == null) {
            errorResponse(response, "get_block_response", "Unknown world: " + worldName);
            return;
        }

        Block block = world.getBlockAt(x, y, z);
        String blockId = block.getType().getKey().toString();

        JsonObject blockState = new JsonObject();
        String blockDataStr = block.getBlockData().getAsString();
        int bracketStart = blockDataStr.indexOf('[');
        if (bracketStart != -1 && blockDataStr.endsWith("]")) {
            String stateStr = blockDataStr.substring(bracketStart + 1, blockDataStr.length() - 1);
            if (!stateStr.isEmpty()) {
                for (String pair : stateStr.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        blockState.addProperty(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        }

        JsonObject nbt = new JsonObject();
        StringBuilder nbtOutput = new StringBuilder();
        org.bukkit.command.CommandSender nbtSender = Bukkit.createCommandSender(component -> {
            nbtOutput.append(PlainTextComponentSerializer.plainText().serialize(component));
        });

        String cmd = String.format("data get block %d %d %d", x, y, z);
        Bukkit.dispatchCommand(nbtSender, cmd);

        String nbtStr = nbtOutput.toString().trim();
        int braceStart = nbtStr.indexOf('{');
        if (braceStart != -1) {
            String jsonPart = nbtStr.substring(braceStart);
            try {
                nbt = JsonParser.parseString(jsonPart).getAsJsonObject();
            } catch (Exception e) {
                nbt.addProperty("_raw", jsonPart);
            }
        }

        response.addProperty("response_type", "get_block_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        JsonObject blockObj = new JsonObject();
        blockObj.addProperty("id", blockId);
        blockObj.addProperty("world", world.getName());

        JsonObject pos = new JsonObject();
        pos.addProperty("x", block.getX());
        pos.addProperty("y", block.getY());
        pos.addProperty("z", block.getZ());
        blockObj.add("pos", pos);

        blockObj.add("state", blockState);
        blockObj.add("nbt", nbt);
        respData.add("block", blockObj);
        response.add("data", respData);
    }

    private static void handleGetEntity(JsonObject data, JsonObject response) {
        UUID entityUuid = UUID.fromString(data.get("uuid").getAsString());
        Entity entity = Bukkit.getEntity(entityUuid);

        if (entity == null) {
            errorResponse(response, "get_entity_response", "Entity not found");
            return;
        }

        JsonObject nbt = new JsonObject();
        nbt.addProperty("type", entity.getType().getKey().toString());
        nbt.addProperty("uuid", entity.getUniqueId().toString());
        nbt.addProperty("name", entity.getName());
        if (entity.customName() != null) {
            nbt.addProperty("custom_name", entity.customName().toString());
        }
        nbt.addProperty("world", entity.getWorld().getName());
        nbt.addProperty("x", entity.getLocation().getX());
        nbt.addProperty("y", entity.getLocation().getY());
        nbt.addProperty("z", entity.getLocation().getZ());

        if (entity instanceof LivingEntity living) {
            nbt.addProperty("health", living.getHealth());
            nbt.addProperty("max_health", living.getMaxHealth());
        }

        StringBuilder nbtOutput = new StringBuilder();
        org.bukkit.command.CommandSender nbtSender = Bukkit.createCommandSender(component -> {
            nbtOutput.append(PlainTextComponentSerializer.plainText().serialize(component));
        });

        String cmd = String.format("data get entity %s", entityUuid.toString());
        Bukkit.dispatchCommand(nbtSender, cmd);

        String nbtStr = nbtOutput.toString().trim();
        int braceStart = nbtStr.indexOf('{');
        if (braceStart != -1) {
            String jsonPart = nbtStr.substring(braceStart);
            try {
                JsonObject fullNbt = JsonParser.parseString(jsonPart).getAsJsonObject();
                nbt.add("_full", fullNbt);
            } catch (Exception e) {
                nbt.addProperty("_full_raw", jsonPart);
            }
        }

        response.addProperty("response_type", "get_entity_response");
        response.addProperty("ok", true);
        JsonObject respData = new JsonObject();
        respData.add("entity", nbt);
        response.add("data", respData);
    }

    private static void handleShowTitle(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            errorResponse(response, "show_title_response", "Player not found or offline");
            return;
        }

        String titleType = data.has("title_type") ? data.get("title_type").getAsString() : "pure_text";
        String titleContent = data.get("title").getAsString();
        String subtitleType = data.has("subtitle_type") ? data.get("subtitle_type").getAsString() : "pure_text";
        String subtitleContent = data.has("subtitle") ? data.get("subtitle").getAsString() : "";
        int fadeIn = data.has("fade_in") ? data.get("fade_in").getAsInt() : 10;
        int stay = data.has("stay") ? data.get("stay").getAsInt() : 70;
        int fadeOut = data.has("fade_out") ? data.get("fade_out").getAsInt() : 20;

        try {
            Component title = "text_component".equals(titleType)
                    ? GsonComponentSerializer.gson().deserialize(titleContent)
                    : Component.text(titleContent);

            Component subtitle = "text_component".equals(subtitleType) && !subtitleContent.isEmpty()
                    ? GsonComponentSerializer.gson().deserialize(subtitleContent)
                    : (subtitleContent.isEmpty() ? Component.empty() : Component.text(subtitleContent));

            player.showTitle(net.kyori.adventure.title.Title.title(
                    title,
                    subtitle,
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(fadeIn * 50L),
                            java.time.Duration.ofMillis(stay * 50L),
                            java.time.Duration.ofMillis(fadeOut * 50L)
                    )
            ));

            response.addProperty("response_type", "show_title_response");
            response.addProperty("ok", true);
            response.add("data", new JsonObject());
        } catch (Exception e) {
            errorResponse(response, "show_title_response", "Invalid title format: " + e.getMessage());
        }
    }

    private static void handlePlaySound(JsonObject data, JsonObject response) {
        String worldName = data.has("world") ? data.get("world").getAsString() : null;
        double x = data.get("x").getAsDouble();
        double y = data.get("y").getAsDouble();
        double z = data.get("z").getAsDouble();
        String sound = data.get("sound").getAsString();
        String categoryStr = data.has("category") ? data.get("category").getAsString() : "master";
        float volume = data.has("volume") ? data.get("volume").getAsFloat() : 1.0f;
        float pitch = data.has("pitch") ? data.get("pitch").getAsFloat() : 1.0f;

        World world = worldName != null ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);
        if (world == null) {
            errorResponse(response, "play_sound_response", "Unknown world: " + worldName);
            return;
        }

        org.bukkit.SoundCategory category;
        try {
            category = org.bukkit.SoundCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            category = org.bukkit.SoundCategory.MASTER;
        }

        Location location = new Location(world, x, y, z);
        world.playSound(location, sound, category, volume, pitch);

        response.addProperty("response_type", "play_sound_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handlePlaySoundPrivate(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String sound = data.get("sound").getAsString();
        String categoryStr = data.has("category") ? data.get("category").getAsString() : "master";
        float volume = data.has("volume") ? data.get("volume").getAsFloat() : 1.0f;
        float pitch = data.has("pitch") ? data.get("pitch").getAsFloat() : 1.0f;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            errorResponse(response, "play_sound_private_response", "Player not found or offline");
            return;
        }

        org.bukkit.SoundCategory category;
        try {
            category = org.bukkit.SoundCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            category = org.bukkit.SoundCategory.MASTER;
        }

        player.playSound(player.getLocation(), sound, category, volume, pitch);

        response.addProperty("response_type", "play_sound_private_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }

    private static void handleActionBarSet(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        String messageType = data.has("message_type") ? data.get("message_type").getAsString() : "pure_text";
        String messageContent = data.get("message_content").getAsString();

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            errorResponse(response, "actionbar_set_response", "Player not found or offline");
            return;
        }

        try {
            if ("text_component".equals(messageType)) {
                Component component = GsonComponentSerializer.gson().deserialize(messageContent);
                player.sendActionBar(component);
            } else {
                player.sendActionBar(Component.text(messageContent));
            }

            response.addProperty("response_type", "actionbar_set_response");
            response.addProperty("ok", true);
            response.add("data", new JsonObject());
        } catch (Exception e) {
            errorResponse(response, "actionbar_set_response", "Invalid message format: " + e.getMessage());
        }
    }

    private static void handleActionBarClear(JsonObject data, JsonObject response) {
        UUID playerUuid = UUID.fromString(data.get("player_uuid").getAsString());
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            errorResponse(response, "actionbar_clear_response", "Player not found or offline");
            return;
        }

        player.sendActionBar(Component.empty());

        response.addProperty("response_type", "actionbar_clear_response");
        response.addProperty("ok", true);
        response.add("data", new JsonObject());
    }
}