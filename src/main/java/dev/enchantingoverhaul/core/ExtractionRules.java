package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Objects;

/** Pure two-slot validation and transformation; never mutates inputs or invents ingredient storage. */
public final class ExtractionRules {
    public enum Component { APPLIED, STORED }
    public record Key(Component component, String id) {
        public Key { Objects.requireNonNull(component); Objects.requireNonNull(id); }
    }
    public record Entry(Key key, int level, int naturalMax, boolean curse) {
        public Entry { Objects.requireNonNull(key); }
    }
    public record Source(int count, boolean enchantedBook, boolean eligible, List<Entry> enchantments) {
        public Source { enchantments = List.copyOf(enchantments); }
    }
    public enum ReagentType { EMPTY, BLANK_BOOK, DRAGON_BREATH, OTHER }
    public record Reagent(ReagentType type, int count) {
        public Reagent { Objects.requireNonNull(type); }
    }
    public enum ReagentOutput { NONE, ENCHANTED_BOOK, GLASS_BOTTLE }
    public enum Failure {
        NONE, INVALID_TARGET, INVALID_DATA, SELECT_ENCHANTMENT, CURSED_SOURCE, SINGLE_ENCHANTMENT_BOOK,
        INVALID_LEVEL, OUTPUT_OCCUPIED, INSUFFICIENT_XP, NO_CURSES, MISSING_DRAGON_BREATH, BOTTLE_RETURN_FULL,
        MISSING_BLANK_BOOK, INVALID_REAGENT_COUNT
    }
    /** output is the transferred beneficial entry (null for a curse); reagentOutput replaces slot 1. */
    public record Plan(Failure failure, int rawXp, List<Entry> remaining, Entry output, ReagentOutput reagentOutput) {
        public Plan {
            Objects.requireNonNull(failure);
            Objects.requireNonNull(reagentOutput);
            remaining = List.copyOf(remaining);
        }
        public boolean accepted() { return failure == Failure.NONE; }
    }
    private final ExtractionCosts costs;
    public ExtractionRules(ExtractionCosts costs) { this.costs = Objects.requireNonNull(costs); }

    /** One action for beneficial entries AND curses; only the selected component/key is removed. */
    public Plan extract(Source source, Key selected, Reagent reagent, boolean canAfford) {
        Entry entry = source == null ? null : source.enchantments().stream()
                .filter(e -> e.key().equals(selected)).findFirst().orElse(null);
        int fee = entry == null ? 0 : entry.curse() ? costs.purificationXp()
                : entry.level() >= 1 && entry.level() <= 5 ? costs.extraction(entry.level()) : 0;
        Failure invalid = invalid(source);
        if (invalid != Failure.NONE) return reject(invalid, fee);
        if (entry == null) return reject(Failure.SELECT_ENCHANTMENT, fee);
        if (!entry.curse() && source.enchantments().stream().anyMatch(Entry::curse))
            return reject(Failure.CURSED_SOURCE, fee);
        if (!entry.curse() && source.enchantedBook() && source.enchantments().size() <= 1)
            return reject(Failure.SINGLE_ENCHANTMENT_BOOK, fee);
        if (entry.level() < 1 || entry.level() > entry.naturalMax() || fee == 0)
            return reject(Failure.INVALID_LEVEL, fee);
        if (reagent == null) return reject(Failure.INVALID_DATA, fee);
        // Completed output must be taken; it is never merged, displaced or sent to the player.
        if (reagent.type() == ReagentType.OTHER) return reject(Failure.OUTPUT_OCCUPIED, fee);
        var required = entry.curse() ? ReagentType.DRAGON_BREATH : ReagentType.BLANK_BOOK;
        if (reagent.type() != required)
            return reject(entry.curse() ? Failure.MISSING_DRAGON_BREATH : Failure.MISSING_BLANK_BOOK, fee);
        if (reagent.count() != 1) return reject(Failure.INVALID_REAGENT_COUNT, fee);
        if (!canAfford) return reject(Failure.INSUFFICIENT_XP, fee);
        return new Plan(Failure.NONE, fee,
                source.enchantments().stream().filter(e -> !e.key().equals(selected)).toList(),
                entry.curse() ? null : entry,
                entry.curse() ? ReagentOutput.GLASS_BOTTLE : ReagentOutput.ENCHANTED_BOOK);
    }
    private Failure invalid(Source source) {
        if (source == null || source.count() != 1 || !source.eligible()) return Failure.INVALID_TARGET;
        if (source.enchantments().stream().map(Entry::key).distinct().count() != source.enchantments().size()
                || source.enchantments().stream().anyMatch(e -> e.key().id().isBlank() || e.level() < 1 || e.naturalMax() < 1))
            return Failure.INVALID_DATA;
        return Failure.NONE;
    }
    private Plan reject(Failure failure, int fee) {
        return new Plan(failure, fee, List.of(), null, ReagentOutput.NONE);
    }
}
