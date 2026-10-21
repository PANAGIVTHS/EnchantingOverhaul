# Development status

## Current milestone — stages 4–5, review before player checkpoint
Library/Replicator were player-tested: all requested checks passed except search-field E closing and creative XP requirements. Player approved continuing and confirmed empty glass-bottle returns. Those fixes plus selective extraction, purification and full synthesis are implemented. Final independent review is pending before the manual-checkpoint handoff. **This is not yet the complete survival replacement.** `SourceMaterial/README.md` remains unchanged/authoritative.

## Implemented / key decisions
- Target unchanged: Minecraft **26.3**, Java **25**, Fabric Loader **0.19.5**, Fabric API **0.164.0+26.3**, Loom **1.19.2**, checked Gradle wrapper **9.8.0**. Mod checkpoint version **0.2.0**.
- Replicator search handles focused text input before container shortcuts; Escape closes; clicking outside clears focus and restores normal inventory-key behavior. Actual keyboard interaction still needs player retest.
- Creative bypasses **XP only**, with zero XP preview/debit for replication, extraction and purification. Material, blueprint, compatibility and maximum-level requirements remain enforced.
- One empty glass bottle per consumed potion/splash/lingering potion, Dragon's Breath or Ominous Bottle. Replicator returns into catalyst slot; Extractor reuses emptied reagent slot or dedicated bottle output; Combiner returns into its own ingredient storage. No implicit player inventory use or dropping. Insufficient return room rejects the entire operation.
- Extractor: select one beneficial enchantment, fees **55/160/280/441/679 raw XP**, exactly one book output, source retained with other enchantments/components. Full output, insufficient XP, invalid levels and single-enchantment-book conversion reject. Any curse in applied OR stored enchantments blocks extraction.
- Separate Purify: **1 Dragon's Breath + 315 raw XP** (XP waived in creative), removes all curses from both enchantment components, preserves beneficial enchants/base item/unrelated data, returns one empty bottle. Curse-only enchanted books remain that same base item with empty enchantments; no invented conversion/refund.
- Combiner: two clean single-enchantment books with matching type/level; all **85** documented upgrades across 41 enchantments; 20 internal ingredient slots, take-only output. Exact potion variants and standard pristine/unenchanted equipment ingredients; wool accepts any/mixed colors. Two books + exact materials consumed; surplus retained; one next-level book; no additional XP/lapis, randomness, provenance restrictions or levels above vanilla maxima.
- All operations rebuild validation from live server state and prepare transformed/payment copies before committing. Outputs are not auto-crafted on input insertion. Repeated clicks revalidate, stale selections cannot authorize nonexistent enchants, and occupied outputs block duplication.
- All three minimal screens are 320×232, with layouts separate from rules. Appearance is provisional; no game rendering or visual testing by the agent.
- Existing library behavior retained: actual clean single-enchantment bookshelf books, highest level includes lower levels; inclusive five-block cube in all axes through walls; no forced chunks/per-player knowledge/ownership system.

## Architecture / important files
- `core/Replication*`, `LibraryKnowledge`, `ExperienceMath`: prior pure economy/library logic.
- New `core/ExperiencePayment`, `BottleAccounting`: testable creative XP and immutable bottle-return accounting. `game/BottledIngredients`: prepares real slot copies without mutation.
- `core/Extraction*`, `game/ExtractionService`, `menu/ExtractorMenu`, `client/ExtractorScreen`: selective extraction/purification.
- `core/Synthesis*`, `game/SynthesisService`, `menu/CombinerMenu`, `client/CombinerScreen`: matching, consumption, result and preview.
- Bundled `data/enchantingoverhaul/{replication,extraction,synthesis}.json`: balancing/recipes; not runtime-reloadable datapacks yet.
- `ModContent`, `block/WorkstationBlock*`: registrations/persistence. Extractor inventory grew safely from 3 to **4** slots (target, breath, book output, bottle output); Replicator 3 and Combiner 23 unchanged. Vanilla block-entity removal drops contents once.
- Vanilla menu slots/data/bounded buttons synchronize per-viewer menus; clients never supply costs or output stacks.

