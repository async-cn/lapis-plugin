package org.asdf.lapisPlugin.event;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;

public class EventSerializer {

    public static JsonObject serialize(Event event) {
        if (event instanceof PlayerJoinEvent e) {
            return serializePlayerEvent(e.getPlayer());
        }
        return new JsonObject();
    }

    private static JsonObject serializePlayerEvent(Player player) {
        JsonObject data = new JsonObject();
        JsonObject playerObj = new JsonObject();

        playerObj.addProperty("uuid", player.getUniqueId().toString());

        JsonObject nbt = new JsonObject();
        playerObj.add("nbt", nbt);

        data.add("player", playerObj);
        return data;
    }
}