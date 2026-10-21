package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;
import static dev.enchantingoverhaul.core.ReplicatorLayout.*;

/** Geometry and bytecode only: no client/game bootstrap or claimed visual validation. */
class ReplicatorLayoutTest {
    record Rect(int x, int y, int w, int h) {
        boolean overlaps(Rect other) {
            return x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h;
        }
    }
    private ClassNode bytes(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/dev/enchantingoverhaul/" + name + ".class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    @Test void normalizedReferencePanelAndPrimaryRegionsMatchWithinOneNativePixel() {
        assertEquals(176, WIDTH);
        assertEquals(168, HEIGHT);
        assertEquals(936.0 / 980, HEIGHT / (double)WIDTH, 0.01);
        assertEquals(478 * 176.0 / 980, LIST_X, 1);
        assertEquals(94 * 176.0 / 980, LIST_Y, 1);
        assertEquals(424 * 176.0 / 980, ROW_WIDTH, 1);
        assertEquals(298 * 176.0 / 980, ACTION_Y, 1);
        assertEquals(380 * 176.0 / 980, ACTION_WIDTH, 1);
        assertEquals(494 * 176.0 / 980, INVENTORY_Y, 1);
        assertEquals(808 * 176.0 / 980, HOTBAR_Y, 1);
        assertTrue(WIDTH <= 320 && HEIGHT <= 240);
    }
    @Test void everyRealSlotFitsAndHitboxesDoNotOverlap() {
        var slots = new ArrayList<Rect>();
        for (int i = 0; i < 3; i++) slots.add(new Rect(INPUT_X + i * INPUT_PITCH - 1, INPUT_Y - 1, 18, 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            slots.add(new Rect(INVENTORY_X + col * SLOT_PITCH - 1, INVENTORY_Y + row * SLOT_PITCH - 1, 18, 18));
        for (int col = 0; col < 9; col++) slots.add(new Rect(INVENTORY_X + col * SLOT_PITCH - 1, HOTBAR_Y - 1, 18, 18));
        assertEquals(39, slots.size());
        for (var slot : slots) {
            assertTrue(slot.x >= 2 && slot.y >= 2 && slot.x + slot.w <= WIDTH - 2 && slot.y + slot.h <= HEIGHT - 2);
            for (var other : slots) if (slot != other) assertFalse(slot.overlaps(other));
        }
        var controls = new ArrayList<Rect>();
        controls.add(new Rect(ACTION_X, ACTION_Y, ACTION_WIDTH, ACTION_HEIGHT));
        controls.add(new Rect(LIST_X, LIST_Y, ROW_WIDTH, ROW_HEIGHT * ROWS));
        controls.add(new Rect(TRACK_X, LIST_Y, TRACK_WIDTH, TRACK_HEIGHT));
        for (int i = 0; i < 5; i++) controls.add(new Rect(LEVEL_X + i * LEVEL_PITCH, LEVEL_Y, LEVEL_SIZE, LEVEL_SIZE));
        for (var control : controls) for (var slot : slots) assertFalse(control.overlaps(slot));
        for (var control : controls) for (var other : controls) if (control != other) assertFalse(control.overlaps(other));
    }
    @Test void romanNumeralsUseOrdinarySameFontTextIncludingFour() {
        assertEquals(List.of("I", "II", "III", "IV", "V"), java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(ReplicatorLayout::numeral).toList());
        assertEquals("6", numeral(6));
    }
    @Test void standaloneScreenRetainsInputSafetyScrollingAndLiveProtocol() throws Exception {
        var node = bytes("client/ReplicatorScreen");
        assertEquals("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", node.superName);
        for (String required : List.of("mouseScrolled", "mouseDragged", "mouseReleased", "keyPressed", "extractTooltip"))
            assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(required)), required);
        var keys = node.methods.stream().filter(m -> m.name.equals("keyPressed")).findFirst().orElseThrow();
        var calls = new ArrayList<String>();
        for (var instruction : keys.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        assertTrue(calls.contains("hasControlDownWithQuirk"));
        assertTrue(calls.contains("openSearch"));
        assertTrue(calls.contains("isEscape"));
        for (String className : List.of("client/ReplicatorScreen", "client/ReplicatorScreen$ReplicaButton")) {
            var allCalls = new ArrayList<String>();
            for (var method : bytes(className).methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) allCalls.add(call.name);
            assertFalse(allCalls.contains("isHoveredOrFocused"));
            if (className.endsWith("ReplicaButton")) {
                assertTrue(allCalls.contains("isKeyboard"));
                assertTrue(allCalls.contains("requested"));
            } else assertTrue(allCalls.contains("handleInventoryButtonClick"));
        }
    }
}
