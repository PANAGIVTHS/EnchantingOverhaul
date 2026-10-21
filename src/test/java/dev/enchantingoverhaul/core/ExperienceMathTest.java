package dev.enchantingoverhaul.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceMathTest {
    @Test void specificationThresholds() {
        int[] levels = {0, 5, 10, 14, 15, 18, 22};
        int[] points = {0, 55, 160, 280, 315, 441, 679};
        for (int i = 0; i < levels.length; i++) {
            assertEquals(points[i], ExperienceMath.atLevel(levels[i]));
            assertEquals(levels[i], ExperienceMath.state(points[i]).level());
            assertEquals(0, ExperienceMath.state(points[i]).progress());
        }
    }
    @Test void everyBoundaryUsesVanillaNextLevelCurve() {
        for (int level = 0; level < 10_000; level++)
            assertEquals(ExperienceMath.nextLevel(level), ExperienceMath.atLevel(level + 1) - ExperienceMath.atLevel(level));
    }
    @Test void integerXpRoundTripsThroughVisibleLevelAndBar() {
        for (long xp = 0; xp < 50_000; xp++) {
            var state = ExperienceMath.state(xp);
            assertEquals(xp, ExperienceMath.points(state.level(), state.progress()), "XP " + xp);
        }
    }
    @Test void chargeIsPointsNotLevelsAndExactXpCanReachZero() {
        var zero = ExperienceMath.state(ExperienceMath.atLevel(22) - 679);
        assertEquals(0, zero.level()); assertEquals(0, zero.progress());
        long remaining = ExperienceMath.atLevel(40) - 679;
        var state = ExperienceMath.state(remaining);
        assertEquals(remaining, ExperienceMath.points(state.level(), state.progress()));
        assertTrue(state.level() > 22);
    }
    @Test void repeatedChargesEqualDirectChargeAcrossCurveBreaks() {
        for (int start = 17; start <= 50; start++) {
            long xp = ExperienceMath.atLevel(start);
            for (int cost : new int[]{20, 35, 55, 70, 95}) {
                var state = ExperienceMath.state(xp - cost);
                xp = ExperienceMath.points(state.level(), state.progress());
            }
            assertEquals(ExperienceMath.atLevel(start) - 275, xp);
        }
    }
    @Test void rejectsCorruptValuesAndDoesNotOverflow() {
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.atLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.state(-1));
        for (float bad : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.1f, 1f})
            assertThrows(IllegalArgumentException.class, () -> ExperienceMath.points(10, bad));
        assertEquals(Long.MAX_VALUE, ExperienceMath.atLevel(Integer.MAX_VALUE));
        assertTrue(ExperienceMath.nextLevel(Integer.MAX_VALUE) > Integer.MAX_VALUE);
    }
}