## Verification actually executed
- `./gradlew clean build --no-daemon --console=plain --warning-mode all` — **BUILD SUCCESSFUL**, no warnings in the clean run.
- **2,003 JUnit cases passed**, 0 failures/errors/skips: existing rules/catalog/library/XP plus creative XP, bottle conservation/capacity, extraction/purification/transformation plans, every synthesis recipe/resource threshold, exact potion/equipment requirements, max levels and cross-system progression plans.
- All **85 recipes** independently checked against the README table, including ingredient quantities. Artifact audit verifies actual 26.3 vanilla data: exactly 41 non-curse enchants + 2 curses, all maxima, all ingredient item/potion IDs, extraction fees, 21 packaged JSON files, block/item/model/texture/loot references, class versions/entrypoints and common/client separation.
- Audit: `python scripts/verify_artifact.py --minecraft-common /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-common.jar --minecraft-client /opt/hermes-tools/caches/gradle/caches/fabric-loom/26.3/minecraft-client.jar`
- Mod: **`build/libs/enchantingoverhaul-0.2.0.jar`** (not the sources JAR). Tests: `build/reports/tests/test/index.html`.
- **No Minecraft/client/server/GameTest or graphical gameplay test launched.** Tests exercise pure planners, not live Minecraft transactions/components. New runtime interactions, persistence, rendering, concurrent viewers and the reported fixes await player testing.
- No commits/synchronization; workspace currently has no Git metadata.

## Next player checks (after review/handoff)
Install only the new 0.2.0 JAR plus required Fabric API on 26.3; remove old mod versions. Use a disposable world. Workstations remain creative-tab/`/give` obtainable; survival recipes are not decided.
1. Replicator: focus search and type `efficiency`/E plus hotbar keys without closing/moving items; click away then E closes; Escape closes while focused. With zero XP in creative, valid operations succeed at zero XP but still require deposited materials/knowledge. Retest survival fees unchanged.
2. Potion catalyst: fresh Smite on a target consumes the exact base Splash Potion of Healing, returns one empty bottle in its slot; I→III upgrade uses no new catalyst/extra bottle.
3. Extractor: sword with Sharpness V + Unbreaking III; in survival, **679 raw XP succeeds/reaches zero**, 678 fails unchanged. Extract only Sharpness; keep sword name/damage/custom data + Unbreaking; exactly one Sharpness V book. Occupied output blocks; repeated click cannot duplicate. Split a multi-enchantment book until one remains; that last book is already usable.
4. Curses: cursed gear and books cannot extract/use as clean knowledge. Purify for one breath +315 XP; one bottle returned and all curses removed while beneficial enchants persist. Test stacked breath + full bottle output rejects safely, except final breath may reuse its emptied input. No-curse input cannot waste resources.
5. Combiner: two Sharpness I books + **2 flint +1 iron ingot** in workstation storage → Sharpness II. Player-inventory-only ingredients fail. Check surplus retained, mismatched/cursed/multi-enchant/max-level books rejected, full output blocks and bottles return for potion recipes.
6. Higher recipe: two Sharpness IV + **1 netherite scrap +1 Strength II potion** → V; alternatively two Smite III + **1 base Splash Healing +1 glistering melon slice** → IV. Shelf the result and verify higher replication levels. Replicated/extracted books are valid inputs.
7. Save/reload all stocked workstations, survival-break/drop counts, shift-click inputs/outputs and two-player simultaneous/repeated confirmations. Report logs/screenshots plus exact before/after XP, input, output and ingredient counts.

## Remaining scope / next work
- Vanilla enchanting/anvil restrictions, librarian/loot/fishing Mending changes, special initial Mending crafting and Grindstone exploit audit remain stage 6+ work. Vanilla pathways are still unchanged: this checkpoint is for workstation progression, not full survival bypass testing.
- Workstation crafting quantities/patterns and exact initial Mending craft station/amounts remain undecided; do not invent them or edit the authoritative design without approval.
- Final UI/artwork, large-scale multiplayer performance and runtime validation remain open. Resolve player feedback on this progression loop before building later integration on top.
