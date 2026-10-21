package dev.enchantingoverhaul.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Scope bookkeeping only: never instantiate an item, registry, world, entity, or game bootstrap. */
@SuppressWarnings("try")
class BookTradeGenerationTest {
    @Test
    void nestedNonBookOfferSuspendsThenRestoresOuterBookScope() {
        assertFalse(BookTradeGeneration.active());
        try (var outer = BookTradeGeneration.open(true)) {
            assertTrue(BookTradeGeneration.active());
            assertFalse(BookTradeGeneration.fixedBookAdjusted());
            try (var inner = BookTradeGeneration.open(false)) {
                assertFalse(BookTradeGeneration.active());
                assertFalse(BookTradeGeneration.fixedBookAdjusted());
            }
            assertTrue(BookTradeGeneration.active());
        }
        assertFalse(BookTradeGeneration.active());
        assertFalse(BookTradeGeneration.fixedBookAdjusted());
    }

    @Test
    void exceptionsCannotLeakBookOfferScopeIntoLaterLoot() {
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = BookTradeGeneration.open(true)) {
                assertTrue(BookTradeGeneration.active());
                throw new IllegalStateException("simulated offer failure");
            }
        });
        assertFalse(BookTradeGeneration.active());
    }

    @Test
    void scopeIsNotInheritedByIndependentThreads() throws Exception {
        try (var ignored = BookTradeGeneration.open(true);
             var executor = Executors.newSingleThreadExecutor()) {
            assertTrue(BookTradeGeneration.active());
            assertFalse(executor.submit(BookTradeGeneration::active).get());
        }
        assertFalse(BookTradeGeneration.active());
    }
}
