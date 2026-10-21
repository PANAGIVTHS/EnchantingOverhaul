# Development status

## Current milestone — library / Replicator checkpoint
Stages 1–3 implemented for first player testing; final read-only review pending before checkpoint handoff. This is **not the complete survival overhaul**. `SourceMaterial/README.md` is unchanged and remains authoritative. Repository initially contained only the design and two UI mockups; no existing code or Git metadata.

### Implemented
- Fabric project + checked Gradle wrapper; three workstation blocks/items, basic vanilla-texture cube models, item definitions, pickaxe tag and self-drop loot tables.
- Persistent `WorkstationBlockEntity` inventory: Replicator 3 slots; Extractor foundation 3 slots; Combiner foundation 23 slots (2 inputs, output, 20 ingredients). Vanilla container removal handles dropped contents once.
- Inclusive five-block **cube** in all axes (11×11×11), including above/below and through walls, per implementation prompt. No connected-shelf expansion, ownership layer or forced chunk loading.
- Actual vanilla chiseled bookshelf books define shared knowledge. Only clean single-enchantment books qualify; highest natural level wins and grants all lower levels. Menus rescan each server tick; confirmation always rescans/revalidates live state.
- All 41 non-curse catalysts/maxima; proposed cumulative XP/lapis and special Mending costs; strict base potion variants. Potion of Infestation's actual registry ID is `minecraft:infested`.
- Replicator has dedicated target/lapis/catalyst slots; searchable/paginated compatible choices, explicit I–V selection, exact resource preview and rejection messages. One enchantment per click; target remains inserted; other enchantments/components preserved by copy + enchantment-component update. No implicit player-inventory materials.
- Server-side applicability and mutual exclusions delegate to vanilla. No equal-level/downgrade operations or curse replication; cursed books are not valid targets/blueprints. Cursed equipment keeps ordinary curse behavior.
- Existing-level upgrades pay only the XP/lapis difference and no catalyst. Missing payment/knowledge, invalid target/count or incompatibility rejects before mutation; item/payment copies prepared before commit. Repeated same-level clicks cannot charge again.
- Raw XP uses visible level/bar, not stale `totalExperience`; integer debit reconstructs the vanilla curve and explicitly synchronizes the XP display, including after `/experience` commands.

### Key files / architecture
- `core/`: Minecraft-independent catalog, costs, validation plans, library aggregation/range and XP curve.
- `data/enchantingoverhaul/replication.json`: bundled balancing/catalyst data (not a runtime-reloadable datapack yet).
- `game/`: library scan, vanilla enchantment/component bridge, XP debit, prepared replication transaction.
- `menu/ReplicatorMenu`: vanilla menu slot/data synchronization + bounded button IDs; no custom inventory packets. `client/ReplicatorScreen`: provisional functional interface based on supplied mockup, not final visuals.
- `ModContent` and `block/`: registration, inventories and persistence.
- `scripts/verify_artifact.py`: non-game resource/version/vanilla-coverage audit.

## Build / verification actually run
- Target: **Minecraft 26.3**, **Java 25**, **Fabric Loader 0.19.5**, **Fabric API 0.164.0+26.3**, **Loom 1.19.2**, **Gradle 9.8.0**. Java installed via existing `/opt/hermes-tools` infrastructure. Loom required upgrading the initial 9.7.1 wrapper.
- Last build: `./gradlew clean build --no-daemon --console=plain --warning-mode all` — **BUILD SUCCESSFUL**, no compiler/deprecation warnings in final clean run.
- **845 JUnit cases passed**, 0 failures/errors/skips: catalog 58, library 15, planner 766, XP curve 6. Includes every upgrade path/resource threshold, exact catalyst definitions, invalid/over-max levels, absent knowledge, curse/compatibility flags, range boundaries, and 50,000 XP round trips.
- Artifact audit passed against downloaded vanilla 26.3 JARs: actual 43 enchantments = 41 supported + 2 curses, all maxima, catalyst item assets/potion identities, 19 packaged JSON resources, all three block/item/model/texture/loot references, Java 25 class versions, entrypoints and common/client separation.
- Audit command: `python scripts/verify_artifact.py --minecraft-common /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-common.jar --minecraft-client /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-client.jar`
- Mod JAR: `build/libs/enchantingoverhaul-0.1.0.jar` (use this, not the sources JAR). Test report: `build/reports/tests/test/index.html`.
- **No Minecraft, server, GameTest or graphical gameplay test was launched.** Unit tests do not initialize Minecraft. The game-facing transaction, real vanilla compatibility, bootstrap, screen usability, persistence/drops and multiplayer still require player verification.
- No commits/synchronization; workspace is not currently a Git repository.

