package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import static dev.enchantingoverhaul.core.SynthesisRules.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SynthesisRulesTest {
    private static final SynthesisCatalog CATALOG = SynthesisCatalog.loadDefault();
    private static final SynthesisRules RULES = new SynthesisRules(CATALOG);

    static Stream<SynthesisRecipe> recipes() { return CATALOG.recipes().stream(); }
    static Stream<SynthesisRecipe> potionRecipes() {
        return recipes().filter(r -> r.ingredients().stream().anyMatch(i -> i.potionId() != null));
    }
    static Stream<SynthesisRecipe> equipmentRecipes() {
        return recipes().filter(r -> r.ingredients().stream().anyMatch(SynthesisIngredient::standardEquipment));
    }

    private static Book book(String id, int level) { return new Book(true, false, Map.of(id, level), 1); }
    private static List<Material> empty() { return new ArrayList<>(Collections.nCopies(MATERIAL_SLOTS, EMPTY_MATERIAL)); }
    private static List<Material> exact(SynthesisRecipe recipe) {
        List<Material> materials = empty();
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            SynthesisIngredient ingredient = recipe.ingredients().get(i);
            materials.set(i, new Material(ingredient.itemIds().getFirst(), ingredient.potionId(), ingredient.count(), ingredient.standardEquipment()));
        }
        return materials;
    }
    private static Request request(SynthesisRecipe recipe, List<Material> materials, boolean outputEmpty) {
        return new Request(book(recipe.enchantmentId(), recipe.inputLevel()), book(recipe.enchantmentId(), recipe.inputLevel()),
                recipe.resultLevel(), materials, outputEmpty);
    }
    private static void rejected(Plan plan, Failure failure) {
        assertFalse(plan.accepted());
        assertEquals(failure, plan.failure());
        assertEquals(Collections.nCopies(20, 0), plan.consumed());
        assertEquals(0, plan.bottleReturnCount());
    }
    private static int expectedBottles(SynthesisRecipe recipe) {
        // Independent accounting rather than invoking production returnsBottle().
        Set<String> bottled = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
                "minecraft:dragon_breath", "minecraft:ominous_bottle");
        return recipe.ingredients().stream().filter(i -> bottled.contains(i.itemIds().getFirst())).mapToInt(SynthesisIngredient::count).sum();
    }

    @ParameterizedTest @MethodSource("recipes")
    void allEightyFiveRecipesSucceedWithExactPaymentsAndNoInputMutation(SynthesisRecipe recipe) {
        List<Material> materials = exact(recipe);
        List<Material> before = List.copyOf(materials);
        Request request = request(recipe, materials, true);
        Plan plan = RULES.plan(request);
        assertTrue(plan.accepted(), plan.failure().name());
        assertEquals(Failure.NONE, plan.failure());
        assertEquals(recipe, plan.recipe());
        assertEquals(recipe.inputLevel() + 1, plan.recipe().resultLevel());
        for (int i = 0; i < 20; i++) assertEquals(i < recipe.ingredients().size() ? recipe.ingredients().get(i).count() : 0, plan.consumed().get(i));
        assertEquals(expectedBottles(recipe), plan.bottleReturnCount());
        assertEquals(before, materials);
        assertEquals(before, request.materials());
        assertEquals(1, request.first().count());
        assertEquals(1, request.second().count());
        assertEquals(request.first(), request.second());
        assertEquals(plan, RULES.plan(request), "Repeated planning must not consume or alter a request");
    }

    @ParameterizedTest @MethodSource("recipes")
    void missingAnySingleRequiredUnitFailsWithoutPartialPayments(SynthesisRecipe recipe) {
        for (int missing = 0; missing < recipe.ingredients().size(); missing++) {
            List<Material> materials = exact(recipe);
            Material original = materials.get(missing);
            materials.set(missing, new Material(original.itemId(), original.potionId(), original.count() - 1, original.standardEquipment()));
            List<Material> before = List.copyOf(materials);
            Request request = request(recipe, materials, true);
            Plan plan = RULES.plan(request);
            rejected(plan, Failure.INSUFFICIENT_MATERIALS);
            assertEquals(recipe, plan.recipe(), "Keep recipe preview when materials are missing");
            assertEquals(before, materials);
            assertEquals(before, request.materials());
        }
    }

    @ParameterizedTest @MethodSource("recipes")
    void fullOutputRetainsPreviewAndConsumesNothing(SynthesisRecipe recipe) {
        Request request = request(recipe, exact(recipe), false);
        rejected(RULES.plan(request), Failure.OUTPUT_FULL);
        assertEquals(recipe, RULES.plan(request).recipe());
        assertEquals(exact(recipe), request.materials());
    }

    @ParameterizedTest @MethodSource("recipes")
    void surplusAndSplitSlotPaymentsAreExact(SynthesisRecipe recipe) {
        List<Material> materials = empty();
        List<Integer> expected = new ArrayList<>(Collections.nCopies(20, 0));
        int slot = 0;
        for (SynthesisIngredient ingredient : recipe.ingredients()) {
            if (ingredient.count() > 1) {
                materials.set(slot, new Material(ingredient.itemIds().getFirst(), ingredient.potionId(), 1, ingredient.standardEquipment()));
                expected.set(slot++, 1);
            }
            int remaining = ingredient.count() > 1 ? ingredient.count() - 1 : 1;
            materials.set(slot, new Material(ingredient.itemIds().getFirst(), ingredient.potionId(), remaining + 7, ingredient.standardEquipment()));
            expected.set(slot++, remaining);
        }
        materials.set(19, new Material("minecraft:dirt", null, 64, false));
        List<Material> before = List.copyOf(materials);
        Plan plan = RULES.plan(request(recipe, materials, true));
        assertTrue(plan.accepted());
        assertEquals(expected, plan.consumed());
        assertEquals(expectedBottles(recipe), plan.bottleReturnCount());
        assertEquals(before, materials);
    }

    @ParameterizedTest @MethodSource("potionRecipes")
    void requiresExactPotionIdAndContainerForEveryPotionRecipe(SynthesisRecipe recipe) {
        List<String> variants = List.of("healing", "strong_healing", "infested", "swiftness", "long_swiftness", "strong_swiftness",
                "strength", "long_strength", "strong_strength", "water_breathing", "long_water_breathing",
                "turtle_master", "long_turtle_master", "strong_turtle_master", "fire_resistance", "long_fire_resistance",
                "slow_falling", "long_slow_falling");
        for (int slot = 0; slot < recipe.ingredients().size(); slot++) {
            SynthesisIngredient ingredient = recipe.ingredients().get(slot);
            if (ingredient.potionId() == null) continue;
            for (String variant : variants) {
                if (ingredient.potionId().equals("minecraft:" + variant)) continue;
                List<Material> materials = exact(recipe);
                materials.set(slot, new Material(ingredient.itemIds().getFirst(), "minecraft:" + variant, ingredient.count(), false));
                rejected(RULES.plan(request(recipe, materials, true)), Failure.INSUFFICIENT_MATERIALS);
            }
            for (String container : List.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion")) {
                if (ingredient.itemIds().contains(container)) continue;
                List<Material> materials = exact(recipe);
                materials.set(slot, new Material(container, ingredient.potionId(), ingredient.count(), false));
                rejected(RULES.plan(request(recipe, materials, true)), Failure.INSUFFICIENT_MATERIALS);
            }
            List<Material> missingPotion = exact(recipe);
            missingPotion.set(slot, new Material(ingredient.itemIds().getFirst(), null, ingredient.count(), false));
            rejected(RULES.plan(request(recipe, missingPotion, true)), Failure.INSUFFICIENT_MATERIALS);
        }
    }

    @ParameterizedTest @MethodSource("equipmentRecipes")
    void damagedOrEnchantedEquipmentIsNotEligible(SynthesisRecipe recipe) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            if (!recipe.ingredients().get(i).standardEquipment()) continue;
            List<Material> materials = exact(recipe);
            Material equipment = materials.get(i);
            materials.set(i, new Material(equipment.itemId(), equipment.potionId(), equipment.count(), false));
            rejected(RULES.plan(request(recipe, materials, true)), Failure.INSUFFICIENT_MATERIALS);
            materials.set(19, equipment);
            Plan plan = RULES.plan(request(recipe, materials, true));
            assertTrue(plan.accepted());
            assertEquals(0, plan.consumed().get(i));
            assertEquals(equipment.count(), plan.consumed().get(19));
        }
    }

    @Test void standardEquipmentFlagDoesNotGateOrdinaryMaterials() {
        SynthesisRecipe recipe = CATALOG.find("minecraft:sharpness", 1).orElseThrow();
        List<Material> materials = exact(recipe);
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            Material material = materials.get(i);
            materials.set(i, new Material(material.itemId(), material.potionId(), material.count(), true));
        }
        assertTrue(RULES.plan(request(recipe, materials, true)).accepted());
    }

    @Test void woolAcceptsEveryVanillaColorAndMixesColorsAcrossSlots() {
        for (SynthesisRecipe recipe : CATALOG.recipes()) {
            for (int woolSlot = 0; woolSlot < recipe.ingredients().size(); woolSlot++) {
                SynthesisIngredient wool = recipe.ingredients().get(woolSlot);
                if (wool.itemIds().size() != 16) continue;
                for (String color : wool.itemIds()) {
                    List<Material> materials = exact(recipe);
                    materials.set(woolSlot, new Material(color, null, wool.count(), false));
                    assertTrue(RULES.plan(request(recipe, materials, true)).accepted());
                }
                List<Material> materials = exact(recipe);
                materials.set(woolSlot, EMPTY_MATERIAL);
                for (int i = 0; i < wool.count(); i++) materials.set(3 + i, new Material(wool.itemIds().get(i), null, 1, false));
                Plan plan = RULES.plan(request(recipe, materials, true));
                assertTrue(plan.accepted());
                assertEquals(0, plan.consumed().get(woolSlot));
                for (int i = 0; i < wool.count(); i++) assertEquals(1, plan.consumed().get(3 + i));
                materials = exact(recipe);
                materials.set(woolSlot, new Material("minecraft:white_carpet", null, wool.count(), false));
                rejected(RULES.plan(request(recipe, materials, true)), Failure.INSUFFICIENT_MATERIALS);
            }
        }
    }

    @Test void consumesInSlotOrderIncludingTwentiethSlotAndKeepsExtraMatchingStacks() {
        SynthesisRecipe recipe = CATALOG.find("minecraft:sharpness", 1).orElseThrow();
        List<Material> materials = empty();
        materials.set(0, new Material("minecraft:flint", null, 1, false));
        materials.set(7, new Material("minecraft:iron_ingot", null, 3, false));
        materials.set(19, new Material("minecraft:flint", null, 5, false));
        Plan plan = RULES.plan(request(recipe, materials, true));
        assertTrue(plan.accepted());
        assertEquals(1, plan.consumed().get(0));
        assertEquals(1, plan.consumed().get(7));
        assertEquals(1, plan.consumed().get(19));
        assertEquals(3, plan.consumed().stream().mapToInt(Integer::intValue).sum());
        materials.set(18, new Material("minecraft:flint", null, 2, false));
        plan = RULES.plan(request(recipe, materials, true));
        assertEquals(1, plan.consumed().get(18));
        assertEquals(0, plan.consumed().get(19));
    }

    @Test void mismatchesCursesMultipleEnchantmentsAndWrongBookIdentitiesNeverConsume() {
        Book valid = book("minecraft:sharpness", 1);
        record Bad(Book book, Failure failure) {}
        List<Bad> invalid = List.of(
                new Bad(book("minecraft:smite", 1), Failure.MISMATCHED_ENCHANTMENTS),
                new Bad(book("minecraft:sharpness", 2), Failure.MISMATCHED_LEVELS),
                new Bad(new Book(true, true, valid.enchantments(), 1), Failure.CURSED_BOOK),
                new Bad(book("minecraft:binding_curse", 1), Failure.CURSED_BOOK),
                new Bad(book("minecraft:vanishing_curse", 1), Failure.CURSED_BOOK),
                new Bad(new Book(true, false, Map.of("minecraft:sharpness", 1, "minecraft:looting", 1), 1), Failure.MULTI_ENCHANTMENT_BOOK),
                new Bad(new Book(false, false, valid.enchantments(), 1), Failure.INVALID_BOOK),
                new Bad(new Book(true, false, Map.of(), 1), Failure.INVALID_BOOK),
                new Bad(new Book(true, false, null, 1), Failure.INVALID_BOOK),
                new Bad(new Book(true, false, valid.enchantments(), 0), Failure.INVALID_BOOK),
                new Bad(new Book(true, false, valid.enchantments(), 2), Failure.INVALID_BOOK),
                new Bad(new Book(true, false, valid.enchantments(), -1), Failure.INVALID_BOOK));
        List<Material> materials = exact(CATALOG.find("minecraft:sharpness", 1).orElseThrow());
        for (Bad bad : invalid) {
            rejected(RULES.plan(new Request(valid, bad.book(), 5, materials, true)), bad.failure());
            rejected(RULES.plan(new Request(bad.book(), valid, 5, materials, true)), bad.failure());
        }
        Book cursedMulti = new Book(true, false, Map.of("minecraft:sharpness", 1, "minecraft:vanishing_curse", 1), 1);
        rejected(RULES.plan(new Request(cursedMulti, cursedMulti, 5, materials, true)), Failure.CURSED_BOOK);
    }

    @ParameterizedTest @MethodSource("recipes")
    void naturalMaximumRejectsAllRecipesEvenWithEnoughMaterials(SynthesisRecipe recipe) {
        Book book = book(recipe.enchantmentId(), recipe.inputLevel());
        rejected(RULES.plan(new Request(book, book, recipe.inputLevel(), exact(recipe), true)), Failure.NATURAL_MAXIMUM);
    }

    @Test void sevenSingleLevelEnchantmentsAndAlreadyMaxedBooksHaveNoUpgrade() {
        for (String id : List.of("aqua_affinity", "silk_touch", "mending", "flame", "infinity", "multishot", "channeling")) {
            Book book = book("minecraft:" + id, 1);
            rejected(RULES.plan(new Request(book, book, 1, empty(), true)), Failure.NATURAL_MAXIMUM);
            rejected(RULES.plan(new Request(book, book, 5, empty(), true)), Failure.NO_RECIPE);
        }
        for (ReplicationDefinition definition : ReplicationCatalog.loadDefault().definitions().values()) {
            Book maxed = book(definition.enchantmentId(), definition.maxLevel());
            rejected(RULES.plan(new Request(maxed, maxed, definition.maxLevel(), empty(), true)), Failure.NATURAL_MAXIMUM);
        }
    }

    @Test void invalidUnknownAndOverMaxLevelsAreRejected() {
        for (int level : List.of(-1, 0, 6, Integer.MAX_VALUE)) {
            Book book = book("minecraft:sharpness", level);
            rejected(RULES.plan(new Request(book, book, 5, empty(), true)), Failure.INVALID_LEVEL);
        }
        Book unknown = book("test:unknown", 1);
        rejected(RULES.plan(new Request(unknown, unknown, 5, empty(), true)), Failure.NO_RECIPE);
    }

    @Test void missingWorkstationPaymentsCannotBeTakenFromElsewhere() {
        SynthesisRecipe recipe = CATALOG.find("minecraft:sharpness", 3).orElseThrow();
        rejected(RULES.plan(request(recipe, empty(), true)), Failure.INSUFFICIENT_MATERIALS);
        // There is deliberately no player-inventory, XP, lapis or provenance input to this planner.
        List<Material> materials = exact(recipe);
        materials.set(19, new Material("minecraft:lapis_lazuli", null, 64, false));
        assertTrue(RULES.plan(request(recipe, materials, true)).accepted());
        assertEquals(0, RULES.plan(request(recipe, materials, true)).consumed().get(19));
    }

    @Test void malformedRequestsFailClosed() {
        Book valid = book("minecraft:sharpness", 1);
        rejected(RULES.plan(null), Failure.INVALID_REQUEST);
        rejected(RULES.plan(new Request(null, valid, 5, empty(), true)), Failure.INVALID_REQUEST);
        rejected(RULES.plan(new Request(valid, null, 5, empty(), true)), Failure.INVALID_REQUEST);
        rejected(RULES.plan(new Request(valid, valid, 0, empty(), true)), Failure.INVALID_REQUEST);
        rejected(RULES.plan(new Request(valid, valid, 5, null, true)), Failure.INVALID_REQUEST);
        for (int size : List.of(0, 19, 21)) rejected(RULES.plan(new Request(valid, valid, 5, Collections.nCopies(size, EMPTY_MATERIAL), true)), Failure.INVALID_REQUEST);
        List<Material> materials = empty();
        materials.set(19, null);
        rejected(RULES.plan(new Request(valid, valid, 5, materials, true)), Failure.INVALID_REQUEST);
        materials.set(19, new Material("minecraft:flint", null, -1, false));
        rejected(RULES.plan(new Request(valid, valid, 5, materials, true)), Failure.INVALID_REQUEST);
        materials.set(19, new Material(null, null, 1, false));
        rejected(RULES.plan(new Request(valid, valid, 5, materials, true)), Failure.INVALID_REQUEST);
    }

    @Test void snapshotsCannotBeModifiedAndInputCollectionsAreDefensivelyCopied() {
        Map<String, Integer> enchantments = new HashMap<>(Map.of("minecraft:sharpness", 1));
        Book book = new Book(true, false, enchantments, 1);
        enchantments.clear();
        assertEquals(Map.of("minecraft:sharpness", 1), book.enchantments());
        assertThrows(UnsupportedOperationException.class, () -> book.enchantments().clear());
        SynthesisRecipe recipe = CATALOG.find("minecraft:sharpness", 1).orElseThrow();
        List<Material> materials = exact(recipe);
        Request request = new Request(book, book, 5, materials, true);
        materials.clear();
        assertTrue(RULES.plan(request).accepted());
        assertThrows(UnsupportedOperationException.class, () -> request.materials().clear());
        Plan plan = RULES.plan(request);
        assertThrows(UnsupportedOperationException.class, () -> plan.consumed().clear());
        assertThrows(IllegalArgumentException.class, () -> new Plan(Failure.OUTPUT_FULL, recipe, plan.consumed(), 0));
    }

    @Test void everyConsumedBottledItemReturnsExactlyOneBottleIncludingDragonBreathAndOminous() {
        String json = """
                {"schemaVersion":1,"recipes":[{"enchantmentId":"test:all_bottles","inputLevel":1,"ingredients":[
                  {"itemIds":["minecraft:potion"],"potionId":"minecraft:healing","count":2},
                  {"itemIds":["minecraft:splash_potion"],"potionId":"minecraft:healing","count":2},
                  {"itemIds":["minecraft:lingering_potion"],"potionId":"minecraft:healing","count":2},
                  {"itemIds":["minecraft:dragon_breath"],"count":2},
                  {"itemIds":["minecraft:ominous_bottle"],"count":2}]}]}
                """;
        SynthesisCatalog catalog = SynthesisCatalog.load(new StringReader(json));
        SynthesisRules rules = new SynthesisRules(catalog);
        SynthesisRecipe recipe = catalog.recipes().getFirst();
        List<Material> materials = exact(recipe);
        Plan plan = rules.plan(request(recipe, materials, true));
        assertTrue(plan.accepted());
        assertEquals(10, plan.bottleReturnCount());
        Material extra = materials.getFirst();
        materials.set(0, new Material(extra.itemId(), extra.potionId(), 9, false));
        assertEquals(10, rules.plan(request(recipe, materials, true)).bottleReturnCount());
        materials.set(4, EMPTY_MATERIAL);
        rejected(rules.plan(request(recipe, materials, true)), Failure.INSUFFICIENT_MATERIALS);
        assertFalse(SynthesisRules.returnsBottle("minecraft:glass_bottle"));
        assertFalse(SynthesisRules.returnsBottle(null));
    }
}
