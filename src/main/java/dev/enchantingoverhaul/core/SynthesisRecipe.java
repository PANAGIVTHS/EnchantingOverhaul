package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Objects;

/** Two single-enchantment books at inputLevel produce one book at resultLevel. No XP/lapis fee. */
public record SynthesisRecipe(String enchantmentId, int inputLevel, List<SynthesisIngredient> ingredients) {
    public SynthesisRecipe {
        Catalyst.requireId(enchantmentId);
        if (ReplicationCatalog.isCurse(enchantmentId)) throw new IllegalArgumentException("Curses cannot be synthesized");
        if (inputLevel < 1 || inputLevel > 4) throw new IllegalArgumentException("Input level must be I–IV");
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (ingredients.isEmpty()) throw new IllegalArgumentException("Synthesis requires materials");
        // Overlapping requirements would make greedy slot accounting ambiguous. Fail closed.
        for (int i = 0; i < ingredients.size(); i++) {
            for (int j = i + 1; j < ingredients.size(); j++) {
                SynthesisIngredient a = ingredients.get(i), b = ingredients.get(j);
                if (Objects.equals(a.potionId(), b.potionId())
                        && a.itemIds().stream().anyMatch(b.itemIds()::contains)) {
                    throw new IllegalArgumentException("Overlapping ingredient requirements");
                }
            }
        }
    }

    public int resultLevel() { return inputLevel + 1; }
}
