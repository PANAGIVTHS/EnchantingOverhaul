package dev.enchantingoverhaul.core;

/** Match vanilla tooltip input-mode semantics without loading client classes in tests. */
public final class WidgetPreviewPolicy {
    private WidgetPreviewPolicy() {}

    /** Mouse clicks keep focus for navigation, but must not pin previews/highlights after hover ends. */
    public static boolean requested(boolean visible, boolean hovered, boolean focused, boolean keyboardNavigation) {
        return visible && (hovered || focused && keyboardNavigation);
    }
}
