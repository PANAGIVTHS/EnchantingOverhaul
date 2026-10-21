package dev.enchantingoverhaul.game;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Stack-scoped to NEW book-offer generation; never applies to saved MerchantOffers. */
public final class BookTradeGeneration implements AutoCloseable {
    private static final ThreadLocal<BookTradeGeneration> CURRENT = new ThreadLocal<>();
    private final BookTradeGeneration previous;
    private final boolean bookTrade;
    private Holder<Enchantment> fixedEnchantment;
    private int originalFixedLevel;

    private BookTradeGeneration(boolean bookTrade) {
        this.previous = CURRENT.get();
        this.bookTrade = bookTrade;
        CURRENT.set(this);
    }

    public static BookTradeGeneration open(boolean bookTrade) {
        return new BookTradeGeneration(bookTrade);
    }

    public static boolean active() {
        BookTradeGeneration scope = CURRENT.get();
        return scope != null && scope.bookTrade;
    }

    public static void adjustedFixedBook(Holder<Enchantment> enchantment, int originalLevel) {
        BookTradeGeneration scope = CURRENT.get();
        if (scope != null && scope.bookTrade) {
            scope.fixedEnchantment = enchantment;
            scope.originalFixedLevel = originalLevel;
        }
    }

    public static boolean fixedBookAdjusted() {
        BookTradeGeneration scope = CURRENT.get();
        return scope != null && scope.bookTrade && scope.fixedEnchantment != null;
    }

    /** Check the original fixed-level guard using a disposable probe, not the offered stack. */
    public static ItemStack originalLevelProbe(ItemStack offered) {
        BookTradeGeneration scope = CURRENT.get();
        ItemStack probe = offered.copy();
        if (scope != null && scope.bookTrade && scope.fixedEnchantment != null) {
            net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(probe,
                    enchantments -> enchantments.set(scope.fixedEnchantment, scope.originalFixedLevel));
        }
        return probe;
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
