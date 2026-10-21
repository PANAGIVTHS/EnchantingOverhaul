package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Purchase eligibility only: never transforms an offer, a paid item or library knowledge. */
public final class MerchantOfferPolicy {
    private MerchantOfferPolicy() {}

    public static boolean permits(boolean librarian, boolean enchantedBook,
                                  Map<String, Integer> applied, Map<String, Integer> stored) {
        return permitsComponent(librarian && enchantedBook, applied)
                && permitsComponent(librarian && enchantedBook, stored);
    }

    private static boolean permitsComponent(boolean restrictBookLevels, Map<String, Integer> enchantments) {
        return enchantments.entrySet().stream().noneMatch(entry ->
                ("minecraft:mending".equals(entry.getKey()) && entry.getValue() > 0)
                        || (restrictBookLevels && entry.getValue() > 2));
    }

    /** Stable omission, not rerolling: preserve order and the original mutable offer identities. */
    public static <T> List<T> presented(List<T> saved, Predicate<? super T> allowed) {
        return saved.stream().filter(allowed).toList();
    }
}
