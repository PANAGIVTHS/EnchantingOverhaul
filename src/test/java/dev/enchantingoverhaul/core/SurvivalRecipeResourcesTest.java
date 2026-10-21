package dev.enchantingoverhaul.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource contract tests only: deliberately never bootstrap Minecraft or its recipe codecs. */
class SurvivalRecipeResourcesTest {
    private JsonObject resource(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/enchantingoverhaul/" + path + ".json")) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private void shaped(String name, Map<String, Integer> expected) throws Exception {
        var recipe = resource("recipe/" + name);
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("enchantingoverhaul:" + name, recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        var counts = new HashMap<String, Integer>();
        var keys = recipe.getAsJsonObject("key");
        int width = -1;
        assertEquals(3, recipe.getAsJsonArray("pattern").size());
        for (var row : recipe.getAsJsonArray("pattern")) {
            String pattern = row.getAsString();
            if (width == -1) width = pattern.length();
            assertEquals(3, width);
            assertEquals(width, pattern.length());
            for (char symbol : pattern.toCharArray()) if (symbol != ' ') {
                String item = keys.get(String.valueOf(symbol)).getAsString();
                counts.merge(item, 1, Integer::sum);
            }
        }
        assertEquals(expected, counts);
    }
    @Test void extractorHasApprovedQuantitiesAndShapedLayout() throws Exception {
        shaped("enchantment_extractor", Map.of("minecraft:grindstone", 1, "minecraft:copper_ingot", 4, "minecraft:amethyst_shard", 2));
    }
    @Test void replicatorHasApprovedQuantitiesAndShapedLayout() throws Exception {
        shaped("enchantment_replicator", Map.of("minecraft:enchanting_table", 1, "minecraft:amethyst_shard", 4));
    }
    @Test void combinerHasApprovedQuantitiesAndShapedLayout() throws Exception {
        shaped("enchanted_book_combiner", Map.of("minecraft:anvil", 1, "minecraft:bookshelf", 1, "minecraft:amethyst_shard", 4));
    }
    @Test void firstMendingUsesApprovedShapelessIngredientsAndStoredComponent() throws Exception {
        var recipe = resource("recipe/mending");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        var ingredients = new HashMap<String, Integer>();
        for (var ingredient : recipe.getAsJsonArray("ingredients")) ingredients.merge(ingredient.getAsString(), 1, Integer::sum);
        assertEquals(Map.of("minecraft:nether_star", 1, "minecraft:ghast_tear", 1, "minecraft:golden_apple", 1, "minecraft:book", 1), ingredients);
        var result = recipe.getAsJsonObject("result");
        assertEquals("minecraft:enchanted_book", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
        var enchantments = result.getAsJsonObject("components").getAsJsonObject("minecraft:stored_enchantments");
        assertEquals(1, enchantments.size());
        assertEquals(1, enchantments.get("minecraft:mending").getAsInt());
    }
    @Test void recipeBookUnlocksUseAnIngredientOrRecipeKnowledgeWithoutXpRewards() throws Exception {
        for (String name : new String[]{"enchantment_extractor", "enchantment_replicator", "enchanted_book_combiner", "mending"}) {
            var advancement = resource("advancement/recipes/" + name);
            assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
            assertEquals(1, advancement.getAsJsonArray("requirements").size());
            assertEquals(2, advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().size());
            var rewards = advancement.getAsJsonObject("rewards");
            assertEquals(1, rewards.size());
            assertEquals("enchantingoverhaul:" + name, rewards.getAsJsonArray("recipes").get(0).getAsString());
        }
    }
}
