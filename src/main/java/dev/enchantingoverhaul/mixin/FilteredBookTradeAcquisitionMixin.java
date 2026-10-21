package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.BookTradeGeneration;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.functions.FilteredFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FilteredFunction.class)
abstract class FilteredBookTradeAcquisitionMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/predicates/ItemPredicate;test(Lnet/minecraft/world/item/ItemInstance;)Z"))
    private boolean enchantingoverhaul$fixedBookLevelGuard(ItemPredicate predicate, ItemInstance item) {
        if (predicate.test(item)) {
            return true;
        }
        // The exact-level guard was built for the original III/V book. Test the SAME
        // predicate against an original-level COPY; never bypass item/count/other checks,
        // never mutate the offered stack, and never affect non-book or ordinary loot filters.
        return BookTradeGeneration.fixedBookAdjusted() && item instanceof ItemStack stack && stack.is(Items.ENCHANTED_BOOK)
                && predicate.test(BookTradeGeneration.originalLevelProbe(stack));
    }
}
