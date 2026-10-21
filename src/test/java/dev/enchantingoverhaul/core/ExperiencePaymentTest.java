package dev.enchantingoverhaul.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperiencePaymentTest {
    @Test void creativeIgnoresXpButSurvivalRequiresExactPoints() {
        for (int fee : new int[]{20,55,110,180,275,240,160,280,441,679,315}) {
            assertEquals(0, ExperiencePayment.cost(true, fee));
            assertTrue(ExperiencePayment.canAfford(true, 0, fee));
            assertFalse(ExperiencePayment.canAfford(false, fee-1L, fee));
            assertTrue(ExperiencePayment.canAfford(false, fee, fee));
        }
    }
    @Test void creativeReplicationKeepsAllNonXpRules() {
        var catalog = ReplicationCatalog.loadDefault();
        var rules = new ReplicationRules(catalog);
        for (var definition : catalog.definitions().values()) {
            var knowledge = LibraryKnowledge.aggregate(catalog, List.of(LibraryKnowledge.Blueprint.clean(definition.enchantmentId(),definition.maxLevel())));
            var slots = new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli",64,definition.catalyst(),1);
            var request = new ReplicationRules.Request(definition.enchantmentId(),0,definition.maxLevel(),1,definition.maxLevel(),true,true,false,knowledge,slots,0);
            var plan = rules.plan(request,true);
            assertTrue(plan.accepted());
            assertEquals(0,plan.cost().rawXp());
            assertEquals(definition.cost(0,definition.maxLevel()).lapis(),plan.cost().lapis());
            assertEquals(1,plan.cost().catalystCount());
            assertEquals(ReplicationRules.FailureReason.INSUFFICIENT_XP,rules.plan(request).failure());
            var emptyMaterials = new ReplicationRules.Request(definition.enchantmentId(),0,definition.maxLevel(),1,definition.maxLevel(),true,true,false,knowledge,
                    new ReplicationRules.ResourceSlots(null,0,null,0),0);
            assertFalse(rules.plan(emptyMaterials,true).accepted());
            var missingKnowledge = new ReplicationRules.Request(definition.enchantmentId(),0,definition.maxLevel(),1,definition.maxLevel(),true,true,false,
                    LibraryKnowledge.aggregate(catalog,List.of()),slots,0);
            assertEquals(ReplicationRules.FailureReason.LEVEL_NOT_UNLOCKED,rules.plan(missingKnowledge,true).failure());
            var incompatible = new ReplicationRules.Request(definition.enchantmentId(),0,definition.maxLevel(),1,definition.maxLevel(),true,false,false,knowledge,slots,0);
            assertEquals(ReplicationRules.FailureReason.INCOMPATIBLE,rules.plan(incompatible,true).failure());
        }
    }
    @Test void corruptCostsCannotBecomeFree() {
        assertThrows(IllegalArgumentException.class,()->ExperiencePayment.cost(true,-1));
        assertThrows(IllegalArgumentException.class,()->ExperiencePayment.cost(false,-1));
        assertFalse(ExperiencePayment.canAfford(true,-1,10));
    }
}