## Player test checklist (after final handoff)
Use a disposable creative test world with Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API 0.164.0+26.3; the mod belongs in both client and server installations for multiplayer.
1. Obtain via Functional Blocks tab or `/give @s enchantingoverhaul:enchantment_replicator`. Use `/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"minecraft:sharpness":3}]` and place that book in a nearby chiseled bookshelf. Expect I–III only, including shelves through a wall/above/below; coordinate offsets of ±5 qualify, ±6 do not.
2. Set a known XP balance (`/experience set @s 0 levels`, `/experience set @s 0 points`, `/experience add @s 1000 points`). Insert one sword, deposit lapis and flint in workstation slots. Sharpness I costs 20 XP + 2 lapis + 1 flint. Upgrade I→III costs 90 XP + 7 lapis, no catalyst; total 110 XP + 9 lapis + 1 flint. Sword stays in slot.
3. Add an Unbreaking blueprint; apply it to the same sword using obsidian. Sharpness and item name/damage/custom data should remain. Insert a pickaxe and compare choices; Smite on a Sharpness sword should be unavailable.
4. Keep materials only in player inventory, or supply too little lapis/XP/wrong potion variant: no application/payment. On a fresh Smite target use exactly a base Splash Potion of Healing; new Mending costs 240 XP + 18 lapis + 1 Ghast Tear. No creative payment bypass is implemented.
5. Remove/replace the only blueprint while the menu is open; choices should refresh by the next server update, and stale confirmation must not enchant or charge. Check highest-level duplicates, cursed/multi-enchantment books being ignored, and two Replicators sharing shelves.
6. Close/reopen and save/reload with items left in slots. Break a stocked Replicator in survival and check item counts. Test two players sharing it, repeated confirmations and book→enchanted-book conversion for losses/duplication.
Report crashes with `latest.log`, UI screenshots, and exact before/after item/material/XP values.

## Limitations / decisions / next work
- Extractor/purification, Combiner synthesis/85 recipes, all vanilla progression restrictions, librarian/loot changes and special initial Mending crafting are **not implemented**. Extractor/Combiner right-click explicitly reports this checkpoint limitation. Vanilla enchanting/anvils/trades/loot remain unchanged; do not use this build as a completed survival replacement.
- No survival workstation crafting recipes: ingredient quantities/patterns are explicitly undecided. Mending is testable only with a supplied blueprint at this checkpoint; initial acquisition controls come later.
- Final screens/artwork remain provisional. Cube boundary interpretation and clean single-enchantment-only blueprints are explicit checkpoint choices; no permanent unlock/ownership system was invented.
- Potion catalysts are currently consumed as whole specified items, **without a glass-bottle return**. This is provisional, not a finalized bottle-return design decision; obtain player direction before treating it as final.
- Scanning each open menu is bounded but not load-tested at large multiplayer scale. No runtime claims follow from compilation/unit tests alone.
- After the player feedback gate: fix library/Replicator runtime issues first, then implement selective extraction + purification + full synthesis as the next coherent loop. Later integrate vanilla acquisition/bypass controls and audit Grindstone XP loops before release. Do not silently choose undecided crafting/Mending/container rules or alter the gameplay README.
