package dev.enchantingoverhaul.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TooltipBoundsTest {
    @Test void formerlyNegativeSharpnessPreviewStaysVisible() {
        var position = TooltipBounds.place(320, 240, 70, 113, 170, 128);
        assertTrue(position.y() >= 8);
        assertTrue(position.y() + 128 <= 232);
    }
    @Test void fittingTooltipsStayInsideEveryViewportEdge() {
        for (int width : new int[]{320, 427, 640, 1920})
            for (int height : new int[]{240, 360, 480, 1080})
                for (int w : new int[]{20, 170, 250, width - 16})
                    for (int h : new int[]{8, 128, height - 16})
                        for (int x : new int[]{-1, 0, 70, width / 2, width - 1, width + 10})
                            for (int y : new int[]{-1, 0, 113, height / 2, height - 1, height + 10}) {
                                var p = TooltipBounds.place(width, height, x, y, w, h);
                                assertTrue(p.x() >= 8 && p.y() >= 8);
                                assertTrue(p.x() + w <= width - 8);
                                assertTrue(p.y() + h <= height - 8);
                            }
    }
    @Test void oversizedCustomTextAtLeastKeepsTheFirstPaymentLineVisible() {
        assertEquals(8, TooltipBounds.place(320, 240, 0, 0, 500, 500).y());
        assertEquals(8, TooltipBounds.place(320, 240, 0, 0, 500, 500).x());
    }
}
