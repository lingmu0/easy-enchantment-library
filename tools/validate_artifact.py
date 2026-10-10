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

loot = parsed[f'data/{modid}/loot_table/blocks/{modid}.json']
entry = loot['pools'][0]['entries'][0]
assert entry['name'] == f'{modid}:{modid}'
assert set(entry['functions'][0]['include']) == {f'{modid}:stored_enchantments', 'minecraft:container'}
recipe = parsed[f'data/{modid}/recipe/{modid}.json']
assert recipe['result'] == {'id': f'{modid}:{modid}', 'count': 1}
assert all(c == ' ' or c in recipe['key'] for row in recipe['pattern'] for c in row)
assert not any(key.startswith('data/') and key.split('/')[2] in ('recipes', 'loot_tables') for key in parsed), 'Wrong datapack folder for 1.21.1'

vanilla = project.parent / 'demo1.21.1/build/moddev/artifacts/neoforge-21.1.233-client-extra-aka-minecraft-resources.jar'
if vanilla.exists():
    with zipfile.ZipFile(vanilla) as z:
        for resource in re.findall(r'ResourceLocation\.withDefaultNamespace\("([^\"]+)"\)', all_source):
            asset = f'assets/minecraft/{resource}' if resource.startswith('textures/') else f'assets/minecraft/textures/gui/sprites/{resource}.png'
            assert asset in z.namelist(), f'Missing vanilla GUI resource: {asset}'
        for model_path, model in parsed.items():
            if '/models/' not in model_path: continue
            for texture in model.get('textures', {}).values():
                if texture.startswith('minecraft:'):
                    assert f'assets/minecraft/textures/{texture.split(":",1)[1]}.png' in z.namelist(), texture

with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    assert 'META-INF/neoforge.mods.toml' in names
    assert 'LICENSE' in names
    assert 'logo.png' in names and 'pack.mcmeta' in names
    metadata = z.read('META-INF/neoforge.mods.toml').decode()
    assert f'version="{properties["mod_version"]}"' in metadata
    assert 'displayName="Easy Enchantment Library"' in metadata
    assert not any('demo1_21_1' in name for name in names), 'Template code leaked into artifact'
    for path in parsed: assert path in names, f'Resource missing from jar: {path}'
    classes = [name for name in names if name.endswith('.class')]
    assert len(classes) >= 12
    for name in classes:
        assert struct.unpack('>H', z.read(name)[6:8])[0] == 65, f'Not Java 21: {name}'
    assert '${' not in z.read('META-INF/neoforge.mods.toml').decode(), 'Unexpanded metadata'

baseline = project / '.template-baselines.json'
if baseline.exists():
    for directory, files in json.loads(baseline.read_text(encoding='utf-8')).items():
        for path, digest in files.items():
            assert hashlib.sha256((project.parent / directory / path).read_bytes()).hexdigest() == digest, f'Template changed: {directory}/{path}'
    print('Original Forge and NeoForge templates verified unchanged.')

print(f'Validated {len(parsed)} JSON resources, {len(languages[0])} bilingual keys, {len(classes)} Java 21 classes, and packaged metadata.')
print('JAR SHA-256:', hashlib.sha256(jar.read_bytes()).hexdigest())
