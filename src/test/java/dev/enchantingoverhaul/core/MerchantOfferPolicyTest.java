package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class MerchantOfferPolicyTest {
    static Stream<Arguments> mendingSurfaces() {
        var cases = new ArrayList<Arguments>();
        for (boolean librarian : List.of(false, true))
            for (boolean book : List.of(false, true))
                for (boolean stored : List.of(false, true))
                    cases.add(Arguments.of(librarian, book, stored));
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("mendingSurfaces")
    void everyMerchantRejectsMendingInEitherComponent(boolean librarian, boolean book, boolean stored) {
        Map<String, Integer> forbidden = Map.of("minecraft:mending", 1, "minecraft:unbreaking", 2);
        assertFalse(MerchantOfferPolicy.permits(librarian, book,
                stored ? Map.of() : forbidden, stored ? forbidden : Map.of()));
    }

    static Stream<Arguments> levelSurfaces() {
        var cases = new ArrayList<Arguments>();
        for (boolean librarian : List.of(false, true))
            for (boolean book : List.of(false, true))
                for (boolean stored : List.of(false, true))
                    for (int level : List.of(1, 2, 3, 4, 5, 255))
                        cases.add(Arguments.of(librarian, book, stored, level));
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("levelSurfaces")
    void highLevelRestrictionIsOnlyLibrarianBooks(boolean librarian, boolean book, boolean stored, int level) {
        Map<String, Integer> enchantments = Map.of("minecraft:sharpness", level);
        assertEquals(!(librarian && book && level > 2), MerchantOfferPolicy.permits(librarian, book,
                stored ? Map.of() : enchantments, stored ? enchantments : Map.of()));
    }

    @Test void mixedBooksAreRejectedWholeWithoutRemovingEnchantments() {
        var stored = new HashMap<>(Map.of("minecraft:sharpness", 2, "minecraft:unbreaking", 3,
                "minecraft:vanishing_curse", 1));
        var original = Map.copyOf(stored);
        assertFalse(MerchantOfferPolicy.permits(true, true, Map.of(), stored));
        assertEquals(original, stored);
        assertTrue(MerchantOfferPolicy.permits(false, true, Map.of(), stored));
    }

    @Test void ordinaryCurseAndUnknownEligibleOffersRemainUnchanged() {
        assertTrue(MerchantOfferPolicy.permits(true, true,
                Map.of("minecraft:binding_curse", 1), Map.of("minecraft:vanishing_curse", 1)));
        assertTrue(MerchantOfferPolicy.permits(true, true, Map.of(), Map.of("anothermod:mending", 2)));
        assertFalse(MerchantOfferPolicy.permits(true, true, Map.of(), Map.of("anothermod:enchantment", 3)));
        assertTrue(MerchantOfferPolicy.permits(false, false, Map.of(), Map.of()));
    }

    private static final class Offer {
        final boolean allowed;
        int uses;
        final int price;
        final int demand;
        final int discount;
        Offer(boolean allowed, int uses, int price, int demand, int discount) {
            this.allowed = allowed;
            this.uses = uses;
            this.price = price;
            this.demand = demand;
            this.discount = discount;
        }
    }

    @Test void presentationPreservesSavedOrderOfferIdentityAndEconomicState() {
        var forbidden = new Offer(false, 3, 45, 8, -12);
        var first = new Offer(true, 2, 7, 4, -3);
        var second = new Offer(true, 5, 31, 6, -5);
        var saved = new ArrayList<>(List.of(forbidden, first, forbidden, second));
        var view = MerchantOfferPolicy.presented(saved, offer -> offer.allowed);
        assertEquals(List.of(first, second), view);
        assertSame(first, view.getFirst());
        assertSame(second, view.get(1));
        assertEquals(List.of(forbidden, first, forbidden, second), saved);
        assertEquals(List.of(2, 7, 4, -3), List.of(first.uses, first.price, first.demand, first.discount));
        assertEquals(List.of(5, 31, 6, -5), List.of(second.uses, second.price, second.demand, second.discount));
        // Vanilla notifyTrade increments the ORIGINAL offer, not a detached copy.
        view.get(1).uses++;
        assertEquals(6, saved.get(3).uses);
        assertEquals(List.of(3, 45, 8, -12), List.of(forbidden.uses, forbidden.price, forbidden.demand, forbidden.discount));
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertEquals(view, MerchantOfferPolicy.presented(saved, offer -> offer.allowed));
    }

    @Test void clientMenuAndServerRecipeMatchingUseTheSameCompactedIndexOrder() {
        var zero = new Offer(false, 0, 30, 0, 0);
        var one = new Offer(true, 1, 5, 0, 0);
        var two = new Offer(false, 2, 60, 0, 0);
        var three = new Offer(true, 3, 12, 0, 0);
        var saved = List.of(zero, one, two, three);
        var packet = MerchantOfferPolicy.presented(saved, offer -> offer.allowed);
        var autoFill = MerchantOfferPolicy.presented(saved, offer -> offer.allowed);
        var matching = MerchantOfferPolicy.presented(saved, offer -> offer.allowed);
        for (int hint = 0; hint < packet.size(); hint++) {
            assertSame(packet.get(hint), autoFill.get(hint));
            assertSame(packet.get(hint), matching.get(hint));
        }
        assertSame(three, matching.get(1));
        assertFalse(matching.contains(zero));
        assertFalse(matching.contains(two));
    }

    @Test void savedLibrarianArmorerAndWanderingStockUsesTheSameNarrowPolicy() {
        record SavedResult(boolean book, Map<String, Integer> applied, Map<String, Integer> stored) {}
        var mendingBook = new SavedResult(true, Map.of(), Map.of("minecraft:mending", 1));
        var highBook = new SavedResult(true, Map.of(), Map.of("minecraft:sharpness", 5));
        var basicBook = new SavedResult(true, Map.of(), Map.of("minecraft:sharpness", 2));
        var armorerMending = new SavedResult(false,
                Map.of("minecraft:protection", 4, "minecraft:mending", 1), Map.of());
        var armorerOrdinary = new SavedResult(false, Map.of("minecraft:protection", 4), Map.of());
        var saved = List.of(mendingBook, highBook, basicBook, armorerMending, armorerOrdinary);
        var librarian = MerchantOfferPolicy.presented(saved,
                result -> MerchantOfferPolicy.permits(true, result.book(), result.applied(), result.stored()));
        var otherMerchant = MerchantOfferPolicy.presented(saved,
                result -> MerchantOfferPolicy.permits(false, result.book(), result.applied(), result.stored()));
        assertEquals(List.of(basicBook, armorerOrdinary), librarian);
        assertEquals(List.of(highBook, basicBook, armorerOrdinary), otherMerchant);
        assertEquals(List.of(mendingBook, highBook, basicBook, armorerMending, armorerOrdinary), saved);
        // Previously acquired loot/library results are not rewritten or stripped by checking eligibility.
        assertEquals(Map.of("minecraft:protection", 4, "minecraft:mending", 1), armorerMending.applied());
        assertEquals(Map.of("minecraft:sharpness", 5), highBook.stored());
    }

    @Test void allForbiddenAndEmptyViewsDoNotDeleteTheSavedOffers() {
        var forbidden = new Offer(false, 2, 50, 8, -9);
        var saved = new ArrayList<>(List.of(forbidden));
        assertTrue(MerchantOfferPolicy.presented(saved, offer -> offer.allowed).isEmpty());
        assertEquals(1, saved.size());
        assertSame(forbidden, saved.getFirst());
        assertTrue(MerchantOfferPolicy.presented(List.of(), ignored -> true).isEmpty());
    }
}
