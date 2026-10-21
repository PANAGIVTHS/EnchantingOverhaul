package dev.enchantingoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.enchantingoverhaul.game.MerchantOfferRestrictions;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MerchantContainer.class)
abstract class MerchantContainerOffersMixin {
    @Shadow private int futureXp;
    @Shadow public abstract void setItem(int slot, ItemStack stack);

    @WrapOperation(method = "updateSellItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))
    private MerchantOffers enchantingoverhaul$matchPresentedOrder(Merchant merchant, Operation<MerchantOffers> original) {
        MerchantOffers offers = MerchantOfferRestrictions.presented(merchant, original.call(merchant));
        if (offers.isEmpty()) {
            // Vanilla's empty-list branch otherwise leaves an old, unpaid output preview in slot 2.
            // updateSellItem has already cleared activeOffer. Never touch either payment slot.
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        }
        return offers;
    }
}
