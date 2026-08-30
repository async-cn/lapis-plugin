package org.asdf.lapisPlugin.event;

import com.google.gson.JsonObject;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.asdf.lapisPlugin.LapisPlugin;

public class EventSerializer {

    public static JsonObject serialize(String eventType, Event event) {
        return switch (eventType) {
            case "PlayerJoin" -> serializePlayerJoin((PlayerJoinEvent) event);
            case "PlayerQuit" -> serializePlayerQuit((PlayerQuitEvent) event);
            case "BlockBreak" -> serializeBlockBreak((BlockBreakEvent) event);
            case "BlockPlace" -> serializeBlockPlace((BlockPlaceEvent) event);
            case "PlayerChat" -> serializePlayerChat((AsyncPlayerChatEvent) event);
            case "PlayerInteract" -> serializePlayerInteract((org.bukkit.event.player.PlayerInteractEvent) event);
            default -> new JsonObject();
        };
    }

    private static JsonObject serializePlayerJoin(PlayerJoinEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializePlayerQuit(PlayerQuitEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializeBlockBreak(BlockBreakEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.add("block", serializeBlock(e.getBlock()));
        return data;
    }

    private static JsonObject serializeBlockPlace(BlockPlaceEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.add("block", serializeBlock(e.getBlock()));
        data.add("block_against", serializeBlock(e.getBlockAgainst()));
        return data;
    }

    private static JsonObject serializePlayerChat(AsyncPlayerChatEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("message", e.getMessage());
        return data;
    }

    private static JsonObject serializePlayer(Player player) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", player.getUniqueId().toString());
        obj.addProperty("name", player.getName());

        var pdcManager = LapisPlugin.getInstance().getPdcManager();
        var pdc = player.getPersistentDataContainer();

        JsonObject datas = pdcManager.readAllData(pdc);
        if (datas.size() > 0) {
            obj.add("custom_data", datas);
        }

        JsonObject nbt = new JsonObject();
        nbt.addProperty("name", player.getName());
        nbt.addProperty("display_name", player.getDisplayName());
        obj.add("nbt", NbtCollector.collectPlayerNbt(player));
        return obj;
    }

    private static JsonObject serializeBlock(org.bukkit.block.Block block) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", block.getWorld().getName());

        JsonObject pos = new JsonObject();
        pos.addProperty("x", block.getX());
        pos.addProperty("y", block.getY());
        pos.addProperty("z", block.getZ());
        obj.add("pos", pos);

        obj.addProperty("id", block.getType().getKey().toString());

        JsonObject state = new JsonObject();
        String blockDataStr = block.getBlockData().getAsString();
        int bracketStart = blockDataStr.indexOf('[');
        if (bracketStart != -1 && blockDataStr.endsWith("]")) {
            String stateStr = blockDataStr.substring(bracketStart + 1, blockDataStr.length() - 1);
            if (!stateStr.isEmpty()) {
                for (String pair : stateStr.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        state.addProperty(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        }
        obj.add("state", state);

        JsonObject nbt = new JsonObject();
        try {
            BlockState bs = block.getState();
            if (bs instanceof TileState tileState) {
                var pdcManager = LapisPlugin.getInstance().getPdcManager();
                nbt = pdcManager.readAllData(tileState.getPersistentDataContainer());
            }
        } catch (Exception ignored) {}
        obj.add("nbt", NbtCollector.collectBlockNbt(block));

        return obj;
    }

    private static JsonObject serializePlayerInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));

        String interactionType = switch (e.getAction()) {
            case LEFT_CLICK_BLOCK -> "left_click";
            case RIGHT_CLICK_BLOCK -> "right_click";
            case LEFT_CLICK_AIR -> "left_click_air";
            case RIGHT_CLICK_AIR -> "right_click_air";
            case PHYSICAL -> "physical";
        };
        data.addProperty("interaction_type", interactionType);

        if (e.getClickedBlock() != null) {
            data.add("block", serializeBlock(e.getClickedBlock()));
        }

        return data;
    }
}