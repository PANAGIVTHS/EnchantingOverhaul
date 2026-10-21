package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.MerchantOfferPolicy;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Ephemeral server purchase views; never replace the merchant's persisted offers. */
public final class MerchantOfferRestrictions {
    private MerchantOfferRestrictions() {}

    public static MerchantOffers presented(Merchant merchant, MerchantOffers saved) {
        // Clients already receive the filtered server order and do not know the profession here.
        if (merchant.isClientSide()) return saved;
        MerchantOffers presented = new MerchantOffers();
        presented.addAll(MerchantOfferPolicy.presented(saved, offer -> permits(merchant, offer.getResult())));
        return presented;
    }

    public static boolean permits(Merchant merchant, ItemStack result) {
        if (merchant.isClientSide()) return true;
        boolean librarian = merchant instanceof Villager villager
                && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
        return MerchantOfferPolicy.permits(librarian, result.is(Items.ENCHANTED_BOOK),
                levels(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)),
                levels(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)));
    }

    /** Reread the offer AND actual output before removal, including shift-click packet paths. */
    public static boolean permitsPickup(Merchant merchant, MerchantOffer active, ItemStack preview) {
        return merchant.isClientSide() || (active != null
                && permits(merchant, active.getResult()) && permits(merchant, preview));
    }

    private static Map<String, Integer> levels(ItemEnchantments enchantments) {
        Map<String, Integer> result = new HashMap<>();
        for (var entry : enchantments.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key -> result.put(key.identifier().toString(), entry.getIntValue()));
            // Direct (unregistered) holders cannot be vanilla Mending, but still obey the book cap.
            if (entry.getKey().unwrapKey().isEmpty()) result.merge("<direct>", entry.getIntValue(), Math::max);
        }
        return result;
    }
}
