package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Objects;

/** Cumulative tables contain levels I onward (no level-zero element). */
public record ReplicationDefinition(String enchantmentId, int maxLevel, Catalyst catalyst,
                                    List<Integer> cumulativeXp, List<Integer> cumulativeLapis) {
    public ReplicationDefinition {
        Catalyst.requireId(enchantmentId);
        Objects.requireNonNull(catalyst, "catalyst");
        cumulativeXp = List.copyOf(cumulativeXp);
        cumulativeLapis = List.copyOf(cumulativeLapis);
        if (maxLevel < 1 || cumulativeXp.size() < maxLevel || cumulativeLapis.size() < maxLevel) {
            throw new IllegalArgumentException("Missing cumulative costs or invalid maximum");
        }
        validateSchedule(cumulativeXp);
        validateSchedule(cumulativeLapis);
    }

    private static void validateSchedule(List<Integer> values) {
        int previous = 0;
        for (int value : values) {
            if (value <= previous) throw new IllegalArgumentException("Cumulative costs must strictly increase");
            previous = value;
        }
    }

    /** Calculates a strictly increasing application; invalid levels never yield a credit/refund. */
    public ReplicationCost cost(int currentLevel, int desiredLevel) {
        if (currentLevel < 0 || desiredLevel <= currentLevel || desiredLevel > maxLevel) {
            throw new IllegalArgumentException("Invalid level transition");
        }
        int xpBefore = currentLevel == 0 ? 0 : cumulativeXp.get(currentLevel - 1);
        int lapisBefore = currentLevel == 0 ? 0 : cumulativeLapis.get(currentLevel - 1);
        return new ReplicationCost(cumulativeXp.get(desiredLevel - 1) - xpBefore,
                cumulativeLapis.get(desiredLevel - 1) - lapisBefore, currentLevel == 0 ? 1 : 0);
    }
}
