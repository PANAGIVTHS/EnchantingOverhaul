package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AcquisitionRulesTest {
    @Test
    void onlyMendingIsExcludedNotOtherTreasureCursesOrModdedEnchantments() {
        assertFalse(AcquisitionRules.permitsInitialEnchantment("minecraft:mending"));
        for (String id : List.of("minecraft:binding_curse", "minecraft:vanishing_curse", "minecraft:frost_walker",
                "minecraft:soul_speed", "minecraft:swift_sneak", "minecraft:wind_burst", "minecraft:sharpness", "other:mending")) {
            assertTrue(AcquisitionRules.permitsInitialEnchantment(id), id);
        }
    }

    @Test
    void preserveMendingOnlyLootButDoNotExpandLibrarianEligibilityOrRerollMixedLoot() {
        assertTrue(AcquisitionRules.rerollEmptyLootPool(1, 0, false));
        assertFalse(AcquisitionRules.rerollEmptyLootPool(1, 0, true));
        assertFalse(AcquisitionRules.rerollEmptyLootPool(5, 4, false));
        assertFalse(AcquisitionRules.rerollEmptyLootPool(5, 4, true));
        assertFalse(AcquisitionRules.rerollEmptyLootPool(0, 0, false));
        assertFalse(AcquisitionRules.rerollEmptyLootPool(1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.rerollEmptyLootPool(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.rerollEmptyLootPool(1, 2, false));
    }

    @Test
    void exactNinetyTenDistributionForEveryMultiLevelMaximum() {
        for (int maximum = 2; maximum <= 255; maximum++) {
            int ones = 0;
            int twos = 0;
            for (int roll = 0; roll < 10; roll++) {
                int level = AcquisitionRules.librarianLevel(maximum, roll);
                if (level == 1) ones++;
                else if (level == 2) twos++;
                else fail("Librarian bypass at level " + level);
            }
            assertEquals(9, ones, "maximum=" + maximum);
            assertEquals(1, twos, "maximum=" + maximum);
        }
    }

    @Test
    void singleLevelEnchantmentsNeverBecomeLevelTwo() {
        for (int roll = 0; roll < 10; roll++) {
            assertEquals(1, AcquisitionRules.librarianLevel(1, roll));
        }
    }

    @Test
    void actualOfferedLevelControlsEveryPossibleVanillaPriceRoll() {
        for (int level = 1; level <= 5; level++) {
            int bound = 5 + level * 10;
            assertEquals(bound, AcquisitionRules.bookPriceRandomBound(level));
            assertEquals(bound + 1, AcquisitionRules.bookPriceRandomBound(level, true));
            for (int roll = 0; roll <= bound; roll++) {
                assertEquals(2 + roll + 3 * level, AcquisitionRules.bookAdditionalCost(level, roll, true));
            }
            for (int roll = 0; roll < bound; roll++) {
                int undoubled = 2 + roll + 3 * level;
                assertEquals(undoubled, AcquisitionRules.bookAdditionalCost(level, roll));
                // VillagerTrade, not this policy, owns the doubles tag and TradeCost owns the cap.
                assertEquals(Math.min(64, 2 * undoubled), Math.min(64, 2 * AcquisitionRules.bookAdditionalCost(level, roll)));
            }
        }
        assertEquals(5, AcquisitionRules.bookAdditionalCost(1, 0));
        assertEquals(19, AcquisitionRules.bookAdditionalCost(1, 14));
        assertEquals(8, AcquisitionRules.bookAdditionalCost(2, 0));
        assertEquals(32, AcquisitionRules.bookAdditionalCost(2, 24));
    }

    @Test
    void rejectImpossiblePolicyInputsRatherThanInventAnOffer() {
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.librarianLevel(0, 0));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.librarianLevel(3, -1));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.librarianLevel(3, 10));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.bookPriceRandomBound(0));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.bookPriceRandomBound(256));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.bookAdditionalCost(1, -1));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.bookAdditionalCost(1, 15));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionRules.bookAdditionalCost(2, 25));
    }
}
