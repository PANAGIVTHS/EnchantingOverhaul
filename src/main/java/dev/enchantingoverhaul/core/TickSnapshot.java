package dev.enchantingoverhaul.core;

import java.util.Objects;
import java.util.function.Supplier;

/** Instance-owned, one-tick preview memo. Never use a cached value to authorize a transaction. */
public final class TickSnapshot<T> {
    private boolean present;
    private long tick;
    private T value;

    public T preview(long currentTick, Supplier<T> scan) {
        return present && tick == currentTick ? value : refresh(currentTick, scan);
    }

    /** Confirmation bypasses a same-tick preview, then updates the next preview for all viewers. */
    public T refresh(long currentTick, Supplier<T> scan) {
        T fresh = Objects.requireNonNull(scan.get(), "scan result");
        tick = currentTick;
        value = fresh;
        present = true;
        return fresh;
    }

    public void clear() {
        present = false;
        value = null;
    }
}
