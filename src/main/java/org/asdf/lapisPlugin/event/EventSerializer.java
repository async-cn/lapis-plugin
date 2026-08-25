package org.asdf.lapisPlugin.event;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;

public class EventSerializer {

    public static JsonObject serialize(Event event) {
        if (event instanceof PlayerJoinEvent e) {
            JsonObject data = new JsonObject();
            JsonObject player = new JsonObject();

            player.addProperty("uuid", e.getPlayer().getUniqueId().toString());
            player.addProperty("name", e.getPlayer().getName());
            player.addProperty("display_name", e.getPlayer().getDisplayName());
            player.addProperty("health", e.getPlayer().getHealth());
            player.addProperty("food", e.getPlayer().getFoodLevel());
            player.addProperty("world", e.getPlayer().getWorld().getName());
            player.addProperty("x", e.getPlayer().getLocation().getX());
            player.addProperty("y", e.getPlayer().getLocation().getY());
            player.addProperty("z", e.getPlayer().getLocation().getZ());

            data.add("player", player);
            return data;
        }
        return new JsonObject();
    }
}