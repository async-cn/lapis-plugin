package org.asdf.lapisPlugin.event;

import com.google.gson.JsonObject;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.TileState;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.asdf.lapisPlugin.LapisPlugin;

@SuppressWarnings("deprecation")
public class EventSerializer {

    public static JsonObject serialize(String eventType, Event event) {
        return switch (eventType) {
            case "BlockBreak" -> serializeBlockBreak((BlockBreakEvent) event);
            case "BlockPlace" -> serializeBlockPlace((BlockPlaceEvent) event);
            case "BlockBurn" -> serializeBlockBurn((BlockBurnEvent) event);
            case "BlockDropItem" -> serializeBlockDropItem((BlockDropItemEvent) event);
            case "BlockExplode" -> serializeBlockExplode((BlockExplodeEvent) event);
            case "BlockFade" -> serializeBlockFade((BlockFadeEvent) event);
            case "SignChange" -> serializeSignChange((SignChangeEvent) event);
            case "TNTPrime" -> serializeTNTPrime((TNTPrimeEvent) event);

            case "ArrowBodyCountChange" -> serializeArrowBodyCountChange((ArrowBodyCountChangeEvent) event);
            case "CreatureSpawn" -> serializeCreatureSpawn((CreatureSpawnEvent) event);
            case "EntityDamage" -> serializeEntityDamage((EntityDamageEvent) event);
            case "EntityDamageByBlock" -> serializeEntityDamageByBlock((EntityDamageByBlockEvent) event);
            case "EntityDamageByEntity" -> serializeEntityDamageByEntity((EntityDamageByEntityEvent) event);
            case "EntityDeath" -> serializeEntityDeath((EntityDeathEvent) event);
            case "EntityDropItem" -> serializeEntityDropItem((EntityDropItemEvent) event);
            case "EntityExplode" -> serializeEntityExplode((EntityExplodeEvent) event);
            case "EntityPickupItem" -> serializeEntityPickupItem((EntityPickupItemEvent) event);
            case "EntityPotionEffect" -> serializeEntityPotionEffect((EntityPotionEffectEvent) event);
            case "EntityShootBow" -> serializeEntityShootBow((EntityShootBowEvent) event);
            case "EntitySpawn" -> serializeEntitySpawn((EntitySpawnEvent) event);
            case "EntityTarget" -> serializeEntityTarget((EntityTargetEvent) event);
            case "PlayerDeath" -> serializePlayerDeath((PlayerDeathEvent) event);
            case "PotionSplash" -> serializePotionSplash((PotionSplashEvent) event);
            case "ProjectileHit" -> serializeProjectileHit((ProjectileHitEvent) event);
            case "ProjectileLaunch" -> serializeProjectileLaunch((ProjectileLaunchEvent) event);

            case "InventoryAction" -> serializeInventoryAction((InventoryClickEvent) event);
            case "InventoryClick" -> serializeInventoryClick((InventoryClickEvent) event);
            case "InventoryClose" -> serializeInventoryClose((InventoryCloseEvent) event);
            case "InventoryOpen" -> serializeInventoryOpen((InventoryOpenEvent) event);

            case "PlayerJoin" -> serializePlayerJoin((PlayerJoinEvent) event);
            case "PlayerQuit" -> serializePlayerQuit((PlayerQuitEvent) event);
            case "PlayerChat" -> serializePlayerChat((AsyncPlayerChatEvent) event);
            case "PlayerInteract" -> serializePlayerInteract((PlayerInteractEvent) event);
            case "PlayerChangeMainHand" -> serializePlayerChangeMainHand((PlayerChangedMainHandEvent) event);
            case "PlayerChangeWorld" -> serializePlayerChangeWorld((PlayerChangedWorldEvent) event);
            case "PlayerCommandPreprocess" -> serializePlayerCommandPreprocess((PlayerCommandPreprocessEvent) event);
            case "PlayerCommandSend" -> serializePlayerCommandSend((PlayerCommandSendEvent) event);
            case "PlayerDropItem" -> serializePlayerDropItem((PlayerDropItemEvent) event);
            case "PlayerGameModeChange" -> serializePlayerGameModeChange((PlayerGameModeChangeEvent) event);
            case "PlayerInteractAtEntity" -> serializePlayerInteractAtEntity((PlayerInteractAtEntityEvent) event);
            case "PlayerItemBreak" -> serializePlayerItemBreak((PlayerItemBreakEvent) event);
            case "PlayerItemConsume" -> serializePlayerItemConsume((PlayerItemConsumeEvent) event);
            case "PlayerItemHeld" -> serializePlayerItemHeld((PlayerItemHeldEvent) event);
            case "PlayerMove" -> serializePlayerMove((PlayerMoveEvent) event);
            case "PlayerRespawn" -> serializePlayerRespawn((PlayerRespawnEvent) event);

            case "LightningStrike" -> serializeLightningStrike((LightningStrikeEvent) event);
            case "ThunderChange" -> serializeThunderChange((ThunderChangeEvent) event);
            case "WeatherChange" -> serializeWeatherChange((WeatherChangeEvent) event);

            case "PortalCreate" -> serializePortalCreate((PortalCreateEvent) event);
            case "StructureGrow" -> serializeStructureGrow((StructureGrowEvent) event);

            default -> new JsonObject();
        };
    }

