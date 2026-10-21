package dev.enchantingoverhaul;

import dev.enchantingoverhaul.block.WorkstationBlock;
import dev.enchantingoverhaul.block.WorkstationBlockEntity;
import dev.enchantingoverhaul.menu.ReplicatorMenu;
import dev.enchantingoverhaul.menu.ExtractorMenu;
import dev.enchantingoverhaul.menu.CombinerMenu;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registry entry points for workstation content; no survival recipes are chosen here. */
public final class ModContent {
    public static WorkstationBlock REPLICATOR;
    public static WorkstationBlock EXTRACTOR;
    public static WorkstationBlock COMBINER;
    public static BlockEntityType<WorkstationBlockEntity> WORKSTATION_ENTITY;
    public static MenuType<ReplicatorMenu> REPLICATOR_MENU;
    public static MenuType<ExtractorMenu> EXTRACTOR_MENU;
    public static MenuType<CombinerMenu> COMBINER_MENU;

    private static boolean registered;

    private ModContent() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        REPLICATOR = registerWorkstation(WorkstationBlock.Kind.REPLICATOR);
        EXTRACTOR = registerWorkstation(WorkstationBlock.Kind.EXTRACTOR);
        COMBINER = registerWorkstation(WorkstationBlock.Kind.COMBINER);
        WORKSTATION_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("workstation"),
                new BlockEntityType<>(WorkstationBlockEntity::new, Set.of(REPLICATOR, EXTRACTOR, COMBINER)));
        REPLICATOR_MENU = Registry.register(BuiltInRegistries.MENU, id("enchantment_replicator"),
                new MenuType<>((containerId, inventory) ->
                        new ReplicatorMenu(containerId, inventory, new SimpleContainer(3)), FeatureFlags.VANILLA_SET));
        EXTRACTOR_MENU = Registry.register(BuiltInRegistries.MENU, id("enchantment_extractor"),
                new MenuType<>((containerId, inventory) ->
                        new ExtractorMenu(containerId, inventory, new SimpleContainer(2)), FeatureFlags.VANILLA_SET));
        COMBINER_MENU = Registry.register(BuiltInRegistries.MENU, id("enchanted_book_combiner"),
                new MenuType<>((containerId, inventory) ->
                        new CombinerMenu(containerId, inventory, new SimpleContainer(23)), FeatureFlags.VANILLA_SET));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(REPLICATOR);
            entries.accept(EXTRACTOR);
            entries.accept(COMBINER);
        });
        registered = true;
    }

    private static WorkstationBlock registerWorkstation(WorkstationBlock.Kind kind) {
        Identifier id = id(kind.id);
        WorkstationBlock block = Registry.register(BuiltInRegistries.BLOCK, id,
                new WorkstationBlock(kind, BlockBehaviour.Properties.of()
                        .setId(ResourceKey.create(Registries.BLOCK, id))
                        .mapColor(MapColor.COLOR_PURPLE)
                        .strength(3.5F)
                        .sound(SoundType.STONE)
                        .requiresCorrectToolForDrops()));
        BlockItem item = new BlockItem(block, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, id, item);
        return block;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("enchantingoverhaul", path);
    }
}
