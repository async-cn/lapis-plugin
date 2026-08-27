package org.asdf.lapisPlugin.pdc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class PdcManager {

    private static final String DATA_PREFIX = "cdata_";
    private static final String NAMESPACE = "lapis";

    private final JavaPlugin plugin;

    public PdcManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setData(PersistentDataContainer pdc, String packageName, String dataKey, JsonElement value) {
        String pkg = packageName.toLowerCase();
        String name = dataKey.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);
        pdc.remove(key);

        if (value == null || value.isJsonNull()) {
            return;
        }

        if (value.isJsonPrimitive()) {
            JsonPrimitive prim = value.getAsJsonPrimitive();
            if (prim.isNumber()) {
                double d = prim.getAsDouble();
                if (d == (int) d) {
                    pdc.set(key, PersistentDataType.INTEGER, (int) d);
                } else {
                    pdc.set(key, PersistentDataType.DOUBLE, d);
                }
            } else {
                pdc.set(key, PersistentDataType.STRING, prim.getAsString());
            }
        } else {
            pdc.set(key, PersistentDataType.STRING, value.toString());
        }
    }

    public JsonElement getData(PersistentDataContainer pdc, String packageName, String dataKey) {
        String pkg = packageName.toLowerCase();
        String name = dataKey.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);

        try {
            Integer i = pdc.get(key, PersistentDataType.INTEGER);
            if (i != null) return new JsonPrimitive(i);
        } catch (IllegalArgumentException ignored) {}

        try {
            Double d = pdc.get(key, PersistentDataType.DOUBLE);
            if (d != null) return new JsonPrimitive(d);
        } catch (IllegalArgumentException ignored) {}

        try {
            String s = pdc.get(key, PersistentDataType.STRING);
            if (s != null) return new JsonPrimitive(s);
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    public void removeData(PersistentDataContainer pdc, String packageName, String dataKey) {
        String pkg = packageName.toLowerCase();
        String name = dataKey.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);
        pdc.remove(key);
    }

    public JsonObject readAllData(PersistentDataContainer pdc) {
        JsonObject result = new JsonObject();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equals(NAMESPACE)) continue;
            String keyName = key.getKey();
            if (!keyName.startsWith(DATA_PREFIX)) continue;

            String raw = keyName.substring(DATA_PREFIX.length());
            int sep = raw.indexOf('_');
            if (sep < 0) continue;

            String pkg = raw.substring(0, sep);
            String dataKey = raw.substring(sep + 1);

            JsonElement value = tryGetValue(pdc, key);
            if (value == null) continue;

            if (!result.has(pkg)) result.add(pkg, new JsonObject());
            result.getAsJsonObject(pkg).add(dataKey, value);
        }
        return result;
    }

    private JsonElement tryGetValue(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            Integer i = pdc.get(key, PersistentDataType.INTEGER);
            if (i != null) return new JsonPrimitive(i);
        } catch (IllegalArgumentException ignored) {}

        try {
            Double d = pdc.get(key, PersistentDataType.DOUBLE);
            if (d != null) return new JsonPrimitive(d);
        } catch (IllegalArgumentException ignored) {}

        try {
            String s = pdc.get(key, PersistentDataType.STRING);
            if (s != null) return new JsonPrimitive(s);
        } catch (IllegalArgumentException ignored) {}

        return null;
    }
}