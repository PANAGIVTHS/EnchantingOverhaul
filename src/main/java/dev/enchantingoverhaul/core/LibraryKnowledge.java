package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/** A freshly aggregated physical-library snapshot, never permanent player knowledge. */
public final class LibraryKnowledge {
    /** Checkpoint choice: inclusive five-block cube, not a distance sphere or obstruction rule. */
    public static final int RADIUS = 5;
    private static final List<Offset> OFFSETS = createOffsets();
    private final Map<String, Integer> highestLevels;

    private LibraryKnowledge(Map<String, Integer> levels) {
        highestLevels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
    }

    /** Adapter supplies actual enchanted-book status and whether ANY enchantment is cursed. */
    public record Blueprint(boolean enchantedBook, boolean cursed, Map<String, Integer> enchantments) {
        public Blueprint { enchantments = Map.copyOf(enchantments); }
        public static Blueprint clean(String enchantmentId, int level) {
            return new Blueprint(true, false, Map.of(enchantmentId, level));
        }
    }

    public record Offset(int x, int y, int z) {}

    /** Only clean, single-enchantment, in-range bookshelf books should reach this method.
     * Invalid levels, curses, equipment and multi-enchantment books contribute nothing. */
    public static LibraryKnowledge aggregate(ReplicationCatalog catalog, Collection<Blueprint> blueprints) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(blueprints, "blueprints");
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Blueprint book : blueprints) {
            if (book == null || !book.enchantedBook() || book.cursed() || book.enchantments().size() != 1) continue;
            Map.Entry<String, Integer> entry = book.enchantments().entrySet().iterator().next();
            ReplicationDefinition definition = catalog.find(entry.getKey()).orElse(null);
            int level = entry.getValue();
            if (definition == null || ReplicationCatalog.isCurse(entry.getKey()) || level < 1 || level > definition.maxLevel()) continue;
            levels.merge(entry.getKey(), level, Math::max);
        }
        return new LibraryKnowledge(levels);
    }

    public Map<String, Integer> highestLevels() { return highestLevels; }
    public int highestLevel(String enchantmentId) { return highestLevels.getOrDefault(enchantmentId, 0); }
    public boolean unlocks(String enchantmentId, int level) {
        return level >= 1 && level <= highestLevel(enchantmentId);
    }
    public List<Integer> unlockedLevels(String enchantmentId) {
        return IntStream.rangeClosed(1, highestLevel(enchantmentId)).boxed().toList();
    }
    public static boolean withinRange(int dx, int dy, int dz) {
        return inRange(dx) && inRange(dy) && inRange(dz);
    }
    public static boolean withinRange(int originX, int originY, int originZ, int shelfX, int shelfY, int shelfZ) {
        return inRange((long) shelfX - originX) && inRange((long) shelfY - originY) && inRange((long) shelfZ - originZ);
    }
    private static boolean inRange(long value) { return value >= -RADIUS && value <= RADIUS; }
    /** Includes the origin; the adapter filters block type, so the replicator itself contributes nothing. */
    public static List<Offset> offsets() { return OFFSETS; }
    private static List<Offset> createOffsets() {
        List<Offset> offsets = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++)
            for (int y = -RADIUS; y <= RADIUS; y++)
                for (int z = -RADIUS; z <= RADIUS; z++) offsets.add(new Offset(x, y, z));
        return List.copyOf(offsets);
    }
}
