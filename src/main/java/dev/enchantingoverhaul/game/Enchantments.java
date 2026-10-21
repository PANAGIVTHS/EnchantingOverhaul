package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.Catalyst;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/** Version-specific enchantment/component bridge; never trusts client item metadata. */
public final class Enchantments {
    private Enchantments() {}
    public static Optional<Holder.Reference<Enchantment>> find(Level level, String id) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)));
    }
    public static String id(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().orElseThrow().identifier().toString();
    }
    public static ItemEnchantments on(ItemStack stack) { return EnchantmentHelper.getEnchantmentsForCrafting(stack); }
    public static boolean isBook(ItemStack stack) { return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK); }
    public static boolean hasCurse(ItemStack stack) {
        return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream().anyMatch(e -> e.is(EnchantmentTags.CURSE))
                || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream().anyMatch(e -> e.is(EnchantmentTags.CURSE));
    }
    public static boolean applicable(ItemStack target, Holder<Enchantment> enchantment) {
        return !target.isEmpty() && !enchantment.is(EnchantmentTags.CURSE)
                && (isBook(target) ? !hasCurse(target) : enchantment.value().isSupportedItem(target));
    }
    public static boolean compatible(ItemStack target, Holder<Enchantment> selected) {
        return on(target).keySet().stream().allMatch(existing -> existing.equals(selected) || Enchantment.areCompatible(existing, selected));
    }
    public static Catalyst catalyst(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean potion = stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
        if (!potion) return Catalyst.item(id);
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        // Exact vanilla base variant: custom mixed potion contents do not substitute.
        if (contents == null || contents.potion().isEmpty() || !contents.customEffects().isEmpty()) return null;
        return contents.potion().get().unwrapKey().map(k -> new Catalyst(id, k.identifier().toString())).orElse(null);
    }
    public static ItemStack catalystStack(Catalyst catalyst) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(Identifier.parse(catalyst.itemId())).getDefaultInstance();
        if (catalyst.potionId() != null) {
            var potion = BuiltInRegistries.POTION.get(ResourceKey.create(Registries.POTION,
                    Identifier.parse(catalyst.potionId()))).orElseThrow();
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        }
        return stack;
    }
    public static ItemStack appliedCopy(ItemStack original, Holder<Enchantment> enchantment, int level) {
        ItemStack result = original.is(Items.BOOK) ? original.transmuteCopy(Items.ENCHANTED_BOOK, 1) : original.copy();
        ItemEnchantments.Mutable updated = new ItemEnchantments.Mutable(on(original));
        updated.set(enchantment, level);
        EnchantmentHelper.setEnchantments(result, updated.toImmutable());
        return result;
    }
}
