package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SynthesisCatalogTest {
    private static final SynthesisCatalog CATALOG = SynthesisCatalog.loadDefault();
    private static final Set<String> EQUIPMENT = Set.of("shield", "iron_pickaxe", "diamond_pickaxe", "bow", "crossbow");
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Set<String> SINGLE_LEVEL = Set.of("aqua_affinity", "silk_touch", "mending", "flame", "infinity", "multishot", "channeling");

    // The expected recipes come from the authoritative README, independently of synthesis.json.
    // This lexicon translates English item names only; quantities and upgrades are read verbatim.
    private static final String LEXICON = """
            leather=Leather
            iron_ingot=Iron Ingot;Iron Ingots
            shield=Shield
            obsidian=Obsidian
            diamond=Diamond;Diamonds
            magma_cream=Magma Cream
            nether_brick=Nether Brick items
            blaze_rod=Blaze Rod;Blaze Rods
            golden_apple=Golden Apple
            gunpowder=Gunpowder
            tnt=TNT
            blast_furnace=Blast Furnace
            arrow=Arrows
            string=String
            amethyst_shard=Amethyst Shard;Amethyst Shards
            cactus=Cactus
            sweet_berries=Sweet Berries
            pointed_dripstone=Pointed Dripstone
            kelp=Kelp
            pufferfish=Pufferfish
            prismarine_crystals=Prismarine Crystals
            feather=Feathers
            slime_ball=Slimeballs
            hay_block=Hay Bale
            phantom_membrane=Phantom Membrane;Phantom Membranes
            sugar=Sugar
            nautilus_shell=Nautilus Shell
            prismarine_shard=Prismarine Shards
            blue_ice=Blue Ice
            soul_sand=Soul Sand
            soul_torch=Soul Torches
            soul_lantern=Soul Lanterns
            ghast_tear=Ghast Tear
            sculk=Sculk
            sculk_sensor=Sculk Sensor
            echo_shard=Echo Shards
            sculk_shrieker=Sculk Shrieker
            flint=Flint
            quartz=Nether Quartz
            netherite_scrap=Netherite Scrap
            bone_block=Bone Block
            rotten_flesh=Rotten Flesh
            gold_ingot=Gold Ingot
            glistering_melon_slice=Glistering Melon Slice
            spider_eye=Spider Eyes
            brown_mushroom=Brown Mushroom
            fermented_spider_eye=Fermented Spider Eyes
            cobweb=Cobwebs
            piston=Piston;Pistons
            redstone=Redstone Dust
            fire_charge=Fire Charge
            gold_block=Gold Block
            rabbit_foot=Rabbit's Foot;Rabbit's Feet
            emerald=Emeralds
            wind_charge=Wind Charge;Wind Charges
            breeze_rod=Breeze Rod;Breeze Rods
            iron_block=Iron Block;Iron Blocks
            deepslate=Deepslate
            diamond_block=Diamond Block
            amethyst_block=Amethyst Blocks
            iron_pickaxe=Iron Pickaxe
            diamond_pickaxe=Diamond Pickaxe
            copper_block=Copper Blocks
            ominous_bottle=Ominous Bottle
            copper_ingot=Copper Ingot;Copper Ingots
            redstone_block=Redstone Block;Redstone Blocks
            emerald_block=Emerald Block
            ancient_debris=Ancient Debris
            bow=Bow
            firework_star=Firework Star
            firework_rocket=Firework Rocket
            tripwire_hook=Tripwire Hook
            crossbow=Crossbow
            lead=Leads
            compass=Compass
            ender_pearl=Ender Pearl
            heart_of_the_sea=Heart of the Sea
            cod=Raw Cod
            tropical_fish=Tropical Fish
            """;

    private record DesignRow(String id, int max, List<String> upgrades) {}

    private static List<DesignRow> designRows() {
        try {
            String readme = Files.readString(Path.of("SourceMaterial/README.md"));
            String table = readme.substring(readme.indexOf("### Full proposed synthesis recipes"),
                    readme.indexOf("### Enchantments with no synthesis upgrade"));
            return table.lines().filter(line -> line.startsWith("| ") && !line.startsWith("| Enchantment |"))
                    .map(line -> Arrays.stream(line.split("\\|", -1)).map(String::strip).toList())
                    .filter(cells -> cells.size() == 8)
                    .map(cells -> new DesignRow("minecraft:" + cells.get(1).toLowerCase(java.util.Locale.ROOT).replace(' ', '_'),
                            roman(cells.get(2)), cells.subList(3, 7))).toList();
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static int roman(String value) {
        return switch (value) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            default -> throw new IllegalArgumentException(value); };
    }

    private static SynthesisIngredient expectedIngredient(String description) {
        int space = description.indexOf(' ');
        int count = Integer.parseInt(description.substring(0, space));
        String name = description.substring(space + 1);
        if (name.equals("Wool")) return new SynthesisIngredient(COLORS.stream().map(c -> "minecraft:" + c + "_wool").toList(), null, count, false);
        Map<String, String> potionNames = Map.of(
                "Potion of the Turtle Master", "turtle_master", "Potion of Fire Resistance", "fire_resistance",
                "Potion of Water Breathing", "water_breathing", "Potion of Slow Falling", "slow_falling",
                "Potion of Swiftness II", "strong_swiftness", "Potion of Strength II", "strong_strength",
                "Potion of Infestation", "infested", "Splash Potion of Healing", "healing", "Splash Potion of Healing II", "strong_healing");
        if (potionNames.containsKey(name)) return new SynthesisIngredient(
                List.of(name.startsWith("Splash") ? "minecraft:splash_potion" : "minecraft:potion"), "minecraft:" + potionNames.get(name), count, false);
        Map<String, String> names = new HashMap<>();
        LEXICON.lines().forEach(line -> {
            String[] pair = line.split("=", 2);
            for (String alias : pair[1].split(";")) names.put(alias, pair[0]);
        });
        String item = names.get(name);
        assertNotNull(item, "Missing independent README item translation: " + name);
        return new SynthesisIngredient(List.of("minecraft:" + item), null, count, EQUIPMENT.contains(item));
    }

    static Stream<SynthesisRecipe> expectedRecipes() {
        return designRows().stream().flatMap(row -> java.util.stream.IntStream.range(0, 4)
                .filter(i -> !row.upgrades().get(i).equals("—"))
                .mapToObj(i -> new SynthesisRecipe(row.id(), i + 1,
                        Arrays.stream(row.upgrades().get(i).split(" \\+ ")).map(SynthesisCatalogTest::expectedIngredient).toList())));
    }

    @ParameterizedTest @MethodSource("expectedRecipes")
    void everyRecipeAndQuantityMatchesIndependentReadme(SynthesisRecipe expected) {
        assertEquals(expected, CATALOG.find(expected.enchantmentId(), expected.inputLevel()).orElseThrow());
        assertEquals(expected.inputLevel() + 1, expected.resultLevel());
    }

    @Test void completeEightyFiveAcrossFortyOneWithSevenNotUpgradable() {
        assertEquals(41, designRows().size());
        Set<SynthesisRecipe> expected = expectedRecipes().collect(Collectors.toSet());
        assertEquals(85, expected.size());
        assertEquals(85, CATALOG.recipes().size());
        assertEquals(expected, new HashSet<>(CATALOG.recipes()));
        assertEquals(34, CATALOG.recipes().stream().map(SynthesisRecipe::enchantmentId).distinct().count());
        assertEquals(SINGLE_LEVEL, designRows().stream().filter(r -> r.max() == 1)
                .map(r -> r.id().substring("minecraft:".length())).collect(Collectors.toSet()));
        for (DesignRow row : designRows()) {
            for (int level = 1; level < row.max(); level++) assertTrue(CATALOG.find(row.id(), level).isPresent(), row.id());
            for (int level = row.max(); level <= 6; level++) assertTrue(CATALOG.find(row.id(), level).isEmpty(), row.id());
        }
        assertTrue(CATALOG.find("minecraft:binding_curse", 1).isEmpty());
        assertTrue(CATALOG.find("minecraft:vanishing_curse", 1).isEmpty());
        assertTrue(CATALOG.find("minecraft:not_real", 1).isEmpty());
    }

    @Test void immutableCatalogRecipesAndIngredients() {
        assertThrows(UnsupportedOperationException.class, () -> CATALOG.recipes().clear());
        SynthesisRecipe recipe = CATALOG.recipes().getFirst();
        assertThrows(UnsupportedOperationException.class, () -> recipe.ingredients().clear());
        assertThrows(UnsupportedOperationException.class, () -> recipe.ingredients().getFirst().itemIds().clear());
        List<String> ids = new ArrayList<>(List.of("minecraft:diamond"));
        SynthesisIngredient ingredient = new SynthesisIngredient(ids, null, 1, false);
        ids.clear();
        assertEquals(List.of("minecraft:diamond"), ingredient.itemIds());
        List<SynthesisIngredient> ingredients = new ArrayList<>(List.of(ingredient));
        SynthesisRecipe snapshot = new SynthesisRecipe("test:enchantment", 1, ingredients);
        ingredients.clear();
        assertEquals(List.of(ingredient), snapshot.ingredients());
    }

    private static final String VALID = """
            {"schemaVersion":1,"recipes":[{"enchantmentId":"test:one","inputLevel":1,
              "ingredients":[{"itemIds":["minecraft:diamond"],"count":1,"standardEquipment":false}]}]}
            """;

    static Stream<String> malformedCatalogs() {
        return Stream.of(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                VALID.replace("test:one", "NOT AN ID"), VALID.replace("test:one", "minecraft:binding_curse"),
                VALID.replace("test:one", "minecraft:vanishing_curse"),
                VALID.replace("\"inputLevel\":1", "\"inputLevel\":0"), VALID.replace("\"inputLevel\":1", "\"inputLevel\":5"),
                VALID.replace("\"count\":1", "\"count\":0"), VALID.replace("\"count\":1", "\"count\":-1"),
                VALID.replace("\"count\":1", "\"count\":1.5"), VALID.replace("\"count\":1", "\"count\":2147483648"),
                VALID.replace("\"count\":1", "\"count\":\"1\""), VALID.replace("\"count\":1", "\"count\":true"),
                VALID.replace("\"standardEquipment\":false", "\"standardEquipment\":\"false\""),
                VALID.replace("\"count\":1", "\"quantity\":1"),
                VALID.replace("[\"minecraft:diamond\"]", "[]"),
                VALID.replace("[\"minecraft:diamond\"]", "[\"minecraft:diamond\",\"minecraft:diamond\"]"),
                VALID.replace("minecraft:diamond", "minecraft:potion"),
                VALID.replace("\"count\":1", "\"count\":1,\"potionId\":\"minecraft:healing\""),
                "{\"schemaVersion\":1,\"recipes\":[]}",
                "{\"schemaVersion\":1,\"recipes\":[" + VALID.substring(VALID.indexOf("[{" ) + 1, VALID.lastIndexOf(']')) + ","
                        + VALID.substring(VALID.indexOf("[{") + 1, VALID.lastIndexOf(']')) + "]}");
    }

    @ParameterizedTest @MethodSource("malformedCatalogs")
    void rejectsMalformedOrAmbiguousCatalogs(String json) {
        assertThrows(RuntimeException.class, () -> SynthesisCatalog.load(new StringReader(json)));
    }

    @Test void rejectsOverlappingAlternativesAndMissingMaterials() {
        var diamond = new SynthesisIngredient(List.of("minecraft:diamond"), null, 1, false);
        var alternatives = new SynthesisIngredient(List.of("minecraft:emerald", "minecraft:diamond"), null, 2, false);
        assertThrows(IllegalArgumentException.class, () -> new SynthesisRecipe("test:one", 1, List.of(diamond, alternatives)));
        assertThrows(IllegalArgumentException.class, () -> new SynthesisRecipe("test:one", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SynthesisIngredient(List.of("minecraft:potion"), "minecraft:healing", 1, true));
    }
}
