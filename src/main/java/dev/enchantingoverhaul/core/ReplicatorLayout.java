package dev.enchantingoverhaul.core;

/** Native GUI-pixel geometry traced from EnchantReplciator.png; shared by visible slots and their hitboxes. */
public final class ReplicatorLayout {
    private ReplicatorLayout() {}
    public static final int WIDTH = 176, HEIGHT = 168;
    public static final int INPUT_X = 10, INPUT_Y = 22, INPUT_PITCH = 22;
    public static final int INVENTORY_X = 7, INVENTORY_Y = 89, HOTBAR_Y = 145, SLOT_PITCH = 18;
    public static final int LIST_X = 86, LIST_Y = 17, ROW_WIDTH = 76, ROW_HEIGHT = 14, ROWS = 3;
    public static final int TRACK_X = 162, TRACK_WIDTH = 8, TRACK_HEIGHT = 42;
    public static final int LEVEL_X = 87, LEVEL_Y = 66, LEVEL_PITCH = 17, LEVEL_SIZE = 12;
    public static final int ACTION_X = 7, ACTION_Y = 54, ACTION_WIDTH = 68, ACTION_HEIGHT = 16;
    public static final int COST_Y = 44, INVENTORY_LABEL_Y = 80;

    public static String numeral(int level) {
        return switch (level) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> Integer.toString(level);
        };
    }
}
