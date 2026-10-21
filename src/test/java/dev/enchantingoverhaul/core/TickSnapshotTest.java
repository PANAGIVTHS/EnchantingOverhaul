package dev.enchantingoverhaul.core;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class TickSnapshotTest {
    @Test void multipleViewersShareOnePreviewPerTickAndRefreshNextTick() {
        var cache = new TickSnapshot<Integer>();
        var scans = new AtomicInteger();
        for (int viewer = 0; viewer < 20; viewer++) assertEquals(1, cache.preview(10, scans::incrementAndGet));
        assertEquals(1, scans.get());
        assertEquals(2, cache.preview(11, scans::incrementAndGet));
        assertEquals(2, scans.get());
    }
    @Test void confirmationMustRescanAfterSameTickBookshelfRemoval() {
        var cache = new TickSnapshot<LibraryKnowledge>();
        var catalog = ReplicationCatalog.loadDefault();
        var books = new ArrayList<LibraryKnowledge.Blueprint>();
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 3));
        var preview = cache.preview(20, () -> LibraryKnowledge.aggregate(catalog, books));
        assertTrue(preview.unlocks("minecraft:sharpness", 3));
        books.clear();
        var confirmation = cache.refresh(20, () -> LibraryKnowledge.aggregate(catalog, books));
        assertFalse(confirmation.unlocks("minecraft:sharpness", 1));
        assertSame(confirmation, cache.preview(20, () -> fail("fresh result should update previews")));
    }
    @Test void noPersistedKnowledgeOrCrossWorkstationSharing() {
        var first = new TickSnapshot<Integer>();
        var second = new TickSnapshot<Integer>();
        assertEquals(1, first.preview(5, () -> 1));
        assertEquals(2, second.preview(5, () -> 2));
        first.clear();
        assertEquals(3, first.preview(5, () -> 3));
        assertEquals(4, first.preview(4, () -> 4)); // Time rollback cannot reuse a different tick.
    }
    @Test void failedOrNullScansAreNotPublished() {
        var cache = new TickSnapshot<Integer>();
        assertThrows(IllegalStateException.class, () -> cache.preview(1, () -> { throw new IllegalStateException(); }));
        assertThrows(NullPointerException.class, () -> cache.preview(1, () -> null));
        assertEquals(1, cache.preview(1, () -> 1));
        assertThrows(IllegalStateException.class, () -> cache.refresh(2, () -> { throw new IllegalStateException(); }));
        assertEquals(2, cache.preview(2, () -> 2));
    }
    @Test void adapterUsesServerCounterEvenWhenWorldTimeIsFrozen() throws Exception {
        var node = new ClassNode();
        try (InputStream bytes = getClass().getResourceAsStream("/dev/enchantingoverhaul/block/WorkstationBlockEntity.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        for (String name : new String[]{"libraryForPreview", "freshLibrary"}) {
            var method = node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            var calls = new ArrayList<String>();
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
            assertTrue(calls.contains("getServer"), name);
            assertTrue(calls.contains("getTickCount"), name);
            assertFalse(calls.contains("getGameTime"), name);
        }
        var cache = new TickSnapshot<Integer>();
        var scans = new AtomicInteger();
        // World gameTime can remain frozen; consecutive server counters must still expire previews.
        assertEquals(1, cache.preview(101, scans::incrementAndGet));
        assertEquals(2, cache.preview(102, scans::incrementAndGet));
    }
    @Test void targetComparisonResetsSelectionBeforePaymentPlanning() throws Exception {
        var node = new ClassNode();
        try (InputStream bytes = getClass().getResourceAsStream("/dev/enchantingoverhaul/menu/ReplicatorMenu.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("refresh")).findFirst().orElseThrow();
        int compare = -1, reset = -1, copy = -1, payment = -1, index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("matches")) compare = index;
                if (call.owner.equals("net/minecraft/world/inventory/SimpleContainerData") && call.name.equals("set") && reset < 0) {
                    reset = index;
                    assertEquals(org.objectweb.asm.Opcodes.ICONST_M1, instruction.getPrevious().getOpcode());
                }
                if (call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("copy")) copy = index;
                if (call.owner.equals("dev/enchantingoverhaul/game/ReplicationService") && call.name.equals("plan")) payment = index;
            }
            index++;
        }
        assertTrue(compare >= 0 && compare < reset && reset < copy && copy < payment);
    }
    @Test void shippedMenuUsesFreshAuthorizationNotPreviewAndStillChecksShiftClickValidity() throws Exception {
        var node = new ClassNode();
        try (InputStream bytes = getClass().getResourceAsStream("/dev/enchantingoverhaul/menu/ReplicatorMenu.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        for (String method : new String[]{"clickMenuButton", "broadcastChanges", "quickMoveStack"}) {
            var calls = new ArrayList<String>();
            var target = node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
            for (var instruction : target.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
            if (method.equals("clickMenuButton")) {
                assertTrue(calls.contains("freshKnowledge"));
                assertFalse(calls.contains("previewKnowledge"));
                assertTrue(calls.indexOf("freshKnowledge") < calls.indexOf("apply"));
            } else if (method.equals("broadcastChanges")) {
                assertTrue(calls.contains("previewKnowledge"));
            } else assertTrue(calls.contains("stillValid"));
        }
    }
}
