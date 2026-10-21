#!/usr/bin/env python3
"""Non-game checkpoint audit against downloaded vanilla artifacts. Never loads a Minecraft class."""
import argparse
import json
from pathlib import Path
import zipfile


def load(archive, path):
    return json.loads(archive.read(path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mod', type=Path, default=Path('build/libs/enchantingoverhaul-0.3.4.jar'))
    parser.add_argument('--minecraft-common', type=Path, required=True)
    parser.add_argument('--minecraft-client', type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.mod) as mod, zipfile.ZipFile(args.minecraft_common) as common, zipfile.ZipFile(args.minecraft_client) as client:
        mod_names = set(mod.namelist())
        common_names = set(common.namelist())
        client_names = set(client.namelist())
        json_count = 0
        for path in sorted(mod_names):
            if path.endswith('.json'):
                load(mod, path)
                json_count += 1
        metadata = load(mod, 'fabric.mod.json')
        assert metadata['version'] == '0.3.4'
        assert metadata['depends']['minecraft'] == '26.3'
        assert metadata['mixins'] == ['enchantingoverhaul.mixins.json']
        mixin_config = load(mod, metadata['mixins'][0])
        assert mixin_config['required'] is True
        assert mixin_config['compatibilityLevel'] == 'JAVA_25'
        assert mixin_config['injectors']['defaultRequire'] == 1
        expected_mixins = {'EnchantmentMenuMixin', 'AnvilMenuMixin', 'GrindstoneExperienceMixin',
            'EnchantRandomlyAcquisitionMixin', 'EnchantmentHelperAcquisitionMixin',
            'FilteredBookTradeAcquisitionMixin', 'SetEnchantmentsAcquisitionMixin',
            'SingleEnchantmentAcquisitionMixin', 'VillagerTradeAcquisitionMixin',
            'MerchantMenuOffersMixin', 'MerchantContainerOffersMixin',
            'MerchantResultSlotOffersMixin', 'ServerPlayerMerchantOffersMixin'}
        assert len(mixin_config['mixins']) == len(expected_mixins)
        assert set(mixin_config['mixins']) == expected_mixins
        for name in mixin_config['mixins']:
            assert mixin_config['package'].replace('.', '/') + '/' + name + '.class' in mod_names
        assert 'mixins' not in metadata['entrypoints']
        assert '${version}' not in mod.read('fabric.mod.json').decode()
        for kind, entries in metadata['entrypoints'].items():
            for entry in entries:
                assert entry.replace('.', '/') + '.class' in mod_names, (kind, entry)
        vanilla = {'minecraft:' + Path(n).stem: load(common, n) for n in common_names
                   if n.startswith('data/minecraft/enchantment/') and n.endswith('.json')}
        curses = set(load(common, 'data/minecraft/tags/enchantment/curse.json')['values'])
        definitions = load(mod, 'data/enchantingoverhaul/replication.json')['enchantments']
        ids = {entry['enchantmentId'] for entry in definitions}
        assert len(definitions) == len(ids) == 41
        assert len(vanilla) == 43 and len(curses) == 2
        assert ids == vanilla.keys() - curses, (ids - vanilla.keys(), vanilla.keys() - curses - ids)
        lang = load(client, 'assets/minecraft/lang/en_us.json')
        for definition in definitions:
            identity = definition['enchantmentId']
            assert definition['maxLevel'] == vanilla[identity]['max_level'], identity
            catalyst = definition['catalyst']
            namespace, item = catalyst['itemId'].split(':')
            assert f'assets/{namespace}/items/{item}.json' in client_names, catalyst
            if 'potionId' in catalyst:
                potion = catalyst['potionId'].split(':')[1]
                # Vanilla potion registry IDs and translated effect names need not match.
                assert potion.encode() in common.read('net/minecraft/world/item/alchemy/PotionIds.class'), catalyst
                assert f'item.minecraft.{item}.effect.{potion}' in lang, catalyst
        synthesis = load(mod, 'data/enchantingoverhaul/synthesis.json')['recipes']
        expected_upgrades = {(entry['enchantmentId'], level) for entry in definitions
                             for level in range(1, entry['maxLevel'])}
        actual_upgrades = {(recipe['enchantmentId'], recipe['inputLevel']) for recipe in synthesis}
        assert len(synthesis) == len(actual_upgrades) == len(expected_upgrades) == 85
        assert actual_upgrades == expected_upgrades
        potion_class = common.read('net/minecraft/world/item/alchemy/PotionIds.class')
        for recipe in synthesis:
            assert recipe['enchantmentId'] not in curses
            assert recipe['inputLevel'] + 1 <= vanilla[recipe['enchantmentId']]['max_level']
            for ingredient in recipe['ingredients']:
                assert ingredient['count'] > 0
                for identity in ingredient['itemIds']:
                    namespace, item = identity.split(':')
                    assert f'assets/{namespace}/items/{item}.json' in client_names, identity
                    if ingredient.get('potionId'):
                        potion = ingredient['potionId'].split(':')[1]
                        assert potion.encode() in potion_class, ingredient
                        effect = potion.removeprefix('strong_').removeprefix('long_')
                        assert f'item.minecraft.{item}.effect.{effect}' in lang, ingredient
        costs = load(mod, 'data/enchantingoverhaul/extraction.json')
        assert costs['extractionXp'] == [55, 160, 280, 441, 679]
        assert costs['purificationXp'] == 315
        assert costs['mendingExtractionXp'] == 910
        assert costs['mendingExtractionXp'] > max(costs['extractionXp'] + [costs['purificationXp']])
        blocks = ('enchantment_replicator', 'enchantment_extractor', 'enchanted_book_combiner')
        translations = load(mod, 'assets/enchantingoverhaul/lang/en_us.json')
        boosted = {ingredient['potionId'] for recipe in synthesis for ingredient in recipe['ingredients']
                   if ingredient.get('potionId', '').startswith('minecraft:strong_')}
        assert boosted == {'minecraft:strong_strength', 'minecraft:strong_swiftness', 'minecraft:strong_healing'}
        assert lang['potion.potency.1'] == 'II'
        assert 'enchantingoverhaul.potion.strong' in translations
        assert 'enchantingoverhaul.potion.extended' in translations
        # Non-bootstrapping linkage audit: both previews use the variant-aware bridge.
        for screen in ('CombinerScreen', 'ReplicatorScreen'):
            assert b'catalystName' in mod.read(f'dev/enchantingoverhaul/client/{screen}.class'), screen
        assert b'getAmplifier' in mod.read('dev/enchantingoverhaul/game/Enchantments.class')
        assert 'dev/enchantingoverhaul/core/PotionRequirementLabel.class' in mod_names
        recovery = mod.read('dev/enchantingoverhaul/block/WorkstationBlockEntity.class')
        assert b'net/minecraft/world/entity/item/ItemEntity' not in recovery, 'Recovery must retain overflow, not spawn pickup entities'
        assert b'escrowCount' in recovery
        for screen in ('ReferenceWorkstationScreen', 'CombinerScreen'):
            bytecode = mod.read(f'dev/enchantingoverhaul/client/{screen}.class')
            assert b'WorkstationTooltip' in bytecode
            assert b'net/minecraft/client/gui/components/Tooltip' not in bytecode, 'Long previews must not use above-widget positioning'
        assert 'dev/enchantingoverhaul/core/TooltipBounds.class' in mod_names
        replica_screen = mod.read('dev/enchantingoverhaul/client/ReplicatorScreen.class')
        replica_button = mod.read('dev/enchantingoverhaul/client/ReplicatorScreen$ReplicaButton.class')
        assert b'WorkstationTooltip' in replica_screen and b'openSearch' in replica_screen
        assert b'hasControlDownWithQuirk' in replica_screen and b'isHoveredOrFocused' not in replica_screen
        assert b'WidgetPreviewPolicy' in replica_button and b'isKeyboard' in replica_button
        assert b'fillGradient' in replica_screen and b'fillGradient' in replica_button
        assert 'dev/enchantingoverhaul/core/ReplicatorLayout.class' in mod_names
        extraction_screen = mod.read('dev/enchantingoverhaul/client/ExtractorScreen.class')
        extraction_button = mod.read('dev/enchantingoverhaul/client/ExtractorScreen$ExtractionButton.class')
        assert b'WorkstationTooltip' in extraction_screen and b'openSearch' in extraction_screen
        assert b'hasControlDownWithQuirk' in extraction_screen and b'isHoveredOrFocused' not in extraction_screen
        assert b'WidgetPreviewPolicy' in extraction_button and b'isKeyboard' in extraction_button
        assert b'fillGradient' in extraction_screen and b'fillGradient' in extraction_button
        assert 'dev/enchantingoverhaul/core/ExtractorLayout.class' in mod_names
        for screen in ('ReplicatorScreen', 'ExtractorScreen', 'CombinerScreen'):
            bytecode = mod.read(f'dev/enchantingoverhaul/client/{screen}.class')
            assert b'extractTooltip' not in bytecode and b'slotHint' not in bytecode, 'Keep vanilla item hovers'
            for tutorial in (b'Hover Cost', b'Click the title', b'Select to preview', b'Your XP:',
                             b'One enchantment per operation', b'Hover button:', b'materials still required'):
                assert tutorial not in bytecode, (screen, tutorial)
        assert b'isBlank' in mod.read('dev/enchantingoverhaul/client/WorkstationTooltip.class')
        for screen in ('ReferenceWorkstationScreen', 'ReferenceButton', 'CombinerScreen'):
            bytecode = mod.read(f'dev/enchantingoverhaul/client/{screen}.class')
            assert b'isHoveredOrFocused' not in bytecode, 'Mouse focus must not pin previews'
        for screen in ('ReferenceButton', 'CombinerScreen'):
            bytecode = mod.read(f'dev/enchantingoverhaul/client/{screen}.class')
            assert b'WidgetPreviewPolicy' in bytecode and b'isKeyboard' in bytecode
        assert b'TickSnapshot' in recovery and b'freshLibrary' in recovery and b'libraryForPreview' in recovery
        assert b'getTickCount' in recovery and b'getGameTime' not in recovery, 'Preview expiry must work during tick freeze'
        for name in ('MerchantMenuOffersMixin', 'MerchantContainerOffersMixin', 'MerchantResultSlotOffersMixin'):
            assert b'MerchantOfferRestrictions' in mod.read('dev/enchantingoverhaul/mixin/' + name + '.class')
        assert b'MerchantOfferPresentation' in mod.read('dev/enchantingoverhaul/mixin/ServerPlayerMerchantOffersMixin.class')
        replicator = mod.read('dev/enchantingoverhaul/menu/ReplicatorMenu.class')
        assert b'freshLibrary' in replicator and b'libraryForPreview' in replicator
        for block in blocks:
            prefix = 'assets/enchantingoverhaul/'
            assert 'block.enchantingoverhaul.' + block in translations
            state = load(mod, prefix + 'blockstates/' + block + '.json')
            assert state['variants']['']['model'] == 'enchantingoverhaul:block/' + block
            model = load(mod, prefix + 'models/block/' + block + '.json')
            ns, parent = model['parent'].split(':')
            assert f'assets/{ns}/models/{parent}.json' in client_names
            for texture in model.get('textures', {}).values():
                if texture.startswith('#'):
                    continue
                ns, path = texture.split(':')
                assert f'assets/{ns}/textures/{path}.png' in client_names, texture
            item = load(mod, prefix + 'items/' + block + '.json')
            ns, path = item['model']['model'].split(':')
            assert f'assets/{ns}/models/{path}.json' in mod_names
            loot = load(mod, 'data/enchantingoverhaul/loot_table/blocks/' + block + '.json')
            assert loot['type'] == 'minecraft:block'
            assert loot['pools'][0]['entries'][0]['name'] == 'enchantingoverhaul:' + block
        main_classes = 0
        for path in mod_names:
            if not path.endswith('.class'):
                continue
            assert not path.endswith('Test.class'), path
            data = mod.read(path)
            assert data[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8], 'big') == 69, path
            if path.startswith('dev/enchantingoverhaul/') and '/client/' not in path:
                main_classes += 1
                assert b'net/minecraft/client/' not in data, ('client linkage in common class', path)
        from collections import Counter
        approved = {
            'enchantment_extractor': {'minecraft:grindstone': 1, 'minecraft:copper_ingot': 4, 'minecraft:amethyst_shard': 2},
            'enchantment_replicator': {'minecraft:enchanting_table': 1, 'minecraft:amethyst_shard': 4},
            'enchanted_book_combiner': {'minecraft:anvil': 1, 'minecraft:bookshelf': 1, 'minecraft:amethyst_shard': 4},
            'mending': {'minecraft:nether_star': 1, 'minecraft:ghast_tear': 1, 'minecraft:golden_apple': 1, 'minecraft:book': 1},
        }
        recipe_paths = {n for n in mod_names if '/recipe/' in n and n.endswith('.json')}
        assert recipe_paths == {f'data/enchantingoverhaul/recipe/{name}.json' for name in approved}
        for name, expected in approved.items():
            recipe = load(mod, f'data/enchantingoverhaul/recipe/{name}.json')
            if name == 'mending':
                assert recipe['type'] == 'minecraft:crafting_shapeless'
                actual = Counter(recipe['ingredients'])
                assert recipe['result'] == {'id': 'minecraft:enchanted_book', 'count': 1,
                    'components': {'minecraft:stored_enchantments': {'minecraft:mending': 1}}}
            else:
                assert recipe['type'] == 'minecraft:crafting_shaped'
                assert len(recipe['pattern']) == 3 and all(len(row) == 3 for row in recipe['pattern'])
                actual = Counter(recipe['key'][symbol] for row in recipe['pattern'] for symbol in row if symbol != ' ')
                assert recipe['result'] == {'id': 'enchantingoverhaul:' + name, 'count': 1}
            assert actual == expected, (name, actual)
            for item in actual:
                namespace, path = item.split(':')
                assert f'assets/{namespace}/items/{path}.json' in client_names, item
            unlock = load(mod, f'data/enchantingoverhaul/advancement/recipes/{name}.json')
            assert unlock['rewards'] == {'recipes': ['enchantingoverhaul:' + name]}
        print('PASS: all 4 approved survival recipes, exact quantities, stored Mending result and recipe-book unlocks checked.')
        print('PASS: all 13 required common mixins registered and packaged; default required injection count 1.')
        print(f'PASS: {json_count} packaged JSON resources parsed; entrypoints and Java 25 class versions checked.')
        print('PASS: exact 41 non-curse catalysts/maxima cover the actual 43-enchantment Minecraft 26.3 data set.')
        print('PASS: all catalyst assets/potion identities exist; both previews link variant-aware potency labels with vanilla II translation.')
        print('PASS: all 85 synthesis upgrades cover exactly the vanilla maxima, with real vanilla ingredients/potion variants; extraction/purification fees match.')
        print('PASS: vanilla item hovers and concise requirements; no tutorial popups in active screens; previews bounded and non-sticky; recovery retains overflow and action scans stay fresh.')
        print('PASS: all 3 workstation blockstates, model/texture references, item definitions, translations and self-drop loot tables checked.')
        print(f'PASS: {main_classes} common mod classes have no direct client-class linkage; no test classes packaged.')
        print('NOT TESTED: game bootstrap, runtime registries/rendering, inventory persistence, interaction, networking or multiplayer.')


if __name__ == '__main__':
    main()
