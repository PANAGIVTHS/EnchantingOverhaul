#!/usr/bin/env python3
"""Source/data-only 26.3 Grindstone audit; never launches Gradle or Minecraft.

Checks every vanilla enchantment/level, all ordered pairs of replicable entries,
and the contribution bound for arbitrary multi-enchantment/two-input sums. JSON
contains every row, including retained zero-refund curses. Counterexamples are
possible positive XP outcomes, not a claim of positive expected-value farming.
"""

import argparse
import itertools
import json
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def refund_bounds(contribution):
    if contribution == 0:
        return 0, 0
    half = (contribution + 1) // 2
    return half, 2 * half - 1


def compact(source):
    return re.sub(r"\s+", "", source)


def require_source(source, *fragments):
    normalized = compact(source)
    for fragment in fragments:
        if compact(fragment) not in normalized:
            raise AssertionError(f"Cached source formula changed: {fragment}")


def audit(common, sources, catalog_path):
    catalog = json.loads(catalog_path.read_text())
    definitions = {entry["enchantmentId"]: entry for entry in catalog["enchantments"]}
    assert len(definitions) == len(catalog["enchantments"]), "Duplicate catalog IDs"
    with zipfile.ZipFile(sources) as archive:
        grindstone = archive.read("net/minecraft/world/inventory/GrindstoneMenu.java").decode()
        enchantment = archive.read("net/minecraft/world/item/enchantment/Enchantment.java").decode()
        anvil = archive.read("net/minecraft/world/inventory/AnvilMenu.java").decode()
        table = archive.read("net/minecraft/world/inventory/EnchantmentMenu.java").decode()
    require_source(grindstone,
                   "amount += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));",
                   "amount += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));",
                   "int halfAmount = (int)Math.ceil(amount / 2.0);",
                   "return halfAmount + level.getRandom().nextInt(halfAmount);",
                   "if (!enchant.is(EnchantmentTags.CURSE)) { amount += enchant.value().getMinCost(lvl); }",
                   "enchantments -> enchantments.removeIf(enchantment -> !enchantment.is(EnchantmentTags.CURSE))")
    require_source(enchantment,
                   "return this.definition.minCost().calculate(level);",
                   "return this.base + this.perLevelAboveFirst * (level - 1);")
    # Guard the anvil redirect's ordinal contract against source-version drift.
    start = anvil.index("public void createResult()")
    end = anvil.index("public static int calculateIncreasedRepairCost", start)
    reads = re.findall(r"EnchantmentHelper\.getEnchantmentsForCrafting\((\w+)\)", anvil[start:end])
    assert reads == ["result", "addition"], reads
    require_source(table, "public void slotsChanged(final Container container)",
                   "public boolean clickMenuButton(final Player player, final int buttonId)",
                   "DataSlot.shared(this.costs, 0)", "DataSlot.shared(this.enchantClue, 0)",
                   "DataSlot.shared(this.levelClue, 0)")

    with zipfile.ZipFile(common) as archive:
        vanilla = {"minecraft:" + Path(name).stem: json.loads(archive.read(name))
                   for name in archive.namelist()
                   if name.startswith("data/minecraft/enchantment/") and name.endswith(".json")}
        curses = set(json.loads(archive.read("data/minecraft/tags/enchantment/curse.json"))["values"])
        assert len(vanilla) == 43 and len(curses) == 2, "Target is not the audited 26.3 registry"
        assert set(definitions) == set(vanilla) - curses, "Replication coverage differs from vanilla"
        # Simple constant-pool UTF8 membership identifies the private method's actual class.
        owners = [name for name in archive.namelist()
                  if re.fullmatch(r"net/minecraft/world/inventory/GrindstoneMenu\$\d+\.class", name)
                  and b"getExperienceFromItem" in archive.read(name)]
        assert owners == ["net/minecraft/world/inventory/GrindstoneMenu$4.class"], owners

    rows = []
    for enchantment_id, data in sorted(vanilla.items()):
        for level in range(1, data["max_level"] + 1):
            minimum = data["min_cost"]["base"] + data["min_cost"]["per_level_above_first"] * (level - 1)
            curse = enchantment_id in curses
            row = {"id": enchantment_id, "level": level, "curse": curse, "vanilla_min_cost": minimum}
            if curse:
                row.update(replication_xp=None, vanilla_contribution=0, patched_contribution=0,
                           vanilla_refund_min=0, vanilla_refund_max=0,
                           patched_refund_min=0, patched_refund_max=0)
            else:
                definition = definitions[enchantment_id]
                assert definition["maxLevel"] == data["max_level"], enchantment_id
                fee = definition.get("cumulativeXp", catalog["cumulativeXp"])[level - 1]
                assert fee > 0
                patched = min(minimum, fee)
                before = refund_bounds(minimum)
                after = refund_bounds(patched)
                row.update(replication_xp=fee, vanilla_contribution=minimum, patched_contribution=patched,
                           vanilla_refund_min=before[0], vanilla_refund_max=before[1],
                           patched_refund_min=after[0], patched_refund_max=after[1])
                assert after[1] <= fee, row
            rows.append(row)

    replicable = [row for row in rows if not row["curse"]]
    unsafe = [row for row in replicable if row["vanilla_refund_max"] > row["replication_xp"]]
    changed = [row for row in replicable if row["vanilla_contribution"] != row["patched_contribution"]]
    pair_count = 0
    vanilla_unsafe_pairs = 0
    for left, right in itertools.product(replicable, repeat=2):
        paid = left["replication_xp"] + right["replication_xp"]
        vanilla_sum = left["vanilla_contribution"] + right["vanilla_contribution"]
        patched_sum = left["patched_contribution"] + right["patched_contribution"]
        vanilla_unsafe_pairs += refund_bounds(vanilla_sum)[1] > paid
        assert patched_sum <= paid
        assert refund_bounds(patched_sum)[1] <= paid
        pair_count += 1
    # Each capped entry <= its fee; sum preserves <= across arbitrary item/input
    # groupings. Vanilla ceil(sum/2)+nextInt(ceil(sum/2)) <= sum, including odd sums.
    for total in range(sum(row["patched_contribution"] for row in replicable) * 2 + 1):
        assert refund_bounds(total)[1] <= total

    return {"minecraft": "26.3", "enchantments": len(vanilla), "curses": len(curses),
            "enchantment_level_rows": len(rows), "replicable_level_rows": len(replicable),
            "ordered_pair_checks": pair_count, "vanilla_unsafe_pairs": vanilla_unsafe_pairs,
            "grindstone_result_slot_class": owners[0][:-6].replace("/", "."),
            "anvil_crafting_reads": reads, "vanilla_possible_positive_xp_outcomes": unsafe,
            "changed_contributions": changed, "patched_unsafe_outcomes": 0,
            "scope": "Registry/default-cost audit, not runtime gameplay verification; arbitrary sums bounded by per-entry proof.",
            "rows": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-common", type=Path, required=True)
    parser.add_argument("--minecraft-sources", type=Path)
    parser.add_argument("--catalog", type=Path, default=ROOT / "src/main/resources/data/enchantingoverhaul/replication.json")
    parser.add_argument("--json", type=Path, help="Optional full machine-readable audit report")
    args = parser.parse_args()
    if args.minecraft_sources is None:
        matches = list((ROOT / ".gradle/loom-cache/minecraftMaven").rglob("minecraft-common-*-sources.jar"))
        if len(matches) != 1:
            parser.error("Pass --minecraft-sources; cached common sources are missing or ambiguous")
        args.minecraft_sources = matches[0]
    report = audit(args.minecraft_common, args.minecraft_sources, args.catalog)
    if args.json is not None:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=2) + "\n")
    summary = {key: value for key, value in report.items() if key != "rows"}
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