    private static JsonObject serializeEntity(Entity entity) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", entity.getUniqueId().toString());
        obj.addProperty("type", entity.getType().toString());
        obj.addProperty("world", entity.getWorld().getName());
        obj.addProperty("x", entity.getLocation().getX());
        obj.addProperty("y", entity.getLocation().getY());
        obj.addProperty("z", entity.getLocation().getZ());
        obj.add("nbt", NbtCollector.collectEntityNbt(entity));
        return obj;
    }

    private static JsonObject serializeLivingEntity(LivingEntity entity) {
        JsonObject obj = serializeEntity(entity);
        obj.addProperty("health", entity.getHealth());
        obj.addProperty("max_health", entity.getAttribute(Attribute.MAX_HEALTH) != null
                ? entity.getAttribute(Attribute.MAX_HEALTH).getValue()
                : entity.getHealth());
        return obj;
    }

    private static JsonObject serializePlayer(Player player) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", player.getUniqueId().toString());
        obj.addProperty("name", player.getName());
        obj.addProperty("display_name", player.getDisplayName());

        var pdcManager = LapisPlugin.getInstance().getPdcManager();
        var pdc = player.getPersistentDataContainer();

        JsonObject tags = pdcManager.readAllTags(pdc);
        if (!tags.isEmpty()) obj.add("custom_tags", tags);

        JsonObject datas = pdcManager.readAllData(pdc);
        if (!datas.isEmpty()) obj.add("custom_data", datas);

        obj.add("nbt", NbtCollector.collectPlayerNbt(player));
        return obj;
    }

    private static JsonObject serializeBlock(org.bukkit.block.Block block) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", block.getType().getKey().toString());
        obj.addProperty("x", block.getX());
        obj.addProperty("y", block.getY());
        obj.addProperty("z", block.getZ());
        obj.addProperty("world", block.getWorld().getName());
        obj.add("nbt", NbtCollector.collectBlockNbt(block));
        if (block.getState() instanceof TileState tileState) {
            JsonObject custom = LapisPlugin.getInstance().getPdcManager().readAllData(tileState.getPersistentDataContainer());
            if (!custom.isEmpty()) obj.add("custom_data", custom);
        }
        return obj;
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

    private static JsonObject serializeBlockBurn(BlockBurnEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        if (e.getIgnitingBlock() != null) {
            data.add("igniting_block", serializeBlock(e.getIgnitingBlock()));
        }
        return data;
    }

    private static JsonObject serializeBlockDropItem(BlockDropItemEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        if (e.getPlayer() != null) data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializeBlockExplode(BlockExplodeEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        data.addProperty("yield", e.getYield());
        return data;
    }

    private static JsonObject serializeBlockFade(BlockFadeEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        data.addProperty("new_state", e.getNewState().getType().getKey().toString());
        return data;
    }

    private static JsonObject serializeSignChange(SignChangeEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializeTNTPrime(TNTPrimeEvent e) {
        JsonObject data = new JsonObject();
        data.add("block", serializeBlock(e.getBlock()));
        if (e.getPrimingEntity() != null) data.add("priming_entity", serializeEntity(e.getPrimingEntity()));
        return data;
    }

    private static JsonObject serializeArrowBodyCountChange(ArrowBodyCountChangeEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity(e.getEntity()));
        data.addProperty("old_amount", e.getOldAmount());
        data.addProperty("new_amount", e.getNewAmount());
        return data;
    }

    private static JsonObject serializeCreatureSpawn(CreatureSpawnEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity(e.getEntity()));
        data.addProperty("spawn_reason", e.getSpawnReason().toString());
        return data;
    }

    private static JsonObject serializeEntityDamage(EntityDamageEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity((LivingEntity) e.getEntity()));
        data.addProperty("damage_cause", e.getCause().toString());
        data.addProperty("damage", e.getDamage());
        data.addProperty("final_damage", e.getFinalDamage());
        return data;
    }

    private static JsonObject serializeEntityDamageByBlock(EntityDamageByBlockEvent e) {
        JsonObject data = serializeEntityDamage(e);
        if (e.getDamager() != null) data.add("block", serializeBlock(e.getDamager()));
        return data;
    }

    private static JsonObject serializeEntityDamageByEntity(EntityDamageByEntityEvent e) {
        JsonObject data = serializeEntityDamage(e);
        data.add("entity_another", serializeEntity(e.getDamager()));
        return data;
    }

    private static JsonObject serializeEntityDeath(EntityDeathEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity(e.getEntity()));
        data.addProperty("dropped_exp", e.getDroppedExp());
        return data;
    }

    private static JsonObject serializeEntityDropItem(EntityDropItemEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        return data;
    }

    private static JsonObject serializeEntityExplode(EntityExplodeEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        data.addProperty("yield", e.getYield());
        return data;
    }

    private static JsonObject serializeEntityPickupItem(EntityPickupItemEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity(e.getEntity()));
        return data;
    }

    private static JsonObject serializeEntityPotionEffect(EntityPotionEffectEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity((LivingEntity) e.getEntity()));
        data.addProperty("action", e.getAction().toString());
        data.addProperty("cause", e.getCause().toString());
        return data;
    }

    private static JsonObject serializeEntityShootBow(EntityShootBowEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity(e.getEntity()));
        data.add("projectile", serializeEntity(e.getProjectile()));
        return data;
    }

    private static JsonObject serializeEntitySpawn(EntitySpawnEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        data.addProperty("location_world", e.getLocation().getWorld().getName());
        data.addProperty("location_x", e.getLocation().getX());
        data.addProperty("location_y", e.getLocation().getY());
        data.addProperty("location_z", e.getLocation().getZ());
        return data;
    }

    private static JsonObject serializeEntityTarget(EntityTargetEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeLivingEntity((LivingEntity) e.getEntity()));
        if (e.getTarget() != null) data.add("target", serializeEntity(e.getTarget()));
        data.addProperty("reason", e.getReason().toString());
        return data;
    }

    private static JsonObject serializePlayerDeath(PlayerDeathEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getEntity()));
        if (e.deathMessage() != null) {
            data.addProperty("death_message", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.deathMessage()));
        }
        data.addProperty("dropped_exp", e.getDroppedExp());
        data.addProperty("new_level", e.getNewLevel());
        data.addProperty("new_total_exp", e.getNewTotalExp());
        return data;
    }

    private static JsonObject serializePotionSplash(PotionSplashEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        data.addProperty("affected_entities_count", e.getAffectedEntities().size());
        return data;
    }

    private static JsonObject serializeProjectileHit(ProjectileHitEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        if (e.getHitBlock() != null) data.add("hit_block", serializeBlock(e.getHitBlock()));
        if (e.getHitEntity() != null) data.add("hit_entity", serializeEntity(e.getHitEntity()));
        return data;
    }

    private static JsonObject serializeProjectileLaunch(ProjectileLaunchEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getEntity()));
        return data;
    }

    private static JsonObject serializeInventoryAction(InventoryClickEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer((Player) e.getWhoClicked()));
        data.addProperty("action", e.getAction().toString());
        data.addProperty("click", e.getClick().toString());
        data.addProperty("slot", e.getSlot());
        return data;
    }

    private static JsonObject serializeInventoryClick(InventoryClickEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer((Player) e.getWhoClicked()));
        data.addProperty("action", e.getAction().toString());
        data.addProperty("click", e.getClick().toString());
        data.addProperty("slot", e.getSlot());
        data.addProperty("inventory_type", e.getInventory().getType().toString());
        return data;
    }

    private static JsonObject serializeInventoryClose(InventoryCloseEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer((Player) e.getPlayer()));
        data.addProperty("inventory_type", e.getInventory().getType().toString());
        return data;
    }

    private static JsonObject serializeInventoryOpen(InventoryOpenEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer((Player) e.getPlayer()));
        data.addProperty("inventory_type", e.getInventory().getType().toString());
        return data;
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

    private static JsonObject serializePlayerChat(AsyncPlayerChatEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("message", e.getMessage());
        return data;
    }

    private static JsonObject serializePlayerInteract(PlayerInteractEvent e) {
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
        if (e.getClickedBlock() != null) data.add("block", serializeBlock(e.getClickedBlock()));
        return data;
    }

    private static JsonObject serializePlayerChangeMainHand(PlayerChangedMainHandEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("main_hand", e.getNewMainHand().toString());
        return data;
    }

    private static JsonObject serializePlayerChangeWorld(PlayerChangedWorldEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("from_world", e.getFrom().getName());
        data.addProperty("to_world", e.getPlayer().getWorld().getName());
        return data;
    }

    private static JsonObject serializePlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("message", e.getMessage());
        return data;
    }

    private static JsonObject serializePlayerCommandSend(PlayerCommandSendEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializePlayerDropItem(PlayerDropItemEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializePlayerGameModeChange(PlayerGameModeChangeEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("new_game_mode", e.getNewGameMode().toString());
        return data;
    }

    private static JsonObject serializePlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.add("entity", serializeEntity(e.getRightClicked()));
        return data;
    }

    private static JsonObject serializePlayerItemBreak(PlayerItemBreakEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializePlayerItemConsume(PlayerItemConsumeEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        return data;
    }

    private static JsonObject serializePlayerItemHeld(PlayerItemHeldEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("previous_slot", e.getPreviousSlot());
        data.addProperty("new_slot", e.getNewSlot());
        return data;
    }

    private static JsonObject serializePlayerMove(PlayerMoveEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("from_x", e.getFrom().getX());
        data.addProperty("from_y", e.getFrom().getY());
        data.addProperty("from_z", e.getFrom().getZ());
        data.addProperty("to_x", e.getTo().getX());
        data.addProperty("to_y", e.getTo().getY());
        data.addProperty("to_z", e.getTo().getZ());
        return data;
    }

    private static JsonObject serializePlayerRespawn(PlayerRespawnEvent e) {
        JsonObject data = new JsonObject();
        data.add("player", serializePlayer(e.getPlayer()));
        data.addProperty("respawn_world", e.getRespawnLocation().getWorld().getName());
        data.addProperty("respawn_x", e.getRespawnLocation().getX());
        data.addProperty("respawn_y", e.getRespawnLocation().getY());
        data.addProperty("respawn_z", e.getRespawnLocation().getZ());
        data.addProperty("is_bed_spawn", e.isBedSpawn());
        data.addProperty("is_anchor_spawn", e.isAnchorSpawn());
        return data;
    }

    private static JsonObject serializeLightningStrike(LightningStrikeEvent e) {
        JsonObject data = new JsonObject();
        data.add("entity", serializeEntity(e.getLightning()));
        data.addProperty("cause", e.getCause().toString());
        return data;
    }

    private static JsonObject serializeThunderChange(ThunderChangeEvent e) {
        JsonObject data = new JsonObject();
        data.addProperty("world", e.getWorld().getName());
        data.addProperty("to_thunder_state", e.toThunderState());
        return data;
    }

    private static JsonObject serializeWeatherChange(WeatherChangeEvent e) {
        JsonObject data = new JsonObject();
        data.addProperty("world", e.getWorld().getName());
        data.addProperty("to_rain_state", e.toWeatherState());
        return data;
    }

    private static JsonObject serializePortalCreate(PortalCreateEvent e) {
        JsonObject data = new JsonObject();
        data.addProperty("world", e.getWorld().getName());
        data.addProperty("create_reason", e.getReason().toString());
        return data;
    }

    private static JsonObject serializeStructureGrow(StructureGrowEvent e) {
        JsonObject data = new JsonObject();
        data.addProperty("world", e.getWorld().getName());
        data.addProperty("location_x", e.getLocation().getX());
        data.addProperty("location_y", e.getLocation().getY());
        data.addProperty("location_z", e.getLocation().getZ());
        data.addProperty("is_from_bonemeal", e.isFromBonemeal());
        return data;
    }
}
