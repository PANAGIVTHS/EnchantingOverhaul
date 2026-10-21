package dev.enchantingoverhaul.core;

/** Tooltip content coordinates with an eight-pixel margin for the vanilla background. */
public final class TooltipBounds {
    private TooltipBounds() {}
    public record Position(int x, int y) {}
    public static Position place(int screenWidth, int screenHeight, int pointerX, int pointerY,
                                 int tooltipWidth, int tooltipHeight) {
        int x = pointerX + 12;
        if ((long)x + tooltipWidth + 8 > screenWidth) x = pointerX - 12 - tooltipWidth;
        int maxX = Math.max(8, screenWidth - tooltipWidth - 8);
        int maxY = Math.max(8, screenHeight - tooltipHeight - 8);
        return new Position(Math.clamp(x, 8, maxX), Math.clamp(pointerY - 12, 8, maxY));
    }
}
