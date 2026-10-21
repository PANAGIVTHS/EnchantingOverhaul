package dev.enchantingoverhaul.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.enchantingoverhaul.core.ScrollWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Shared reference layout and input only; every operation remains menu/server-authoritative. */
abstract class ReferenceWorkstationScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected static final int LIST_X = 154, LIST_Y = 38, ROW_WIDTH = 142, ROW_HEIGHT = 22;
    private static final int TRACK_X = LIST_X + ROW_WIDTH, TRACK_WIDTH = 8, TRACK_HEIGHT = 66, MIN_THUMB = 12;
    private final ScrollWindow scroll = new ScrollWindow(3);
    private final List<Integer> filtered = new ArrayList<>();
    private final ReferenceButton[] rows = new ReferenceButton[3];
    private EditBox search;
    private ReferenceButton action;
    private boolean scrollbarGesture, draggingThumb;
    private double grabOffset;

    ReferenceWorkstationScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 232);
        inventoryLabelX = 8;
        inventoryLabelY = 138;
    }
    @Override protected void init() {
        super.init();
        draggingThumb = scrollbarGesture = false;
        String oldSearch = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + LIST_X, topPos + 22, ROW_WIDTH + TRACK_WIDTH, 12,
                Component.literal("Search enchantments")));
        search.setMaxLength(80);
        search.setHint(Component.literal("Search..."));
        search.setValue(oldSearch);
        search.setResponder(value -> { scroll.reset(); refreshWidgets(); });
        for (int r = 0; r < rows.length; r++) {
            int row = r;
            rows[r] = addRenderableWidget(new ReferenceButton(font, leftPos + LIST_X, topPos + LIST_Y + r * ROW_HEIGHT,
                    ROW_WIDTH, ROW_HEIGHT, false, b -> {
                int position = scroll.first() + row;
                if (position < filtered.size()) selectEntry(filtered.get(position));
            }));
        }
        action = addRenderableWidget(new ReferenceButton(font, leftPos + 12, topPos + 94, 116, 30, true, b -> performAction()));
        action.label(actionName(), "");
        initExtraWidgets();
        refreshWidgets();
    }
    protected abstract List<Integer> availableEntries();
    protected abstract String entryName(int index);
    protected abstract String entrySuffix(int index);
    protected String entrySearchText(int index) { return entryName(index); }
    protected abstract String entryTooltip(int index);
    protected abstract int selectedIndex();
    protected abstract void selectEntry(int index);
    protected abstract String actionName();
    protected abstract void performAction();
    protected abstract boolean actionReady();
    protected abstract int xpCost();
    protected abstract String previewTooltip();
    protected abstract String emptyText();
    protected abstract int inputSlotCount();
    protected abstract String slotHint(int index);
    protected void initExtraWidgets() { }
    protected void refreshExtraWidgets() { }
    protected final void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    protected final void refreshWidgets() {
        if (action == null) return;
        filtered.clear();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        for (int index : availableEntries()) {
            if (entrySearchText(index).toLowerCase(Locale.ROOT).contains(query)) filtered.add(index);
        }
        scroll.setItemCount(filtered.size());
        for (int r = 0; r < rows.length; r++) {
            int position = scroll.first() + r;
            boolean present = position < filtered.size();
            rows[r].active = present;
            rows[r].selected(present && filtered.get(position) == selectedIndex());
            rows[r].label(present ? entryName(filtered.get(position)) : "", present ? entrySuffix(filtered.get(position)) : "");
            rows[r].setTooltip(present ? Tooltip.create(Component.literal(entryTooltip(filtered.get(position)))) : null);
        }
        action.active = actionReady();
        action.setTooltip(Tooltip.create(Component.literal(previewTooltip())));
        refreshExtraWidgets();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && !event.isEscape()) {
            search.keyPressed(event);
            return true; // Printable input arrives through normal charTyped, never container hotkeys.
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (search != null && !search.isMouseOver(event.x(), event.y())) {
            search.setFocused(false);
            if (getFocused() == search) setFocused(null);
        }
        refreshWidgets();
        if (inside(event.x(), event.y(), TRACK_X, LIST_Y, TRACK_WIDTH, TRACK_HEIGHT)) {
            scrollbarGesture = true; // Consume the complete gesture, even if dragged onto inventory slots.
            draggingThumb = event.button() == InputConstants.MOUSE_BUTTON_LEFT && scroll.scrollable();
            if (draggingThumb) {
                double relativeY = event.y() - topPos - LIST_Y;
                int thumbTop = scroll.thumbOffset(TRACK_HEIGHT, MIN_THUMB);
                int thumbSize = scroll.thumbSize(TRACK_HEIGHT, MIN_THUMB);
                grabOffset = relativeY >= thumbTop && relativeY < thumbTop + thumbSize ? relativeY - thumbTop : thumbSize / 2.0;
                scroll.dragThumb(relativeY - grabOffset, TRACK_HEIGHT, MIN_THUMB);
                refreshWidgets();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrollbarGesture) {
            if (draggingThumb) {
                refreshWidgets();
                scroll.dragThumb(event.y() - topPos - LIST_Y - grabOffset, TRACK_HEIGHT, MIN_THUMB);
                refreshWidgets();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollbarGesture) {
            draggingThumb = scrollbarGesture = false;
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inside(x, y, LIST_X, LIST_Y, ROW_WIDTH + TRACK_WIDTH, TRACK_HEIGHT)) {
            refreshWidgets();
            scroll.scroll(scrollY);
            refreshWidgets();
            return true; // Never forward list scrolling to a stale hovered item slot.
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
    protected final boolean inside(double x, double y, int localX, int localY, int w, int h) {
        return x >= leftPos + localX && x < leftPos + localX + w && y >= topPos + localY && y < topPos + localY + h;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refreshWidgets();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (inside(mouseX, mouseY, 12, 68, 116, 16)) tooltip(graphics, previewTooltip(), mouseX, mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        ReferenceButton.bevel(graphics, leftPos, topPos, imageWidth, imageHeight, 0xffc6c6c6, 0xffffffff, 0xff555555);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xffffffff);
            graphics.fill(x, y, x + 17, y + 1, 0xff373737);
            graphics.fill(x, y, x + 1, y + 17, 0xff373737);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xff8b8b8b);
        }
        int x = leftPos + TRACK_X, y = topPos + LIST_Y;
        graphics.fill(x, y, x + TRACK_WIDTH, y + TRACK_HEIGHT, 0xff303030);
        int thumbY = y + scroll.thumbOffset(TRACK_HEIGHT, MIN_THUMB);
        ReferenceButton.bevel(graphics, x, thumbY, TRACK_WIDTH, scroll.thumbSize(TRACK_HEIGHT, MIN_THUMB),
                scroll.scrollable() ? 0xffb3b3b3 : 0xff777777, 0xffd7d7d7, 0xff777777);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 7, 0xff303030, false);
        graphics.text(font, playerInventoryTitle, 8, 138, 0xff404040, false);
        String cost = "Cost: " + xpCost();
        graphics.text(font, cost, 12 + (116 - font.width(cost)) / 2, 72, 0xff80ff20, true);
        if (filtered.isEmpty()) {
            String empty = font.plainSubstrByWidth(emptyText(), ROW_WIDTH - 8);
            graphics.text(font, empty, LIST_X + (ROW_WIDTH - font.width(empty)) / 2, LIST_Y + 7, 0xffc4b692, true);
        }
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index < inputSlotCount() && menu.getCarried().isEmpty()) {
            ItemStack stack = hoveredSlot.getItem();
            var lines = new ArrayList<FormattedCharSequence>();
            if (!stack.isEmpty()) {
                for (var line : getTooltipFromContainerItem(stack)) lines.addAll(font.split(line, Math.min(250, width - 24)));
            }
            lines.addAll(font.split(Component.literal(slotHint(hoveredSlot.index)), Math.min(250, width - 24)));
            graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else super.extractTooltip(graphics, mouseX, mouseY);
    }
    private void tooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(font, font.split(Component.literal(text), Math.min(250, width - 24)), mouseX, mouseY);
    }
    protected static String levelLabel(int level) {
        return switch (level) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(level); };
    }
    protected static String playerXpLabel(int xp) { return xp == 32767 ? "32767+" : Integer.toString(xp); }
}
