package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionRulesTest {
    private static final ExtractionCosts COSTS = ExtractionCosts.loadDefault();
    private static final ExtractionRules RULES = new ExtractionRules(COSTS);
    private static final ExtractionRules.Reagent BOOK = reagent(ExtractionRules.ReagentType.BLANK_BOOK, 1);
    private static final ExtractionRules.Reagent BREATH = reagent(ExtractionRules.ReagentType.DRAGON_BREATH, 1);
    private static final ExtractionRules.Reagent OUTPUT = reagent(ExtractionRules.ReagentType.OTHER, 1);
    private static ExtractionRules.Reagent reagent(ExtractionRules.ReagentType type, int count) {
        return new ExtractionRules.Reagent(type, count);
    }
    private static ExtractionRules.Entry entry(String id, ExtractionRules.Component component, int level, int max, boolean curse) {
        return new ExtractionRules.Entry(new ExtractionRules.Key(component, id), level, max, curse);
    }
    private static ExtractionRules.Entry beneficial(int level, int max) {
        return entry("minecraft:sharpness", ExtractionRules.Component.APPLIED, level, max, false);
    }
    private static ExtractionRules.Source source(boolean book, ExtractionRules.Entry... entries) {
        return new ExtractionRules.Source(1, book, true, List.of(entries));
    }
    static Stream<Arguments> naturalLevels() {
        return IntStream.rangeClosed(1, 5).boxed().flatMap(max -> IntStream.rangeClosed(1, 5)
                .mapToObj(level -> Arguments.of(max, level)));
    }
    @ParameterizedTest @MethodSource("naturalLevels")
    void usesExistingLevelFeeAndNeverExceedsNaturalMaximum(int maximum, int level) {
        var selected = beneficial(level, maximum);
        var plan = RULES.extract(source(false, selected), selected.key(), BOOK, true);
        assertEquals(COSTS.extraction(level), plan.rawXp());
        assertEquals(level <= maximum ? ExtractionRules.Failure.NONE : ExtractionRules.Failure.INVALID_LEVEL, plan.failure());
        if (plan.accepted()) {
            assertEquals(selected, plan.output()); assertTrue(plan.remaining().isEmpty());
            assertEquals(ExtractionRules.ReagentOutput.ENCHANTED_BOOK, plan.reagentOutput());
        }
    }
    static Stream<Arguments> catalogLevels() {
        return ReplicationCatalog.loadDefault().definitions().values().stream().flatMap(def -> IntStream.rangeClosed(1, def.maxLevel())
                .boxed().flatMap(level -> Stream.of(ExtractionRules.Component.values()).flatMap(component -> Stream.of(false, true)
                        .map(book -> Arguments.of(def.enchantmentId(), level, def.maxLevel(), component, book)))));
    }
    @ParameterizedTest @MethodSource("catalogLevels")
    void everyVanillaBeneficialLevelUsesOneSuppliedBookAndPreservesSource(String id, int level, int max, ExtractionRules.Component component, boolean book) {
        var selected = entry(id, component, level, max, false);
        var other = entry("example:unrelated", component, 1, 1, false);
        var original = source(book, selected, other);
        var result = RULES.extract(original, selected.key(), BOOK, true);
        assertTrue(result.accepted());
        assertEquals(COSTS.extraction(level), result.rawXp());
        assertEquals(selected, result.output());
        assertEquals(ExtractionRules.ReagentOutput.ENCHANTED_BOOK, result.reagentOutput());
        assertEquals(List.of(other), result.remaining());
        assertEquals(List.of(selected, other), original.enchantments());
        assertEquals(1, original.count()); assertEquals(book, original.enchantedBook());
        assertThrows(UnsupportedOperationException.class, () -> result.remaining().clear());
    }
    @Test void exactly679PaysSharpnessFiveBut678DoesNotAndNeitherMutatesSource() {
        var selected = beneficial(5, 5);
        var source = source(false, selected);
        assertEquals(ExtractionRules.Failure.INSUFFICIENT_XP,
                RULES.extract(source, selected.key(), BOOK, ExperiencePayment.canAfford(false, 678, 679)).failure());
        assertTrue(RULES.extract(source, selected.key(), BOOK, ExperiencePayment.canAfford(false, 679, 679)).accepted());
        assertEquals(List.of(selected), source.enchantments());
        var after = ExperienceMath.state(679 - COSTS.extraction(5));
        assertEquals(0, after.level()); assertEquals(0f, after.progress());
    }
    @Test void outputInReagentSlotBlocksAnotherOperationWithoutPlayerInventoryOrHiddenOutputRequirements() {
        var selected = beneficial(5, 5);
        var other = entry("minecraft:unbreaking", ExtractionRules.Component.APPLIED, 3, 3, false);
        var first = RULES.extract(source(false, selected, other), selected.key(), BOOK, true);
        assertTrue(first.accepted());
        var after = new ExtractionRules.Source(1, false, true, first.remaining());
        var second = RULES.extract(after, other.key(), OUTPUT, true);
        assertEquals(ExtractionRules.Failure.OUTPUT_OCCUPIED, second.failure());
        assertNull(second.output()); assertTrue(second.remaining().isEmpty());
        assertEquals(ExtractionRules.ReagentOutput.NONE, second.reagentOutput());
        // Taking the output and manually supplying another single book enables the next action.
        assertTrue(RULES.extract(after, other.key(), BOOK, true).accepted());
        assertEquals(List.of(other), after.enchantments());
    }
    @Test void sourceAndSelectedComponentAreIndependentAndOtherComponentIsPreserved() {
        var applied = beneficial(3, 5);
        var stored = entry(applied.key().id(), ExtractionRules.Component.STORED, 4, 5, false);
        var result = RULES.extract(source(false, applied, stored), applied.key(), BOOK, true);
        assertEquals(List.of(stored), result.remaining()); assertEquals(3, result.output().level());
    }
    @Test void missingAndStaleSelectionCannotTransferOrCharge() {
        var selected = beneficial(2, 5);
        var first = RULES.extract(source(false, selected), selected.key(), BOOK, true);
        assertTrue(first.accepted());
        var after = new ExtractionRules.Source(1, false, true, first.remaining());
        assertEquals(ExtractionRules.Failure.SELECT_ENCHANTMENT, RULES.extract(after, selected.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.SELECT_ENCHANTMENT, RULES.extract(source(false, selected), null, BOOK, true).failure());
    }
    @Test void splitBookStopsWhenOneBeneficialEnchantmentRemains() {
        var a = beneficial(3, 5);
        var b = entry("minecraft:mending", ExtractionRules.Component.STORED, 1, 1, false);
        var first = RULES.extract(source(true, a, b), a.key(), BOOK, true);
        assertTrue(first.accepted());
        var last = new ExtractionRules.Source(1, true, true, first.remaining());
        assertEquals(ExtractionRules.Failure.SINGLE_ENCHANTMENT_BOOK, RULES.extract(last, b.key(), BOOK, true).failure());
    }
    static Stream<Arguments> curses() {
        return Stream.of(ExtractionRules.Component.values()).flatMap(component -> Stream.of(false, true)
                .map(book -> Arguments.of(component, book)));
    }
    @ParameterizedTest @MethodSource("curses")
    void anyCurseInEitherComponentBlocksBeneficialButSelectedCurseUsesSameAction(ExtractionRules.Component curseComponent, boolean book) {
        var selected = beneficial(4, 5);
        var curse = entry("minecraft:vanishing_curse", curseComponent, 1, 1, true);
        var source = source(book, selected, curse);
        assertEquals(ExtractionRules.Failure.CURSED_SOURCE, RULES.extract(source, selected.key(), BOOK, true).failure());
        var purified = RULES.extract(source, curse.key(), BREATH, true);
        assertTrue(purified.accepted()); assertEquals(315, purified.rawXp());
        assertEquals(List.of(selected), purified.remaining()); assertNull(purified.output());
        assertEquals(ExtractionRules.ReagentOutput.GLASS_BOTTLE, purified.reagentOutput());
        assertEquals(2, source.enchantments().size());
        var clean = new ExtractionRules.Source(1, book, true, purified.remaining());
        assertEquals(book ? ExtractionRules.Failure.SINGLE_ENCHANTMENT_BOOK : ExtractionRules.Failure.NONE,
                RULES.extract(clean, selected.key(), BOOK, true).failure());
    }
    @Test void eachCurseInBothComponentsRequiresItsOwnSelectionBreathAnd315Xp() {
        var good = beneficial(5, 5);
        var storedGood = entry("minecraft:unbreaking", ExtractionRules.Component.STORED, 17, 3, false);
        List<ExtractionRules.Entry> entries = new ArrayList<>(List.of(good, storedGood));
        for (var component : ExtractionRules.Component.values()) for (String curse : List.of("binding_curse", "vanishing_curse"))
            entries.add(entry("minecraft:" + curse, component, 1, 1, true));
        var source = new ExtractionRules.Source(1, true, true, entries);
        entries.clear();
        var current = source;
        long xp = 1260;
        for (var selected : source.enchantments().stream().filter(ExtractionRules.Entry::curse).toList()) {
            var plan = RULES.extract(current, selected.key(), BREATH, ExperiencePayment.canAfford(false, xp, 315));
            assertTrue(plan.accepted()); assertEquals(315, plan.rawXp());
            assertEquals(current.enchantments().size() - 1, plan.remaining().size());
            assertTrue(plan.remaining().containsAll(List.of(good, storedGood)));
            assertNull(plan.output()); assertEquals(ExtractionRules.ReagentOutput.GLASS_BOTTLE, plan.reagentOutput());
            xp -= plan.rawXp();
            current = new ExtractionRules.Source(1, true, true, plan.remaining());
            if (current.enchantments().stream().anyMatch(ExtractionRules.Entry::curse))
                assertEquals(ExtractionRules.Failure.CURSED_SOURCE, RULES.extract(current, good.key(), BOOK, true).failure());
        }
        assertEquals(0, xp); assertEquals(List.of(good, storedGood), current.enchantments());
        assertEquals(6, source.enchantments().size());
    }
    @Test void curseOnlyBookStaysSameBaseItemAndCanBecomeEmpty() {
        var curse = entry("minecraft:binding_curse", ExtractionRules.Component.STORED, 1, 1, true);
        var book = source(true, curse);
        var plan = RULES.extract(book, curse.key(), BREATH, true);
        assertTrue(plan.accepted()); assertTrue(plan.remaining().isEmpty()); assertNull(plan.output());
        assertTrue(book.enchantedBook()); assertEquals(ExtractionRules.ReagentOutput.GLASS_BOTTLE, plan.reagentOutput());
    }
    @Test void selectedCurseRequiresOneBreathAnd315XpAndCannotReuseReturnedBottle() {
        var curse = entry("minecraft:binding_curse", ExtractionRules.Component.APPLIED, 1, 1, true);
        var source = source(false, curse);
        assertEquals(ExtractionRules.Failure.MISSING_DRAGON_BREATH, RULES.extract(source, curse.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.MISSING_DRAGON_BREATH,
                RULES.extract(source, curse.key(), reagent(ExtractionRules.ReagentType.EMPTY, 0), true).failure());
        assertEquals(ExtractionRules.Failure.INSUFFICIENT_XP,
                RULES.extract(source, curse.key(), BREATH, ExperiencePayment.canAfford(false, 314, 315)).failure());
        assertTrue(RULES.extract(source, curse.key(), BREATH, ExperiencePayment.canAfford(false, 315, 315)).accepted());
        assertEquals(ExtractionRules.Failure.OUTPUT_OCCUPIED, RULES.extract(source, curse.key(), OUTPUT, true).failure());
        assertEquals(List.of(curse), source.enchantments());
    }
    @ParameterizedTest @ValueSource(ints={-1, 0, 2, 64})
    void reagentMustBeExactlyOneForBothOperationsEvenWhenXpWaived(int count) {
        var selected = beneficial(3, 5);
        var curse = entry("minecraft:binding_curse", ExtractionRules.Component.APPLIED, 1, 1, true);
        assertEquals(ExtractionRules.Failure.INVALID_REAGENT_COUNT,
                RULES.extract(source(false, selected), selected.key(), reagent(ExtractionRules.ReagentType.BLANK_BOOK, count), true).failure());
        assertEquals(ExtractionRules.Failure.INVALID_REAGENT_COUNT,
                RULES.extract(source(false, curse), curse.key(), reagent(ExtractionRules.ReagentType.DRAGON_BREATH, count), true).failure());
    }
    @Test void creativeWaivesOnlyXpAndStillRequiresCorrectReagentAndCurseOrder() {
        var good = beneficial(5, 5);
        var curse = entry("minecraft:binding_curse", ExtractionRules.Component.STORED, 1, 1, true);
        boolean creativeAffordable = ExperiencePayment.canAfford(true, 0, 679);
        assertEquals(0, ExperiencePayment.cost(true, 679));
        assertTrue(RULES.extract(source(false, good), good.key(), BOOK, creativeAffordable).accepted());
        assertEquals(ExtractionRules.Failure.MISSING_BLANK_BOOK,
                RULES.extract(source(false, good), good.key(), BREATH, creativeAffordable).failure());
        assertEquals(ExtractionRules.Failure.MISSING_BLANK_BOOK,
                RULES.extract(source(false, good), good.key(), reagent(ExtractionRules.ReagentType.EMPTY, 0), creativeAffordable).failure());
        assertEquals(ExtractionRules.Failure.CURSED_SOURCE,
                RULES.extract(source(false, good, curse), good.key(), BOOK, creativeAffordable).failure());
        assertEquals(ExtractionRules.Failure.MISSING_DRAGON_BREATH,
                RULES.extract(source(false, curse), curse.key(), BOOK, creativeAffordable).failure());
        assertTrue(RULES.extract(source(false, curse), curse.key(), BREATH, creativeAffordable).accepted());
    }
    @ParameterizedTest @ValueSource(ints={-1, 0, 2, 64})
    void targetMustBeExactlyOneEvenWhenXpWaived(int count) {
        var good = beneficial(3, 5);
        var curse = entry("minecraft:binding_curse", ExtractionRules.Component.APPLIED, 1, 1, true);
        assertEquals(ExtractionRules.Failure.INVALID_TARGET,
                RULES.extract(new ExtractionRules.Source(count, false, true, List.of(good)), good.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.INVALID_TARGET,
                RULES.extract(new ExtractionRules.Source(count, false, true, List.of(curse)), curse.key(), BREATH, true).failure());
    }
    @ParameterizedTest @ValueSource(ints={-1, 0, 6, 255})
    void unsupportedLevelsFailWithoutTransferEvenWithXpWaived(int level) {
        var selected = beneficial(level, 5);
        assertFalse(RULES.extract(source(false, selected), selected.key(), BOOK, true).accepted());
    }
    @Test void malformedDataAndUnsupportedBaseItemsFailClosed() {
        var selected = beneficial(1, 5);
        assertEquals(ExtractionRules.Failure.INVALID_DATA, RULES.extract(source(false, selected, selected), selected.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.INVALID_TARGET,
                RULES.extract(new ExtractionRules.Source(1, false, false, List.of(selected)), selected.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.INVALID_TARGET, RULES.extract(null, selected.key(), BOOK, true).failure());
        assertEquals(ExtractionRules.Failure.INVALID_DATA, RULES.extract(source(false, selected), selected.key(), null, true).failure());
    }
}
