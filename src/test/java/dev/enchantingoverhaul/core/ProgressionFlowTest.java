package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-system tests exercise pure plans only; they deliberately do not bootstrap Minecraft. */
class ProgressionFlowTest {
    private static final ExtractionRules.Reagent BOOK = new ExtractionRules.Reagent(ExtractionRules.ReagentType.BLANK_BOOK, 1);
    private static final ExtractionRules.Reagent BREATH = new ExtractionRules.Reagent(ExtractionRules.ReagentType.DRAGON_BREATH, 1);
    @Test void extractedDiscoveryUnlocksReplicationThenSynthesisUnlocksNextLevel() {
        var catalog = ReplicationCatalog.loadDefault();
        var extraction = new ExtractionRules(ExtractionCosts.loadDefault());
        var selected = new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED,"minecraft:smite"),3,5,false);
        var unchanged = new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED,"minecraft:unbreaking"),3,3,false);
        var source = new ExtractionRules.Source(1,false,true,List.of(selected,unchanged));
        var transferred = extraction.extract(source,selected.key(),BOOK,ExperiencePayment.canAfford(false,280,280));
        assertTrue(transferred.accepted()); assertEquals(List.of(unchanged),transferred.remaining());
        assertEquals(0,ExperienceMath.state(280-transferred.rawXp()).level());
        var knowledge = LibraryKnowledge.aggregate(catalog,List.of(LibraryKnowledge.Blueprint.clean(transferred.output().key().id(),transferred.output().level())));
        assertEquals(List.of(1,2,3),knowledge.unlockedLevels("minecraft:smite"));
        var definition = catalog.require("minecraft:smite");
        var replication = new ReplicationRules(catalog);
        var resources = new ReplicationRules.ResourceSlots("minecraft:lapis_lazuli",9,definition.catalyst(),1);
        var request = new ReplicationRules.Request("minecraft:smite",0,3,1,5,true,true,false,knowledge,resources,110);
        for (int book=0;book<2;book++) assertEquals(new ReplicationCost(110,9,1),replication.plan(request).cost());
        var books = new SynthesisRules.Book(true,false,Map.of("minecraft:smite",3),1);
        List<SynthesisRules.Material> materials = new ArrayList<>(Collections.nCopies(20,SynthesisRules.EMPTY_MATERIAL));
        materials.set(0,new SynthesisRules.Material("minecraft:splash_potion","minecraft:healing",1,false));
        materials.set(1,new SynthesisRules.Material("minecraft:glistering_melon_slice",null,2,false));
        var synthesis = new SynthesisRules(SynthesisCatalog.loadDefault());
        var synthesized = synthesis.plan(new SynthesisRules.Request(books,books,5,materials,true));
        assertTrue(synthesized.accepted()); assertEquals(4,synthesized.recipe().resultLevel());
        assertEquals(1,synthesized.consumed().get(1)); assertEquals(1,synthesized.bottleReturnCount());
        var advanced = LibraryKnowledge.aggregate(catalog,List.of(LibraryKnowledge.Blueprint.clean("minecraft:smite",4)));
        assertEquals(List.of(1,2,3,4),advanced.unlockedLevels("minecraft:smite"));
        assertFalse(knowledge.unlocks("minecraft:smite",4));
    }
    @Test void cursedLootMustPaySeparatePurificationBeforeExtractionOrBlueprintUse() {
        var catalog=ReplicationCatalog.loadDefault();
        var extraction=new ExtractionRules(ExtractionCosts.loadDefault());
        var beneficial=new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED,"minecraft:sharpness"),5,5,false);
        var curse=new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED,"minecraft:vanishing_curse"),1,1,true);
        var source=new ExtractionRules.Source(1,false,true,List.of(beneficial,curse));
        assertFalse(extraction.extract(source,beneficial.key(),BOOK,true).accepted());
        assertEquals(0,LibraryKnowledge.aggregate(catalog,List.of(new LibraryKnowledge.Blueprint(true,true,Map.of("minecraft:sharpness",5)))).highestLevel("minecraft:sharpness"));
        var purification=extraction.extract(source,curse.key(),BREATH,ExperiencePayment.canAfford(false,994,315));
        assertTrue(purification.accepted());
        var clean=new ExtractionRules.Source(1,false,true,purification.remaining());
        long balance=994-purification.rawXp();
        assertEquals(679,balance);
        var salvaged=extraction.extract(clean,beneficial.key(),BOOK,ExperiencePayment.canAfford(false,balance,679));
        assertTrue(salvaged.accepted()); assertEquals(0,balance-salvaged.rawXp());
        assertEquals(5,LibraryKnowledge.aggregate(catalog,List.of(LibraryKnowledge.Blueprint.clean(salvaged.output().key().id(),salvaged.output().level()))).highestLevel("minecraft:sharpness"));
    }
    @Test void twoCursesRequireTwoBreathsTwoFeesAndOutputPickupBeforeBookSalvage() {
        var extraction = new ExtractionRules(ExtractionCosts.loadDefault());
        var good = new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED, "minecraft:sharpness"), 5, 5, false);
        var binding = new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.STORED, "minecraft:binding_curse"), 1, 1, true);
        var vanishing = new ExtractionRules.Entry(new ExtractionRules.Key(ExtractionRules.Component.APPLIED, "minecraft:vanishing_curse"), 1, 1, true);
        var source = new ExtractionRules.Source(1, false, true, List.of(good, binding, vanishing));
        long xp = 1309;
        int breaths = 0;
        for (var curse : List.of(binding, vanishing)) {
            var plan = extraction.extract(source, curse.key(), BREATH, ExperiencePayment.canAfford(false, xp, 315));
            assertTrue(plan.accepted());
            assertEquals(ExtractionRules.ReagentOutput.GLASS_BOTTLE, plan.reagentOutput());
            xp -= plan.rawXp(); breaths++;
            source = new ExtractionRules.Source(1, false, true, plan.remaining());
            // Leaving the glass bottle in slot 1 cannot authorize the next extraction.
            var occupied = new ExtractionRules.Reagent(ExtractionRules.ReagentType.OTHER, 1);
            var next = source.enchantments().stream().filter(ExtractionRules.Entry::curse).findFirst().orElse(good);
            assertEquals(ExtractionRules.Failure.OUTPUT_OCCUPIED, extraction.extract(source, next.key(), occupied, true).failure());
            if (source.enchantments().stream().anyMatch(ExtractionRules.Entry::curse)) {
                assertEquals(ExtractionRules.Failure.CURSED_SOURCE, extraction.extract(source, good.key(), BOOK, true).failure());
                assertEquals(0, LibraryKnowledge.aggregate(ReplicationCatalog.loadDefault(),
                        List.of(new LibraryKnowledge.Blueprint(true, true, Map.of("minecraft:sharpness", 5)))).highestLevel("minecraft:sharpness"));
            }
        }
        assertEquals(2, breaths); assertEquals(679, xp);
        var salvaged = extraction.extract(source, good.key(), BOOK, ExperiencePayment.canAfford(false, xp, 679));
        assertTrue(salvaged.accepted()); assertEquals(0, xp - salvaged.rawXp());
        assertEquals(ExtractionRules.ReagentOutput.ENCHANTED_BOOK, salvaged.reagentOutput());
        assertTrue(salvaged.remaining().isEmpty());
    }
    @Test void everySynthesisPaymentAlsoFitsBottleAccountingAndPreservesSurplus() {
        for(var recipe:SynthesisCatalog.loadDefault().recipes()) {
            List<SynthesisRules.Material> materials=new ArrayList<>(Collections.nCopies(20,SynthesisRules.EMPTY_MATERIAL));
            List<BottleAccounting.Slot> slots=new ArrayList<>(Collections.nCopies(20,new BottleAccounting.Slot(0,64,false,false)));
            for(int i=0;i<recipe.ingredients().size();i++) {
                var ingredient=recipe.ingredients().get(i);
                var id=ingredient.itemIds().getFirst();
                materials.set(i,new SynthesisRules.Material(id,ingredient.potionId(),ingredient.count(),ingredient.standardEquipment()));
                slots.set(i,new BottleAccounting.Slot(ingredient.count(),64,SynthesisRules.returnsBottle(id),false));
            }
            var book=new SynthesisRules.Book(true,false,Map.of(recipe.enchantmentId(),recipe.inputLevel()),1);
            var synthesis=new SynthesisRules(SynthesisCatalog.loadDefault()).plan(new SynthesisRules.Request(book,book,recipe.resultLevel(),materials,true));
            assertTrue(synthesis.accepted());
            Map<Integer,Integer> payments=new java.util.HashMap<>();
            for(int i=0;i<20;i++) payments.put(i,synthesis.consumed().get(i));
            var bottles=BottleAccounting.plan(slots,payments,java.util.stream.IntStream.range(0,20).boxed().toList()).orElseThrow();
            assertEquals(synthesis.bottleReturnCount(),bottles.bottlesAdded().stream().mapToInt(Integer::intValue).sum());
            assertEquals(synthesis.consumed(),bottles.consumed());
        }
    }
}
