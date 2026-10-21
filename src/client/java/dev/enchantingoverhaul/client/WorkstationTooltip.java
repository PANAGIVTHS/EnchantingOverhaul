package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.TooltipBounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;

/** Full requirement previews use screen bounds, not vanilla's above-widget menu positioning. */
final class WorkstationTooltip {
    private WorkstationTooltip() {}
    private static final ClientTooltipPositioner POSITIONER = (width, height, x, y, w, h) -> {
        var p = TooltipBounds.place(width, height, x, y, w, h);
        return new Vector2i(p.x(), p.y());
    };
    static void show(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        show(graphics, font, List.of(Component.literal(text)), x, y);
    }
    static void show(GuiGraphicsExtractor graphics, Font font, List<Component> text, int x, int y) {
        int maximumWidth = Math.max(1, graphics.guiWidth() - 16);
        int wrapWidth = Math.min(250, maximumWidth);
        var lines = wrap(font, text, wrapWidth);
        // Wider wrapping keeps full payment/result text visible on the supported 320x240 viewport.
        if (lines.size() * 10 - 2 > graphics.guiHeight() - 16 && wrapWidth < maximumWidth)
            lines = wrap(font, text, maximumWidth);
        graphics.setTooltipForNextFrame(font, lines, POSITIONER, x, y, true);
    }
    private static List<FormattedCharSequence> wrap(Font font, List<Component> text, int width) {
        var lines = new ArrayList<FormattedCharSequence>();
        for (var paragraph : text) lines.addAll(font.split(paragraph, width));
        return lines;
    }
}
