package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionCostsTest {
    @Test void exactBundledFeesAndDefensiveCopies() {
        var costs = ExtractionCosts.loadDefault();
        assertEquals(List.of(55, 160, 280, 441, 679), costs.extractionXp());
        assertEquals(315, costs.purificationXp());
        assertEquals(910, costs.mendingExtractionXp());
        assertEquals(910, costs.extraction("minecraft:mending", 1));
        assertTrue(costs.mendingExtractionXp() > costs.purificationXp());
        assertTrue(costs.extractionXp().stream().allMatch(fee -> fee < costs.mendingExtractionXp()));
        for (int level = 1; level <= 5; level++) assertEquals(costs.extractionXp().get(level - 1), costs.extraction(level));
        assertThrows(UnsupportedOperationException.class, () -> costs.extractionXp().set(0, 1));
        var fees = new ArrayList<>(costs.extractionXp());
        var copied = new ExtractionCosts(fees, 315, 910);
        fees.clear(); assertEquals(5, copied.extractionXp().size());
    }
    @ParameterizedTest @ValueSource(ints={-1, 0, 6, 255, Integer.MAX_VALUE})
    void outOfRangeLevelCannotIndexSchedule(int level) {
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.loadDefault().extraction(level));
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.loadDefault().extraction("minecraft:mending", level));
    }
    @Test void onlyExactVanillaMendingKeyGetsSpecialFee() {
        var costs = ExtractionCosts.loadDefault();
        for (String id : List.of("minecraft:sharpness", "example:mending", "mending", "minecraft:mending_extra"))
            for (int level = 1; level <= 5; level++) assertEquals(costs.extraction(level), costs.extraction(id, level));
    }
    @Test void mendingMustStrictlyExceedEveryConfiguredOrdinaryAndCurseFee() {
        var ordinary = List.of(55, 160, 280, 441, 679);
        assertThrows(IllegalArgumentException.class, () -> new ExtractionCosts(ordinary, 315, 679));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionCosts(ordinary, 910, 910));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionCosts(ordinary, 911, 910));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionCosts(List.of(55, 160, 280, 441, 911), 315, 910));
        assertEquals(680, new ExtractionCosts(ordinary, 315, 680).mendingExtractionXp());
        assertEquals(32767, new ExtractionCosts(ordinary, 315, 32767).mendingExtractionXp());
    }
    @ParameterizedTest @ValueSource(strings={"-1", "0", "315", "678", "679", "32768", "2147483648", "910.5", "\"910\"", "true", "null", "[]", "{}"})
    void invalidMendingFeeFailsClosed(String value) {
        String json = "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":315,\"mendingExtractionXp\":" + value + "}";
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.load(new StringReader(json)));
    }
    @Test void mendingFeeIsRequiredRatherThanSilentlyUsingOrdinaryLevelOne() {
        String json = "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":315}";
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.load(new StringReader(json)));
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "null", "[]", "not json",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":2,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,160,280,441],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679,1000],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,0,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,-1,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,54,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,32768],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":0}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":32768}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55.1,160,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[\"55\",160,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[true,160,280,441,679],\"purificationXp\":315}",
        "{\"mendingExtractionXp\":910,\"schemaVersion\":1,\"extractionXp\":[55,null,280,441,679],\"purificationXp\":315}"
    }) void malformedTuningFailsClosed(String json) {
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.load(new StringReader(json)));
    }
}
