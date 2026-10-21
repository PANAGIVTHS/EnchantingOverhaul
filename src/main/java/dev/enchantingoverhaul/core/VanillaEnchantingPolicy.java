package dev.enchantingoverhaul.core;

import java.util.Arrays;

/** Pure rules shared by the vanilla-menu integration; no game bootstrap required. */
public final class VanillaEnchantingPolicy {
    private VanillaEnchantingPolicy() {}

    /** A disabled table has no payable costs and no enchantment/level hints. */
    public static void clearTableOffers(int[] costs, int[] enchantClues, int[] levelClues) {
        Arrays.fill(costs, 0);
        Arrays.fill(enchantClues, -1);
        Arrays.fill(levelClues, -1);
    }

    /** Reject every button, including forged/stale IDs, without indexing the offer arrays. */
    public static boolean tableButtonAllowed(int buttonId) {
        return false;
    }

    /** Only the left item's existing enchantments may participate in an anvil result. */
    public static boolean anvilEnchantmentSourceAllowed(int inputSlot) {
        return inputSlot == 0;
    }

    /**
     * Vanilla refunds at most the sum of these contributions across both inputs.
     * Cap only contributions exceeding the full replication XP fee; all other
     * enchantments, curses, item removal and vanilla random refund rolls are untouched.
     */
    public static int grindstoneContribution(int vanillaMinimumCost, int replicationXp) {
        if (vanillaMinimumCost < 0 || replicationXp < 0) {
            throw new IllegalArgumentException("XP contributions and fees cannot be negative");
        }
        return Math.min(vanillaMinimumCost, replicationXp);
    }
}
