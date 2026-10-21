package dev.enchantingoverhaul.menu;

import dev.enchantingoverhaul.ModContent;
import dev.enchantingoverhaul.core.ExtractionRules;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.game.ExtractionService;
import dev.enchantingoverhaul.game.PlayerExperience;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Two visible workstation slots; one server-authoritative action removes the selected entry. */
public final class ExtractorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 320, HEIGHT = 232;
    public static final int EXTRACT_BUTTON = 0;
    private static final int VISIBLE_SLOTS = 2;
    // Preserve the established selection-button IDs; the former Purify button (1) is rejected.
    private static final int SELECTION_BUTTON_START = 2;
    private final Container inventory;
    private final Player owner;
    private final List<ExtractionRules.Key> choices;
    private final List<Boolean> curses;
    private final SimpleContainerData data;
    private final int selected, xp, extractionStatus, playerXp, creative;
    private ItemStack previousTarget = ItemStack.EMPTY;

    public ExtractorMenu(int syncId, Inventory playerInventory, Container inventory) {
        super(ModContent.EXTRACTOR_MENU, syncId);
        checkContainerSize(inventory, VISIBLE_SLOTS);
        this.inventory = inventory;
        owner = playerInventory.player;
        var enchantments = owner.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .sorted(Comparator.comparing(Enchantments::id)).toList();
        var keys = new ArrayList<ExtractionRules.Key>();
        var curseFlags = new ArrayList<Boolean>();
        for (var enchantment : enchantments) for (var component : ExtractionRules.Component.values()) {
            keys.add(new ExtractionRules.Key(component, Enchantments.id(enchantment)));
            curseFlags.add(enchantment.is(EnchantmentTags.CURSE));
        }
        choices = List.copyOf(keys);
        curses = List.copyOf(curseFlags);
        selected = choices.size(); xp = selected + 1; extractionStatus = selected + 2;
        playerXp = selected + 3; creative = selected + 4;
        data = new SimpleContainerData(selected + 5);
        data.set(selected, -1);
        data.set(extractionStatus, ExtractionRules.Failure.SELECT_ENCHANTMENT.ordinal());
        addSlot(new Slot(inventory, 0, 20, 40) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) { return ExtractionService.acceptsTarget(stack); }
        });
        addSlot(new Slot(inventory, 1, 60, 40) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) { return ExtractionService.acceptsReagent(stack); }
        });
        addStandardInventorySlots(playerInventory, 8, 150);
        addDataSlots(data);
    }
    public List<ExtractionRules.Key> choices() { return choices; }
    public boolean isCurse(int index) { return index >= 0 && index < curses.size() && curses.get(index); }
    public int level(int index) { return index < 0 || index >= choices.size() ? 0 : data.get(index); }
    public int selectedIndex() { return data.get(selected); }
    public ExtractionRules.Key selection() { return selectedIndex() >= 0 && selectedIndex() < choices.size() ? choices.get(selectedIndex()) : null; }
    public int xpCost() { return data.get(xp); }
    public int playerXp() { return data.get(playerXp); }
    public boolean creativeXp() { return data.get(creative) != 0; }
    public ExtractionRules.Failure extractionStatus() { return failure(data.get(extractionStatus)); }
    private ExtractionRules.Failure failure(int value) {
        return value >= 0 && value < ExtractionRules.Failure.values().length ? ExtractionRules.Failure.values()[value] : ExtractionRules.Failure.INVALID_DATA;
    }
    public static int selectionButton(int index) { return index + SELECTION_BUTTON_START; }
    @Override public boolean stillValid(Player player) { return player == owner && inventory.stillValid(player); }
    private void refresh() {
        ItemStack target = inventory.getItem(0);
        if (!ItemStack.matches(previousTarget, target)) {
            data.set(selected, -1); // Never apply an old selection to a replaced/modified source.
            previousTarget = target.copy();
        }
        var snapshot = ExtractionService.source(target);
        for (int i = 0; i < choices.size(); i++) {
            var key = choices.get(i);
            data.set(i, snapshot.enchantments().stream().filter(e -> e.key().equals(key))
                    .mapToInt(ExtractionRules.Entry::level).findFirst().orElse(0));
        }
        var extract = ExtractionService.extractionPlan(owner, inventory, selection());
        data.set(xp, PlayerExperience.effectiveCost(owner, extract.rawXp()));
        data.set(extractionStatus, extract.failure().ordinal());
        data.set(playerXp, (int)Math.min(32767, PlayerExperience.available(owner)));
        data.set(creative, owner.hasInfiniteMaterials() ? 1 : 0);
    }
    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide() && stillValid(owner)) refresh();
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide() || player.isSpectator() || !stillValid(player)
                || button < 0 || button >= choices.size() + SELECTION_BUTTON_START || button == 1) return false;
        refresh();
        if (button == EXTRACT_BUTTON) ExtractionService.extract(player, inventory, selection());
        else {
            int index = button - SELECTION_BUTTON_START;
            if (level(index) <= 0) return false;
            data.set(selected, index);
        }
        refresh();
        super.broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), copy = original.copy();
        boolean moved;
        if (index < VISIBLE_SLOTS) moved = moveItemStackTo(original, VISIBLE_SLOTS, slots.size(), true);
        else if (ExtractionService.acceptsReagent(original)) moved = moveItemStackTo(original, 1, 2, false);
        else if (slots.get(0).mayPlace(original)) moved = moveItemStackTo(original, 0, 1, false);
        else return ItemStack.EMPTY;
        if (!moved) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original);
        return copy;
    }
}
