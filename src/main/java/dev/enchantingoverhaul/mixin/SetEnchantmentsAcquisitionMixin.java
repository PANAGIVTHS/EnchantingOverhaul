package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.AcquisitionEnchantments;
import dev.enchantingoverhaul.game.BookTradeGeneration;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetEnchantmentsFunction.class)
abstract class SetEnchantmentsAcquisitionMixin {
    @Shadow @Final private Map<Holder<Enchantment>, Holder<ContextIntProvider>> enchantments;
    @Shadow @Final private boolean add;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void enchantingoverhaul$generationAssignments(ItemStack input, LootContext context,
                                                          CallbackInfoReturnable<ItemStack> cir) {
        // Rebalance master librarians use set_enchantments followed by an exact-level filter,
        // NOT EnchantRandomlyFunction. Keep the fixed enchantment and biome eligibility.
        if (BookTradeGeneration.active() && this.enchantments.size() == 1) {
            var entry = this.enchantments.entrySet().iterator().next();
            if (AcquisitionEnchantments.allowed(entry.getKey())) {
                int originalLevel = Mth.clamp(entry.getValue().value().getInt(context), 0, 255);
                BookTradeGeneration.adjustedFixedBook(entry.getKey(), originalLevel);
                cir.setReturnValue(AcquisitionEnchantments.librarianBook(input, entry.getKey(), context, true));
            } else {
                // The vanilla downstream valid-book filter suppresses the unavailable trade.
                cir.setReturnValue(input);
            }
            return;
        }
        if (this.enchantments.keySet().stream().allMatch(AcquisitionEnchantments::allowed)) {
            return;
        }
        ItemStack result = AcquisitionEnchantments.bookCopy(input);
        // Ignore only new Mending assignments; do not remove an existing component entry.
        EnchantmentHelper.updateEnchantments(result, mutable -> this.enchantments.forEach((enchantment, provider) -> {
            if (AcquisitionEnchantments.allowed(enchantment)) {
                int value = provider.value().getInt(context);
                mutable.set(enchantment, Mth.clamp(this.add ? mutable.getLevel(enchantment) + value : value, 0, 255));
            }
        }));
        // Explicit Mending-only vault/experimental chest entries must remain real loot stacks.
        // Never reroll a mixed book or an existing enchanted/player stack.
        if (!BookTradeGeneration.active()) {
            int requestedLevel = this.enchantments.entrySet().stream()
                    .filter(entry -> !AcquisitionEnchantments.allowed(entry.getKey()))
                    .mapToInt(entry -> entry.getValue().value().getInt(context)).max().orElse(0);
            if (requestedLevel > 0) {
                AcquisitionEnchantments.replaceMendingOnlyRoll(result, requestedLevel, context);
            }
        }
        cir.setReturnValue(result);
    }
}
