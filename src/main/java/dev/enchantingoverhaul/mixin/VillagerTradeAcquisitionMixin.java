package dev.enchantingoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.enchantingoverhaul.game.BookTradeGeneration;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VillagerTrade.class)
abstract class VillagerTradeAcquisitionMixin {
    @Shadow @Final private ItemStackTemplate gives;

    @WrapMethod(method = "getOffer")
    @SuppressWarnings("try")
    private MerchantOffer enchantingoverhaul$scopeNewBookOffer(LootContext context, Operation<MerchantOffer> original) {
        // Both default/rebalance book paths share this data-driven 26.3 offer generator.
        // try/finally also restores nesting and exception paths; saved offers are untouched.
        try (BookTradeGeneration ignored = BookTradeGeneration.open(this.gives.create().is(Items.ENCHANTED_BOOK))) {
            return original.call(context);
        }
    }

    @WrapOperation(method = "getOffer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/TradeCost;toItemCost(Lnet/minecraft/world/level/storage/loot/LootContext;I)Lnet/minecraft/world/item/trading/ItemCost;",
            ordinal = 0))
    private ItemCost enchantingoverhaul$priceActualFixedLevel(TradeCost cost, LootContext context, int additional,
                                                             Operation<ItemCost> original) {
        if (BookTradeGeneration.fixedBookAdjusted()) {
            // Rebalance fixed-book prices embed their OLD level in wants.count. Replace just
            // that base, retaining item/components, tag-based doubling above us, and vanilla cap.
            TradeCost actualLevelCost = new TradeCost(cost.item(), ContextIntProviders.exactly(0), cost.components());
            return actualLevelCost.toItemCost(context, additional);
        }
        return original.call(cost, context, additional);
    }
}
