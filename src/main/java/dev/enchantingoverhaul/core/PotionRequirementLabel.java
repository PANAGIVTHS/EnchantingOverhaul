package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Optional;

/** Disambiguates vanilla potion variants whose item hover names deliberately omit potency/duration. */
public final class PotionRequirementLabel {
    private PotionRequirementLabel() {}

    /** Amplifiers come from the actual registered potion effects, never an English-name inference. */
    public static Optional<String> qualifierKey(String potionId, List<Integer> amplifiers) {
        if (potionId == null) return Optional.empty();
        if (potionId.startsWith("minecraft:long_"))
            return Optional.of("enchantingoverhaul.potion.extended");
        if (!potionId.startsWith("minecraft:strong_")) return Optional.empty();
        if (amplifiers.size() == 1 && amplifiers.getFirst() > 0)
            return Optional.of("potion.potency." + amplifiers.getFirst());
        // Mixed-effect potions must not be falsely advertised as a single effect level.
        return Optional.of("enchantingoverhaul.potion.strong");
    }
}
