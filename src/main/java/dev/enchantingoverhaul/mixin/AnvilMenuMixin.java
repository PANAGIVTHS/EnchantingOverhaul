package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.core.VanillaEnchantingPolicy;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suppress only the sacrifice's enchantments, not ordinary vanilla anvil mechanics. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    /**
     * Actual 26.3 createResult has two getEnchantmentsForCrafting calls:
     * ordinal 0 copies the left/result enchantments; ordinal 1 reads the sacrifice.
     * Keeping ordinal 0 and the repair/name/cost/take paths unchanged preserves the
     * left item's enchantments and vanilla material/item repair. An enchanted-book
     * sacrifice now contributes no application/merge/upgrade (even in creative).
     */
    @Redirect(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentsForCrafting(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/enchantment/ItemEnchantments;",
            ordinal = 1), require = 1, allow = 1)
    private ItemEnchantments enchantingoverhaul$ignoreSacrificeEnchantments(ItemStack sacrifice) {
        return VanillaEnchantingPolicy.anvilEnchantmentSourceAllowed(AnvilMenu.ADDITIONAL_SLOT)
                ? EnchantmentHelper.getEnchantmentsForCrafting(sacrifice) : ItemEnchantments.EMPTY;
    }
}
