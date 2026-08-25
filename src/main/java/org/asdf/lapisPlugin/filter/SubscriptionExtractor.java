package org.asdf.lapisPlugin.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SubscriptionExtractor {

    public static JsonObject apply(JsonObject data, JsonArray subscription) {
        if (subscription == null || subscription.isEmpty()) return data;

        JsonObject result = new JsonObject();
        for (JsonElement elem : subscription) {
            String path = elem.getAsString();
            JsonElement value = resolvePath(data, path);
            if (value != null) {
                setPath(result, path, value);
            }
        }
        return result;
    }

    private static JsonElement resolvePath(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonElement current = root;
        for (String part : parts) {
            if (!current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
            if (current == null) return null;
        }
        return current;
    }

    private static void setPath(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i])) {
                current.add(parts[i], new JsonObject());
            }
            current = current.getAsJsonObject(parts[i]);
        }
        current.add(parts[parts.length - 1], value);
    }
}