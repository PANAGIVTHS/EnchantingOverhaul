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
    parser.add_argument('--mod', type=Path, default=Path('build/libs/enchantingoverhaul-0.1.0.jar'))
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
        assert metadata['version'] == '0.1.0'
        assert metadata['depends']['minecraft'] == '26.3'
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
                assert potion.encode() in common.read('net/minecraft/world/item/alchemy/Potions.class'), catalyst
                assert f'item.minecraft.{item}.effect.{potion}' in lang, catalyst
        blocks = ('enchantment_replicator', 'enchantment_extractor', 'enchanted_book_combiner')
        translations = load(mod, 'assets/enchantingoverhaul/lang/en_us.json')
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
        assert not any('/recipe/' in n for n in mod_names), 'Unapproved crafting recipes in checkpoint'
        print(f'PASS: {json_count} packaged JSON resources parsed; entrypoints and Java 25 class versions checked.')
        print('PASS: exact 41 non-curse catalysts/maxima cover the actual 43-enchantment Minecraft 26.3 data set.')
        print('PASS: all catalyst item assets and exact potion translation identities exist in vanilla.')
        print('PASS: all 3 workstation blockstates, model/texture references, item definitions, translations and self-drop loot tables checked.')
        print(f'PASS: {main_classes} common mod classes have no direct client-class linkage; no test classes/unapproved recipes packaged.')
        print('NOT TESTED: game bootstrap, runtime registries/rendering, inventory persistence, interaction, networking or multiplayer.')


if __name__ == '__main__':
    main()
