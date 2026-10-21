package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.BottleAccounting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Never modifies live slots or the player inventory. Capacity failure rejects the whole operation. */
public final class BottledIngredients {
    private BottledIngredients() {}
    public static boolean bottled(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.DRAGON_BREATH) || stack.is(Items.OMINOUS_BOTTLE);
    }
    public static Optional<List<ItemStack>> consumeAndReturn(Container inventory, Map<Integer,Integer> consumption,
                                                             List<Integer> returnSlots) {
        List<BottleAccounting.Slot> slots = new ArrayList<>();
        List<ItemStack> copies = new ArrayList<>();
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            // Reject malformed overstacked data rather than deleting or legitimizing it.
            int max = stack.isEmpty() ? 64 : Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize(stack));
            if (stack.getCount() > max) return Optional.empty();
            slots.add(new BottleAccounting.Slot(stack.getCount(), max, bottled(stack),
                    ItemStack.isSameItemSameComponents(stack, bottle)));
            copies.add(stack.copy());
        }
        var plan = BottleAccounting.plan(slots, consumption, returnSlots);
        if (plan.isEmpty()) return Optional.empty();
        for (int i = 0; i < copies.size(); i++) {
            ItemStack copy = copies.get(i);
            copy.shrink(plan.get().consumed().get(i));
            int returned = plan.get().bottlesAdded().get(i);
            if (returned > 0) {
                if (copy.isEmpty()) copy = new ItemStack(Items.GLASS_BOTTLE, returned);
                else copy.grow(returned);
            }
            copies.set(i, copy);
        }
        return Optional.of(List.copyOf(copies));
    }
}
