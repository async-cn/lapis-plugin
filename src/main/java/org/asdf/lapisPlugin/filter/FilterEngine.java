package org.asdf.lapisPlugin.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public class FilterEngine {

    public static boolean evaluate(JsonObject ast, JsonObject data) {
        if (ast == null || ast.isEmpty()) return true;

        if ("void".equals(ast.get("op_type").getAsString())) return true;

        String opType = ast.get("op_type").getAsString();
        String opName = ast.get("op_name").getAsString();

        return switch (opType) {
            case "logic" -> evaluateLogic(opName, ast, data);
            case "condition" -> evaluateCondition(opName, ast, data);
            case "special_condition" -> evaluateSpecial(opName, ast, data);
            default -> false;
        };
    }

    private static boolean evaluateLogic(String opName, JsonObject node, JsonObject data) {
        return switch (opName) {
            case "and" -> {
                for (JsonElement elem : node.getAsJsonArray("operands")) {
                    if (!evaluate(elem.getAsJsonObject(), data)) yield false;
                }
                yield true;
            }
            case "or" -> {
                for (JsonElement elem : node.getAsJsonArray("operands")) {
                    if (evaluate(elem.getAsJsonObject(), data)) yield true;
                }
                yield false;
            }
            case "not" -> !evaluate(node.get("a").getAsJsonObject(), data);
            default -> false;
        };
    }

    private static boolean evaluateCondition(String opName, JsonObject node, JsonObject data) {
        String path = node.get("a").getAsString();
        JsonElement expected = node.get("b");
        JsonElement actual = resolvePath(data, path);

        if (actual == null) return false;

        return switch (opName) {
            case "eq" -> actual.equals(expected);
            case "ne" -> !actual.equals(expected);
            case "lt" -> compare(actual, expected) < 0;
            case "le" -> compare(actual, expected) <= 0;
            case "gt" -> compare(actual, expected) > 0;
            case "ge" -> compare(actual, expected) >= 0;
            default -> false;
        };
    }

    private static boolean evaluateSpecial(String opName, JsonObject node, JsonObject data) {
        return switch (opName) {
            case "array_include" -> {
                String path = node.get("path").getAsString();
                JsonArray contents = node.getAsJsonArray("contents");
                JsonElement actual = resolvePath(data, path);
                if (actual == null || !actual.isJsonArray()) yield false;
                JsonArray arr = actual.getAsJsonArray();
                for (JsonElement target : contents) {
                    for (JsonElement item : arr) {
                        if (item.equals(target)) yield true;
                    }
                }
                yield false;
            }
            case "object_match" -> {
                JsonObject pattern = node.getAsJsonObject("pattern");
                yield matchObject(data, pattern);
            }
            default -> false;
        };
    }

    private static JsonElement resolvePath(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonElement current = root;
        for (String part : parts) {
            if (!current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
            if (current == null || current.isJsonNull()) return null;
        }
        return current;
    }

    private static int compare(JsonElement a, JsonElement b) {
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            return Double.compare(a.getAsDouble(), b.getAsDouble());
        }
        return 0;
    }

    private static boolean matchObject(JsonObject data, JsonObject pattern) {
        for (Map.Entry<String, JsonElement> entry : pattern.entrySet()) {
            String key = entry.getKey();
            JsonElement expected = entry.getValue();
            if (!data.has(key)) return false;
            JsonElement actual = data.get(key);
            if (expected.isJsonObject() && actual.isJsonObject()) {
                if (!matchObject(actual.getAsJsonObject(), expected.getAsJsonObject())) return false;
            } else if (!actual.equals(expected)) {
                return false;
            }
        }
        return true;
    }
}