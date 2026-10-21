package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.game.AcquisitionEnchantments;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperAcquisitionMixin {
    /** Shared by level-based loot/fishing and both cost-based generated-equipment providers. */
    @ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> enchantingoverhaul$initialCandidates(Stream<Holder<Enchantment>> source) {
        return AcquisitionEnchantments.candidates(source);
    }

    /** Vanilla allocates a count-one blank book here, losing the loot stack's components/count. */
    @Redirect(method = "enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack enchantingoverhaul$keepBookStack(ItemLike item, RandomSource random, ItemStack original,
                                                            int cost, Stream<Holder<Enchantment>> source) {
        return AcquisitionEnchantments.bookCopy(original);
    }
}
