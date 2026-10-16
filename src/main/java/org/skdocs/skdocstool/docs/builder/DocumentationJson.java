package org.skdocs.skdocstool.docs.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DocumentationJson {
    private DocumentationJson() {}

    public static void addStrings(JsonObject obj, String key, String[] values) {
        if (values == null || values.length == 0) return;
        JsonArray arr = new JsonArray();
        for (String v : values) if (v != null && !v.isBlank()) arr.add(v);
        if (!arr.isEmpty()) obj.add(key, arr);
    }

    public static JsonArray strings(Object values) {
        JsonArray arr = new JsonArray();
        if (values instanceof String[] arr2) {
            for (String v : arr2) arr.add(v);
        } else if (values instanceof Collection<?> col) {
            for (Object v : col) arr.add(v.toString());
        }
        return arr;
    }

    
    public static JsonArray mergeEntries(JsonArray entries) {
        Map<JsonObject, JsonObject> groups = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject key = entry.deepCopy();
            key.remove("patterns");
            JsonObject existing = groups.get(key);
            if (existing == null) {
                groups.put(key, entry);
            } else if (entry.has("patterns")) {
                JsonArray patterns = existing.getAsJsonArray("patterns");
                if (patterns == null) { patterns = new JsonArray(); existing.add("patterns", patterns); }
                for (JsonElement pattern : entry.getAsJsonArray("patterns"))
                    if (!patterns.contains(pattern)) patterns.add(pattern);
            }
        }
        JsonArray result = new JsonArray();
        groups.values().forEach(result::add);
        return result;
    }

    public static void assignDocIds(String addonName, JsonArray... arrays) {
        Set<String> seen = new LinkedHashSet<>();
        for (JsonArray arr : arrays) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonElement idEl = obj.get("id");
                if (idEl == null || idEl.isJsonNull()) continue;
                String base = addonName + "-" + idEl.getAsString();
                String unique = base;
                int count = 2;
                while (!seen.add(unique)) unique = base + "-" + count++;
                obj.addProperty("doc-id", unique);
            }
        }
    }

}
