package org.asdf.lapisPlugin.event;

import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class EventTypeMap {

    private static final Map<String, Class<? extends Event>> MAP = new HashMap<>();

    static {
        register("PlayerJoin", PlayerJoinEvent.class);
        register("PlayerQuit", PlayerQuitEvent.class);
        register("BlockBreak", BlockBreakEvent.class);
        register("BlockPlace", BlockPlaceEvent.class);
        register("PlayerChat", AsyncPlayerChatEvent.class);
        // 以后加事件就这里加一行
    }

    private static void register(String eventType, Class<? extends Event> clazz) {
        MAP.put(eventType, clazz);
    }

    public static Class<? extends Event> get(String eventType) {
        return MAP.get(eventType);
    }

    public static boolean isSupported(String eventType) {
        return MAP.containsKey(eventType);
    }
}