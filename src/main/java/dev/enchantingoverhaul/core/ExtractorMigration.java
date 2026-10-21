package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.List;

/** Count-only migration/placement accounting, independent of Minecraft item and player bootstrap. */
public final class ExtractorMigration {
    private ExtractorMigration() {}
    public record Slot(int retained, int recover) {}
    public record Delivery(List<Integer> inserted, int overflow) {
        public Delivery { inserted = List.copyOf(inserted); }
    }
    /** Keep one target/reagent; all old output slots remain persistent escrow until recovered. */
    public static List<Slot> plan(List<Integer> counts) {
        if (counts.size() < 2) throw new IllegalArgumentException("Extractor requires two input slots");
        List<Slot> result = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            int count = counts.get(i);
            if (count < 0) throw new IllegalArgumentException("Negative item count");
            int retained = i < 2 ? Math.min(count, 1) : 0;
            result.add(new Slot(retained, count - retained));
        }
        return List.copyOf(result);
    }
    /** Overflow stays owned by the source slot in every game mode; successful spawning is NOT delivery. */
    public static int escrowCount(int retained, Delivery delivery) {
        if (retained < 0 || delivery.overflow() < 0) throw new IllegalArgumentException("Negative escrow count");
        return Math.addExact(retained, delivery.overflow());
    }
    /** Caller supplies compatible slot capacity in merge-first order; never discards the remainder. */
    public static Delivery delivery(int count, List<Integer> capacities) {
        if (count < 0) throw new IllegalArgumentException("Negative recovery count");
        List<Integer> inserted = new ArrayList<>();
        int remaining = count;
        for (int capacity : capacities) {
            if (capacity < 0) throw new IllegalArgumentException("Negative inventory capacity");
            int amount = Math.min(remaining, capacity);
            inserted.add(amount);
            remaining -= amount;
        }
        return new Delivery(inserted, remaining);
    }
}
