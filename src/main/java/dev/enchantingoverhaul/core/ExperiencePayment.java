package dev.enchantingoverhaul.core;

/** Creative waives XP only, never materials, knowledge or enchantment validation. */
public final class ExperiencePayment {
    private ExperiencePayment() {}
    public static int cost(boolean creative, int rawXp) {
        if (rawXp < 0) throw new IllegalArgumentException("Negative XP payment");
        return creative ? 0 : rawXp;
    }
    public static boolean canAfford(boolean creative, long available, int rawXp) {
        return available >= 0 && available >= cost(creative, rawXp);
    }
}
