package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.MerchantOfferPresentation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMerchantOffersMixin {
    @ModifyVariable(method = "sendMerchantOffers", at = @At("HEAD"), argsOnly = true)
    private MerchantOffers enchantingoverhaul$sendPurchaseOrder(MerchantOffers offers) {
        // Covers default Merchant.openTradingScreen AND Villager.updateSpecialPrices refreshes.
        // Do not globally intercept getOffers: generation/restocking/save code must see the real list.
        return ((ServerPlayer)(Object)this).containerMenu instanceof MerchantOfferPresentation presentation
                ? presentation.enchantingoverhaul$presentOffers(offers) : offers;
    }
}
