package dev.enchantingoverhaul.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable Minecraft-independent bundled recipe catalog; not a runtime datapack loader. */
public final class SynthesisCatalog {
    public static final String RESOURCE = "/data/enchantingoverhaul/synthesis.json";
    private record Key(String enchantmentId, int inputLevel) {}
    private final List<SynthesisRecipe> recipes;
    private final Map<Key, SynthesisRecipe> byInput;

    private SynthesisCatalog(List<SynthesisRecipe> recipes) {
        if (recipes.isEmpty()) throw new IllegalArgumentException("Empty synthesis catalog");
        this.recipes = List.copyOf(recipes);
        Map<Key, SynthesisRecipe> indexed = new LinkedHashMap<>();
        for (SynthesisRecipe recipe : recipes) {
            if (indexed.putIfAbsent(new Key(recipe.enchantmentId(), recipe.inputLevel()), recipe) != null) {
                throw new IllegalArgumentException("Duplicate synthesis recipe: " + recipe.enchantmentId() + " / " + recipe.inputLevel());
            }
        }
        byInput = Map.copyOf(indexed);
    }

    public static SynthesisCatalog loadDefault() {
        InputStream input = SynthesisCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing bundled resource: " + RESOURCE);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + RESOURCE, exception);
        }
    }

    /** Caller owns the reader. Invalid and ambiguous recipes are rejected before use. */
    public static SynthesisCatalog load(Reader reader) {
        JsonObject root = JsonParser.parseReader(Objects.requireNonNull(reader, "reader")).getAsJsonObject();
        fields(root, Set.of("schemaVersion", "recipes"));
        if (integer(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unsupported schemaVersion");
        List<SynthesisRecipe> recipes = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("recipes")) {
            JsonObject entry = element.getAsJsonObject();
            fields(entry, Set.of("enchantmentId", "inputLevel", "ingredients"));
            List<SynthesisIngredient> ingredients = new ArrayList<>();
            for (JsonElement value : entry.getAsJsonArray("ingredients")) {
                JsonObject ingredient = value.getAsJsonObject();
                fields(ingredient, Set.of("itemIds", "potionId", "count", "standardEquipment"));
                List<String> itemIds = new ArrayList<>();
                for (JsonElement id : ingredient.getAsJsonArray("itemIds")) itemIds.add(string(id));
                JsonElement potion = ingredient.get("potionId");
                JsonElement equipment = ingredient.get("standardEquipment");
                if (equipment != null && (!equipment.isJsonPrimitive() || !equipment.getAsJsonPrimitive().isBoolean())) {
                    throw new IllegalArgumentException("standardEquipment must be a boolean");
                }
                ingredients.add(new SynthesisIngredient(itemIds,
                        potion == null || potion.isJsonNull() ? null : string(potion), integer(ingredient.get("count")),
                        equipment != null && equipment.getAsBoolean()));
            }
            recipes.add(new SynthesisRecipe(string(entry.get("enchantmentId")), integer(entry.get("inputLevel")), ingredients));
        }
        return new SynthesisCatalog(recipes);
    }

    private static void fields(JsonObject object, Set<String> allowed) {
        if (!allowed.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown synthesis field");
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string");
        }
        return value.getAsString();
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer number");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    public List<SynthesisRecipe> recipes() { return recipes; }

    public Optional<SynthesisRecipe> find(String enchantmentId, int inputLevel) {
        return Optional.ofNullable(byInput.get(new Key(enchantmentId, inputLevel)));
    }
}
