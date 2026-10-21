package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.SynthesisCatalog;
import dev.enchantingoverhaul.core.SynthesisRecipe;
import dev.enchantingoverhaul.core.SynthesisRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Prepares the entire synthesis result on copies; consumes workstation storage only. */
public final class SynthesisService {
    public static final SynthesisCatalog CATALOG = SynthesisCatalog.loadDefault();
    private static final SynthesisRules RULES = new SynthesisRules(CATALOG);
    private static final List<Integer> STORAGE = IntStream.range(3,23).boxed().toList();
    private SynthesisService() {}
    public enum Status { READY, INVALID_BOOKS, CURSED_BOOKS, MISMATCHED_BOOKS, MAXIMUM_LEVEL,
                         NO_RECIPE, MISSING_INGREDIENTS, OUTPUT_BLOCKED, NO_BOTTLE_SPACE }
    public record Plan(Status status, SynthesisRecipe recipe, List<ItemStack> prepared) {
        public Plan { prepared = List.copyOf(prepared); }
        public boolean accepted() { return status == Status.READY; }
    }
    public static SynthesisRules.Book book(ItemStack stack) {
        Map<String,Integer> enchantments = new HashMap<>();
        Enchantments.on(stack).entrySet().forEach(e -> enchantments.put(Enchantments.id(e.getKey()), e.getIntValue()));
        return new SynthesisRules.Book(stack.is(Items.ENCHANTED_BOOK), Enchantments.hasCurse(stack), enchantments, stack.getCount());
    }
    public static SynthesisRules.Material material(ItemStack stack) {
        var identity = Enchantments.catalyst(stack);
        // An invalid/custom potion variant is deliberately not represented as a valid base potion.
        String potion = identity == null ? null : identity.potionId();
        boolean pristine = stack.getDamageValue() == 0
                && stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty()
                && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty()
                && ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
        return new SynthesisRules.Material(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), potion, stack.getCount(), pristine);
    }
    public static Plan plan(Player player, Container inventory) {
        var first = book(inventory.getItem(0));
        var second = book(inventory.getItem(1));
        int naturalMax = first.enchantments().size() != 1 ? 0 : Enchantments.find(player.level(), first.enchantments().keySet().iterator().next())
                .map(e -> e.value().getMaxLevel()).orElse(0);
        List<SynthesisRules.Material> materials = new ArrayList<>();
        for (int slot : STORAGE) materials.add(material(inventory.getItem(slot)));
        var rule = RULES.plan(new SynthesisRules.Request(first,second,naturalMax,materials,inventory.getItem(2).isEmpty()));
        if (!rule.accepted()) return new Plan(status(rule.failure()), rule.recipe(), List.of());
        Map<Integer,Integer> consumption = new HashMap<>();
        for (int i=0;i<rule.consumed().size();i++) if (rule.consumed().get(i)>0) consumption.put(i+3,rule.consumed().get(i));
        var resources = BottledIngredients.consumeAndReturn(inventory,consumption,STORAGE);
        if (resources.isEmpty()) return new Plan(Status.NO_BOTTLE_SPACE,rule.recipe(),List.of());
        var enchantment = Enchantments.find(player.level(),rule.recipe().enchantmentId()).orElseThrow();
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment,rule.recipe().resultLevel());
        EnchantmentHelper.setEnchantments(result,enchantments.toImmutable());
        List<ItemStack> prepared = new ArrayList<>(resources.get());
        ItemStack left = prepared.get(0).copy(), right = prepared.get(1).copy();
        left.shrink(1); right.shrink(1);
        prepared.set(0,left); prepared.set(1,right); prepared.set(2,result);
        return new Plan(Status.READY,rule.recipe(),prepared);
    }
    private static Status status(SynthesisRules.Failure failure) {
        return switch (failure) {
            case NONE -> Status.READY;
            case CURSED_BOOK -> Status.CURSED_BOOKS;
            case MISMATCHED_ENCHANTMENTS, MISMATCHED_LEVELS -> Status.MISMATCHED_BOOKS;
            case INVALID_LEVEL, NATURAL_MAXIMUM -> Status.MAXIMUM_LEVEL;
            case OUTPUT_FULL -> Status.OUTPUT_BLOCKED;
            case INSUFFICIENT_MATERIALS -> Status.MISSING_INGREDIENTS;
            case NO_RECIPE -> Status.NO_RECIPE;
            default -> Status.INVALID_BOOKS;
        };
    }
    public static Plan apply(Player player, Container inventory) {
        if (player.level().isClientSide() || player.isSpectator() || !inventory.stillValid(player))
            return new Plan(Status.INVALID_BOOKS,null,List.of());
        var plan = plan(player,inventory); // Live validation; no preview result is authorization.
        if (!plan.accepted()) return plan;
        for (int i=0;i<inventory.getContainerSize();i++) inventory.setItem(i,plan.prepared().get(i));
        inventory.setChanged();
        return plan;
    }
}
