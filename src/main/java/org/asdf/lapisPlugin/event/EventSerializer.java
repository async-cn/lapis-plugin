package org.asdf.lapisPlugin.event;

import com.google.gson.JsonObject;
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

        var pdcManager = LapisPlugin.getInstance().getPdcManager();
        var pdc = player.getPersistentDataContainer();

        JsonObject tags = pdcManager.readAllTags(pdc);
        if (tags.size() > 0) {
            obj.add("custom_tags", tags);
        }

        JsonObject datas = pdcManager.readAllData(pdc);
        if (datas.size() > 0) {
            obj.add("custom_data", datas);
        }

        JsonObject nbt = new JsonObject();
        nbt.addProperty("name", player.getName());
        nbt.addProperty("display_name", player.getDisplayName());
        obj.add("nbt", nbt);
        return obj;
    }

    private static JsonObject serializeBlock(org.bukkit.block.Block block) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", block.getType().getKey().toString());
        obj.addProperty("x", block.getX());
        obj.addProperty("y", block.getY());
        obj.addProperty("z", block.getZ());
        obj.addProperty("world", block.getWorld().getName());
        JsonObject nbt = new JsonObject();
        obj.add("nbt", nbt);
        return obj;
    }
}