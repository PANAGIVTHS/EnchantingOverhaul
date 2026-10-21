package dev.enchantingoverhaul.core;

/** Rules for newly generated acquisitions, never for existing item contents. */
public final class AcquisitionRules {
    private AcquisitionRules() {}

    public static boolean permitsInitialEnchantment(String identifier) {
        return !"minecraft:mending".equals(identifier);
    }

    /** Keep a Mending-only loot opportunity, but never invent a new librarian eligibility. */
    public static boolean rerollEmptyLootPool(int originalCandidates, int allowedCandidates, boolean librarianTrade) {
        if (originalCandidates < 0 || allowedCandidates < 0 || allowedCandidates > originalCandidates) {
            throw new IllegalArgumentException("Invalid candidate counts");
        }
        return originalCandidates > 0 && allowedCandidates == 0 && !librarianTrade;
    }

    /** Ten equiprobable buckets: nine I, one II; single-level enchantments stay I. */
    public static int librarianLevel(int naturalMaximum, int roll) {
        if (naturalMaximum < 1 || roll < 0 || roll >= 10) {
            throw new IllegalArgumentException("Invalid natural maximum or ten-way roll");
        }
        return naturalMaximum >= 2 && roll == 9 ? 2 : 1;
    }

    public static int bookPriceRandomBound(int offeredLevel) {
        return bookPriceRandomBound(offeredLevel, false);
    }

    public static int bookPriceRandomBound(int offeredLevel, boolean fixedRebalanceTrade) {
        if (offeredLevel < 1 || offeredLevel > 255) {
            throw new IllegalArgumentException("Invalid offered level");
        }
        // 26.3 rebalance fixed trades use an inclusive ContextIntProviders.between,
        // whereas EnchantRandomlyFunction uses an exclusive RandomSource.nextInt.
        return (fixedRebalanceTrade ? 6 : 5) + offeredLevel * 10;
    }

    /** Undoubled vanilla additional cost. The live trade applies its own doubles tag and cap. */
    public static int bookAdditionalCost(int offeredLevel, int priceRoll) {
        return bookAdditionalCost(offeredLevel, priceRoll, false);
    }

    public static int bookAdditionalCost(int offeredLevel, int priceRoll, boolean fixedRebalanceTrade) {
        int bound = bookPriceRandomBound(offeredLevel, fixedRebalanceTrade);
        if (priceRoll < 0 || priceRoll >= bound) {
            throw new IllegalArgumentException("Price roll outside vanilla bound");
        }
        return 2 + priceRoll + 3 * offeredLevel;
    }
}
