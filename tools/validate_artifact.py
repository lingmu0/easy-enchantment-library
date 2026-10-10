from pathlib import Path
import hashlib
import json
import re
import struct
import zipfile

project = Path(__file__).resolve().parents[1]
resources = project / 'src/main/resources'
modid = 'enchantment_library'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text(encoding='utf-8').splitlines()
    if '=' in line and not line.startswith('#'))
jar = project / f"build/libs/{modid}-{properties['mod_version']}.jar"
parsed = {p.relative_to(resources).as_posix(): json.loads(p.read_text(encoding='utf-8')) for p in resources.rglob('*.json')}
languages = [parsed[f'assets/{modid}/lang/{locale}.json'] for locale in ('en_us', 'zh_cn')]
assert languages[0].keys() == languages[1].keys(), 'Missing translations'
for key in languages[0]:
    assert languages[0][key].count('%s') == languages[1][key].count('%s'), key

sources = list((project / 'src/main/java').rglob('*.java'))
all_source = '\n'.join(p.read_text(encoding='utf-8') for p in sources)
for key in re.findall(r'Component\.translatable\("([a-z._]+)"', all_source):
    if not key.endswith('.'):
        assert key in languages[0], key
for p in sources:
    if 'client' not in p.relative_to(project / 'src/main/java').parts:
        assert 'net.minecraft.client' not in p.read_text(encoding='utf-8'), f'Client dependency on server: {p}'

loot = parsed[f'data/{modid}/loot_tables/blocks/{modid}.json']
entry = loot['pools'][0]['entries'][0]
assert entry['name'] == f'{modid}:{modid}'
assert entry['functions'][0]['function'] == 'minecraft:copy_nbt'
assert {(op['source'], op['target'], op['op']) for op in entry['functions'][0]['ops']} == {('StoredEnchantments', 'BlockEntityTag.StoredEnchantments', 'replace'), ('Items', 'BlockEntityTag.Items', 'replace')}
recipe = parsed[f'data/{modid}/recipes/{modid}.json']
assert recipe['result'] == {'item': f'{modid}:{modid}', 'count': 1}
assert all(c == ' ' or c in recipe['key'] for row in recipe['pattern'] for c in row)
assert not any(key.startswith('data/') and key.split('/')[2] in ('recipe', 'loot_table', 'advancement') for key in parsed), 'Wrong datapack folder for 1.20.1'

vanilla = Path.home() / '.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar'
if vanilla.exists():
    with zipfile.ZipFile(vanilla) as z:
        for resource in re.findall(r'new ResourceLocation\("(textures/[^"]+)"\)', all_source):
            assert f'assets/minecraft/{resource}' in z.namelist(), f'Missing vanilla GUI resource: {resource}'
        for model_path, model in parsed.items():
            if '/models/' not in model_path: continue
            for texture in model.get('textures', {}).values():
                if texture.startswith('minecraft:'):
                    assert f'assets/minecraft/textures/{texture.split(":",1)[1]}.png' in z.namelist(), texture

with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    assert 'META-INF/mods.toml' in names
    assert 'LICENSE' in names
    assert 'logo.png' in names and 'pack.mcmeta' in names
    metadata = z.read('META-INF/mods.toml').decode()
    assert f'version="{properties["mod_version"]}"' in metadata
    assert 'displayName="Easy Enchantment Library"' in metadata
    assert b'm_7648_' in z.read('net/xuwu/enchantmentlibrary/menu/LibraryMenu.class'), 'Forge artifact has not been reobfuscated'
    assert not any('demo1_21_1' in name for name in names), 'Template code leaked into artifact'
    for path in parsed: assert path in names, f'Resource missing from jar: {path}'
    classes = [name for name in names if name.endswith('.class')]
    assert len(classes) >= 12
    for name in classes:
        assert struct.unpack('>H', z.read(name)[6:8])[0] == 61, f'Not Java 17: {name}'
    assert '${' not in z.read('META-INF/mods.toml').decode(), 'Unexpanded metadata'

baseline = project / '.template-baselines.json'
if baseline.exists():
    for directory, files in json.loads(baseline.read_text(encoding='utf-8')).items():
        for path, digest in files.items():
            assert hashlib.sha256((project.parent / directory / path).read_bytes()).hexdigest() == digest, f'Template changed: {directory}/{path}'
    print('Original Forge and NeoForge templates verified unchanged.')

print(f'Validated {len(parsed)} JSON resources, {len(languages[0])} bilingual keys, {len(classes)} Java 17 classes, and packaged metadata.')
print('JAR SHA-256:', hashlib.sha256(jar.read_bytes()).hexdigest())
