package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.MerchantOfferRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantResultSlot.class)
abstract class MerchantResultSlotOffersMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private MerchantContainer slots;

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void enchantingoverhaul$guardPurchase(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!MerchantOfferRestrictions.permitsPickup(this.merchant,
                this.slots.getActiveOffer(), this.slots.getItem(2))) {
            cir.setReturnValue(false);
        }
        // Allowed offers still pass all vanilla checks; no material/uses/XP changes happen here.
    }
}
