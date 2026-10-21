package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.MerchantOfferPresentation;
import dev.enchantingoverhaul.game.MerchantOfferRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantMenu.class)
abstract class MerchantMenuOffersMixin implements MerchantOfferPresentation {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;

    @Override
    public MerchantOffers enchantingoverhaul$presentOffers(MerchantOffers offers) {
        return MerchantOfferRestrictions.presented(this.trader, offers);
    }

    @Inject(method = "getOffers", at = @At("RETURN"), cancellable = true)
    private void enchantingoverhaul$presentPurchaseOrder(CallbackInfoReturnable<MerchantOffers> cir) {
        cir.setReturnValue(this.enchantingoverhaul$presentOffers(cir.getReturnValue()));
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void enchantingoverhaul$guardQuickPurchase(Player player, int slotIndex,
                                                       CallbackInfoReturnable<ItemStack> cir) {
        // Vanilla moves the result before onTake, so rejecting payment in onTake would be too late.
        if (slotIndex == 2 && !MerchantOfferRestrictions.permitsPickup(this.trader,
                this.tradeContainer.getActiveOffer(), this.tradeContainer.getItem(2))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
