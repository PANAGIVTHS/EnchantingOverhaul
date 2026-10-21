package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure transaction planner. Rebuild from live server slots before committing any changes.
 * The adapter must prepare output/payment copies, ensure bottle capacity, then consume one
 * book from each input and exactly consumed from the workstation's material slots.
 * Never use a recipe alone as authorization: check accepted(). No XP or lapis is charged. */
public final class SynthesisRules {
    public static final int MATERIAL_SLOTS = 20;
    public static final Material EMPTY_MATERIAL = new Material("minecraft:air", null, 0, false);
    private static final Set<String> BOTTLED_ITEMS = Set.of("minecraft:potion", "minecraft:splash_potion",
            "minecraft:lingering_potion", "minecraft:dragon_breath", "minecraft:ominous_bottle");
    private final SynthesisCatalog catalog;

    public SynthesisRules(SynthesisCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    /** Adapter reports actual enchanted-book identity and whether ANY curse is present. */
    public record Book(boolean enchantedBook, boolean cursed, Map<String, Integer> enchantments, int count) {
        public Book { if (enchantments != null) enchantments = Map.copyOf(enchantments); }
    }

    /** Null itemId and zero count denote an empty slot. standardEquipment means pristine and
     * unenchanted, not simply an equipment item. Adapter must reject custom potion effects
     * or altered variants when mapping standard potions to their exact registry potionId. */
    public record Material(String itemId, String potionId, int count, boolean standardEquipment) {}

    /** naturalMaxLevel must come from the selected enchantment's live registry entry. */
    public record Request(Book first, Book second, int naturalMaxLevel,
                          List<Material> materials, boolean outputEmpty) {
        public Request {
            // Keep malformed null slots representable so plan() can reject rather than mutate.
            if (materials != null) materials = Collections.unmodifiableList(new ArrayList<>(materials));
        }
    }

    public enum Failure {
        NONE, INVALID_REQUEST, INVALID_BOOK, CURSED_BOOK, MULTI_ENCHANTMENT_BOOK,
        MISMATCHED_ENCHANTMENTS, MISMATCHED_LEVELS, INVALID_LEVEL, NATURAL_MAXIMUM,
        NO_RECIPE, OUTPUT_FULL, INSUFFICIENT_MATERIALS
    }

    /** On EVERY failure, all consumed and bottleReturnCount are zero. recipe can
     * remain non-null to show a valid preview when output/materials prevent confirmation. */
    public record Plan(Failure failure, SynthesisRecipe recipe, List<Integer> consumed, int bottleReturnCount) {
        public Plan {
            Objects.requireNonNull(failure, "failure");
            consumed = List.copyOf(consumed);
            if (consumed.size() != MATERIAL_SLOTS || consumed.stream().anyMatch(n -> n < 0)
                    || bottleReturnCount < 0) throw new IllegalArgumentException("Invalid consumption plan");
            if (failure == Failure.NONE) Objects.requireNonNull(recipe, "recipe");
            else if (bottleReturnCount != 0 || consumed.stream().anyMatch(n -> n != 0)) {
                throw new IllegalArgumentException("Failed plans cannot consume resources");
            }
        }
        public boolean accepted() { return failure == Failure.NONE; }
    }

    public Plan plan(Request request) {
        if (request == null || request.first() == null || request.second() == null
                || request.materials() == null || request.materials().size() != MATERIAL_SLOTS
                || request.naturalMaxLevel() < 1) return reject(Failure.INVALID_REQUEST, null);
        for (Material material : request.materials()) {
            if (material == null || material.count() < 0 || (material.count() > 0 && material.itemId() == null)) {
                return reject(Failure.INVALID_REQUEST, null);
            }
        }
        Book first = request.first(), second = request.second();
        if (!first.enchantedBook() || !second.enchantedBook() || first.count() != 1 || second.count() != 1
                || first.enchantments() == null || second.enchantments() == null
                || first.enchantments().isEmpty() || second.enchantments().isEmpty()) return reject(Failure.INVALID_BOOK, null);
        if (first.cursed() || second.cursed() || first.enchantments().keySet().stream().anyMatch(ReplicationCatalog::isCurse)
                || second.enchantments().keySet().stream().anyMatch(ReplicationCatalog::isCurse)) return reject(Failure.CURSED_BOOK, null);
        if (first.enchantments().size() != 1 || second.enchantments().size() != 1) return reject(Failure.MULTI_ENCHANTMENT_BOOK, null);
        var a = first.enchantments().entrySet().iterator().next();
        var b = second.enchantments().entrySet().iterator().next();
        if (!a.getKey().equals(b.getKey())) return reject(Failure.MISMATCHED_ENCHANTMENTS, null);
        if (!a.getValue().equals(b.getValue())) return reject(Failure.MISMATCHED_LEVELS, null);
        int level = a.getValue();
        if (level < 1 || level > request.naturalMaxLevel()) return reject(Failure.INVALID_LEVEL, null);
        if (level == request.naturalMaxLevel()) return reject(Failure.NATURAL_MAXIMUM, null);
        SynthesisRecipe recipe = catalog.find(a.getKey(), level).orElse(null);
        if (recipe == null) return reject(Failure.NO_RECIPE, null);
        if (!request.outputEmpty()) return reject(Failure.OUTPUT_FULL, recipe);

        int[] consumed = new int[MATERIAL_SLOTS];
        long bottles = 0;
        for (SynthesisIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < MATERIAL_SLOTS && remaining > 0; slot++) {
                Material material = request.materials().get(slot);
                if (!ingredient.matches(material)) continue;
                int take = Math.min(remaining, material.count() - consumed[slot]);
                consumed[slot] += take;
                remaining -= take;
                if (returnsBottle(material.itemId())) bottles += take;
            }
            if (remaining != 0) return reject(Failure.INSUFFICIENT_MATERIALS, recipe);
        }
        if (bottles > Integer.MAX_VALUE) return reject(Failure.INVALID_REQUEST, recipe);
        List<Integer> counts = new ArrayList<>(MATERIAL_SLOTS);
        for (int count : consumed) counts.add(count);
        return new Plan(Failure.NONE, recipe, counts, (int) bottles);
    }

    /** User-confirmed rule: each consumed bottled ingredient returns one empty glass bottle. */
    public static boolean returnsBottle(String itemId) { return itemId != null && BOTTLED_ITEMS.contains(itemId); }

    private static Plan reject(Failure failure, SynthesisRecipe recipe) {
        return new Plan(failure, recipe, Collections.nCopies(MATERIAL_SLOTS, 0), 0);
    }
}
