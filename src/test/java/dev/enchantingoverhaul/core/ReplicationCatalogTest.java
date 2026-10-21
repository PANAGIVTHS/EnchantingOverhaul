package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReplicationCatalogTest {
    private static final ReplicationCatalog CATALOG = ReplicationCatalog.loadDefault();
    // Independent transcription of the confirmed design table, not derived from the JSON under test.
    private static final String EXPECTED = """
            protection 4 shield
            fire_protection 4 magma_cream
            blast_protection 4 obsidian
            projectile_protection 4 shield
            thorns 3 cactus
            respiration 3 nautilus_shell
            aqua_affinity 1 prismarine_shard
            feather_falling 4 feather
            depth_strider 3 potion swiftness
            frost_walker 2 packed_ice
            soul_speed 3 soul_soil
            swift_sneak 3 sculk
            sharpness 5 flint
            smite 5 splash_potion healing
            bane_of_arthropods 5 potion infested
            knockback 2 piston
            fire_aspect 2 blaze_powder
            looting 3 rabbit_foot
            sweeping_edge 3 wind_charge
            density 5 iron_block
            breach 4 iron_pickaxe
            wind_burst 3 wind_charge
            efficiency 5 redstone
            silk_touch 1 honeycomb
            fortune 3 emerald
            unbreaking 3 obsidian
            mending 1 ghast_tear
            power 5 gunpowder
            punch 2 slime_ball
            flame 1 blaze_powder
            infinity 1 ender_pearl
            quick_charge 3 redstone
            multishot 1 firework_rocket
            piercing 4 pointed_dripstone
            loyalty 3 lead
            riptide 3 nautilus_shell
            channeling 1 lightning_rod
            impaling 5 prismarine_shard
            luck_of_the_sea 3 nautilus_shell
            lure 3 tropical_fish
            lunge 3 wind_charge
            """;

    static Stream<Arguments> expectedCatalysts() {
        return EXPECTED.lines().map(line -> line.split(" ")).map(parts -> Arguments.of(
                "minecraft:" + parts[0], Integer.parseInt(parts[1]), "minecraft:" + parts[2],
                parts.length == 4 ? "minecraft:" + parts[3] : null));
    }

    @ParameterizedTest @MethodSource("expectedCatalysts")
    void exactCatalystMaximumAndEveryCumulativeCost(String id, int max, String item, String potion) {
        ReplicationDefinition definition = CATALOG.require(id);
        assertEquals(max, definition.maxLevel());
        assertEquals(new Catalyst(item, potion), definition.catalyst());
        int[] xp = {20, 55, 110, 180, 275};
        int[] lapis = {2, 5, 9, 14, 20};
        for (int level = 1; level <= max; level++) {
            boolean mending = id.equals("minecraft:mending");
            assertEquals(new ReplicationCost(mending ? 240 : xp[level - 1], mending ? 18 : lapis[level - 1], 1), definition.cost(0, level));
        }
    }

    @Test void exactlyFortyOneNonCursesAndSevenSingleLevels() {
        Set<String> expected = EXPECTED.lines().map(line -> "minecraft:" + line.split(" ")[0]).collect(Collectors.toSet());
        assertEquals(41, expected.size());
        assertEquals(expected, CATALOG.definitions().keySet());
        assertEquals(7L, CATALOG.definitions().values().stream().filter(d -> d.maxLevel() == 1).count());
        assertTrue(CATALOG.find("minecraft:binding_curse").isEmpty());
        assertTrue(CATALOG.find("minecraft:vanishing_curse").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CATALOG.require("minecraft:missing"));
    }

    @Test void snapshotsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> CATALOG.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> CATALOG.require("minecraft:smite").cumulativeXp().set(0, 0));
        var xp = new java.util.ArrayList<>(List.of(20));
        var definition = new ReplicationDefinition("test:one", 1, Catalyst.item("minecraft:flint"), xp, List.of(2));
        xp.set(0, 99);
        assertEquals(20, definition.cost(0, 1).rawXp());
    }

    private static String json(String entries, String xp, String lapis) {
        return "{\"schemaVersion\":1,\"cumulativeXp\":" + xp + ",\"cumulativeLapis\":" + lapis + ",\"enchantments\":[" + entries + "]}";
    }
    private static final String ENTRY = "{\"enchantmentId\":\"test:one\",\"maxLevel\":1,\"catalyst\":{\"itemId\":\"minecraft:flint\"}}";

    static Stream<String> malformedCatalogs() {
        return Stream.of(json(ENTRY + "," + ENTRY, "[20]", "[2]"),
                json(ENTRY, "[20,19]", "[2,5]"), json(ENTRY, "[0]", "[2]"),
                json(ENTRY, "[20.5]", "[2]"), json(ENTRY, "[2147483648]", "[2]"),
                json(ENTRY, "[true]", "[2]"), json(ENTRY, "[\"20\"]", "[2]"),
                json(ENTRY.replace("\"maxLevel\":1", "\"maxLevel\":2"), "[20]", "[2]"),
                json(ENTRY.replace("test:one", "minecraft:binding_curse"), "[20]", "[2]"),
                json(ENTRY.replace("test:one", "minecraft:vanishing_curse"), "[20]", "[2]"),
                json(ENTRY.replace("test:one", "NOT AN ID"), "[20]", "[2]"),
                json(ENTRY.replace("minecraft:flint", "minecraft:potion"), "[20]", "[2]"),
                json("", "[20]", "[2]"), json(ENTRY, "[20]", "[2]").replace("schemaVersion\":1", "schemaVersion\":2"));
    }

    @ParameterizedTest @MethodSource("malformedCatalogs")
    void rejectsMalformedCatalogs(String json) {
        assertThrows(RuntimeException.class, () -> ReplicationCatalog.load(new StringReader(json)));
    }

    @Test void rejectsInvalidPotionIdentitiesAndCosts() {
        assertThrows(IllegalArgumentException.class, () -> new Catalyst("minecraft:flint", "minecraft:healing"));
        assertThrows(IllegalArgumentException.class, () -> new Catalyst("minecraft:splash_potion", null));
        assertThrows(IllegalArgumentException.class, () -> new ReplicationCost(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReplicationCost(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReplicationCost(0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> CATALOG.require("minecraft:smite").cost(3, 2));
        assertThrows(IllegalArgumentException.class, () -> CATALOG.require("minecraft:smite").cost(-1, 2));
    }
}
