package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.ExtractionCosts;
import dev.enchantingoverhaul.core.ExtractionRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Server-thread transactions: prepare both slot replacements and validate XP before any mutation. */
public final class ExtractionService {
    public static final ExtractionCosts COSTS = ExtractionCosts.loadDefault();
    private static final ExtractionRules RULES = new ExtractionRules(COSTS);
    private ExtractionService() {}
    private static DataComponentType<ItemEnchantments> type(ExtractionRules.Component component) {
        return component == ExtractionRules.Component.STORED ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }
    public static boolean acceptsTarget(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.BOOK)) return false;
        if (stack.is(Items.ENCHANTED_BOOK) || stack.isDamageableItem()) return true;
        for (var component : ExtractionRules.Component.values())
            if (stack.getOrDefault(type(component), ItemEnchantments.EMPTY).keySet().stream()
                    .anyMatch(e -> e.value().isSupportedItem(stack))) return true;
        return false;
    }
    public static boolean acceptsReagent(ItemStack stack) {
        // A book with enchantments attached through commands is not a supplied blank book.
        return !stack.isEmpty() && (stack.is(Items.DRAGON_BREATH)
                || stack.is(Items.BOOK) && entries(stack).isEmpty());
    }
    public static List<ExtractionRules.Entry> entries(ItemStack target) {
        List<ExtractionRules.Entry> entries = new ArrayList<>();
        for (var component : ExtractionRules.Component.values()) {
            var enchantments = target.getOrDefault(type(component), ItemEnchantments.EMPTY);
            for (var enchantment : enchantments.keySet())
                entries.add(new ExtractionRules.Entry(new ExtractionRules.Key(component, Enchantments.id(enchantment)),
                        enchantments.getLevel(enchantment), enchantment.value().getMaxLevel(), enchantment.is(EnchantmentTags.CURSE)));
        }
        return List.copyOf(entries);
    }
    public static ExtractionRules.Source source(ItemStack target) {
        return new ExtractionRules.Source(target.getCount(), target.is(Items.ENCHANTED_BOOK), acceptsTarget(target), entries(target));
    }
    private static ExtractionRules.Reagent reagent(ItemStack stack) {
        var type = stack.isEmpty() ? ExtractionRules.ReagentType.EMPTY
                : !acceptsReagent(stack) ? ExtractionRules.ReagentType.OTHER
                : stack.is(Items.BOOK) ? ExtractionRules.ReagentType.BLANK_BOOK : ExtractionRules.ReagentType.DRAGON_BREATH;
        return new ExtractionRules.Reagent(type, stack.getCount());
    }
    public static ExtractionRules.Plan extractionPlan(Player player, Container inventory, ExtractionRules.Key selected) {
        var snapshot = source(inventory.getItem(0));
        var reagent = reagent(inventory.getItem(1));
        var preview = RULES.extract(snapshot, selected, reagent, true);
        return RULES.extract(snapshot, selected, reagent, PlayerExperience.canAfford(player, preview.rawXp()));
    }
    public static ExtractionRules.Plan extract(Player player, Container inventory, ExtractionRules.Key selected) {
        var plan = extractionPlan(player, inventory, selected);
        if (!plan.accepted()) return plan;
        if (player.level().isClientSide() || player.isSpectator() || !inventory.stillValid(player))
            return new ExtractionRules.Plan(ExtractionRules.Failure.INVALID_TARGET, plan.rawXp(), List.of(), null,
                    ExtractionRules.ReagentOutput.NONE);
        // Holder resolution and both transformed copies are prepared before touching XP or live slots.
        var holder = Enchantments.find(player.level(), selected.id()).orElse(null);
        if (holder == null) return new ExtractionRules.Plan(ExtractionRules.Failure.INVALID_DATA, plan.rawXp(), List.of(), null,
                ExtractionRules.ReagentOutput.NONE);
        ItemStack result = inventory.getItem(0).copy();
        var changed = new ItemEnchantments.Mutable(result.getOrDefault(type(selected.component()), ItemEnchantments.EMPTY));
        changed.set(holder, 0);
        result.set(type(selected.component()), changed.toImmutable());
        // Transmutation preserves supplied reagent custom components; the target remains the same base item.
        ItemStack output = inventory.getItem(1).transmuteCopy(
                plan.reagentOutput() == ExtractionRules.ReagentOutput.GLASS_BOTTLE ? Items.GLASS_BOTTLE : Items.ENCHANTED_BOOK, 1);
        if (plan.reagentOutput() == ExtractionRules.ReagentOutput.ENCHANTED_BOOK) {
            var book = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            book.set(holder, plan.output().level());
            output.set(DataComponents.STORED_ENCHANTMENTS, book.toImmutable());
        }
        PlayerExperience.debit(player, plan.rawXp());
        inventory.setItem(0, result);
        inventory.setItem(1, output);
        inventory.setChanged();
        return plan;
    }
}
