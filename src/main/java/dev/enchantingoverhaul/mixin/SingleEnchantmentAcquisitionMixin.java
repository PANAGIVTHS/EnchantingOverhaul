package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.AcquisitionEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleEnchantment.class)
abstract class SingleEnchantmentAcquisitionMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;

    @Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
    private void enchantingoverhaul$noNewMending(ItemStack item, ItemEnchantments.Mutable enchantments,
                                                RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (!AcquisitionEnchantments.allowed(this.enchantment)) {
            ci.cancel();
        }
    }
}
