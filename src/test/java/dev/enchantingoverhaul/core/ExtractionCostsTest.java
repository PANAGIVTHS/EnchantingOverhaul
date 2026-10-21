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
        for (int level = 1; level <= 5; level++) assertEquals(costs.extractionXp().get(level - 1), costs.extraction(level));
        assertThrows(UnsupportedOperationException.class, () -> costs.extractionXp().set(0, 1));
        var fees = new ArrayList<>(costs.extractionXp());
        var copied = new ExtractionCosts(fees, 315);
        fees.clear(); assertEquals(5, copied.extractionXp().size());
    }
    @ParameterizedTest @ValueSource(ints={-1, 0, 6, 255, Integer.MAX_VALUE})
    void outOfRangeLevelCannotIndexSchedule(int level) {
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.loadDefault().extraction(level));
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "null", "[]", "not json",
        "{\"schemaVersion\":2,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679,1000],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,0,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,-1,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,54,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,32768],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":0}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,160,280,441,679],\"purificationXp\":32768}",
        "{\"schemaVersion\":1,\"extractionXp\":[55.1,160,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[\"55\",160,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[true,160,280,441,679],\"purificationXp\":315}",
        "{\"schemaVersion\":1,\"extractionXp\":[55,null,280,441,679],\"purificationXp\":315}"
    }) void malformedTuningFailsClosed(String json) {
        assertThrows(IllegalArgumentException.class, () -> ExtractionCosts.load(new StringReader(json)));
    }
}
