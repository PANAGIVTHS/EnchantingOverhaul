package dev.enchantingoverhaul.core;

import java.io.InputStream;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class WidgetPreviewPolicyTest {
    @Test void clickingThenLeavingDoesNotPinPreviewOrHighlight() {
        assertTrue(WidgetPreviewPolicy.requested(true, true, false, false));
        assertTrue(WidgetPreviewPolicy.requested(true, true, true, false));
        assertFalse(WidgetPreviewPolicy.requested(true, false, true, false));
        assertFalse(WidgetPreviewPolicy.requested(true, false, false, false));
    }
    @Test void keyboardFocusStillSupportsPreviewAndActivationNavigation() {
        assertTrue(WidgetPreviewPolicy.requested(true, false, true, true));
        assertFalse(WidgetPreviewPolicy.requested(true, false, true, false));
        assertTrue(WidgetPreviewPolicy.requested(true, true, true, false));
    }
    @Test void everyVisibilityHoverFocusInputModeCombinationMatchesVanillaTooltipSemantics() {
        for (boolean visible : new boolean[]{false, true})
            for (boolean hovered : new boolean[]{false, true})
                for (boolean focused : new boolean[]{false, true})
                    for (boolean keyboard : new boolean[]{false, true}) {
                        boolean expected = visible && (hovered || focused && keyboard);
                        assertEquals(expected, WidgetPreviewPolicy.requested(visible, hovered, focused, keyboard));
                    }
    }
    @Test void unavailableActionsMayExplainRequirementsWithoutBecomingClickable() {
        // The policy deliberately ignores active: explaining a disabled button must still work.
        assertTrue(WidgetPreviewPolicy.requested(true, true, false, false));
        assertFalse(WidgetPreviewPolicy.requested(false, true, true, true));
    }
    @Test void shippedScreensAndHighlightsCannotRegressToUnconditionalFocus() throws Exception {
        for (String name : new String[]{"ReferenceWorkstationScreen", "ReferenceButton", "CombinerScreen"}) {
            var calls = new ArrayList<String>();
            // Bytecode inspection only, never initialize any Minecraft/client class.
            try (InputStream bytes = getClass().getResourceAsStream("/dev/enchantingoverhaul/client/" + name + ".class")) {
                assertNotNull(bytes, name);
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (var method : node.methods) for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call) calls.add(call.owner + "." + call.name);
            }
            assertFalse(calls.stream().anyMatch(call -> call.endsWith(".isHoveredOrFocused")), name);
            if (name.equals("ReferenceWorkstationScreen")) {
                assertTrue(calls.contains("dev/enchantingoverhaul/client/ReferenceButton.previewRequested"));
            } else {
                assertTrue(calls.contains("dev/enchantingoverhaul/core/WidgetPreviewPolicy.requested"), name);
                assertTrue(calls.contains("net/minecraft/client/InputType.isKeyboard"), name);
            }
        }
    }
}
