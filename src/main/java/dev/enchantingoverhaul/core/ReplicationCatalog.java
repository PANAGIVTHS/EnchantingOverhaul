package dev.enchantingoverhaul.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Minecraft-independent immutable catalog; all ingredients and tuning live in JSON. */
public final class ReplicationCatalog {
    public static final String RESOURCE = "/data/enchantingoverhaul/replication.json";
    private final Map<String, ReplicationDefinition> definitions;

    private ReplicationCatalog(Map<String, ReplicationDefinition> definitions) {
        if (definitions.isEmpty()) throw new IllegalArgumentException("Empty replication catalog");
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static ReplicationCatalog loadDefault() {
        InputStream input = ReplicationCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing bundled resource: " + RESOURCE);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + RESOURCE, exception);
        }
    }

    /** Caller owns the reader. Malformed/ambiguous definitions fail closed. */
    public static ReplicationCatalog load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unsupported schemaVersion");
        List<Integer> xp = schedule(root.getAsJsonArray("cumulativeXp"));
        List<Integer> lapis = schedule(root.getAsJsonArray("cumulativeLapis"));
        Map<String, ReplicationDefinition> definitions = new LinkedHashMap<>();
        for (JsonElement element : root.getAsJsonArray("enchantments")) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("enchantmentId").getAsString();
            if (isCurse(id)) throw new IllegalArgumentException("Curses cannot be replicated");
            JsonObject catalyst = entry.getAsJsonObject("catalyst");
            Catalyst ingredient = new Catalyst(catalyst.get("itemId").getAsString(),
                    catalyst.has("potionId") ? catalyst.get("potionId").getAsString() : null);
            ReplicationDefinition definition = new ReplicationDefinition(id, integer(entry.get("maxLevel")), ingredient,
                    entry.has("cumulativeXp") ? schedule(entry.getAsJsonArray("cumulativeXp")) : xp,
                    entry.has("cumulativeLapis") ? schedule(entry.getAsJsonArray("cumulativeLapis")) : lapis);
            if (definitions.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate enchantment: " + id);
        }
        return new ReplicationCatalog(definitions);
    }

    private static int integer(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer number");
        }
        return element.getAsBigDecimal().intValueExact();
    }

    private static List<Integer> schedule(JsonArray array) {
        List<Integer> result = new ArrayList<>();
        for (JsonElement value : array) result.add(integer(value));
        return List.copyOf(result);
    }

    public Map<String, ReplicationDefinition> definitions() { return definitions; }
    public Optional<ReplicationDefinition> find(String enchantmentId) {
        return Optional.ofNullable(definitions.get(enchantmentId));
    }
    public ReplicationDefinition require(String enchantmentId) {
        return find(enchantmentId).orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + enchantmentId));
    }
    public static boolean isCurse(String enchantmentId) {
        return "minecraft:binding_curse".equals(enchantmentId) || "minecraft:vanishing_curse".equals(enchantmentId);
    }
}
