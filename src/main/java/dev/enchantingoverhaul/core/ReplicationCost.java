package dev.enchantingoverhaul.core;

/** Resources to debit for one operation; rawXp is points, never displayed levels. */
public record ReplicationCost(int rawXp, int lapis, int catalystCount) {
    public ReplicationCost {
        if (rawXp < 0 || lapis < 0 || catalystCount < 0 || catalystCount > 1) {
            throw new IllegalArgumentException("Invalid replication cost");
        }
    }
}
