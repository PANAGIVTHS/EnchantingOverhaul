#!/usr/bin/env python3
"""Read-only acquisition audit against the actual 26.3 jars; does not start Minecraft."""
import argparse
import json
import subprocess
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-common', required=True, type=Path)
    parser.add_argument('--minecraft-sources', required=True, type=Path)
    parser.add_argument('--classpath', help='Optional exact compile classpath for javap bytecode checks')
    parser.add_argument('--javap', default='javap')
    args = parser.parse_args()
    counts = Counter()
    explicit = []
    random_mending_sources = []
    with ZipFile(args.minecraft_common) as jar:
        for name in jar.namelist():
            if not name.endswith('.json') or not any(part in name for part in ('/loot_table/', '/villager_trade/', '/enchantment_provider/')):
                continue
            document = json.loads(jar.read(name))
            if '/villager_trade/librarian/' in name and document.get('gives', {}).get('id') == 'minecraft:enchanted_book':
                mode = 'rebalance' if '/datapacks/trade_rebalance/' in name else 'default'
                assert document.get('double_trade_price_enchantments') == '#minecraft:double_trade_price', name
                assert document.get('additional_wants', {}).get('id') == 'minecraft:book', name
                functions = [v for v in walk(document.get('given_item_modifier')) if isinstance(v, dict)]
                generators = [v for v in functions if v.get('type') in ('minecraft:enchant_randomly', 'minecraft:set_enchantments')]
                assert len(generators) == 1, name
                counts[f'{mode}_book_trades'] += 1
                counts[f'{mode}_{generators[0]["type"].split(":")[1]}'] += 1
                assert any(v.get('type') == 'minecraft:filtered' for v in functions), name
                if generators[0]['type'] == 'minecraft:set_enchantments':
                    assignments = generators[0]['enchantments']
                    assert len(assignments) == 1, name
                    level = next(iter(assignments.values()))
                    # Fixed rebalance prices have an INCLUSIVE upper bound, unlike random trades.
                    provider = document['wants']['count']
                    assert provider == {'type': 'minecraft:add', 'inputs': [3 * level + 2,
                            {'type': 'minecraft:uniform', 'max': 5 + level * 10, 'min': 0}]}, name
            if b'minecraft:mending' in jar.read(name):
                known_before = len(explicit) + len(random_mending_sources)
                for value in walk(document):
                    if not isinstance(value, dict):
                        continue
                    if 'minecraft:mending' in value.get('enchantments', {}):
                        assert value.get('type') == 'minecraft:set_enchantments', (name, value)
                        counts['explicit_mending_assignment'] += 1
                        explicit.append(name)
                    options = value.get('options')
                    if options == 'minecraft:mending' or isinstance(options, list) and 'minecraft:mending' in options:
                        assert value.get('type') == 'minecraft:enchant_randomly', name
                        counts['mending_only_random_source' if options == 'minecraft:mending' else 'mixed_mending_random_source'] += 1
                        random_mending_sources.append(name)
                assert len(explicit) + len(random_mending_sources) > known_before, ('Uncovered direct Mending source', name)
        fishing = json.loads(jar.read('data/minecraft/loot_table/gameplay/fishing/treasure.json'))
        entries = fishing['pools'][0]['entries']
        book = next(entry for entry in entries if entry.get('name') == 'minecraft:book')
        assert book['modifier']['type'] == 'minecraft:enchant_with_levels'
        assert book['modifier']['options'] == '#minecraft:on_random_loot'
        assert book['modifier']['levels'] == 30
        assert len(entries) == 6 and all(entry.get('weight', 1) == 1 for entry in entries)
        counts['unchanged_fishing_treasure_entries'] = len(entries)

    with ZipFile(args.minecraft_sources) as sources:
        def source(suffix):
            return sources.read(next(name for name in sources.namelist() if name.endswith('/' + suffix))).decode()
        contracts = {
            'ItemStack.java': ['return this.transmuteCopy(newItem, this.getCount());',
                    'return new ItemStack(newItem.asItem().builtInRegistryHolder(), newCount, this.components.asPatch());'],
            'EnchantmentHelper.java': ['getAvailableEnchantmentResults(enchantmentCost, itemStack, source)',
                    'List<EnchantmentInstance> enchants = selectEnchantment(random, itemStack, enchantmentCost, source);'],
            'EnchantWithLevelsFunction.java': ['EnchantmentHelper.enchantItem(random, itemStack, enchantmentCost, registryAccess, this.options)'],
            'EnchantmentsByCost.java': ['EnchantmentHelper.selectEnchantment'],
            'EnchantmentsByCostWithDifficulty.java': ['EnchantmentHelper.selectEnchantment'],
            'EnchantRandomlyFunction.java': ['Util.getRandomSafe(compatibleEnchantments, random)',
                    '2 + random.nextInt(5 + level * 10) + 3 * level'],
            'SetEnchantmentsFunction.java': ['this.enchantments', 'EnchantmentHelper.updateEnchantments('],
            'VillagerTrade.java': ['ItemStack result = this.gives.create();',
                    'result = this.givenItemModifier.get().value().apply(result, lootContext);',
                    'additionalCost *= 2;', 'this.wants.toItemCost(lootContext, additionalCost)'],
            'VillagerTrades.java': ['includeAdditionalCostComponent()', 'MinMaxBounds.Ints.exactly(level)'],
            'TradeRebalanceVillagerTrades.java': ['ContextIntProviders.between(0, 5 + level * 10)',
                    'enchantments.getOrThrow(Enchantments.MENDING)'],
        }
        for filename, tokens in contracts.items():
            text = source(filename)
            for token in tokens:
                assert token in text, (filename, token)
        counts['exact_source_contracts'] = len(contracts)

    if args.classpath:
        bytecode = {
            'net.minecraft.world.level.storage.loot.functions.FilteredFunction':
                'ItemPredicate.test:(Lnet/minecraft/world/item/ItemInstance;)Z',
            'net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction':
                'Util.getRandomSafe:(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;',
            'net.minecraft.world.item.enchantment.EnchantmentHelper':
                'ItemStack."<init>":(Lnet/minecraft/world/level/ItemLike;)V',
            'net.minecraft.world.item.trading.VillagerTrade':
                'TradeCost.toItemCost:(Lnet/minecraft/world/level/storage/loot/LootContext;I)Lnet/minecraft/world/item/trading/ItemCost;',
        }
        for clazz, descriptor in bytecode.items():
            result = subprocess.run([args.javap, '-p', '-c', '-s', '-classpath', args.classpath, clazz],
                                    check=True, capture_output=True, text=True)
            assert descriptor in result.stdout, (clazz, descriptor)
        counts['exact_bytecode_targets'] = len(bytecode)

    root = Path(__file__).resolve().parents[1]
    mixins = sorted((root / 'src/main/java/dev/enchantingoverhaul/mixin').glob('*AcquisitionMixin.java'))
    assert len(mixins) == 6, [p.name for p in mixins]
    combined = '\n'.join(p.read_text() for p in mixins)
    assert 'removeIf' not in combined and 'removeAll' not in combined
    assert '@Mixin(ItemStack.class)' not in combined and 'setEnchantments(' not in combined
    # No loot table overrides: candidate/assignment hooks preserve entry weights, pools and rolls.
    assert not (root / 'src/main/resources/data/minecraft/loot_table/gameplay/fishing.json').exists()
    print(json.dumps({'counts': dict(sorted(counts.items())),
                      'explicit_mending_sources': sorted(explicit),
                      'random_mending_sources': sorted(random_mending_sources),
                      'mixins_to_register': [p.stem for p in mixins]}, indent=2))
    print('PASS: exact 26.3 acquisition source/resource contracts (not live mixin/gameplay verification)')


if __name__ == '__main__':
    main()
