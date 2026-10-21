package dev.enchantingoverhaul.block;

import dev.enchantingoverhaul.ModContent;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Persistent inventory shared by all three workstation kinds, without any client-only dependencies. */
public final class WorkstationBlockEntity extends BlockEntity implements Container, MenuProvider {
    // Replicator: target / lapis / catalyst. Extractor: future target / reagent / output.
    // Combiner: book input / book input / output, followed by twenty ingredient slots.
    public static final int TARGET_SLOT = 0;
    public static final int LAPIS_SLOT = 1;
    public static final int CATALYST_SLOT = 2;
    public static final int COMBINER_STORAGE_START = 3;
    public static final int COMBINER_STORAGE_SIZE = 20;

    private final WorkstationBlock.Kind kind;
    private final NonNullList<ItemStack> items;

    public WorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WORKSTATION_ENTITY, pos, state);
        this.kind = ((WorkstationBlock) state.getBlock()).getKind();
        this.items = NonNullList.withSize(kind.inventorySize, ItemStack.EMPTY);
    }

    public WorkstationBlock.Kind getKind() {
        return kind;
    }

    /** Live backing list. Call setChanged() after directly mutating it or one of its stacks. */
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(kind.translationKey());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return kind == WorkstationBlock.Kind.REPLICATOR
                ? new ReplicatorMenu(containerId, inventory, this)
                : null;
    }

    // Intentionally inherit BlockEntity.preRemoveSideEffects: vanilla drops and drains this
    // Container exactly once. Do not also drop it from block-removal callbacks or loot tables.
}
