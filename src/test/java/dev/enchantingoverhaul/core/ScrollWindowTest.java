package dev.enchantingoverhaul.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollWindowTest {
    @Test void rejectsNonpositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new ScrollWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new ScrollWindow(-1));
    }
    @Test void emptyAndShortListsHaveNoScrolling() {
        var scroll = new ScrollWindow(3);
        for (int count = -1; count <= 3; count++) {
            scroll.setItemCount(count);
            scroll.scroll(-50);
            scroll.dragThumb(900, 66, 12);
            assertEquals(0, scroll.first());
            assertFalse(scroll.scrollable());
            assertEquals(66, scroll.thumbSize(66, 12));
            assertEquals(0, scroll.thumbOffset(66, 12));
        }
    }
    @Test void wheelMovesOneRowNotOnePageAndClamps() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(8);
        scroll.scroll(-1);
        assertEquals(1, scroll.first());
        scroll.scroll(-1);
        assertEquals(2, scroll.first());
        scroll.scroll(-1000);
        assertEquals(5, scroll.first());
        scroll.scroll(1000);
        assertEquals(0, scroll.first());
    }
    @Test void fractionalTrackpadMovementAccumulates() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(10);
        scroll.scroll(-0.5);
        assertEquals(0, scroll.first());
        scroll.scroll(-0.5);
        assertEquals(1, scroll.first());
        scroll.scroll(0.5);
        assertEquals(1, scroll.first());
        scroll.scroll(0.5);
        assertEquals(0, scroll.first());
    }
    @Test void shrinkingLibraryOrTargetClampsWithoutSelectingAnything() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(41);
        scroll.scroll(-100);
        assertEquals(38, scroll.first());
        scroll.setItemCount(7);
        assertEquals(4, scroll.first());
        scroll.setItemCount(2);
        assertEquals(0, scroll.first());
        scroll.setItemCount(41);
        assertEquals(0, scroll.first());
    }
    @Test void searchResetClearsPositionAndFraction() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(41);
        scroll.scroll(-4.5);
        scroll.reset();
        assertEquals(0, scroll.first());
        scroll.scroll(-0.5);
        assertEquals(0, scroll.first());
    }
    @Test void proportionalThumbAndDragEndpoints() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(9);
        assertEquals(22, scroll.thumbSize(66, 12));
        scroll.dragThumb(-10, 66, 12);
        assertEquals(0, scroll.first());
        scroll.dragThumb(22, 66, 12);
        assertEquals(3, scroll.first());
        assertEquals(22, scroll.thumbOffset(66, 12));
        scroll.dragThumb(1000, 66, 12);
        assertEquals(6, scroll.first());
        assertEquals(44, scroll.thumbOffset(66, 12));
    }
    @Test void largeLibrariesUseMinimumThumbAndAllPositionsAreReachable() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(100);
        assertEquals(12, scroll.thumbSize(66, 12));
        for (int first = 0; first <= 97; first++) {
            scroll.dragThumb(first * 54.0 / 97, 66, 12);
            assertEquals(first, scroll.first());
        }
    }
    @Test void invalidGeometryAndNonfiniteInputAreHarmless() {
        var scroll = new ScrollWindow(3);
        scroll.setItemCount(10);
        scroll.scroll(-2);
        scroll.scroll(Double.NaN);
        scroll.scroll(Double.POSITIVE_INFINITY);
        scroll.dragThumb(Double.NaN, 66, 12);
        scroll.dragThumb(Double.NEGATIVE_INFINITY, 66, 12);
        scroll.dragThumb(4, 0, 12);
        scroll.dragThumb(4, 6, 12);
        assertEquals(2, scroll.first());
        assertEquals(0, scroll.thumbSize(0, 12));
    }
    @Test void exhaustiveDragBoundsAcrossListAndTrackSizes() {
        var scroll = new ScrollWindow(3);
        for (int count = 0; count < 120; count++) {
            scroll.setItemCount(count);
            for (int height = 1; height <= 80; height++) {
                assertTrue(scroll.thumbSize(height, 12) >= 1);
                assertTrue(scroll.thumbSize(height, 12) <= height);
                for (int offset = -10; offset <= 90; offset += 10) {
                    scroll.dragThumb(offset, height, 12);
                    assertTrue(scroll.first() >= 0 && scroll.first() <= scroll.maxFirst());
                    assertTrue(scroll.thumbOffset(height, 12) >= 0);
                    assertTrue(scroll.thumbOffset(height, 12) + scroll.thumbSize(height, 12) <= height);
                }
            }
        }
    }
}
