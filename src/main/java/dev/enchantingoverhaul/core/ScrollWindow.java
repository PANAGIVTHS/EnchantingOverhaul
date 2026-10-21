package dev.enchantingoverhaul.core;

/** Pure, selection-independent window and proportional scrollbar math. */
public final class ScrollWindow {
    private final int visibleRows;
    private int itemCount;
    private int first;
    private double wheelRemainder;

    public ScrollWindow(int visibleRows) {
        if (visibleRows < 1) throw new IllegalArgumentException("visibleRows must be positive");
        this.visibleRows = visibleRows;
    }
    public int first() { return first; }
    public int maxFirst() { return Math.max(0, itemCount - visibleRows); }
    public boolean scrollable() { return maxFirst() > 0; }
    public void setItemCount(int count) {
        itemCount = Math.max(0, count);
        first = Math.clamp(first, 0, maxFirst());
        if (!scrollable()) wheelRemainder = 0;
    }
    public void reset() { first = 0; wheelRemainder = 0; }
    public void scroll(double wheelDelta) {
        if (!Double.isFinite(wheelDelta) || !scrollable()) return;
        wheelRemainder -= wheelDelta;
        int steps = (int)Math.clamp(wheelRemainder, -itemCount, itemCount);
        wheelRemainder -= steps;
        first = (int)Math.clamp((long)first + steps, 0L, maxFirst());
        if (first == 0 && wheelRemainder < 0 || first == maxFirst() && wheelRemainder > 0) wheelRemainder = 0;
    }
    public int thumbSize(int trackHeight, int minimumSize) {
        if (trackHeight <= 0) return 0;
        if (!scrollable()) return trackHeight;
        return Math.clamp((int)Math.round((double)trackHeight * visibleRows / itemCount),
                Math.clamp(minimumSize, 1, trackHeight), trackHeight);
    }
    public int thumbOffset(int trackHeight, int minimumSize) {
        int travel = trackHeight - thumbSize(trackHeight, minimumSize);
        return !scrollable() ? 0 : (int)Math.round((double)first * travel / maxFirst());
    }
    /** Offset is the thumb's top relative to the track, not the pointer's center. */
    public void dragThumb(double topOffset, int trackHeight, int minimumSize) {
        int travel = trackHeight - thumbSize(trackHeight, minimumSize);
        if (!Double.isFinite(topOffset) || !scrollable() || travel <= 0) return;
        first = (int)Math.round(Math.clamp(topOffset / travel, 0.0, 1.0) * maxFirst());
        wheelRemainder = 0;
    }
}
