package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable slot-count planning for consumption plus guaranteed empty-bottle returns. */
public final class BottleAccounting {
    private BottleAccounting() {}
    public record Slot(int count, int maxStack, boolean bottledIngredient, boolean acceptsBottleMerge) {
        public Slot {
            if (count < 0 || maxStack < 1 || count > maxStack) throw new IllegalArgumentException("Invalid slot state");
        }
    }
    public record Plan(List<Integer> consumed, List<Integer> bottlesAdded) {
        public Plan { consumed = List.copyOf(consumed); bottlesAdded = List.copyOf(bottlesAdded); }
    }
    public static Optional<Plan> plan(List<Slot> slots, Map<Integer,Integer> consumption, List<Integer> returnSlots) {
        if (slots == null || consumption == null || returnSlots == null) return Optional.empty();
        int[] remaining = new int[slots.size()];
        List<Integer> consumed = new ArrayList<>(), bottlesAdded = new ArrayList<>();
        for (Slot slot : slots) { remaining[consumed.size()] = slot.count(); consumed.add(0); bottlesAdded.add(0); }
        long bottles = 0;
        for (var entry : consumption.entrySet()) {
            int index = entry.getKey(), amount = entry.getValue();
            if (index < 0 || index >= slots.size() || amount < 0 || amount > remaining[index]) return Optional.empty();
            remaining[index] -= amount;
            consumed.set(index, amount);
            if (slots.get(index).bottledIngredient()) bottles += amount;
        }
        if (new HashSet<>(returnSlots).size() != returnSlots.size()) return Optional.empty();
        for (int index : returnSlots) if (index < 0 || index >= slots.size()) return Optional.empty();
        // Merge into existing ordinary bottles first, then use slots freed by consumption.
        for (int pass = 0; pass < 2; pass++) for (int index : returnSlots) {
            Slot slot = slots.get(index);
            boolean empty = remaining[index] == 0;
            if (pass == 0 ? empty || !slot.acceptsBottleMerge() : !empty) continue;
            int capacity = 64 - remaining[index];
            int added = (int)Math.min(bottles, capacity);
            remaining[index] += added;
            bottlesAdded.set(index, bottlesAdded.get(index) + added);
            bottles -= added;
        }
        return bottles == 0 ? Optional.of(new Plan(consumed, bottlesAdded)) : Optional.empty();
    }
}
