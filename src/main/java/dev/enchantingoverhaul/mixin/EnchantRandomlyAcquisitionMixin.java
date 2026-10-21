package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.core.AcquisitionRules;
import dev.enchantingoverhaul.game.AcquisitionEnchantments;
import dev.enchantingoverhaul.game.BookTradeGeneration;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantRandomlyFunction.class)
abstract class EnchantRandomlyAcquisitionMixin {
    @Shadow @Final private boolean onlyCompatible;

    /** Filter before choosing, rather than deleting Mending after a successful catch/loot roll. */
    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
    private Optional<Holder<Enchantment>> enchantingoverhaul$withoutMending(List<Holder<Enchantment>> choices, RandomSource random,
                                                                         ItemStack stack, LootContext context) {
        List<Holder<Enchantment>> allowed = choices.stream().filter(AcquisitionEnchantments::allowed).toList();
        if (AcquisitionRules.rerollEmptyLootPool(choices.size(), allowed.size(), BookTradeGeneration.active())) {
            // The rebalance Ancient City has an explicit Mending-only book entry. Reroll
            // its enchantment, never discard the weighted book entry or yield a blank book.
            // Do NOT do this for librarians: a Mending-only biome trade has no eligible substitute.
            allowed = AcquisitionEnchantments.randomLootCandidates(stack, this.onlyCompatible, context);
        }
        return Util.getRandomSafe(allowed, random);
    }

    @Inject(method = "enchantItem", at = @At("HEAD"), cancellable = true)
    private void enchantingoverhaul$librarianLevelAndPrice(ItemStack stack, Holder<Enchantment> enchantment,
                                                          LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        if (BookTradeGeneration.active()) {
            cir.setReturnValue(AcquisitionEnchantments.librarianBook(stack, enchantment, context));
        }
    }

    /** Selection/compatibility has already run, so conversion here cannot change the candidate set. */
    @ModifyVariable(method = "enchantItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack enchantingoverhaul$preserveBookStack(ItemStack stack) {
        return AcquisitionEnchantments.bookCopy(stack);
    }
}
