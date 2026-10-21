package dev.enchantingoverhaul.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LibraryKnowledgeTest {
    private final ReplicationCatalog catalog = ReplicationCatalog.loadDefault();

    @Test void highestDuplicateGrantsAllLowerLevelsAndRemovalLosesKnowledge() {
        var books = new ArrayList<>(List.of(LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 4),
                LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 2), LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 4)));
        var snapshot = LibraryKnowledge.aggregate(catalog, books);
        assertEquals(Map.of("minecraft:sharpness", 4), snapshot.highestLevels());
        assertEquals(List.of(1, 2, 3, 4), snapshot.unlockedLevels("minecraft:sharpness"));
        assertFalse(snapshot.unlocks("minecraft:sharpness", 0));
        assertFalse(snapshot.unlocks("minecraft:sharpness", 5));
        books.removeIf(book -> book.enchantments().get("minecraft:sharpness") == 4);
        assertEquals(2, LibraryKnowledge.aggregate(catalog, books).highestLevel("minecraft:sharpness"));
        books.clear();
        assertTrue(LibraryKnowledge.aggregate(catalog, books).highestLevels().isEmpty());
        assertEquals(4, snapshot.highestLevel("minecraft:sharpness"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.highestLevels().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unlockedLevels("minecraft:sharpness").clear());
    }

    @Test void ignoresCursesMultiBooksEquipmentUnknownAndUnnaturalLevels() {
        var books = new ArrayList<LibraryKnowledge.Blueprint>();
        books.add(new LibraryKnowledge.Blueprint(true, true, Map.of("minecraft:sharpness", 5)));
        books.add(new LibraryKnowledge.Blueprint(true, false, Map.of("minecraft:sharpness", 5, "minecraft:vanishing_curse", 1)));
        books.add(new LibraryKnowledge.Blueprint(true, false, Map.of("minecraft:sharpness", 5, "minecraft:unbreaking", 3)));
        books.add(new LibraryKnowledge.Blueprint(false, false, Map.of("minecraft:sharpness", 5)));
        books.add(new LibraryKnowledge.Blueprint(true, false, Map.of()));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:binding_curse", 1));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:vanishing_curse", 1));
        books.add(LibraryKnowledge.Blueprint.clean("test:missing", 1));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 0));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:sharpness", -1));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:sharpness", 6));
        books.add(LibraryKnowledge.Blueprint.clean("minecraft:mending", 2));
        books.add(null);
        assertTrue(LibraryKnowledge.aggregate(catalog, books).highestLevels().isEmpty());
    }

    @Test void blueprintDefensivelyCopiesItsEnchantmentMap() {
        var enchantments = new java.util.HashMap<>(Map.of("minecraft:smite", 3));
        var blueprint = new LibraryKnowledge.Blueprint(true, false, enchantments);
        enchantments.clear();
        assertEquals(Map.of("minecraft:smite", 3), blueprint.enchantments());
        assertThrows(UnsupportedOperationException.class, () -> blueprint.enchantments().clear());
    }

    @ParameterizedTest @CsvSource({"0,0,0,true", "5,5,5,true", "-5,-5,-5,true", "6,0,0,false", "0,6,0,false", "0,0,6,false", "-6,0,0,false", "0,-6,0,false", "0,0,-6,false", "-2147483648,0,0,false", "2147483647,0,0,false"})
    void inclusiveCubeNotSphere(int x, int y, int z, boolean expected) {
        assertEquals(expected, LibraryKnowledge.withinRange(x, y, z));
    }

    @Test void offsetsExactlyCoverThePredicateAndDoNotOverflow() {
        assertEquals(1331, LibraryKnowledge.offsets().size());
        assertEquals(1331, new HashSet<>(LibraryKnowledge.offsets()).size());
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            assertEquals(LibraryKnowledge.withinRange(x, y, z), LibraryKnowledge.offsets().contains(new LibraryKnowledge.Offset(x, y, z)));
        }
        assertFalse(LibraryKnowledge.withinRange(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0));
        assertTrue(LibraryKnowledge.withinRange(Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE - 5, 5, -5));
        assertThrows(UnsupportedOperationException.class, () -> LibraryKnowledge.offsets().clear());
    }
}
