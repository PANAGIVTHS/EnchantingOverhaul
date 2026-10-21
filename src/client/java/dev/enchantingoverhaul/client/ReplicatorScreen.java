package dev.enchantingoverhaul.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.enchantingoverhaul.core.ReplicationDefinition;
import dev.enchantingoverhaul.core.ReplicationRules;
import dev.enchantingoverhaul.core.ScrollWindow;
import dev.enchantingoverhaul.core.WidgetPreviewPolicy;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import static dev.enchantingoverhaul.core.ReplicatorLayout.*;

/**
 * Java-drawn reconstruction of EnchantReplciator.png (176 x 168 native GUI pixels).
 * No screenshot items, bookcase backdrop or hard-coded enchantment/cost examples are painted.
 * All five numerals, including IV, use the SAME font/style/scale. No hand-drawn IV glyph.
 * Search is a Ctrl+F / clickable-title overlay so the idle layout stays faithful to the reference.
 * Slot geometry is shared with ReplicatorMenu; transactions/protocol remain unchanged.
 */
public final class ReplicatorScreen extends AbstractContainerScreen<ReplicatorMenu> {
    private static final int GOLD = 0xffedc75a, PINK = 0xffff80ff;
    private static final int MIN_THUMB = 8;
    private final ScrollWindow scroll = new ScrollWindow(ROWS);
    private final List<Integer> filtered = new ArrayList<>();
    private final ReplicaButton[] rows = new ReplicaButton[ROWS], levels = new ReplicaButton[5];
    private ReplicaButton action;
    private EditBox search;
    private boolean scrollbarGesture, draggingThumb;
    private double grabOffset;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = INVENTORY_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override protected void init() {
        super.init();
        scrollbarGesture = draggingThumb = false;
        String previous = search == null ? "" : search.getValue();
        boolean searching = search != null && search.visible;
        search = addRenderableWidget(new EditBox(font, leftPos + 7, topPos + 4, 162, 11,
                Component.literal("Search enchantments (Ctrl+F)")));
        search.setMaxLength(80);
        search.setBordered(false);
        search.setTextColor(0xff303030);
        search.setHint(Component.literal("Search enchantments..."));
        search.setValue(previous);
        search.visible = searching;
        search.setResponder(value -> { scroll.reset(); refreshWidgets(); });
        for (int r = 0; r < ROWS; r++) {
            int row = r;
            rows[r] = addRenderableWidget(new ReplicaButton(font, leftPos + LIST_X,
                    topPos + LIST_Y + r * ROW_HEIGHT, ROW_WIDTH, ROW_HEIGHT, false, false, b -> {
                int position = scroll.first() + row;
                if (position < filtered.size()) selectEntry(filtered.get(position));
            }));
        }
        action = addRenderableWidget(new ReplicaButton(font, leftPos + ACTION_X, topPos + ACTION_Y,
                ACTION_WIDTH, ACTION_HEIGHT, true, false, b -> send(0)));
        action.label("Replicate");
        for (int i = 0; i < levels.length; i++) {
            int level = i + 1;
            levels[i] = addRenderableWidget(new ReplicaButton(font, leftPos + LEVEL_X + i * LEVEL_PITCH,
                    topPos + LEVEL_Y, LEVEL_SIZE, LEVEL_SIZE, false, true, b -> select(menu.selectedIndex(), level)));
            levels[i].label(numeral(level));
        }
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (action == null) return;
        filtered.clear();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < ReplicatorMenu.DEFINITIONS.size(); i++) {
            if (menu.availableMax(i) > 0 && (entryName(i) + " " + ReplicatorMenu.DEFINITIONS.get(i).enchantmentId())
                    .toLowerCase(Locale.ROOT).contains(query)) filtered.add(i);
        }
        scroll.setItemCount(filtered.size());
        for (int r = 0; r < ROWS; r++) {
            int position = scroll.first() + r;
            boolean present = position < filtered.size();
            rows[r].active = present;
            rows[r].selected = present && filtered.get(position) == menu.selectedIndex();
            rows[r].label(present ? entryName(filtered.get(position)) + entrySuffix(filtered.get(position)) : "");
        }
        action.active = actionReady();
        var selected = menu.selected();
        for (int i = 0; i < levels.length; i++) {
            int level = i + 1;
            boolean unlocked = selected != null && level <= menu.availableMax(menu.selectedIndex());
            levels[i].active = unlocked && level > currentLevel(selected);
            levels[i].selected = selected != null && menu.desiredLevel() == level;
        }
    }

    private String entryName(int index) {
        var definition = ReplicatorMenu.DEFINITIONS.get(index);
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> e.value().description().getString()).orElse(definition.enchantmentId());
    }
    private String entrySuffix(int index) {
        int level = index == menu.selectedIndex() ? menu.desiredLevel() : menu.availableMax(index);
        // The reference's single-level Silk Touch has no redundant I suffix.
        return ReplicatorMenu.DEFINITIONS.get(index).maxLevel() == 1 ? "" : " " + numeral(level);
    }
    private String entryTooltip(int index) {
        return entryName(index) + entrySuffix(index) + "\nLibrary: levels I–" + numeral(menu.availableMax(index))
                + ". Select one enchantment, then choose its level below.\n"
                + (index == menu.selectedIndex() ? previewTooltip() : "Catalyst for a new application: 1 "
                + Enchantments.catalystName(ReplicatorMenu.DEFINITIONS.get(index).catalyst()).getString() + ".");
    }
    private void selectEntry(int index) {
        int preferred = Math.min(menu.availableMax(index), currentLevel(ReplicatorMenu.DEFINITIONS.get(index)) + 1);
        select(index, Math.max(1, preferred));
    }
    private void select(int index, int level) {
        if (index >= 0 && index < ReplicatorMenu.DEFINITIONS.size()) send(ReplicatorMenu.selectionButton(index, level));
    }
    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private static String playerXpLabel(int xp) { return xp == 32767 ? "32767+" : Integer.toString(xp); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        // Stepped near-white top/left rim and charcoal bottom/right drop shadow.
        g.fill(x + 1, y - 1, x + WIDTH - 1, y + HEIGHT + 1, 0xff080808);
        g.fill(x - 1, y + 1, x + WIDTH + 1, y + HEIGHT - 1, 0xff080808);
        g.fill(x + 2, y + 2, x + WIDTH + 2, y + HEIGHT, 0xff181818);
        g.fillGradient(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xffbdbdbd, 0xffb0b0b0);
        g.fill(x + 2, y, x + WIDTH - 2, y + 1, 0xfffcfcfc);
        g.fill(x, y + 2, x + 1, y + HEIGHT - 2, 0xfffcfcfc);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + 2, 0xffececec);
        g.fill(x + 1, y + 1, x + 2, y + HEIGHT - 1, 0xffececec);
        g.fill(x + 2, y + HEIGHT - 2, x + WIDTH - 1, y + HEIGHT, 0xff777777);
        g.fill(x + WIDTH - 2, y + 2, x + WIDTH, y + HEIGHT - 1, 0xff777777);
        for (var slot : menu.slots) slot(g, x + slot.x - 1, y + slot.y - 1);
        g.fill(x + TRACK_X, y + LIST_Y, x + TRACK_X + TRACK_WIDTH, y + LIST_Y + TRACK_HEIGHT, 0xff171717);
        int thumbY = y + LIST_Y + scroll.thumbOffset(TRACK_HEIGHT, MIN_THUMB);
        int thumbHeight = scroll.thumbSize(TRACK_HEIGHT, MIN_THUMB);
        g.fillGradient(x + TRACK_X + 1, thumbY, x + TRACK_X + TRACK_WIDTH - 1, thumbY + thumbHeight,
                0xffbebebe, 0xff929292);
        g.fill(x + TRACK_X + 1, thumbY, x + TRACK_X + TRACK_WIDTH - 1, thumbY + 1, 0xfff2f2f2);
        g.fill(x + TRACK_X + TRACK_WIDTH - 1, y + LIST_Y, x + TRACK_X + TRACK_WIDTH,
                y + LIST_Y + TRACK_HEIGHT, 0xfff5f5f5);
        if (search.visible) g.fill(x + 5, y + 3, x + WIDTH - 5, y + 15, 0xffd7d7d7);
    }

    private static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xff8b8b8b);
        g.fill(x, y, x + 18, y + 1, 0xff373737);
        g.fill(x, y, x + 1, y + 18, 0xff373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xfff8f8f8);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xfff8f8f8);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!search.visible) centeredText(g, font, title.getString(), WIDTH / 2f, 4, 0xff282828, false, 0.85f, WIDTH - 12);
        scaledText(g, font, playerInventoryTitle.getString(), INVENTORY_X - 1, INVENTORY_LABEL_Y, 0xff202020, false, 0.8f);
        centeredText(g, font, "Cost: " + menu.xpCost(), ACTION_X + ACTION_WIDTH / 2f,
                COST_Y, 0xff168000, false, 0.8f, ACTION_WIDTH);
        if (filtered.isEmpty()) centeredText(g, font, "No blueprints", LIST_X + ROW_WIDTH / 2f,
                LIST_Y + 4, 0xffd4bf83, true, 0.65f, ROW_WIDTH - 5);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        refreshWidgets();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (action.previewRequested() || inside(mouseX, mouseY, ACTION_X, COST_Y - 1, ACTION_WIDTH, 9)) {
            WorkstationTooltip.show(g, font, previewTooltip(), mouseX, mouseY);
        } else {
            for (int r = 0; r < ROWS; r++) {
                int position = scroll.first() + r;
                if (position < filtered.size() && rows[r].previewRequested()) {
                    WorkstationTooltip.show(g, font, entryTooltip(filtered.get(position)), mouseX, mouseY);
                    return;
                }
            }
            for (int i = 0; i < levels.length; i++) if (levels[i].previewRequested()) {
                int level = i + 1;
                boolean unlocked = menu.selected() != null && level <= menu.availableMax(menu.selectedIndex());
                WorkstationTooltip.show(g, font, "Level " + numeral(level) + ": "
                        + (!unlocked ? "not unlocked in the library." : level <= currentLevel(menu.selected())
                        ? "already present; choose a higher level." : "select this level; hover Cost for exact payment."), mouseX, mouseY);
                return;
            }
            if (!search.visible && inside(mouseX, mouseY, 5, 3, WIDTH - 10, 12))
                WorkstationTooltip.show(g, font, "Click the title or press Ctrl+F to search enchantments.", mouseX, mouseY);
        }
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index < 3 && menu.getCarried().isEmpty()) {
            var lines = new ArrayList<Component>();
            if (hoveredSlot.hasItem()) lines.addAll(getTooltipFromContainerItem(hoveredSlot.getItem()));
            lines.add(Component.literal(slotHint(hoveredSlot.index)));
            WorkstationTooltip.show(g, font, lines, mouseX, mouseY);
        } else super.extractTooltip(g, mouseX, mouseY);
    }

    private void openSearch() {
        search.visible = true;
        setFocused(search);
        search.setFocused(true);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.shortcutKey() == 102) { openSearch(); return true; }
        if (search.visible && search.isFocused() && !event.isEscape()) {
            search.keyPressed(event);
            return true; // E and hotbar keys cannot close/mutate the container while typing.
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!search.visible && event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y(), 5, 3, WIDTH - 10, 12)) {
            openSearch(); return true;
        }
        if (search.visible && !search.isMouseOver(event.x(), event.y())) {
            search.setFocused(false);
            if (getFocused() == search) setFocused(null);
            if (search.getValue().isEmpty()) search.visible = false;
        }
        refreshWidgets();
        if (inside(event.x(), event.y(), TRACK_X, LIST_Y, TRACK_WIDTH, TRACK_HEIGHT)) {
            scrollbarGesture = true;
            draggingThumb = event.button() == InputConstants.MOUSE_BUTTON_LEFT && scroll.scrollable();
            if (draggingThumb) {
                double relative = event.y() - topPos - LIST_Y;
                int thumbTop = scroll.thumbOffset(TRACK_HEIGHT, MIN_THUMB), thumbSize = scroll.thumbSize(TRACK_HEIGHT, MIN_THUMB);
                grabOffset = relative >= thumbTop && relative < thumbTop + thumbSize ? relative - thumbTop : thumbSize / 2.0;
                scroll.dragThumb(relative - grabOffset, TRACK_HEIGHT, MIN_THUMB);
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
        if (scrollbarGesture) { scrollbarGesture = draggingThumb = false; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inside(x, y, LIST_X, LIST_Y, ROW_WIDTH + TRACK_WIDTH, TRACK_HEIGHT)) {
            refreshWidgets(); scroll.scroll(scrollY); refreshWidgets(); return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
    private boolean inside(double x, double y, int localX, int localY, int w, int h) {
        return x >= leftPos + localX && x < leftPos + localX + w && y >= topPos + localY && y < topPos + localY + h;
    }

    private static void scaledText(GuiGraphicsExtractor g, Font font, String text, float x, float y, int color, boolean shadow, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, shadow);
        g.pose().popMatrix();
    }
    private static void centeredText(GuiGraphicsExtractor g, Font font, String text, float center, float y,
                                     int color, boolean shadow, float scale, int available) {
        float actual = Math.min(scale, available / (float)Math.max(1, font.width(text)));
        scaledText(g, font, text, center - font.width(text) * actual / 2f, y, color, shadow, actual);
    }

    /** All reference button faces live here; Extractor's existing styling is unaffected. */
    private static final class ReplicaButton extends Button {
        private final Font font;
        private final boolean action, numeral;
        private boolean selected;
        ReplicaButton(Font font, int x, int y, int width, int height, boolean action, boolean numeral, OnPress callback) {
            super(x, y, width, height, Component.empty(), callback, DEFAULT_NARRATION);
            this.font = font; this.action = action; this.numeral = numeral;
        }
        void label(String text) { setMessage(Component.literal(text)); }
        boolean previewRequested() {
            return WidgetPreviewPolicy.requested(visible, isHovered(), isFocused(), Minecraft.getInstance().getLastInputType().isKeyboard());
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean highlight = active && previewRequested();
            g.fill(x, y, x + w, y + h, 0xff080808);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                    highlight ? 0xff797979 : 0xff666666, highlight ? 0xff626262 : 0xff505050);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xffa5a5a5);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xff898989);
            g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xff303030);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xff303030);
            if (selected && !numeral) {
                g.fill(x, y, x + w, y + 1, PINK);
                g.fill(x, y + h - 1, x + w, y + h, PINK);
                g.fill(x, y, x + 1, y + h, PINK);
                g.fill(x + w - 1, y, x + w, y + h, PINK);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xffffb0ff);
            } else if (selected) {
                // Consistent selected-level border, never a different IV glyph or font.
                g.fill(x, y, x + w, y + 1, 0xffbda05a);
                g.fill(x, y + h - 1, x + w, y + h, 0xff806735);
                g.fill(x, y, x + 1, y + h, 0xffbda05a);
                g.fill(x + w - 1, y, x + w, y + h, 0xff806735);
            }
            int color = selected && !numeral ? PINK : action ? (active ? 0xffffffff : 0xffbcbcbc) : GOLD;
            float scale = numeral ? 0.8f : 0.75f;
            centeredText(g, font, getMessage().getString(), x + w / 2f, y + (h - 8 * scale) / 2f,
                    color, true, scale, numeral ? w - 2 : w - 4);
        }
    }

    private int currentLevel(ReplicationDefinition definition) {
        if (definition == null) return 0;
        return Enchantments.find(minecraft.level, definition.enchantmentId())
                .map(e -> Enchantments.on(menu.slots.get(0).getItem()).getLevel(e)).orElse(0);
    }
    private boolean actionReady() { return menu.status() == ReplicationRules.FailureReason.NONE; }
    private String catalystPreview() {
        var selected = menu.selected();
        if (selected == null) return "Choose an enchantment and level to preview its catalyst.";
        if (currentLevel(selected) > 0) return "Upgrade: no catalyst required.";
        return "New enchantment: 1 " + Enchantments.catalystName(selected.catalyst()).getString() + ".";
    }
    private String previewTooltip() {
        String selection = menu.selected() == null ? "Select an enchantment and level." : entryName(menu.selectedIndex()) + entrySuffix(menu.selectedIndex()) + ".";
        return selection + "\nCost: " + menu.xpCost() + " raw XP points + " + menu.lapisCost() + " lapis lazuli.\n"
                + catalystPreview() + "\nYour XP: " + playerXpLabel(menu.playerXp()) + "."
                + (minecraft.player != null && minecraft.player.hasInfiniteMaterials() ? " Creative: XP waived; materials still required." : "")
                + "\n" + statusText() + "\nOne enchantment per operation; target stays in its slot. Bottled catalysts return 1 glass bottle in the catalyst slot.";
    }
    private String slotHint(int index) {
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
