package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.ReplicationDefinition;
import dev.enchantingoverhaul.core.ReplicationRules;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Reference-faithful three-slot, three-row Replicator with explicit Roman-level selection. */
public final class ReplicatorScreen extends ReferenceWorkstationScreen<ReplicatorMenu> {
    private final ReferenceButton[] levels = new ReferenceButton[5];

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override protected void initExtraWidgets() {
        for (int i = 0; i < levels.length; i++) {
            int level = i + 1;
            levels[i] = addRenderableWidget(new ReferenceButton(font, leftPos + LIST_X + 3 + i * 29, topPos + 112,
                    24, 18, false, b -> select(menu.selectedIndex(), level)));
            levels[i].label(levelLabel(level), "");
        }
    }
    @Override protected void refreshExtraWidgets() {
        var selected = menu.selected();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null) continue;
            int level = i + 1;
            boolean unlocked = selected != null && level <= menu.availableMax(menu.selectedIndex());
            levels[i].active = unlocked && level > currentLevel(selected);
            levels[i].selected(selected != null && menu.desiredLevel() == level);
            levels[i].setTooltip(Tooltip.create(Component.literal("Level " + levelLabel(level) + ": "
                    + (!unlocked ? "not unlocked in the library." : level <= currentLevel(selected)
                    ? "already present; choose a higher level." : "select this level; exact payment is previewed before Replicate."))));
        }
    }
    @Override protected List<Integer> availableEntries() {
        var entries = new ArrayList<Integer>();
        for (int i = 0; i < ReplicatorMenu.DEFINITIONS.size(); i++) if (menu.availableMax(i) > 0) entries.add(i);
        return entries;
    }
    @Override protected String entryName(int index) {
        var definition = ReplicatorMenu.DEFINITIONS.get(index);
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> e.value().description().getString()).orElse(definition.enchantmentId());
    }
    @Override protected String entrySearchText(int index) {
        return entryName(index) + " " + ReplicatorMenu.DEFINITIONS.get(index).enchantmentId();
    }
    @Override protected String entrySuffix(int index) {
        int level = index == menu.selectedIndex() ? menu.desiredLevel() : menu.availableMax(index);
        return " " + levelLabel(level);
    }
    @Override protected String entryTooltip(int index) {
        return entryName(index) + entrySuffix(index) + "\nLibrary: levels I–" + levelLabel(menu.availableMax(index))
                + ". Select one enchantment, then choose its level below.\n"
                + (index == menu.selectedIndex() ? previewTooltip() : "Catalyst for a new application: 1 "
                + Enchantments.catalystName(ReplicatorMenu.DEFINITIONS.get(index).catalyst()).getString() + ".");
    }
    @Override protected int selectedIndex() { return menu.selectedIndex(); }
    @Override protected void selectEntry(int index) {
        int preferred = Math.min(menu.availableMax(index), currentLevel(ReplicatorMenu.DEFINITIONS.get(index)) + 1);
        select(index, Math.max(1, preferred));
    }
    private void select(int index, int level) {
        if (index >= 0 && index < ReplicatorMenu.DEFINITIONS.size()) send(ReplicatorMenu.selectionButton(index, level));
    }
    private int currentLevel(ReplicationDefinition definition) {
        if (definition == null) return 0;
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> Enchantments.on(menu.slots.get(0).getItem()).getLevel(e)).orElse(0);
    }
    @Override protected String actionName() { return "Replicate"; }
    @Override protected void performAction() { send(0); }
    @Override protected boolean actionReady() { return menu.status() == ReplicationRules.FailureReason.NONE; }
    @Override protected int xpCost() { return menu.xpCost(); }
    @Override protected String emptyText() { return "No matching blueprints"; }
    @Override protected int inputSlotCount() { return 3; }
    private String catalystPreview() {
        var selected = menu.selected();
        if (selected == null) return "Choose an enchantment and level to preview its catalyst.";
        if (currentLevel(selected) > 0) return "Upgrade: no catalyst required.";
        return "New enchantment: 1 " + Enchantments.catalystName(selected.catalyst()).getString() + ".";
    }
    @Override protected String previewTooltip() {
        String selection = menu.selected() == null ? "Select an enchantment and level." : entryName(menu.selectedIndex()) + entrySuffix(menu.selectedIndex()) + ".";
        return selection + "\nCost: " + menu.xpCost() + " raw XP points + " + menu.lapisCost() + " lapis lazuli.\n"
                + catalystPreview() + "\nYour XP: " + playerXpLabel(menu.playerXp()) + "."
                + (minecraft.player != null && minecraft.player.hasInfiniteMaterials() ? " Creative: XP waived; materials still required." : "")
                + "\n" + statusText() + "\nOne enchantment per operation; target stays in its slot. Bottled catalysts return 1 glass bottle in the catalyst slot.";
    }
    @Override protected String slotHint(int index) {
        return switch (index) {
            case 0 -> "Target: one eligible item or book. It remains here after replication. Hover Cost or Replicate for the full preview.";
            case 1 -> "Lapis slot: deposit " + menu.lapisCost() + " lapis lazuli for the selected operation. No materials are taken from your inventory.";
            default -> "Catalyst slot. " + catalystPreview() + " Bottled catalysts return 1 glass bottle here.";
        };
    }
    private String statusText() {
        if (menu.selected() == null) return "Insert one target and choose an enchantment.";
        return switch (menu.status()) {
            case NONE -> "Ready to replicate.";
            case INVALID_TARGET_COUNT -> "Insert exactly one target item or book.";
            case LEVEL_NOT_INCREASED -> "Choose a higher level; no downgrades.";
            case LEVEL_NOT_UNLOCKED -> "Blueprint removed or level not unlocked.";
            case NOT_APPLICABLE -> "Not applicable, or book requires curse removal.";
            case INCOMPATIBLE -> "Conflicts with an enchantment on this item.";
            case INSUFFICIENT_LAPIS, WRONG_LAPIS -> "Deposit the required lapis in its slot.";
            case INSUFFICIENT_CATALYST, WRONG_CATALYST -> "Deposit the exact catalyst in its slot.";
            case INSUFFICIENT_XP -> "Not enough raw XP points.";
            case NO_BOTTLE_SPACE -> "Make room for the empty bottle in the catalyst slot.";
            default -> "Invalid selection or item data.";
        };
    }
}
