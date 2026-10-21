package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.Catalyst;
import dev.enchantingoverhaul.core.ReplicationDefinition;
import dev.enchantingoverhaul.core.ReplicationRules;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Deliberately simple layout: functional mockup-inspired slots/list, separate from all game rules. */
public final class ReplicatorScreen extends AbstractContainerScreen<ReplicatorMenu> {
    private static final String[] LEVELS = {"I", "II", "III", "IV", "V"};
    private final List<Integer> filtered = new ArrayList<>();
    private final Button[] rows = new Button[3], levels = new Button[5];
    private EditBox search;
    private Button apply, previous, next;
    private int page;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ReplicatorMenu.WIDTH, ReplicatorMenu.HEIGHT);
        inventoryLabelY = 138;
    }
    @Override protected void init() {
        super.init();
        String oldSearch = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + 166, topPos + 20, 146, 16, Component.literal("Search library")));
        search.setMaxLength(80);
        search.setHint(Component.literal("Search library..."));
        search.setValue(oldSearch);
        search.setResponder(value -> { page = 0; refreshWidgets(); });
        for (int row = 0; row < rows.length; row++) {
            int r = row;
            rows[row] = addRenderableWidget(Button.builder(Component.empty(), button -> {
                int entry = page * rows.length + r;
                if (entry < filtered.size()) {
                    int index = filtered.get(entry);
                    int preferred = Math.min(menu.availableMax(index), currentLevel(ReplicatorMenu.DEFINITIONS.get(index)) + 1);
                    select(index, Math.max(1, preferred));
                }
            }).bounds(leftPos + 166, topPos + 40 + row * 20, 146, 18).build());
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refreshWidgets(); })
                .bounds(leftPos + 166, topPos + 100, 24, 14).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refreshWidgets(); })
                .bounds(leftPos + 288, topPos + 100, 24, 14).build());
        for (int i = 0; i < levels.length; i++) {
            int level = i + 1;
            levels[i] = addRenderableWidget(Button.builder(Component.literal(LEVELS[i]), b -> select(menu.selectedIndex(), level))
                    .bounds(leftPos + 166 + i * 29, topPos + 118, 27, 16).build());
        }
        apply = addRenderableWidget(Button.builder(Component.literal("Replicate"), b -> send(0))
                .bounds(leftPos + 8, topPos + 114, 146, 20).build());
        refreshWidgets();
    }
    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private void select(int index, int level) {
        if (index >= 0 && index < ReplicatorMenu.DEFINITIONS.size()) send(ReplicatorMenu.selectionButton(index, level));
    }
    private String name(ReplicationDefinition definition) {
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> e.value().description().getString()).orElse(definition.enchantmentId());
    }
    private int currentLevel(ReplicationDefinition definition) {
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> Enchantments.on(menu.slots.get(0).getItem()).getLevel(e)).orElse(0);
    }
    private void refreshWidgets() {
        if (search == null) return;
        filtered.clear();
        String filter = search.getValue().toLowerCase(Locale.ROOT);
        for (int i = 0; i < ReplicatorMenu.DEFINITIONS.size(); i++) {
            var def = ReplicatorMenu.DEFINITIONS.get(i);
            if (menu.availableMax(i) > 0 && (name(def).toLowerCase(Locale.ROOT).contains(filter)
                    || def.enchantmentId().contains(filter))) filtered.add(i);
        }
        int pages = Math.max(1, (filtered.size() + rows.length - 1) / rows.length);
        page = Math.clamp(page, 0, pages - 1);
        for (int r = 0; r < rows.length; r++) {
            int entry = page * rows.length + r;
            if (rows[r] == null) continue;
            rows[r].visible = entry < filtered.size();
            if (rows[r].visible) {
                int index = filtered.get(entry);
                var def = ReplicatorMenu.DEFINITIONS.get(index);
                rows[r].setMessage(Component.literal((index == menu.selectedIndex() ? "> " : "") + name(def) + " " + LEVELS[menu.availableMax(index) - 1]));
                rows[r].setTooltip(Tooltip.create(Component.literal(name(def) + ": levels I–" + LEVELS[menu.availableMax(index) - 1])));
            }
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page + 1 < pages;
        var selected = menu.selected();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null) continue;
            levels[i].active = selected != null && i + 1 <= menu.availableMax(menu.selectedIndex()) && i + 1 > currentLevel(selected);
            levels[i].setMessage(Component.literal((menu.desiredLevel() == i + 1 && selected != null ? "*" : "") + LEVELS[i]));
        }
        if (apply != null) apply.active = menu.status() == ReplicationRules.FailureReason.NONE;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refreshWidgets();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        graphics.fill(leftPos + 160, topPos + 18, leftPos + 161, topPos + 135, 0xff777777);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff505050);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff8b8b8b);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        text(graphics, "Target", 8, 26); text(graphics, "Lapis", 52, 26); text(graphics, "Catalyst", 94, 26);
        text(graphics, "XP: " + menu.xpCost() + " pts   Lapis: " + menu.lapisCost(), 8, 64);
        var selected = menu.selected();
        String catalyst = selected == null ? "Select enchantment + level" : menu.catalystCount() == 0 && menu.xpCost() > 0
                ? "Upgrade: no catalyst" : "New: 1 " + catalystName(selected.catalyst());
        wrapped(graphics, catalyst, 8, 79, 146, 0xff303030);
        int pages = Math.max(1, (filtered.size() + rows.length - 1) / rows.length);
        text(graphics, (page + 1) + "/" + pages + " (" + filtered.size() + ")", 202, 103);
        text(graphics, "Your XP: " + (menu.playerXp() == 32767 ? "32767+" : menu.playerXp()), 180, 151);
        wrapped(graphics, statusText(), 180, 165, 130, menu.status() == ReplicationRules.FailureReason.NONE ? 0xff216821 : 0xff862525);
        if (filtered.isEmpty()) wrapped(graphics, "No matching clean blueprints for this target.", 166, 42, 146, 0xff505050);
    }
    private String catalystName(Catalyst catalyst) {
        return Enchantments.catalystStack(catalyst).getHoverName().getString();
    }
    private void text(GuiGraphicsExtractor graphics, String value, int x, int y) { graphics.text(font, value, x, y, 0xff303030, false); }
    private void wrapped(GuiGraphicsExtractor graphics, String value, int x, int y, int width, int color) {
        int lineY = y;
        for (var line : font.split(Component.literal(value), width)) { graphics.text(font, line, x, lineY, color, false); lineY += 10; }
    }
    private String statusText() {
        if (menu.selected() == null) return "Insert one target and choose an enchantment.";
        return switch (menu.status()) {
            case NONE -> "Ready. One enchantment per operation.";
            case INVALID_TARGET_COUNT -> "Insert exactly one target item or book.";
            case LEVEL_NOT_INCREASED -> "Choose a higher level; no downgrades.";
            case LEVEL_NOT_UNLOCKED -> "Blueprint removed or level not unlocked.";
            case NOT_APPLICABLE -> "Not applicable, or book requires purification.";
            case INCOMPATIBLE -> "Conflicts with an enchantment on this item.";
            case INSUFFICIENT_LAPIS, WRONG_LAPIS -> "Deposit the required lapis in its slot.";
            case INSUFFICIENT_CATALYST, WRONG_CATALYST -> "Deposit the exact catalyst in its slot.";
            case INSUFFICIENT_XP -> "Not enough raw XP points.";
            default -> "Invalid selection or item data.";
        };
    }
}
