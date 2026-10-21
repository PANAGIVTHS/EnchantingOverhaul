package dev.enchantingoverhaul;

import dev.enchantingoverhaul.core.ReplicationCatalog;
import dev.enchantingoverhaul.core.ReplicationDefinition;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnchantingOverhaul implements ModInitializer {
    public static final String MOD_ID = "enchantingoverhaul";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ReplicationCatalog CATALOG = ReplicationCatalog.loadDefault();
    // Stable ordering is shared by vanilla menu data/button synchronization.
    public static final List<ReplicationDefinition> DEFINITIONS = CATALOG.definitions().values().stream()
            .sorted(java.util.Comparator.comparing(ReplicationDefinition::enchantmentId)).toList();

    @Override public void onInitialize() {
        ModContent.register();
        LOGGER.info("Enchanting Overhaul library/Replicator checkpoint: {} catalyst definitions", DEFINITIONS.size());
    }
}
