package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.ExtractionRules;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.menu.ExtractorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two slots, one Extract action: curses are selectable rows, never a separate Purify panel. */
public final class ExtractorScreen extends ReferenceWorkstationScreen<ExtractorMenu> {
    public ExtractorScreen(ExtractorMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected List<Integer> availableEntries() {
        var entries = new ArrayList<Integer>();
        for (int i = 0; i < menu.choices().size(); i++) if (menu.level(i) > 0) entries.add(i);
        return entries;
    }
    @Override protected String entryName(int index) {
        var key = menu.choices().get(index);
        return Enchantments.find(minecraft.level, key.id()).map(e -> e.value().description().getString()).orElse(key.id());
    }
    @Override protected String entrySuffix(int index) { return " " + levelLabel(menu.level(index)); }
    @Override protected String entrySearchText(int index) { return entryName(index) + " " + menu.choices().get(index).id(); }
    private String componentHint(int index) {
        var key = menu.choices().get(index);
        boolean duplicate = false;
        for (int i = 0; i < menu.choices().size(); i++) {
            if (i != index && menu.level(i) > 0 && menu.choices().get(i).id().equals(key.id())) { duplicate = true; break; }
        }
        return duplicate ? (key.component() == ExtractionRules.Component.STORED ? "Stored book enchantment.\n" : "Applied item enchantment.\n") : "";
    }
    @Override protected String entryTooltip(int index) {
        return entryName(index) + entrySuffix(index) + "\n" + componentHint(index)
                + (index == menu.selectedIndex() ? previewTooltip() : menu.isCurse(index)
                ? "Remove ONLY this curse using 1 Dragon's Breath; returns 1 glass bottle in the reagent slot. Select to preview the XP fee."
                : "Transfer ONLY this enchantment into 1 blank book in the reagent slot. Any remaining curse blocks beneficial extraction. Select to preview the XP fee.");
    }
    @Override protected int selectedIndex() { return menu.selectedIndex(); }
    @Override protected void selectEntry(int index) { send(ExtractorMenu.selectionButton(index)); }
    @Override protected String actionName() { return "Extract"; }
    @Override protected void performAction() { send(ExtractorMenu.EXTRACT_BUTTON); }
    @Override protected boolean actionReady() { return menu.extractionStatus() == ExtractionRules.Failure.NONE; }
    @Override protected int xpCost() { return menu.xpCost(); }
    @Override protected String emptyText() { return "No matching enchantments"; }
    @Override protected int inputSlotCount() { return 2; }
    @Override protected String previewTooltip() {
        String selection = menu.selection() == null ? "Choose one enchantment or curse." : entryName(menu.selectedIndex()) + entrySuffix(menu.selectedIndex()) + ".";
        String result = menu.selection() == null ? "Beneficial enchantment: 1 blank book. Curse: 1 Dragon's Breath."
                : menu.isCurse(menu.selectedIndex())
                ? "Requires 1 Dragon's Breath. Removes ONLY the selected curse; returns 1 glass bottle in the reagent slot. Other curses remain."
                : "Requires 1 blank book. Replaces it with 1 enchanted book containing only the selected enchantment. Any remaining curse blocks extraction.";
        return selection + "\nCost: " + menu.xpCost() + " raw XP points.\n" + result
                + "\nYour XP: " + playerXpLabel(menu.playerXp()) + "." + (menu.creativeXp() ? " Creative: XP waived; reagent still required." : "")
                + "\n" + statusText() + "\nThe source stays in its slot; all other enchantments and item properties are retained.";
    }
    @Override protected String slotHint(int index) {
        return index == 0 ? "Source: one enchanted item or multi-enchantment book. Other properties are retained. Hover Cost or Extract for the full preview."
                : "Reagent/result: insert exactly 1 blank book or 1 Dragon's Breath. Take the resulting enchanted book/glass bottle before repeating. Hover Cost or Extract for the full preview.";
    }
    private String statusText() {
        return switch (menu.extractionStatus()) {
            case NONE -> "Ready to extract the selected entry.";
            case INVALID_TARGET -> "Insert one enchanted item or book.";
            case INVALID_DATA -> "Invalid enchantment data.";
            case SELECT_ENCHANTMENT -> "Choose one enchantment or curse.";
            case CURSED_SOURCE -> "Remove each curse before extracting a beneficial enchantment.";
            case SINGLE_ENCHANTMENT_BOOK -> "This single-enchantment book is already a blueprint; no split needed.";
            case INVALID_LEVEL -> "Invalid level; requires a natural enchantment level.";
            case OUTPUT_OCCUPIED, BOTTLE_RETURN_FULL -> "Take the reagent-slot result before inserting the next reagent.";
            case INSUFFICIENT_XP -> "Not enough raw XP points.";
            case NO_CURSES -> "No selected curse to remove.";
            case MISSING_DRAGON_BREATH -> "Deposit exactly 1 Dragon's Breath in the reagent slot.";
            case MISSING_BLANK_BOOK -> "Deposit exactly 1 blank book in the reagent slot; take any previous result first.";
            case INVALID_REAGENT_COUNT -> "The reagent slot must contain exactly 1 item.";
        };
    }
}
