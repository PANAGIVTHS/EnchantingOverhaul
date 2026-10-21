package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.EnchantingOverhaul;
import dev.enchantingoverhaul.core.LibraryKnowledge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Physical books are the only source of knowledge; no permanent/player unlock state. */
public final class LibraryScanner {
    private LibraryScanner() {}
    public static LibraryKnowledge scan(Level level, BlockPos origin) {
        List<LibraryKnowledge.Blueprint> books = new ArrayList<>();
        for (var offset : LibraryKnowledge.offsets()) {
            BlockPos pos = origin.offset(offset.x(), offset.y(), offset.z());
            // Never load chunks or look through an unloaded shelf to grant stale knowledge.
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                var stack = shelf.getItem(slot);
                if (!stack.is(Items.ENCHANTED_BOOK)) continue;
                Map<String, Integer> entries = new HashMap<>();
                Enchantments.on(stack).entrySet().forEach(e -> entries.put(Enchantments.id(e.getKey()), e.getIntValue()));
                // Reject illegal over-max registry levels rather than legitimizing command data.
                boolean invalid = Enchantments.on(stack).entrySet().stream()
                        .anyMatch(e -> e.getIntValue() < 1 || e.getIntValue() > e.getKey().value().getMaxLevel());
                books.add(new LibraryKnowledge.Blueprint(true, invalid || Enchantments.hasCurse(stack), entries));
            }
        }
        return LibraryKnowledge.aggregate(EnchantingOverhaul.CATALOG, books);
    }
}
