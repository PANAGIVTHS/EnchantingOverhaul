package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.core.ReplicationCatalog;
import dev.enchantingoverhaul.core.VanillaEnchantingPolicy;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The result Slot is anonymous class $4, verified by cached 26.3 bytecode. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneExperienceMixin {
    @Unique
    private static final ReplicationCatalog enchantingoverhaul$replication = ReplicationCatalog.loadDefault();

    /**
     * Vanilla sums each non-curse's min_cost, then rolls ceil(sum/2) + nextInt(ceil(sum/2)).
     * Channeling I and Swift Sneak I each contribute 25 against a 20-XP replication
     * fee, allowing a 25-XP refund. Reduce only the excess contribution; keep the
     * vanilla two-input aggregation, RNG, drops, repairs and curse retention.
     */
    @Inject(method = "getExperienceFromItem", at = @At("RETURN"), cancellable = true, require = 1)
    private void enchantingoverhaul$boundReplicationRefund(ItemStack item, CallbackInfoReturnable<Integer> cir) {
        int amount = cir.getReturnValueI();
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(item).entrySet()) {
            var enchantment = entry.getKey();
            if (enchantment.is(EnchantmentTags.CURSE)) continue;
            var key = enchantment.unwrapKey();
            if (key.isEmpty()) continue;
            var definition = enchantingoverhaul$replication.find(key.get().identifier().toString());
            int level = entry.getIntValue();
            // Non-catalog/modded enchantments and non-natural levels stay vanilla.
            if (definition.isEmpty() || level < 1 || level > definition.get().maxLevel()) continue;
            int vanilla = enchantment.value().getMinCost(level);
            int capped = VanillaEnchantingPolicy.grindstoneContribution(vanilla,
                    definition.get().cost(0, level).rawXp());
            amount -= vanilla - capped;
        }
        cir.setReturnValue(amount);
    }
}
