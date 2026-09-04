package org.asdf.lapisPlugin.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTEntity;
import de.tr7zw.changeme.nbtapi.NBTItem;
import de.tr7zw.changeme.nbtapi.NBTTileEntity;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.asdf.lapisPlugin.LapisPlugin;

import java.util.Set;
import java.util.logging.Logger;

public final class NbtCollector {

    private static boolean warned = false;

    public static JsonObject collectPlayerNbt(Player player) {
        JsonObject nbt = new JsonObject();
        try {
            NBTEntity nbtEntity = new NBTEntity(player);
            nbt = toJsonObject(nbtEntity);
        } catch (Exception e) {
            warnOnce("Failed to collect player NBT for " + player.getName(), e);
        }

        try {
            if (!nbt.has("SelectedItemSlot")) {
                nbt.addProperty("SelectedItemSlot", player.getInventory().getHeldItemSlot());
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.AIR) {
                if (!nbt.has("SelectedItem")) {
                    JsonObject selectedItem = new JsonObject();
                    selectedItem.addProperty("id", mainHand.getType().getKey().toString());
                    selectedItem.addProperty("Count", mainHand.getAmount());

                    try {
                        NBTItem nbtItem = new NBTItem(mainHand);
                        JsonObject itemNbt = toJsonObject(nbtItem);
                        if (!itemNbt.isEmpty()) {
                            selectedItem.add("tag", itemNbt);
                        }
                    } catch (Exception e) {
                        LapisPlugin.getInstance().getLogger().fine(
                                "Failed to read SelectedItem NBT for " + player.getName() + ": " + e.getMessage()
                        );
                    }

                    nbt.add("SelectedItem", selectedItem);
                }
            }
        } catch (Exception e) {
            LapisPlugin.getInstance().getLogger().warning(
                    "Failed to supplement player NBT fields for " + player.getName() + ": " + e.getMessage()
            );
        }

        return nbt;
    }

    public static JsonObject collectBlockNbt(Block block) {
        try {
            if (!(block.getState() instanceof TileState tileState)) {
                return new JsonObject();
            }
            NBTTileEntity nbtTile = new NBTTileEntity(tileState);
            return toJsonObject(nbtTile);
        } catch (Exception e) {
            warnOnce("Failed to collect block NBT at " + block.getX() + "," + block.getY() + "," + block.getZ(), e);
            return new JsonObject();
        }
    }

    public static JsonObject collectEntityNbt(Entity entity) {
        try {
            NBTEntity nbtEntity = new NBTEntity(entity);
            return toJsonObject(nbtEntity);
        } catch (Exception e) {
            warnOnce("Failed to collect entity NBT for " + entity.getType(), e);
            return new JsonObject();
        }
    }

    static JsonObject toJsonObject(NBTCompound nbt) {
        JsonObject result = new JsonObject();
        if (nbt == null) return result;

        Set<String> keys = nbt.getKeys();
        for (String key : keys) {
            try {
                NBTType type = nbt.getType(key);
                if (type == null) continue;

                switch (type) {
                    case NBTTagString -> result.add(key, new JsonPrimitive(nbt.getString(key)));
                    case NBTTagByte -> result.add(key, new JsonPrimitive(nbt.getByte(key)));
                    case NBTTagShort -> result.add(key, new JsonPrimitive(nbt.getShort(key)));
                    case NBTTagInt -> result.add(key, new JsonPrimitive(nbt.getInteger(key)));
                    case NBTTagLong -> result.add(key, new JsonPrimitive(nbt.getLong(key)));
                    case NBTTagFloat -> result.add(key, new JsonPrimitive(nbt.getFloat(key)));
                    case NBTTagDouble -> result.add(key, new JsonPrimitive(nbt.getDouble(key)));
                    case NBTTagByteArray -> {
                        byte[] arr = nbt.getByteArray(key);
                        JsonArray jsonArr = new JsonArray();
                        if (arr != null) {
                            for (byte b : arr) jsonArr.add(b);
                        }
                        result.add(key, jsonArr);
                    }
                    case NBTTagIntArray -> {
                        int[] arr = nbt.getIntArray(key);
                        JsonArray jsonArr = new JsonArray();
                        if (arr != null) {
                            for (int i : arr) jsonArr.add(i);
                        }
                        result.add(key, jsonArr);
                    }
                    case NBTTagLongArray -> {
                        long[] arr = nbt.getLongArray(key);
                        JsonArray jsonArr = new JsonArray();
                        if (arr != null) {
                            for (long l : arr) jsonArr.add(l);
                        }
                        result.add(key, jsonArr);
                    }
                    case NBTTagCompound -> {
                        NBTCompound compound = nbt.getCompound(key);
                        result.add(key, toJsonObject(compound));
                    }
                    case NBTTagList -> {
                        NBTType listType = nbt.getListType(key);
                        if (listType == null) {
                            result.add(key, new JsonArray());
                            continue;
                        }
                        JsonArray arr = listToJsonArray(nbt, key, listType);
                        result.add(key, arr);
                    }
                    case NBTTagEnd -> {}
                }
            } catch (Exception e) {
                LapisPlugin.getInstance().getLogger().fine(
                        "Failed to serialize NBT key '" + key + "' (" + nbt.getType(key) + "): " + e.getMessage()
                );
            }
        }
        return result;
    }

    private static JsonArray listToJsonArray(NBTCompound nbt, String key, NBTType listType) {
        JsonArray arr = new JsonArray();
        try {
            switch (listType) {
                case NBTTagString -> {
                    var list = nbt.getStringList(key);
                    if (list != null) list.forEach(arr::add);
                }
                case NBTTagInt -> {
                    var list = nbt.getIntegerList(key);
                    if (list != null) list.forEach(arr::add);
                }
                case NBTTagLong -> {
                    var list = nbt.getLongList(key);
                    if (list != null) list.forEach(arr::add);
                }
                case NBTTagFloat -> {
                    var list = nbt.getFloatList(key);
                    if (list != null) list.forEach(arr::add);
                }
                case NBTTagDouble -> {
                    var list = nbt.getDoubleList(key);
                    if (list != null) list.forEach(arr::add);
                }
                case NBTTagByte -> {
                    byte[] bytes = nbt.getByteArray(key);
                    if (bytes != null) {
                        for (byte b : bytes) arr.add(b);
                    }
                }
                case NBTTagCompound -> {
                    var list = nbt.getCompoundList(key);
                    if (list != null) {
                        for (ReadWriteNBT compound : list) {
                            arr.add(toJsonObject((NBTCompound) compound));
                        }
                    }
                }
                case NBTTagIntArray -> {
                    var list = nbt.getIntArrayList(key);
                    if (list != null) {
                        for (int[] ia : list) {
                            JsonArray inner = new JsonArray();
                            for (int i : ia) inner.add(i);
                            arr.add(inner);
                        }
                    }
                }
                default -> {}
            }
        } catch (Exception e) {
            LapisPlugin.getInstance().getLogger().fine(
                    "Failed to serialize NBT list '" + key + "' (" + listType + "): " + e.getMessage()
            );
        }
        return arr;
    }

    private static void warnOnce(String message, Exception e) {
        Logger logger = LapisPlugin.getInstance().getLogger();
        if (!warned) {
            logger.warning("[NbtCollector] " + message + " | " + e.getMessage());
            warned = true;
        }
    }
}
