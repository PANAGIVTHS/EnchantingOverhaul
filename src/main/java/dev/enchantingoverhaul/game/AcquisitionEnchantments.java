package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.AcquisitionRules;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;

/** Generation-only adapters: do not hook stack loading, enchant(), or updateEnchantments(). */
public final class AcquisitionEnchantments {
    private AcquisitionEnchantments() {}

    public static boolean allowed(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> AcquisitionRules.permitsInitialEnchantment(key.identifier().toString())).orElse(true);
    }

    public static Stream<Holder<Enchantment>> candidates(Stream<Holder<Enchantment>> source) {
        return source.filter(AcquisitionEnchantments::allowed);
    }

    public static ItemStack bookCopy(ItemStack stack) {
        return stack.is(Items.BOOK) ? stack.transmuteCopy(Items.ENCHANTED_BOOK) : stack;
    }

    public static ItemStack librarianBook(ItemStack stack, Holder<Enchantment> enchantment, LootContext context) {
        return librarianBook(stack, enchantment, context, false);
    }

    public static ItemStack librarianBook(ItemStack stack, Holder<Enchantment> enchantment, LootContext context,
                                         boolean fixedRebalanceTrade) {
        RandomSource random = context.getRandom();
        int level = AcquisitionRules.librarianLevel(enchantment.value().getMaxLevel(), random.nextInt(10));
        ItemStack result = bookCopy(stack);
        result.enchant(enchantment, level);
        result.set(DataComponents.ADDITIONAL_TRADE_COST, AcquisitionRules.bookAdditionalCost(level,
                random.nextInt(AcquisitionRules.bookPriceRandomBound(level, fixedRebalanceTrade)), fixedRebalanceTrade));
        return result;
    }

    public static List<Holder<Enchantment>> randomLootCandidates(ItemStack stack, boolean onlyCompatible, LootContext context) {
        boolean book = stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK);
        return context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT)
                .stream().filter(AcquisitionEnchantments::allowed)
                .filter(candidate -> !onlyCompatible || book || candidate.value().canEnchant(stack)).toList();
    }

    /** Reroll a Mending-only generated item, not a loot entry/pool or a preexisting enchantment. */
    public static void replaceMendingOnlyRoll(ItemStack stack, int requestedLevel, LootContext context) {
        if (!EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty()) {
            return;
        }
        List<Holder<Enchantment>> pool = randomLootCandidates(stack, true, context);
        if (!pool.isEmpty()) {
            Holder<Enchantment> selected = pool.get(context.getRandom().nextInt(pool.size()));
            int level = Math.clamp(requestedLevel, selected.value().getMinLevel(), selected.value().getMaxLevel());
            stack.enchant(selected, level);
        }
    }
}
