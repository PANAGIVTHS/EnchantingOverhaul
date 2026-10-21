package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import static dev.enchantingoverhaul.core.ReplicationRules.FailureReason.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReplicationRulesTest {
    private static final ReplicationCatalog CATALOG = ReplicationCatalog.loadDefault();
    private static final ReplicationRules RULES = new ReplicationRules(CATALOG);
    private static final LibraryKnowledge KNOWLEDGE = LibraryKnowledge.aggregate(CATALOG,
            CATALOG.definitions().values().stream().map(d -> LibraryKnowledge.Blueprint.clean(d.enchantmentId(), d.maxLevel())).toList());

    private static ReplicationRules.Request request(String id, int current, int desired) {
        var definition = CATALOG.require(id);
        return new ReplicationRules.Request(id, current, desired, 1, definition.maxLevel(), true, true, false,
                KNOWLEDGE, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64, definition.catalyst(), 64), 10000);
    }
    private static ReplicationRules.Request payment(ReplicationRules.Request r, ReplicationRules.ResourceSlots slots, long xp) {
        return new ReplicationRules.Request(r.enchantmentId(), r.currentLevel(), r.desiredLevel(), r.targetCount(),
                r.naturalMaxLevel(), r.applicable(), r.compatible(), r.curse(), r.knowledge(), slots, xp);
    }
    private static void rejects(ReplicationRules.Request request, ReplicationRules.FailureReason expected) {
        var plan = RULES.plan(request);
        assertFalse(plan.accepted());
        assertEquals(expected, plan.failure());
    }

    static Stream<Arguments> paths() {
        var paths = new ArrayList<Arguments>();
        for (var definition : CATALOG.definitions().values()) {
            for (int desired = 1; desired <= definition.maxLevel(); desired++) {
                // Every ordered upgrade path: each lower level is either visited or skipped.
                for (int mask = 0; mask < (1 << (desired - 1)); mask++) {
                    paths.add(Arguments.of(definition.enchantmentId(), desired, mask));
                }
            }
        }
        return paths.stream();
    }

    @ParameterizedTest @MethodSource("paths")
    void everyUpgradePathTelescopesToDirectApplication(String id, int desired, int mask) {
        var direct = RULES.plan(request(id, 0, desired));
        assertTrue(direct.accepted());
        int previous = 0, xp = 0, lapis = 0, catalysts = 0;
        for (int level = 1; level <= desired; level++) {
            if (level != desired && (mask & (1 << (level - 1))) == 0) continue;
            var operation = request(id, previous, level);
            if (previous > 0) operation = payment(operation,
                    new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64, null, 0), 10000);
            var plan = RULES.plan(operation);
            assertTrue(plan.accepted());
            assertEquals(previous == 0 ? 1 : 0, plan.cost().catalystCount());
            xp += plan.cost().rawXp();
            lapis += plan.cost().lapis();
            catalysts += plan.cost().catalystCount();
            previous = level;
        }
        assertEquals(direct.cost(), new ReplicationCost(xp, lapis, catalysts));
    }

    static Stream<Arguments> transitions() {
        var transitions = new ArrayList<Arguments>();
        for (var definition : CATALOG.definitions().values())
            for (int desired = 1; desired <= definition.maxLevel(); desired++)
                for (int current = 0; current < desired; current++)
                    transitions.add(Arguments.of(definition.enchantmentId(), current, desired));
        return transitions.stream();
    }

    @ParameterizedTest @MethodSource("transitions")
    void exactResourceThresholdsRejectWithoutMutatingAnything(String id, int current, int desired) {
        var original = request(id, current, desired);
        var cost = RULES.plan(original).cost();
        var slots = new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", cost.lapis(),
                current == 0 ? CATALOG.require(id).catalyst() : null, cost.catalystCount());
        var exact = payment(original, slots, cost.rawXp());
        assertTrue(RULES.plan(exact).accepted());
        var missingXp = payment(exact, slots, cost.rawXp() - 1L);
        var xpPlan = RULES.plan(missingXp);
        assertEquals(INSUFFICIENT_XP, xpPlan.failure());
        assertEquals(cost, xpPlan.cost());
        assertEquals(cost.rawXp() - 1L, missingXp.availableRawXp());
        var missingLapis = payment(exact, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", cost.lapis() - 1,
                slots.catalyst(), slots.catalystCount()), cost.rawXp());
        assertEquals(INSUFFICIENT_LAPIS, RULES.plan(missingLapis).failure());
        assertEquals(cost, RULES.plan(missingLapis).cost());
        if (current == 0) {
            var missingCatalyst = payment(exact, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", cost.lapis(),
                    slots.catalyst(), 0), cost.rawXp());
            assertEquals(INSUFFICIENT_CATALYST, RULES.plan(missingCatalyst).failure());
            assertEquals(cost, RULES.plan(missingCatalyst).cost());
        }
        // Replanning never debits even a successful request or changes physical-library knowledge.
        assertEquals(new ReplicationRules.Plan(NONE, cost), RULES.plan(exact));
        assertEquals(slots, exact.resources());
        assertEquals(cost.rawXp(), exact.availableRawXp());
        assertEquals(CATALOG.require(id).maxLevel(), exact.knowledge().highestLevel(id));
    }

    @Test void confirmedSmiteAndMendingExamplesAndBookGearCostParity() {
        assertEquals(new ReplicationCost(90, 7, 0), RULES.plan(request("minecraft:smite", 1, 3)).cost());
        assertEquals(new ReplicationCost(110, 9, 1), RULES.plan(request("minecraft:smite", 0, 3)).cost());
        assertEquals(new ReplicationCost(240, 18, 1), RULES.plan(request("minecraft:mending", 0, 1)).cost());
        // Domain intentionally has no price-affecting target-kind flag: both adapters use this same request.
        var blankBook = request("minecraft:smite", 0, 3);
        var eligibleSword = request("minecraft:smite", 0, 3);
        assertEquals(RULES.plan(blankBook), RULES.plan(eligibleSword));
    }

    @ParameterizedTest @CsvSource({"-1,1,5,INVALID_LEVEL", "0,0,5,INVALID_LEVEL", "0,-1,5,INVALID_LEVEL",
            "0,6,5,INVALID_LEVEL", "6,7,5,INVALID_LEVEL", "2,2,5,LEVEL_NOT_INCREASED", "3,1,5,LEVEL_NOT_INCREASED",
            "0,1,0,INVALID_LEVEL", "0,3,2,INVALID_LEVEL", "4,5,3,INVALID_LEVEL",
            "-2147483648,1,5,INVALID_LEVEL", "0,2147483647,5,INVALID_LEVEL", "2147483647,1,5,INVALID_LEVEL"})
    void rejectsForgedLevelsAndDowngrades(int current, int desired, int naturalMax, ReplicationRules.FailureReason failure) {
        var r = request("minecraft:smite", 0, 1);
        rejects(new ReplicationRules.Request(r.enchantmentId(), current, desired, 1, naturalMax,
                true, true, false, KNOWLEDGE, r.resources(), 10000), failure);
    }

    @ParameterizedTest @ValueSource(ints = {-2147483648, -1, 0, 2, 64, 2147483647})
    void targetMustBeExactlyOne(int count) {
        var r = request("minecraft:smite", 0, 1);
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 1, count, 5,
                true, true, false, KNOWLEDGE, r.resources(), 10000), INVALID_TARGET_COUNT);
    }

    @Test void enforcesKnowledgeRegistryMaximumApplicabilityCompatibilityAndCurses() {
        var r = request("minecraft:smite", 0, 3);
        var low = LibraryKnowledge.aggregate(CATALOG, List.of(LibraryKnowledge.Blueprint.clean("minecraft:smite", 2)));
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 3, 1, 5, true, true, false, low, r.resources(), 10000), LEVEL_NOT_UNLOCKED);
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 3, 1, 5, false, true, false, KNOWLEDGE, r.resources(), 10000), NOT_APPLICABLE);
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 3, 1, 5, true, false, false, KNOWLEDGE, r.resources(), 10000), INCOMPATIBLE);
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 3, 1, 5, true, true, true, KNOWLEDGE, r.resources(), 10000), CURSE_NOT_REPLICABLE);
        for (String id : List.of("minecraft:binding_curse", "minecraft:vanishing_curse"))
            rejects(new ReplicationRules.Request(id, 0, 1, 1, 1, true, true, false, KNOWLEDGE, r.resources(), 10000), CURSE_NOT_REPLICABLE);
        for (String id : List.of("minecraft:unknown", "NOT AN ID", "", " minecraft:smite"))
            rejects(new ReplicationRules.Request(id, 0, 1, 1, 1, true, true, false, KNOWLEDGE, r.resources(), 10000), UNKNOWN_ENCHANTMENT);
        rejects(new ReplicationRules.Request("minecraft:mending", 0, 2, 1, 100, true, true, false, KNOWLEDGE, r.resources(), 10000), INVALID_LEVEL);
        var removed = LibraryKnowledge.aggregate(CATALOG, List.of());
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 3, 1, 5, true, true, false, removed, r.resources(), 10000), LEVEL_NOT_UNLOCKED);
    }

    @Test void validatesExactSlotItemsAndBasePotionVariants() {
        var r = request("minecraft:smite", 0, 1);
        for (var wrong : List.of(Catalyst.item("minecraft:flint"),
                new Catalyst("minecraft:potion", "minecraft:healing"),
                new Catalyst("minecraft:splash_potion", "minecraft:strong_healing"),
                new Catalyst("minecraft:lingering_potion", "minecraft:healing"))) {
            rejects(payment(r, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64, wrong, 1), 10000), WRONG_CATALYST);
        }
        rejects(payment(r, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64, null, 1), 10000), WRONG_CATALYST);
        rejects(payment(r, new ReplicationRules.ResourceSlots("minecraft:dirt", 64, r.resources().catalyst(), 1), 10000), WRONG_LAPIS);
        rejects(payment(r, new ReplicationRules.ResourceSlots(null, 64, r.resources().catalyst(), 1), 10000), WRONG_LAPIS);
        var upgrade = request("minecraft:smite", 1, 3);
        assertTrue(RULES.plan(payment(upgrade, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64,
                Catalyst.item("minecraft:dirt"), 64), 10000)).accepted());
        for (String id : List.of("minecraft:depth_strider", "minecraft:bane_of_arthropods")) {
            var potionRequest = request(id, 0, 1);
            String incorrect = id.endsWith("depth_strider") ? "minecraft:long_swiftness" : "minecraft:swiftness";
            rejects(payment(potionRequest, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64,
                    new Catalyst("minecraft:potion", incorrect), 1), 10000), WRONG_CATALYST);
        }
    }

    @Test void rejectsNullsAndNegativeResourceCountsWithoutExceptionsOrOverflow() {
        var r = request("minecraft:smite", 0, 1);
        rejects(null, INVALID_REQUEST);
        rejects(new ReplicationRules.Request(null, 0, 1, 1, 5, true, true, false, KNOWLEDGE, r.resources(), 10000), INVALID_REQUEST);
        rejects(new ReplicationRules.Request(r.enchantmentId(), 0, 1, 1, 5, true, true, false, null, r.resources(), 10000), INVALID_REQUEST);
        rejects(payment(r, null, 10000), INVALID_REQUEST);
        rejects(payment(r, r.resources(), -1), INVALID_REQUEST);
        rejects(payment(r, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", -1, null, 0), 10000), INVALID_REQUEST);
        rejects(payment(r, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", 64, null, -1), 10000), INVALID_REQUEST);
        assertTrue(RULES.plan(payment(r, new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli", Integer.MAX_VALUE,
                r.resources().catalyst(), Integer.MAX_VALUE), Long.MAX_VALUE)).accepted());
        assertThrows(NullPointerException.class, () -> new ReplicationRules.Plan(NONE, null));
    }
}
