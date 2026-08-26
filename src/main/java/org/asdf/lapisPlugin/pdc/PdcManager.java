package org.asdf.lapisPlugin.pdc;

import com.google.gson.JsonObject;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class PdcManager {

    private static final String TAG_PREFIX = "ctag_";
    private static final String DATA_PREFIX = "cdata_";
    private static final String NAMESPACE = "lapis";

    private final JavaPlugin plugin;

    public PdcManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setTag(PersistentDataContainer pdc, String packageName, String tagName, String value) {
        String pkg = packageName.toLowerCase();
        String name = tagName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, TAG_PREFIX + pkg + "_" + name);
        pdc.set(key, PersistentDataType.STRING, value);
    }

    public String getTag(PersistentDataContainer pdc, String packageName, String tagName) {
        String pkg = packageName.toLowerCase();
        String name = tagName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, TAG_PREFIX + pkg + "_" + name);
        return pdc.get(key, PersistentDataType.STRING);
    }

    public void removeTag(PersistentDataContainer pdc, String packageName, String tagName) {
        String pkg = packageName.toLowerCase();
        String name = tagName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, TAG_PREFIX + pkg + "_" + name);
        pdc.remove(key);
    }

    public void setData(PersistentDataContainer pdc, String packageName, String dataName, String jsonValue) {
        String pkg = packageName.toLowerCase();
        String name = dataName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);
        pdc.set(key, PersistentDataType.STRING, jsonValue);
    }

    public String getData(PersistentDataContainer pdc, String packageName, String dataName) {
        String pkg = packageName.toLowerCase();
        String name = dataName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);
        return pdc.get(key, PersistentDataType.STRING);
    }

    public void removeData(PersistentDataContainer pdc, String packageName, String dataName) {
        String pkg = packageName.toLowerCase();
        String name = dataName.toLowerCase();
        NamespacedKey key = new NamespacedKey(NAMESPACE, DATA_PREFIX + pkg + "_" + name);
        pdc.remove(key);
    }

    public JsonObject readAllTags(PersistentDataContainer pdc) {
        JsonObject result = new JsonObject();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equals(NAMESPACE)) continue;
            String keyName = key.getKey();
            if (!keyName.startsWith(TAG_PREFIX)) continue;

            String raw = keyName.substring(TAG_PREFIX.length());
            int sep = raw.indexOf('_');
            if (sep < 0) continue;

            String pkg = raw.substring(0, sep);
            String tagName = raw.substring(sep + 1);
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null) continue;

            if (!result.has(pkg)) result.add(pkg, new JsonObject());
            result.getAsJsonObject(pkg).addProperty(tagName, value);
        }
        return result;
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
            String dataName = raw.substring(sep + 1);
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null) continue;

            if (!result.has(pkg)) result.add(pkg, new JsonObject());
            result.getAsJsonObject(pkg).addProperty(dataName, value);
        }
        return result;
    }
}