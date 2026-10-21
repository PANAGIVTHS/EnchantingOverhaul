package dev.enchantingoverhaul.block;

import dev.enchantingoverhaul.ModContent;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import dev.enchantingoverhaul.menu.ExtractorMenu;
import dev.enchantingoverhaul.core.ExtractorMigration;
import dev.enchantingoverhaul.game.ExtractionService;
import dev.enchantingoverhaul.menu.CombinerMenu;
import java.util.ArrayList;
import net.minecraft.world.entity.item.ItemEntity;
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
    // Replicator: target / lapis / catalyst. Extractor: target / reagent + two persistent LEGACY escrow slots.
    // Only Extractor slots 0/1 are exposed; never shrink its four-slot backing storage when loading old worlds.
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (kind != WorkstationBlock.Kind.EXTRACTOR) return Container.super.canPlaceItem(slot, stack);
        // Automation cannot fill legacy escrow or introduce a stacked two-slot reagent.
        return stack.getCount() == 1 && (slot == 0 && ExtractionService.acceptsTarget(stack)
                || slot == 1 && ExtractionService.acceptsReagent(stack));
    }

    @Override
    public boolean canTakeItem(Container destination, int slot, ItemStack stack) {
        return kind != WorkstationBlock.Kind.EXTRACTOR || slot < 2;
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

    /** Opening-time recovery only; normal workstation transactions NEVER inspect/withdraw player ingredients. */
    private void recoverExtractorLegacy(Player player, Inventory playerInventory) {
        if (player.level().isClientSide() || player.isSpectator()) return;
        var plan = ExtractorMigration.plan(items.stream().map(ItemStack::getCount).toList());
        long inserted = 0, dropped = 0, escrow = 0;
        for (int sourceSlot = 0; sourceSlot < plan.size(); sourceSlot++) {
            var migration = plan.get(sourceSlot);
            if (migration.recover() == 0) continue;
            ItemStack original = items.get(sourceSlot);
            ItemStack recovery = original.copyWithCount(migration.recover());
            // Detach from persistent ownership before handing anything to another container/entity.
            items.set(sourceSlot, original.copyWithCount(migration.retained()));
            setChanged();
            var indices = new ArrayList<Integer>();
            var capacities = new ArrayList<Integer>();
            // Merge into identical stacks first, then use empty main-inventory/hotbar slots.
            // Do NOT use Inventory.add: vanilla discards overflow for creative players.
            for (boolean empty : new boolean[]{false, true}) {
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    ItemStack existing = playerInventory.getItem(i);
                    if (existing.isEmpty() != empty || !empty && !ItemStack.isSameItemSameComponents(existing, recovery)) continue;
                    int room = Math.max(0, playerInventory.getMaxStackSize(recovery) - existing.getCount());
                    if (room == 0) continue;
                    indices.add(i);
                    capacities.add(room);
                }
            }
            var delivery = ExtractorMigration.delivery(recovery.getCount(), capacities);
            for (int i = 0; i < indices.size(); i++) {
                int amount = delivery.inserted().get(i);
                if (amount == 0) continue;
                int destination = indices.get(i);
                ItemStack existing = playerInventory.getItem(destination);
                playerInventory.setItem(destination, existing.isEmpty() ? recovery.copyWithCount(amount)
                        : existing.copyWithCount(existing.getCount() + amount));
                inserted += amount;
            }
            recovery.setCount(delivery.overflow());
            if (!recovery.isEmpty()) {
                // Dropping is permitted ONLY for legacy recovery overflow, never an extraction output.
                var entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), recovery.copy());
                entity.setTarget(player.getUUID());
                entity.setDefaultPickUpDelay();
                if (player.level().addFreshEntity(entity)) {
                    dropped += recovery.getCount();
                    recovery = ItemStack.EMPTY;
                }
            }
            if (!recovery.isEmpty()) {
                // A failed entity spawn must not delete an item: retain it for the next opening/break.
                items.set(sourceSlot, original.copyWithCount(migration.retained() + recovery.getCount()));
                escrow += recovery.getCount();
            }
            setChanged();
        }
        if (inserted + dropped + escrow > 0) {
            playerInventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("Extractor legacy recovery: " + inserted
                    + " item(s) returned to your inventory, " + dropped + " overflow item(s) dropped at your feet"
                    + (escrow > 0 ? "; " + escrow + " item(s) remain safely stored; reopen to recover" : "") + "."));
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (kind == WorkstationBlock.Kind.EXTRACTOR) recoverExtractorLegacy(player, inventory);
        return switch (kind) {
            case REPLICATOR -> new ReplicatorMenu(containerId, inventory, this);
            case EXTRACTOR -> new ExtractorMenu(containerId, inventory, this);
            case COMBINER -> new CombinerMenu(containerId, inventory, this);
        };
    }

    // Intentionally inherit BlockEntity.preRemoveSideEffects: vanilla drops and drains this
    // Container exactly once. Do not also drop it from block-removal callbacks or loot tables.
}
