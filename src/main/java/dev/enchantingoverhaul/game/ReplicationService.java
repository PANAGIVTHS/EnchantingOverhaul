package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.EnchantingOverhaul;
import dev.enchantingoverhaul.core.LibraryKnowledge;
import dev.enchantingoverhaul.core.ReplicationDefinition;
import dev.enchantingoverhaul.core.ReplicationRules;
import java.util.Map;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Prepare copies first; the server thread performs one validated operation with no callbacks/async gaps. */
public final class ReplicationService {
    private static final ReplicationRules RULES = new ReplicationRules(EnchantingOverhaul.CATALOG);
    private ReplicationService() {}
    public static ReplicationRules.Plan plan(Player player, Container inventory, ReplicationDefinition definition,
                                               int desiredLevel, LibraryKnowledge knowledge) {
        ItemStack target = inventory.getItem(0);
        var enchantment = Enchantments.find(player.level(), definition.enchantmentId()).orElse(null);
        ItemStack lapis = inventory.getItem(1), catalyst = inventory.getItem(2);
        var slots = new ReplicationRules.ResourceSlots(BuiltInRegistries.ITEM.getKey(lapis.getItem()).toString(), lapis.getCount(),
                Enchantments.catalyst(catalyst), catalyst.getCount());
        var plan = RULES.plan(new ReplicationRules.Request(definition.enchantmentId(),
                enchantment == null ? 0 : Enchantments.on(target).getLevel(enchantment), desiredLevel, target.getCount(),
                enchantment == null ? 0 : enchantment.value().getMaxLevel(),
                enchantment != null && Enchantments.applicable(target, enchantment),
                enchantment != null && Enchantments.compatible(target, enchantment),
                enchantment != null && enchantment.is(EnchantmentTags.CURSE), knowledge, slots, PlayerExperience.available(player)), player.hasInfiniteMaterials());
        if (plan.accepted() && BottledIngredients.consumeAndReturn(inventory,
                Map.of(1, plan.cost().lapis(), 2, plan.cost().catalystCount()), List.of(2)).isEmpty()) {
            return new ReplicationRules.Plan(ReplicationRules.FailureReason.NO_BOTTLE_SPACE, plan.cost());
        }
        return plan;
    }
    public static ReplicationRules.Plan apply(Player player, Container inventory, ReplicationDefinition definition,
                                                int desiredLevel, LibraryKnowledge freshKnowledge) {
        if (player.level().isClientSide() || player.isSpectator() || !inventory.stillValid(player))
            return new ReplicationRules.Plan(ReplicationRules.FailureReason.INVALID_REQUEST,null);
        var plan = plan(player, inventory, definition, desiredLevel, freshKnowledge);
        if (!plan.accepted()) return plan;
        Holder<Enchantment> enchantment = Enchantments.find(player.level(), definition.enchantmentId()).orElseThrow();
        // Nothing live is mutated until both item transformation and resource copies are ready.
        ItemStack result = Enchantments.appliedCopy(inventory.getItem(0), enchantment, desiredLevel);
        var prepared = BottledIngredients.consumeAndReturn(inventory,
                Map.of(1, plan.cost().lapis(), 2, plan.cost().catalystCount()), List.of(2));
        if (prepared.isEmpty()) return new ReplicationRules.Plan(ReplicationRules.FailureReason.NO_BOTTLE_SPACE, plan.cost());
        PlayerExperience.debit(player, plan.cost().rawXp());
        inventory.setItem(1, prepared.get().get(1));
        inventory.setItem(2, prepared.get().get(2));
        inventory.setItem(0, result); // Target deliberately remains in its input slot.
        inventory.setChanged();
        return plan;
    }
}
