package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class EnchantingOverhaulClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(ModContent.REPLICATOR_MENU, ReplicatorScreen::new);
        MenuScreens.register(ModContent.EXTRACTOR_MENU, ExtractorScreen::new);
        MenuScreens.register(ModContent.COMBINER_MENU, CombinerScreen::new);
    }
}
