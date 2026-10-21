package dev.enchantingoverhaul.core;

/** Vanilla raw-XP curve, isolated from Minecraft so thresholds/debits are executable unit tests. */
public final class ExperienceMath {
    private ExperienceMath() {}
    public record State(int level, float progress) {}
    public static long atLevel(int level) {
        if (level < 0) throw new IllegalArgumentException("Negative XP level");
        long n = level;
        try {
            long square = Math.multiplyExact(n, n);
            if (level <= 16) return Math.addExact(square, 6 * n);
            if (level <= 31) return (5 * square - 81 * n + 720) / 2;
            return Math.addExact(Math.subtractExact(Math.multiplyExact(9, square), 325 * n), 4440) / 2;
        } catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }
    public static long nextLevel(int level) {
        if (level < 0) throw new IllegalArgumentException("Negative XP level");
        return level >= 30 ? 112L + (level - 30L) * 9 : level >= 15 ? 37L + (level - 15L) * 5 : 7L + level * 2L;
    }
    public static long points(int level, float progress) {
        if (!Float.isFinite(progress) || progress < 0 || progress >= 1) throw new IllegalArgumentException("Invalid XP bar");
        long base = atLevel(level);
        long bar = Math.round((double)progress * nextLevel(level));
        return base > Long.MAX_VALUE - bar ? Long.MAX_VALUE : base + bar;
    }
    public static State state(long points) {
        if (points < 0) throw new IllegalArgumentException("Negative XP");
        int low = 0, high = Integer.MAX_VALUE;
        while (low < high) {
            int middle = low + (int)(((long)high - low + 1) / 2);
            if (atLevel(middle) <= points) low = middle; else high = middle - 1;
        }
        return new State(low, (float)((double)(points - atLevel(low)) / nextLevel(low)));
    }
}
