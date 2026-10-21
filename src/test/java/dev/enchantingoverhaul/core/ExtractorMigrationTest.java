package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Migration accounting only: no Minecraft/server bootstrap, NBT or live drop simulation. */
class ExtractorMigrationTest {
    static Stream<Arguments> legacyInventories() {
        return IntStream.rangeClosed(0, 64).boxed().flatMap(breath -> Stream.of(0, 1, 16, 64)
                .map(bottles -> Arguments.of(List.of(1, breath, 1, bottles))));
    }
    @ParameterizedTest @MethodSource("legacyInventories")
    void legacyFourSlotOutputsAndAllReagentCountsAreConserved(List<Integer> counts) {
        var plan = ExtractorMigration.plan(counts);
        assertEquals(4, plan.size());
        for (int i = 0; i < counts.size(); i++) {
            var slot = plan.get(i);
            assertEquals(counts.get(i).intValue(), slot.retained() + slot.recover());
            assertTrue(slot.retained() >= 0); assertTrue(slot.recover() >= 0);
            assertEquals(i < 2 ? Math.min(1, counts.get(i)) : 0, slot.retained());
        }
        assertEquals(1, plan.get(2).recover());
        assertEquals(counts.get(3).intValue(), plan.get(3).recover());
        var after = plan.stream().map(ExtractorMigration.Slot::retained).toList();
        assertTrue(ExtractorMigration.plan(after).stream().allMatch(s -> s.recover() == 0));
        assertEquals(counts.stream().mapToInt(Integer::intValue).sum(),
                plan.stream().mapToInt(s -> s.retained() + s.recover()).sum());
    }
    @Test void oldThreeSlotSavesAndTwoVisibleSlotInventoriesRemainSupported() {
        assertEquals(List.of(new ExtractorMigration.Slot(1, 0), new ExtractorMigration.Slot(1, 15),
                new ExtractorMigration.Slot(0, 1)), ExtractorMigration.plan(List.of(1, 16, 1)));
        assertTrue(ExtractorMigration.plan(List.of(1, 1)).stream().allMatch(s -> s.recover() == 0));
        assertTrue(ExtractorMigration.plan(List.of(0, 0, 0, 0)).stream().allMatch(s -> s.recover() == 0));
    }
    @Test void unexpectedStackedTargetIsRecoveredRatherThanClampedOrDeleted() {
        assertEquals(new ExtractorMigration.Slot(1, 63), ExtractorMigration.plan(List.of(64, 1, 0, 0)).get(0));
    }
    @ParameterizedTest @ValueSource(ints={0, 1, 2, 16, 63, 64})
    void compatibleInventoryPlacementNeverLosesOrDuplicatesRecovery(int count) {
        for (var capacities : List.of(List.<Integer>of(), List.of(0, 0, 0), List.of(1),
                List.of(3, 0, 2), List.of(64, 64), List.of(1, 1, 1, 1))) {
            var delivery = ExtractorMigration.delivery(count, capacities);
            int total = delivery.inserted().stream().mapToInt(Integer::intValue).sum();
            assertEquals(count, total + delivery.overflow());
            assertTrue(delivery.overflow() >= 0);
            for (int i = 0; i < capacities.size(); i++) {
                assertTrue(delivery.inserted().get(i) >= 0);
                assertTrue(delivery.inserted().get(i) <= capacities.get(i));
            }
            assertThrows(UnsupportedOperationException.class, () -> delivery.inserted().clear());
        }
    }
    @Test void fullInventoryOverflowStaysPersistedUntilSpaceExistsInAnyGameMode() {
        var migration = ExtractorMigration.plan(List.of(1, 64, 1, 16));
        int recovered = migration.stream().mapToInt(ExtractorMigration.Slot::recover).sum();
        var delivery = ExtractorMigration.delivery(recovered, List.of(0, 0));
        assertEquals(80, recovered); assertEquals(recovered, delivery.overflow());
        assertEquals(0, delivery.inserted().stream().mapToInt(Integer::intValue).sum());
        int retained = migration.stream().mapToInt(ExtractorMigration.Slot::retained).sum();
        assertEquals(82, ExtractorMigration.escrowCount(retained, delivery));
        var stocked = List.of(1, 64, 1, 16);
        // Full inventories never hand overflow to an ItemEntity/Inventory.add pickup path.
        for (int reopening = 0; reopening < 4; reopening++) {
            var retry = ExtractorMigration.plan(stocked);
            var persisted = retry.stream().map(slot -> ExtractorMigration.escrowCount(slot.retained(),
                    ExtractorMigration.delivery(slot.recover(), List.of(0, 0)))).toList();
            assertEquals(stocked, persisted);
            stocked = persisted;
        }
        // When room appears, return each previously escrowed item exactly once.
        var finalPlan = ExtractorMigration.plan(stocked);
        int returned = 0;
        var remaining = new java.util.ArrayList<Integer>();
        for (var slot : finalPlan) {
            var accepted = ExtractorMigration.delivery(slot.recover(), List.of(64));
            returned += accepted.inserted().stream().mapToInt(Integer::intValue).sum();
            remaining.add(ExtractorMigration.escrowCount(slot.retained(), accepted));
        }
        assertEquals(80, returned);
        assertEquals(List.of(1, 1, 0, 0), remaining);
        assertTrue(ExtractorMigration.plan(remaining).stream().allMatch(slot -> slot.recover() == 0));
    }
    @Test void allocationUsesMergeFirstOrderAndLeavesSurplusExplicit() {
        assertEquals(new ExtractorMigration.Delivery(List.of(2, 5, 0), 0), ExtractorMigration.delivery(7, List.of(2, 64, 64)));
        assertEquals(new ExtractorMigration.Delivery(List.of(2, 3), 2), ExtractorMigration.delivery(7, List.of(2, 3)));
    }
    @Test void invalidMigrationInputsAreRejectedRatherThanInventingCounts() {
        assertThrows(IllegalArgumentException.class, () -> ExtractorMigration.plan(List.of(1)));
        assertThrows(IllegalArgumentException.class, () -> ExtractorMigration.plan(List.of(1, -1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ExtractorMigration.delivery(-1, List.of(64)));
        assertThrows(IllegalArgumentException.class, () -> ExtractorMigration.delivery(1, List.of(-1)));
    }
}
