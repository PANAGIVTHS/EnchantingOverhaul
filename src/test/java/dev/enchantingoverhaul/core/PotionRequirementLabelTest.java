package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PotionRequirementLabelTest {
    static Stream<String> boostedRecipes() {
        return SynthesisCatalog.loadDefault().recipes().stream().flatMap(r -> r.ingredients().stream())
                .map(SynthesisIngredient::potionId).filter(id -> id != null && id.startsWith("minecraft:strong_"));
    }
    @ParameterizedTest @MethodSource("boostedRecipes")
    void everyCurrentBoostedRecipeShowsLevelTwo(String id) {
        assertTrue(List.of("minecraft:strong_strength", "minecraft:strong_swiftness", "minecraft:strong_healing").contains(id));
        assertEquals("potion.potency.1", PotionRequirementLabel.qualifierKey(id, List.of(1)).orElseThrow());
    }
    @Test void basePotionsAndNonPotionsDoNotGainAnInventedLevel() {
        assertTrue(PotionRequirementLabel.qualifierKey(null, List.of()).isEmpty());
        assertTrue(PotionRequirementLabel.qualifierKey("minecraft:strength", List.of(0)).isEmpty());
        // The base Turtle Master has strong effects already; it is not the strong potion variant.
        assertTrue(PotionRequirementLabel.qualifierKey("minecraft:turtle_master", List.of(3, 2)).isEmpty());
    }
    @Test void suffixUsesActualAmplifierInsteadOfAssumingEveryStrongPotionIsLevelTwo() {
        assertEquals("potion.potency.3", PotionRequirementLabel.qualifierKey("minecraft:strong_slowness", List.of(3)).orElseThrow());
        assertEquals("enchantingoverhaul.potion.strong", PotionRequirementLabel.qualifierKey("minecraft:strong_turtle_master", List.of(5, 3)).orElseThrow());
    }
    @Test void extendedAndForeignVariantsAreNotMisrepresented() {
        assertEquals("enchantingoverhaul.potion.extended", PotionRequirementLabel.qualifierKey("minecraft:long_strength", List.of(0)).orElseThrow());
        assertTrue(PotionRequirementLabel.qualifierKey("other:strong_strength", List.of(4)).isEmpty());
    }
}
