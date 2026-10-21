package dev.enchantingoverhaul.menu;

import dev.enchantingoverhaul.EnchantingOverhaul;
import dev.enchantingoverhaul.ModContent;
import dev.enchantingoverhaul.block.WorkstationBlockEntity;
import dev.enchantingoverhaul.core.LibraryKnowledge;
import dev.enchantingoverhaul.core.ReplicationDefinition;
import dev.enchantingoverhaul.core.ReplicationRules;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.game.LibraryScanner;
import dev.enchantingoverhaul.game.PlayerExperience;
import dev.enchantingoverhaul.game.ReplicationService;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla menu slots/data are the entire protocol. No client-supplied cost, item or XP payload exists. */
public final class ReplicatorMenu extends AbstractContainerMenu {
    public static final List<ReplicationDefinition> DEFINITIONS = EnchantingOverhaul.DEFINITIONS;
    public static final int WIDTH = 320, HEIGHT = 232;
    private static final int BASE = DEFINITIONS.size();
    private static final int SELECTED = BASE, LEVEL = BASE + 1, XP = BASE + 2, LAPIS = BASE + 3,
            CATALYST = BASE + 4, STATUS = BASE + 5, PLAYER_XP = BASE + 6;
    private final Container inventory;
    private final Player owner;
    private final SimpleContainerData data = new SimpleContainerData(BASE + 7);

    public ReplicatorMenu(int syncId, Inventory playerInventory, Container inventory) {
        super(ModContent.REPLICATOR_MENU, syncId);
        checkContainerSize(inventory, 3);
        this.inventory = inventory;
        this.owner = playerInventory.player;
        data.set(SELECTED, -1);
        data.set(LEVEL, 1);
        data.set(STATUS, ReplicationRules.FailureReason.INVALID_REQUEST.ordinal());
        addSlot(new Slot(inventory, 0, 20, 40) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) {
                return Enchantments.isBook(stack) || DEFINITIONS.stream().anyMatch(d ->
                        Enchantments.find(owner.level(), d.enchantmentId()).map(e -> e.value().isSupportedItem(stack)).orElse(false));
            }
        });
        addSlot(new Slot(inventory, 1, 60, 40) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }
        });
        addSlot(new Slot(inventory, 2, 100, 40));
        addStandardInventorySlots(playerInventory, 8, 150);
        addDataSlots(data);
    }
    public int availableMax(int index) { return data.get(index); }
    public int selectedIndex() { return data.get(SELECTED); }
    public int desiredLevel() { return data.get(LEVEL); }
    public int xpCost() { return data.get(XP); }
    public int lapisCost() { return data.get(LAPIS); }
    public int catalystCount() { return data.get(CATALYST); }
    public int playerXp() { return data.get(PLAYER_XP); }
    public ReplicationRules.FailureReason status() {
        int value = data.get(STATUS);
        return value >= 0 && value < ReplicationRules.FailureReason.values().length
                ? ReplicationRules.FailureReason.values()[value] : ReplicationRules.FailureReason.INVALID_REQUEST;
    }
    public ReplicationDefinition selected() {
        int index = selectedIndex();
        return index < 0 || index >= DEFINITIONS.size() ? null : DEFINITIONS.get(index);
    }
    public static int selectionButton(int index, int level) { return 1 + index * 5 + level - 1; }
    @Override public boolean stillValid(Player player) {
        return player == owner && inventory.stillValid(player);
    }
    private LibraryKnowledge freshKnowledge() {
        return inventory instanceof WorkstationBlockEntity workstation
                ? LibraryScanner.scan(owner.level(), workstation.getBlockPos())
                : LibraryKnowledge.aggregate(EnchantingOverhaul.CATALOG, List.of());
    }
    private void refresh(LibraryKnowledge knowledge) {
        ItemStack target = inventory.getItem(0);
        for (int i = 0; i < DEFINITIONS.size(); i++) {
            var def = DEFINITIONS.get(i);
            var enchantment = Enchantments.find(owner.level(), def.enchantmentId()).orElse(null);
            boolean eligible = enchantment != null && (target.isEmpty()
                    || Enchantments.applicable(target, enchantment) && Enchantments.compatible(target, enchantment));
            data.set(i, eligible ? Math.min(knowledge.highestLevel(def.enchantmentId()), enchantment.value().getMaxLevel()) : 0);
        }
        data.set(PLAYER_XP, (int)Math.min(32767, PlayerExperience.available(owner)));
        var def = selected();
        var plan = def == null ? null : ReplicationService.plan(owner, inventory, def, desiredLevel(), knowledge);
        var cost = plan == null ? null : plan.cost();
        data.set(XP, cost == null ? 0 : cost.rawXp());
        data.set(LAPIS, cost == null ? 0 : cost.lapis());
        data.set(CATALYST, cost == null ? 0 : cost.catalystCount());
        data.set(STATUS, plan == null ? ReplicationRules.FailureReason.INVALID_REQUEST.ordinal() : plan.failure().ordinal());
    }
    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide() && stillValid(owner)) refresh(freshKnowledge());
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide() || player.isSpectator() || !stillValid(player)
                || button < 0 || button > DEFINITIONS.size() * 5) return false;
        var knowledge = freshKnowledge(); // Never authorize using a stale UI snapshot.
        refresh(knowledge);
        if (button == 0) {
            var def = selected();
            if (def == null) return false;
            ReplicationService.apply(player, inventory, def, desiredLevel(), knowledge);
        } else if (button >= 1 && button <= DEFINITIONS.size() * 5) {
            int index = (button - 1) / 5, level = (button - 1) % 5 + 1;
            if (level > availableMax(index)) return false;
            data.set(SELECTED, index);
            data.set(LEVEL, level);
        } else return false;
        refresh(knowledge);
        super.broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), copy = original.copy();
        boolean moved;
        if (index < 3) moved = moveItemStackTo(original, 3, slots.size(), true);
        else if (original.is(Items.LAPIS_LAZULI)) moved = moveItemStackTo(original, 1, 2, false);
        else if (slots.get(0).mayPlace(original)) moved = moveItemStackTo(original, 0, 1, false);
        else moved = moveItemStackTo(original, 2, 3, false);
        if (!moved) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original);
        return copy;
    }
}
