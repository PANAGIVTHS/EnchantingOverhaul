package dev.enchantingoverhaul.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.enchantingoverhaul.core.ExtractionRules;
import dev.enchantingoverhaul.core.ScrollWindow;
import dev.enchantingoverhaul.core.WidgetPreviewPolicy;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.menu.ExtractorMenu;
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
import static dev.enchantingoverhaul.core.ExtractorLayout.*;

/**
 * Java-drawn reconstruction of the supplied two-slot Enchantment Extractor reference.
 * No screenshot items or example enchantments/cost are painted; every displayed value is live.
 * Ctrl+F / clicking the title reveals search without adding a permanent field to the reference.
 * ExtractorLayout shares geometry with the real menu slots; transaction IDs/rules are unchanged.
 */
public final class ExtractorScreen extends AbstractContainerScreen<ExtractorMenu> {
    private static final int GOLD = 0xffedc75a, PINK = 0xffff80ff;
    private static final int MIN_THUMB = 8;
    private final ScrollWindow scroll = new ScrollWindow(ROWS);
    private final List<Integer> filtered = new ArrayList<>();
    private final ExtractionButton[] rows = new ExtractionButton[ROWS];
    private ExtractionButton action;
    private EditBox search;
    private boolean scrollbarGesture, draggingThumb;
    private double grabOffset;

    public ExtractorScreen(ExtractorMenu menu, Inventory inventory, Component title) {
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
            rows[r] = addRenderableWidget(new ExtractionButton(font, leftPos + LIST_X,
                    topPos + LIST_Y + r * ROW_HEIGHT, ROW_WIDTH, ROW_HEIGHT, false, b -> {
                int position = scroll.first() + row;
                if (position < filtered.size()) selectEntry(filtered.get(position));
            }));
        }
        action = addRenderableWidget(new ExtractionButton(font, leftPos + ACTION_X, topPos + ACTION_Y,
                ACTION_WIDTH, ACTION_HEIGHT, true, b -> send(ExtractorMenu.EXTRACT_BUTTON)));
        action.label("Extract");
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (action == null) return;
        filtered.clear();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < menu.choices().size(); i++) {
            if (menu.level(i) > 0 && (entryName(i) + " " + menu.choices().get(i).id())
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
    }

    private String entryName(int index) {
        var key = menu.choices().get(index);
        return Enchantments.find(minecraft.level, key.id()).map(e -> e.value().description().getString()).orElse(key.id());
    }
    private String entrySuffix(int index) {
        var key = menu.choices().get(index);
        boolean singleLevel = Enchantments.find(minecraft.level, key.id())
                .map(e -> e.value().getMaxLevel() == 1).orElse(false);
        return singleLevel && menu.level(index) == 1 ? "" : " " + numeral(menu.level(index));
    }
    private String componentHint(int index) {
        var key = menu.choices().get(index);
        boolean duplicate = false;
        for (int i = 0; i < menu.choices().size(); i++) {
            if (i != index && menu.level(i) > 0 && menu.choices().get(i).id().equals(key.id())) { duplicate = true; break; }
        }
        return duplicate ? (key.component() == ExtractionRules.Component.STORED ? "\nStored" : "\nApplied") : "";
    }
    private String entryTooltip(int index) {
        return entryName(index) + entrySuffix(index) + componentHint(index);
    }
    private void selectEntry(int index) { send(ExtractorMenu.selectionButton(index)); }
    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

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
        if (filtered.isEmpty()) centeredText(g, font, "No enchantments", LIST_X + ROW_WIDTH / 2f,
                LIST_Y + 4, 0xffd4bf83, true, 0.65f, ROW_WIDTH - 5);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        refreshWidgets();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (menu.selection() != null && (action.previewRequested()
                || inside(mouseX, mouseY, ACTION_X, COST_Y - 1, ACTION_WIDTH, 9))) {
            WorkstationTooltip.show(g, font, previewTooltip(), mouseX, mouseY);
        } else {
            for (int r = 0; r < ROWS; r++) {
                int position = scroll.first() + r;
                if (position < filtered.size() && rows[r].previewRequested()) {
                    WorkstationTooltip.show(g, font, entryTooltip(filtered.get(position)), mouseX, mouseY);
                    return;
                }
            }
        }
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

    /** Local Extractor styling: the accepted Replicator and Combiner renderers are untouched. */
    private static final class ExtractionButton extends Button {
        private final Font font;
        private final boolean action;
        private boolean selected;
        ExtractionButton(Font font, int x, int y, int width, int height, boolean action, OnPress callback) {
            super(x, y, width, height, Component.empty(), callback, DEFAULT_NARRATION);
            this.font = font; this.action = action;
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
            if (selected) {
                g.fill(x, y, x + w, y + 1, PINK);
                g.fill(x, y + h - 1, x + w, y + h, PINK);
                g.fill(x, y, x + 1, y + h, PINK);
                g.fill(x + w - 1, y, x + w, y + h, PINK);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xffffb0ff);
            }
            int color = selected ? PINK : action ? (active ? 0xffffffff : 0xffbcbcbc) : GOLD;
            float scale = 0.75f;
            centeredText(g, font, getMessage().getString(), x + w / 2f, y + (h - 8 * scale) / 2f,
                    color, true, scale, w - 4);
        }
    }

    private boolean actionReady() { return menu.extractionStatus() == ExtractionRules.Failure.NONE; }
    private String previewTooltip() {
        if (menu.selection() == null) return "";
        String reagent = menu.isCurse(menu.selectedIndex()) ? "Dragon's Breath" : "Book";
        String failure = statusText();
        return menu.xpCost() + " XP\n1 × " + reagent + (failure.isEmpty() ? "" : "\n" + failure);
    }
    private String statusText() {
        return switch (menu.extractionStatus()) {
            case NONE -> "";
            case INVALID_TARGET -> "Requires an enchanted item";
            case INVALID_DATA -> "Invalid enchantment";
            case SELECT_ENCHANTMENT -> "";
            case CURSED_SOURCE -> "Cursed item";
            case SINGLE_ENCHANTMENT_BOOK -> "Single-enchantment book";
            case INVALID_LEVEL -> "Invalid level";
            case OUTPUT_OCCUPIED, BOTTLE_RETURN_FULL -> "Result slot occupied";
            case INSUFFICIENT_XP -> "Not enough experience";
            case NO_CURSES -> "No curse selected";
            case MISSING_DRAGON_BREATH -> "Missing Dragon's Breath";
            case MISSING_BLANK_BOOK -> "Missing book";
            case INVALID_REAGENT_COUNT -> "Requires one reagent";
        };
    }
}
