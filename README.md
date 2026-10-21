# Enchanting Overhaul

Fabric mod for **Minecraft Java 26.3**, requiring **Java 25**, **Fabric Loader 0.19.5+** and **Fabric API 0.164.0+26.3**. Install the built mod and Fabric API on the client and server. This is a development build; back up existing worlds before upgrading. Use only one Enchanting Overhaul JAR at a time.

The gameplay specification is [`SourceMaterial/README.md`](SourceMaterial/README.md). Newer approved gameplay decisions, verification results and remaining limitations are recorded in [`SourceMaterial/DEVELOPMENT_STATUS.md`](SourceMaterial/DEVELOPMENT_STATUS.md).

## Player progression

1. Find enchanted equipment or books. Use the **Extractor** to transfer a selected enchantment into a supplied blank book. Each beneficial transfer costs raw XP; the source and its other properties remain intact.
2. Remove curses first: select **one curse**, supply **one Dragon's Breath**, and spend **315 raw XP**. The reagent slot becomes a glass bottle. Each remaining curse requires a separate operation.
3. Place clean, single-enchantment books in vanilla **chiseled bookshelves** within five blocks of the **Replicator**, including above/below and through walls. The highest book level unlocks that level and all lower levels. Removing the last qualifying book removes that knowledge. No player research unlock is stored.
4. Put a target, lapis and the required initiation catalyst in the Replicator. Select one enchantment and level; confirm one application. The target stays inserted. Upgrading an enchantment already present pays only the cumulative XP/lapis difference and no second catalyst.
5. Put two matching, equal-level books and the displayed recipe materials into the **Combiner**. Confirm to consume the recipe and create the next-level book. All 85 natural upgrades are covered; no levels above vanilla maxima are added. Place the result in a shelf to expand the library.

Workstations never silently withdraw materials from your player inventory. Creative mode waives **XP only**, not materials, knowledge or compatibility requirements. Bottled ingredients return empty bottles. Result space must be available before payment is committed.

## Crafting

All patterns below are **crafting-table** recipes. Dots represent empty slots. The workstations are shaped, not shapeless; each recipe produces one block.

### Extractor

```text
A C A
C G C
. C .
```

`A` = Amethyst Shard; `C` = Copper Ingot; `G` = Grindstone. Total: two shards, four ingots, one grindstone.

### Replicator

```text
A . A
. T .
A . A
```

`A` = Amethyst Shard; `T` = Enchanting Table. Total: four shards and one table.

### Combiner

```text
A . A
. V .
A B A
```

`A` = Amethyst Shard; `V` = Anvil; `B` = Bookshelf. Total: four shards, one anvil, one ordinary bookshelf.

### First Mending book

**Shapeless:** one Nether Star + one Ghast Tear + one Golden Apple + one Book → one **Mending I enchanted book**. No XP charge. Once stored in the library, Mending replication costs **240 raw XP + 18 lapis + one Ghast Tear** on a fresh target; another Nether Star is not required.

## Extraction costs

| Selected enchantment | Raw XP |
| --- | ---: |
| Ordinary level I | 55 |
| Ordinary level II | 160 |
| Ordinary level III | 280 |
| Ordinary level IV | 441 |
| Ordinary level V | 679 |
| **Mending I** | **910** |
| Each selected curse | 315 |

Mending is the most expensive extraction. Every beneficial operation also requires **one blank book**. The book or glass bottle replaces the reagent in the same slot; take it before supplying the next reagent. A single-beneficial-enchantment book needs no splitting. Costs are raw XP points, not a fixed number of displayed XP levels.

## Vanilla integration

- Enchanting tables no longer offer random enchantment rolls. Existing placed tables are not destroyed or replaced; they remain crafting ingredients for Replicators.
- Anvils retain ordinary repair and renaming, preserving the left item's enchantments. They cannot transfer, combine or upgrade enchantments from the right item/book. Smithing upgrades retain vanilla component-preserving behavior.
- New librarian book trades offer level I in nine of ten level rolls and level II in one of ten when valid; single-level enchantments remain I. Vanilla eligibility/biome rules and level-appropriate emerald pricing remain. Ordinary generated Mending sources are excluded; fishing treasure opportunities and loot weights are not reduced.
- Old saved merchant offers cannot bypass progression: Mending outputs are hidden and blocked for all vanilla merchants; level III+ enchanted books are hidden and blocked for librarians. Saved offer contents, uses, prices and discounts are retained, not rerolled or rewritten. Already-owned items and library books are untouched.
- Grindstones retain disenchanting, repair, curses and small random XP refunds. Only Channeling I and Swift Sneak I refund contributions are capped to prevent a refund exceeding their replication XP payment.

## Controls and shared workstations

The Replicator follows the compact screenshot layout: click its title or press **Ctrl+F** to reveal search. Clear the query and click outside to restore the title. Extractor search is unchanged. Search supports ordinary typing, including E, while focused; Escape closes the screen. Click outside search to restore container shortcuts. Scroll enchantment rows with the wheel or drag the vertical thumb. Hover Cost or a button for full payment/material information, including unavailable actions.

Mouse-click focus no longer pins requirement popups or hover highlights after the pointer leaves. Tab/arrow keyboard focus still supports previews. A selected enchantment's pink border is intentional selection, not a held-down mouse button. Changing the Replicator target clears its old selection to avoid applying a previously previewed operation to a different item; the target itself remains inserted after a successful operation.

Screen previews share one library scan per workstation per server tick, including while world ticks are frozen. Every transaction rescans the physical library immediately before validation; removing a blueprint cannot be bypassed using an old screen snapshot. This reduces redundant scans for viewers of the same workstation, but is not a measured multiplayer-performance guarantee.

## Existing worlds

Old four-slot Extractor contents are retained. On opening, old outputs and oversized input/reagent surplus are returned to compatible player-inventory slots. If your inventory is full, overflow remains stored; make room and reopen. Recovery does not spawn item drops. Back up worlds before migration testing.

## Development and verification

```sh
./gradlew clean build --no-daemon --console=plain --warning-mode all
python scripts/verify_artifact.py \
  --minecraft-common /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-common.jar \
  --minecraft-client /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-client.jar
```

Main artifact: `build/libs/enchantingoverhaul-0.3.2.jar` (not the `-sources.jar`). Reports: `build/reports/tests/test/index.html`.

Automated work uses ordinary non-game JUnit and resource/bytecode/source audits. Minecraft, dedicated servers, GameTests and graphical gameplay testing are **not** launched by the assistant. Builds and static audits do not establish runtime mixin application, networking, rendering, persistence or multiplayer correctness; the player owns those checks. No commits or synchronization are performed without permission.
