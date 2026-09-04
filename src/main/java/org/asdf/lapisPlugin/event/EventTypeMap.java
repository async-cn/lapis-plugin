package org.asdf.lapisPlugin.event;

import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EventTypeMap {

    private static final Map<String, Class<? extends Event>> MAP = new HashMap<>();

    static {
        register("BlockBreak", BlockBreakEvent.class);
        register("BlockPlace", BlockPlaceEvent.class);
        register("BlockBurn", BlockBurnEvent.class);
        register("BlockDropItem", BlockDropItemEvent.class);
        register("BlockExplode", BlockExplodeEvent.class);
        register("BlockFade", BlockFadeEvent.class);
        register("SignChange", SignChangeEvent.class);
        register("TNTPrime", TNTPrimeEvent.class);

        register("ArrowBodyCountChange", ArrowBodyCountChangeEvent.class);
        register("CreatureSpawn", CreatureSpawnEvent.class);
        register("EntityDamage", EntityDamageEvent.class);
        register("EntityDamageByBlock", EntityDamageByBlockEvent.class);
        register("EntityDamageByEntity", EntityDamageByEntityEvent.class);
        register("EntityDeath", EntityDeathEvent.class);
        register("EntityDropItem", EntityDropItemEvent.class);
        register("EntityExplode", EntityExplodeEvent.class);
        register("EntityPickupItem", EntityPickupItemEvent.class);
        register("EntityPotionEffect", EntityPotionEffectEvent.class);
        register("EntityShootBow", EntityShootBowEvent.class);
        register("EntitySpawn", EntitySpawnEvent.class);
        register("EntityTarget", EntityTargetEvent.class);
        register("PlayerDeath", PlayerDeathEvent.class);
        register("PotionSplash", PotionSplashEvent.class);
        register("ProjectileHit", ProjectileHitEvent.class);
        register("ProjectileLaunch", ProjectileLaunchEvent.class);

        register("InventoryAction", InventoryClickEvent.class);
        register("InventoryClick", InventoryClickEvent.class);
        register("InventoryClose", InventoryCloseEvent.class);
        register("InventoryOpen", InventoryOpenEvent.class);

        register("PlayerJoin", PlayerJoinEvent.class);
        register("PlayerQuit", PlayerQuitEvent.class);
        register("PlayerChat", AsyncPlayerChatEvent.class);
        register("PlayerInteract", PlayerInteractEvent.class);
        register("PlayerChangeMainHand", PlayerChangedMainHandEvent.class);
        register("PlayerChangeWorld", PlayerChangedWorldEvent.class);
        register("PlayerCommandPreprocess", PlayerCommandPreprocessEvent.class);
        register("PlayerCommandSend", PlayerCommandSendEvent.class);
        register("PlayerDropItem", PlayerDropItemEvent.class);
        register("PlayerGameModeChange", PlayerGameModeChangeEvent.class);
        register("PlayerInteractAtEntity", PlayerInteractAtEntityEvent.class);
        register("PlayerItemBreak", PlayerItemBreakEvent.class);
        register("PlayerItemConsume", PlayerItemConsumeEvent.class);
        register("PlayerItemHeld", PlayerItemHeldEvent.class);
        register("PlayerMove", PlayerMoveEvent.class);
        register("PlayerRespawn", PlayerRespawnEvent.class);

        register("LightningStrike", LightningStrikeEvent.class);
        register("ThunderChange", ThunderChangeEvent.class);
        register("WeatherChange", WeatherChangeEvent.class);

        register("PortalCreate", PortalCreateEvent.class);
        register("StructureGrow", StructureGrowEvent.class);
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

    public static Set<String> getAllSupported() {
        return Collections.unmodifiableSet(MAP.keySet());
    }
}
