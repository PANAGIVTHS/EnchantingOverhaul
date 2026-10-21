package dev.enchantingoverhaul.game;

import net.minecraft.world.item.trading.MerchantOffers;

/** Bridge used by outgoing initial and refreshed offer packets to share the menu's policy/order. */
public interface MerchantOfferPresentation {
    MerchantOffers enchantingoverhaul$presentOffers(MerchantOffers offers);
}
