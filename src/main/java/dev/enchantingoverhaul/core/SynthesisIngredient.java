package dev.enchantingoverhaul.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Exact alternatives for one ingredient. Equipment eligibility is supplied by the adapter. */
public record SynthesisIngredient(List<String> itemIds, String potionId, int count, boolean standardEquipment) {
    public SynthesisIngredient {
        itemIds = List.copyOf(Objects.requireNonNull(itemIds, "itemIds"));
        if (itemIds.isEmpty() || new HashSet<>(itemIds).size() != itemIds.size() || count < 1) {
            throw new IllegalArgumentException("Ingredient needs distinct item alternatives and a positive count");
        }
        for (String itemId : itemIds) {
            // Also validates potion vs non-potion identities without initializing Minecraft.
            new Catalyst(itemId, potionId);
        }
        if (standardEquipment && potionId != null) throw new IllegalArgumentException("Potions are not equipment");
    }

    public boolean matches(SynthesisRules.Material material) {
        return material != null && material.count() > 0 && itemIds.contains(material.itemId())
                && Objects.equals(potionId, material.potionId())
                && (!standardEquipment || material.standardEquipment());
    }
}
