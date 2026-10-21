package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.WidgetPreviewPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Fill-only Minecraft bevels; clipping preserves the enchantment's level suffix. */
final class ReferenceButton extends Button {
    static final int GOLD = 0xffd4bf83, PINK = 0xffe2a7d8;
    private final Font font;
    private final boolean action;
    private String name = "", suffix = "";
    private boolean selected;

    ReferenceButton(Font font, int x, int y, int width, int height, boolean action, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.font = font;
        this.action = action;
    }
    void label(String name, String suffix) {
        this.name = name;
        this.suffix = suffix;
        setMessage(Component.literal(name + suffix));
    }
    void selected(boolean selected) { this.selected = selected; }
    boolean previewRequested() {
        return WidgetPreviewPolicy.requested(visible, isHovered(), isFocused(),
                Minecraft.getInstance().getLastInputType().isKeyboard());
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        bevel(graphics, x, y, w, h, active && previewRequested() ? 0xff818181 : 0xff707070,
                0xffaaaaaa, 0xff393939);
        if (selected) {
            graphics.fill(x, y, x + w, y + 1, PINK);
            graphics.fill(x, y + h - 1, x + w, y + h, PINK);
            graphics.fill(x, y, x + 1, y + h, PINK);
            graphics.fill(x + w - 1, y, x + w, y + h, PINK);
        }
        int available = w - 8 - font.width(suffix);
        String clipped = name;
        if (font.width(clipped) > available) clipped = font.plainSubstrByWidth(name, Math.max(0, available - font.width("..."))) + "...";
        String text = clipped + suffix;
        int color = selected ? PINK : action ? (active ? 0xffffffff : 0xffb0b0b0) : (active ? GOLD : 0xff8e856e);
        graphics.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, true);
    }
    static void bevel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int light, int dark) {
        graphics.fill(x, y, x + w, y + h, 0xff1b1b1b);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, dark);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, dark);
    }
}
