package dev.enchantingoverhaul.core;

import java.util.Objects;

/** Pure server-side planner. No item, slot, player or library is ever mutated.
 * The adapter must rebuild a request from live server state on confirmation, then
 * atomically debit precisely the accepted cost and apply only the selected enchantment. */
public final class ReplicationRules {
    private final ReplicationCatalog catalog;
    public ReplicationRules(ReplicationCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    /** Null item/catalyst identities represent empty slots. Counts are only workstation counts. */
    public record ResourceSlots(String lapisItemId, int lapisCount, Catalyst catalyst, int catalystCount) {}

    /** naturalMaxLevel/applicable/compatible/curse come from the actual server registry/target.
     * curse describes the SELECTED enchantment, not a ban on otherwise cursed target gear.
     * currentLevel is zero only when the selected enchantment is absent. */
    public record Request(String enchantmentId, int currentLevel, int desiredLevel, int targetCount,
                          int naturalMaxLevel, boolean applicable, boolean compatible, boolean curse,
                          LibraryKnowledge knowledge, ResourceSlots resources, long availableRawXp) {}

    public enum FailureReason {
        NONE, INVALID_REQUEST, UNKNOWN_ENCHANTMENT, CURSE_NOT_REPLICABLE,
        INVALID_TARGET_COUNT, INVALID_LEVEL, LEVEL_NOT_INCREASED, LEVEL_NOT_UNLOCKED,
        NOT_APPLICABLE, INCOMPATIBLE, WRONG_LAPIS, INSUFFICIENT_LAPIS,
        WRONG_CATALYST, INSUFFICIENT_CATALYST, INSUFFICIENT_XP, NO_BOTTLE_SPACE
    }

    /** cost is null for semantic failures, but retained for valid previews lacking payment.
     * A non-null cost alone is NOT authorization: always check accepted(). */
    public record Plan(FailureReason failure, ReplicationCost cost) {
        public Plan {
            Objects.requireNonNull(failure);
            if (failure == FailureReason.NONE) Objects.requireNonNull(cost);
        }
        public boolean accepted() { return failure == FailureReason.NONE; }
    }

    public Plan plan(Request request) { return plan(request, false); }

    public Plan plan(Request request, boolean creative) {
        if (request == null || request.enchantmentId() == null || request.knowledge() == null || request.resources() == null
                || request.availableRawXp() < 0 || request.resources().lapisCount() < 0 || request.resources().catalystCount() < 0) {
            return reject(FailureReason.INVALID_REQUEST);
        }
        if (request.curse() || ReplicationCatalog.isCurse(request.enchantmentId())) return reject(FailureReason.CURSE_NOT_REPLICABLE);
        ReplicationDefinition definition = catalog.find(request.enchantmentId()).orElse(null);
        if (definition == null) return reject(FailureReason.UNKNOWN_ENCHANTMENT);
        if (request.targetCount() != 1) return reject(FailureReason.INVALID_TARGET_COUNT);
        int maximum = Math.min(request.naturalMaxLevel(), definition.maxLevel());
        if (maximum < 1 || request.currentLevel() < 0 || request.currentLevel() > maximum
                || request.desiredLevel() < 1 || request.desiredLevel() > maximum) return reject(FailureReason.INVALID_LEVEL);
        if (request.desiredLevel() <= request.currentLevel()) return reject(FailureReason.LEVEL_NOT_INCREASED);
        if (!request.knowledge().unlocks(request.enchantmentId(), request.desiredLevel())) return reject(FailureReason.LEVEL_NOT_UNLOCKED);
        if (!request.applicable()) return reject(FailureReason.NOT_APPLICABLE);
        if (!request.compatible()) return reject(FailureReason.INCOMPATIBLE);
        ReplicationCost nominal = definition.cost(request.currentLevel(), request.desiredLevel());
        ReplicationCost cost = new ReplicationCost(ExperiencePayment.cost(creative, nominal.rawXp()), nominal.lapis(), nominal.catalystCount());
        ResourceSlots slots = request.resources();
        if (slots.lapisCount() < cost.lapis()) return new Plan(FailureReason.INSUFFICIENT_LAPIS, cost);
        if (!"minecraft:lapis_lazuli".equals(slots.lapisItemId())) return new Plan(FailureReason.WRONG_LAPIS, cost);
        if (cost.catalystCount() > 0) {
            if (slots.catalystCount() < cost.catalystCount()) return new Plan(FailureReason.INSUFFICIENT_CATALYST, cost);
            if (!definition.catalyst().equals(slots.catalyst())) return new Plan(FailureReason.WRONG_CATALYST, cost);
        }
        if (request.availableRawXp() < cost.rawXp()) return new Plan(FailureReason.INSUFFICIENT_XP, cost);
        return new Plan(FailureReason.NONE, cost);
    }

    private static Plan reject(FailureReason reason) { return new Plan(reason, null); }
}
