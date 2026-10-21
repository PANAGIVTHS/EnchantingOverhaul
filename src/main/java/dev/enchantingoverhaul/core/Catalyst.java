package dev.enchantingoverhaul.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Exact catalyst identity, including the base potion variant when applicable.
 * Container/bottle returns intentionally belong to no rule in this domain. */
public record Catalyst(String itemId, String potionId) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public Catalyst {
        requireId(itemId);
        if (potionId != null) requireId(potionId);
        boolean potionItem = itemId.equals("minecraft:potion")
                || itemId.equals("minecraft:splash_potion")
                || itemId.equals("minecraft:lingering_potion");
        if (potionItem != (potionId != null)) {
            throw new IllegalArgumentException("Potion items require an exact potion id; other items cannot have one");
        }
    }

    public static Catalyst item(String itemId) { return new Catalyst(itemId, null); }

    static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid namespaced id: " + id);
        return id;
    }
}
