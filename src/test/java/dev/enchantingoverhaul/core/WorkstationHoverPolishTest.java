package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Structural UI regressions only: reads class bytes without loading Minecraft/client classes. */
class WorkstationHoverPolishTest {
    private static final List<String> SCREENS = List.of("ReplicatorScreen", "ExtractorScreen", "CombinerScreen");

    private ClassNode bytes(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/dev/enchantingoverhaul/client/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private MethodNode method(String screen, String name) throws Exception {
        return bytes(screen).methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private List<String> calls(MethodNode method) {
        var calls = new ArrayList<String>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        return calls;
    }
    private List<String> literals(MethodNode method) {
        var strings = new ArrayList<String>();
        for (var instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value) strings.add(value);
            if (instruction instanceof InvokeDynamicInsnNode dynamic)
                for (var argument : dynamic.bsmArgs) if (argument instanceof String value) strings.add(value);
        }
        return strings;
    }

    @Test void allLiveScreensInheritNormalItemTooltipsWithoutSlotInstructions() throws Exception {
        for (String screen : SCREENS) {
            var node = bytes(screen);
            assertEquals("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", node.superName);
            assertFalse(node.methods.stream().anyMatch(m -> m.name.equals("extractTooltip")), screen);
            assertFalse(node.methods.stream().anyMatch(m -> m.name.equals("slotHint")), screen);
        }
    }
    @Test void rowsOnlyIdentifyEnchantmentAndNeverExpandIntoTutorialOrPayment() throws Exception {
        for (String screen : List.of("ReplicatorScreen", "ExtractorScreen")) {
            var calls = calls(method(screen, "entryTooltip"));
            assertTrue(calls.contains("entryName"));
            assertTrue(calls.contains("entrySuffix"));
            assertFalse(calls.contains("previewTooltip"));
            assertFalse(calls.contains("catalystName"));
            assertFalse(calls.contains("statusText"));
            assertTrue(literals(method(screen, "entryTooltip")).stream().allMatch(s -> s.length() <= 4));
        }
    }
    @Test void titleAndLevelControlsNoLongerRequestTheirOwnHoverPopup() throws Exception {
        for (String screen : List.of("ReplicatorScreen", "ExtractorScreen")) {
            var render = method(screen, "extractRenderState");
            assertEquals(2, calls(render).stream().filter("show"::equals).count(), screen);
            assertFalse(calls(render).contains("openSearch"));
            // Only cost/action and full-name row hovers; no title/level instructional strings.
            assertTrue(literals(render).isEmpty(), screen);
        }
    }
    @Test void compactRequirementsStillUseLiveCostsAndExactCatalystNames() throws Exception {
        var replica = calls(method("ReplicatorScreen", "previewTooltip"));
        assertTrue(replica.containsAll(List.of("xpCost", "lapisCost", "catalystName", "currentLevel", "statusText")));
        assertFalse(replica.contains("playerXp"));
        var extractor = calls(method("ExtractorScreen", "previewTooltip"));
        assertTrue(extractor.containsAll(List.of("xpCost", "isCurse", "statusText")));
        assertTrue(literals(method("ExtractorScreen", "previewTooltip")).contains("Dragon's Breath"));
        for (String screen : List.of("ReplicatorScreen", "ExtractorScreen"))
            assertTrue(literals(method(screen, "previewTooltip")).stream().allMatch(s -> s.length() <= 32), screen);
    }
    @Test void statusesAreShortAndOldInstructionalProseIsNotPackagedInLiveScreens() throws Exception {
        for (String screen : SCREENS) {
            for (String message : literals(method(screen, "statusText"))) {
                assertTrue(message.length() <= 32, screen + ": " + message);
                assertFalse(message.contains("\n"));
            }
            for (var method : bytes(screen).methods) for (String text : literals(method))
                for (String forbidden : List.of("Hover Cost", "Click the title", "Select to preview", "ONLY this",
                        "Your XP:", "target stays", "source stays", "Bottles returned", "Hover button:",
                        "materials still required", "No materials are taken", "One enchantment per operation"))
                    assertFalse(text.contains(forbidden), screen + ": " + forbidden);
        }
    }
    @Test void fullCombinerRecipeKeepsExactQuantitiesVariantsAndAllIngredients() throws Exception {
        var recipe = calls(method("CombinerScreen", "recipeDetails"));
        assertTrue(recipe.containsAll(List.of("ingredients", "count", "ingredientName", "statusText")));
        assertTrue(calls(method("CombinerScreen", "ingredientName")).containsAll(List.of("catalystName", "standardEquipment")));
        assertTrue(calls(method("WorkstationTooltip", "show")).contains("isBlank"));
    }
}
