package org.asdf.lapisPlugin.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.asdf.lapisPlugin.LapisPlugin;

import java.util.Set;
import java.util.logging.Logger;

public final class NbtCollector {

    private static boolean warned = false;

    /**
     * 采集玩家完整原生 NBT（Inventory、Attributes、Health 等）
     */
    public static JsonObject collectPlayerNbt(Player player) {
        try {
            return NBT.get(player, NbtCollector::toJsonObject);
        } catch (Exception e) {
            warnOnce("Failed to collect player NBT for " + player.getName(), e);
            return new JsonObject();
        }
    }

    /**
     * 采集方块实体原生 NBT；非 TileState 返回空对象
     */
    public static JsonObject collectBlockNbt(Block block) {
        try {
            if (!(block.getState() instanceof TileState tileState)) {
                return new JsonObject();
            }
            return NBT.get(tileState, NbtCollector::toJsonObject);
        } catch (Exception e) {
            warnOnce("Failed to collect block NBT at " + block.getX() + "," + block.getY() + "," + block.getZ(), e);
            return new JsonObject();
        }
    }

    /**
     * ReadableNBT -> Gson JsonObject 递归转换
     */
    static JsonObject toJsonObject(ReadableNBT nbt) {
        JsonObject result = new JsonObject();
        if (nbt == null) return result;

        Set<String> keys = nbt.getKeys();
        for (String key : keys) {
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
                    JsonArray arr = new JsonArray();
                    for (byte b : nbt.getByteArray(key)) arr.add(b);
                    result.add(key, arr);
                }
                case NBTTagIntArray -> {
                    JsonArray arr = new JsonArray();
                    for (int i : nbt.getIntArray(key)) arr.add(i);
                    result.add(key, arr);
                }
                case NBTTagLongArray -> {
                    JsonArray arr = new JsonArray();
                    for (long l : nbt.getLongArray(key)) arr.add(l);
                    result.add(key, arr);
                }
                case NBTTagCompound -> {
                    ReadableNBT compound = nbt.getCompound(key);
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
                case NBTTagEnd -> {
                    // skip
                }
            }
        }
        return result;
    }

    private static JsonArray listToJsonArray(ReadableNBT nbt, String key, NBTType listType) {
        JsonArray arr = new JsonArray();
        switch (listType) {
            case NBTTagString -> nbt.getStringList(key).forEach(arr::add);
            case NBTTagInt -> nbt.getIntegerList(key).forEach(arr::add);
            case NBTTagLong -> nbt.getLongList(key).forEach(arr::add);
            case NBTTagFloat -> nbt.getFloatList(key).forEach(arr::add);
            case NBTTagDouble -> nbt.getDoubleList(key).forEach(arr::add);
            case NBTTagByte -> {
                // item-nbt-api 没有 getByteList，用 getByteArray 兜底
                for (byte b : nbt.getByteArray(key)) arr.add(b);
            }
            case NBTTagCompound -> {
                for (ReadableNBT compound : nbt.getCompoundList(key)) {
                    arr.add(toJsonObject(compound));
                }
            }
            case NBTTagIntArray -> {
                for (int[] ia : nbt.getIntArrayList(key)) {
                    JsonArray inner = new JsonArray();
                    for (int i : ia) inner.add(i);
                    arr.add(inner);
                }
            }
            default -> {
                // 未知列表类型，返回空数组
            }
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