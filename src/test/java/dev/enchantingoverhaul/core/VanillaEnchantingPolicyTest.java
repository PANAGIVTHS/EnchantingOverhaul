package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VanillaEnchantingPolicyTest {
    @Test
    void slotRefreshErasesAllCostsAndBothKindsOfClueWithoutTouchingInputs() {
        int[] costs = {8, 17, 30};
        int[] clues = {0, 11, 42};
        int[] levels = {1, 2, 5};
        VanillaEnchantingPolicy.clearTableOffers(costs, clues, levels);
        assertArrayEquals(new int[]{0, 0, 0}, costs);
        assertArrayEquals(new int[]{-1, -1, -1}, clues);
        assertArrayEquals(new int[]{-1, -1, -1}, levels);
        // Idempotent for empty/repeated menu refreshes.
        VanillaEnchantingPolicy.clearTableOffers(costs, clues, levels);
        assertTrue(Arrays.stream(costs).allMatch(value -> value == 0));
        assertTrue(Arrays.stream(clues).allMatch(value -> value == -1));
        assertTrue(Arrays.stream(levels).allMatch(value -> value == -1));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2, 3, Integer.MAX_VALUE})
    void staleCreativeOrForgedTableButtonsCannotRoll(int buttonId) {
        assertFalse(VanillaEnchantingPolicy.tableButtonAllowed(buttonId));
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,false", "2,false", "2147483647,false"})
    void onlyLeftAnvilEnchantmentsSurvive(int sourceSlot, boolean allowed) {
        assertEquals(allowed, VanillaEnchantingPolicy.anvilEnchantmentSourceAllowed(sourceSlot));
    }

    @ParameterizedTest
    @CsvSource({"25,20,20", "50,55,50", "75,110,75", "25,240,25", "20,20,20",
            "17,20,17", "1,20,1", "0,20,0", "33,110,33", "45,275,45"})
    void capOnlyContributionsThatExceedReplicationFee(int vanilla, int fee, int expected) {
        assertEquals(expected, VanillaEnchantingPolicy.grindstoneContribution(vanilla, fee));
    }

    @Test
    void actualChannelingAndSwiftSneakOneRefundCounterexamplesAreFixed() {
        // Cached 26.3: min_cost=25 for both level-I enchants; replication fee=20.
        assertEquals(25, maximumRefund(25));
        assertEquals(5, maximumRefund(25) - 20);
        assertEquals(19, maximumRefund(VanillaEnchantingPolicy.grindstoneContribution(25, 20)));
        // Contributions aggregate BEFORE rounding, so check two-input/multi-enchant sums.
        assertEquals(49, maximumRefund(25 + 25));
        assertEquals(39, maximumRefund(20 + 20));
    }

    @Test
    void everyRandomOutcomeIsBoundedBySummedContributions() {
        for (int contribution = 0; contribution <= 550; contribution++) {
            int half = (contribution + 1) / 2;
            if (half == 0) {
                assertEquals(0, maximumRefund(contribution));
            } else {
                for (int random = 0; random < half; random++) {
                    assertTrue(half + random <= contribution);
                }
            }
        }
    }

    @Test
    void negativeRefundInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VanillaEnchantingPolicy.grindstoneContribution(-1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaEnchantingPolicy.grindstoneContribution(25, -1));
    }

    private static int maximumRefund(int contribution) {
        return contribution == 0 ? 0 : 2 * ((contribution + 1) / 2) - 1;
    }
}
