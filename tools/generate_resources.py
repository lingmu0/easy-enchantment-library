from pathlib import Path
import json

root = Path(__file__).resolve().parents[1] / 'src/main/resources'

def write(path, value):
    dest = root / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# 26.1.2 resource packs use 84.0; data packs use 101.1.
write('pack.mcmeta', {'pack': {'min_format': [84, 0], 'max_format': [101, 1],
    'description': 'Enchantment Library resources'}})
write('assets/enchantment_library/blockstates/enchantment_library.json', {'variants': {
    f'facing={facing}': {'model': 'enchantment_library:block/enchantment_library', 'y': rotation}
    for facing, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
}})
# A bookshelf cabinet with an inset enchanting-table panel uses only vanilla textures.
write('assets/enchantment_library/models/block/enchantment_library.json', {
    'parent': 'minecraft:block/block',
    'textures': {'particle': 'minecraft:block/bookshelf', 'side': 'minecraft:block/bookshelf',
        'wood': 'minecraft:block/dark_oak_planks', 'top': 'minecraft:block/enchanting_table_top'},
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            direction: {'texture': '#wood' if direction in ('up', 'down') else '#side', 'cullface': direction}
            for direction in ('north', 'south', 'east', 'west', 'up', 'down')}},
        {'from': [2, 16, 2], 'to': [14, 16.05, 14], 'faces': {'up': {'texture': '#top', 'uv': [0, 0, 16, 16]}}},
    ]
})
write('assets/enchantment_library/items/enchantment_library.json', {
    'model': {'type': 'minecraft:model', 'model': 'enchantment_library:block/enchantment_library'}})
write('data/enchantment_library/recipe/enchantment_library.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['BGB', 'OEO', 'OOO'],
    'key': {'B': 'minecraft:bookshelf', 'G': 'minecraft:gold_ingot',
        'O': 'minecraft:obsidian', 'E': 'minecraft:enchanting_table'},
    'result': {'id': 'enchantment_library:enchantment_library', 'count': 1}
})
write('data/enchantment_library/loot_table/blocks/enchantment_library.json', {
    'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
        'name': 'enchantment_library:enchantment_library', 'functions': [{'function': 'minecraft:copy_components',
            'source': 'block_entity', 'include': ['enchantment_library:stored_enchantments', 'minecraft:container']}]}]}]
})
write('data/minecraft/tags/block/mineable/axe.json', {'replace': False, 'values': ['enchantment_library:enchantment_library']})
write('data/enchantment_library/advancement/recipes/enchantment_library.json', {
    'parent': 'minecraft:recipes/root',
    'criteria': {'has_enchanting_table': {'trigger': 'minecraft:inventory_changed',
        'conditions': {'items': [{'items': 'minecraft:enchanting_table'}]}},
        'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'enchantment_library:enchantment_library'}}},
    'requirements': [['has_enchanting_table', 'has_the_recipe']],
    'rewards': {'recipes': ['enchantment_library:enchantment_library']}
})

translations = {
    'block.enchantment_library.enchantment_library': ('Easy Enchantment Library', '附魔图书馆'),
    'tooltip.enchantment_library.library': ('Stores enchantments; keeps its contents when broken.', '存储附魔；拆除后保留内部库存。'),
    'tooltip.enchantment_library.stored': ('Stored enchantment types: %s', '已存储 %s 种附魔'),
    'message.enchantment_library.failed': ('Check the available points, level, item, enchantment conflicts and output space.', '请检查储量、等级、物品适用性、附魔冲突及输出槽空间。'),
    'gui.enchantment_library.search': ('Search enchantments...', '搜索附魔……'),
    'gui.enchantment_library.pinyin_enabled': ('JECh pinyin search enabled. Search by name or ID. Right-click to clear.', '已启用 JECh 拼音搜索。支持名称和 ID，右键清空。'),
    'gui.enchantment_library.pinyin_optional': ('Search by name or ID. Install Just Enough Characters for pinyin search. Right-click to clear.', '支持名称和 ID 搜索；安装通用拼音搜索（JECh）可启用拼音。右键清空。'),
    'gui.enchantment_library.store': ('Store', '存入附魔'),
    'gui.enchantment_library.enchant': ('Apply all', '批量附魔'),
    'gui.enchantment_library.withdraw': ('Take book', '取出附魔书'),
    'gui.enchantment_library.deposit': ('Book / equipment', '附魔书／装备'),
    'gui.enchantment_library.target': ('Equipment / book', '装备／空白书'),
    'gui.enchantment_library.output': ('Output', '输出'),
    'gui.enchantment_library.level': ('Level', '等级'),
    'gui.enchantment_library.points': ('Stored points: %s', '已存点数：%s'),
    'gui.enchantment_library.maximum': ('Current maximum level: %s', '当前最高等级：%s'),
    'gui.enchantment_library.conversion': ('I = 1 point, II = 2, III = 4; each level doubles.', 'I 级 1 点，II 级 2 点，III 级 4 点；每级翻倍。'),
    'gui.enchantment_library.cost': ('Point cost: %s', '消耗点数：%s'),
    'gui.enchantment_library.empty': ('No stored enchantments', '尚未存储附魔'),
    'gui.enchantment_library.no_results': ('No matching enchantments', '没有匹配的附魔'),
    'gui.enchantment_library.store_help': ('Extract all enchantments, including curses. The clean item is returned in the output slot.', '提取全部附魔（包括诅咒），无附魔的物品返还至输出槽。'),
    'gui.enchantment_library.enchant_help': ('Apply all checked enchantments at their individual slider levels. Conflicts or insufficient points cancel the entire operation; upgrades charge only the difference.', '将全部勾选附魔按各自滑条等级一次附到装备上；冲突或任一储量不足时整次取消，升级只扣差额。'),
    'gui.enchantment_library.withdraw_help': ('Put ordinary books in the middle slot. All checked enchantments are written to one book, consuming one ordinary book and their point costs.', '中间槽放空白书，全部勾选的附魔按各自等级写入同一本附魔书；消耗一本空白书和对应点数。'),
    'gui.enchantment_library.selected_count': ('Selected: %s', '已选 %s 项'),
    'gui.enchantment_library.clear_selection': ('Clear', '清空'),
    'gui.enchantment_library.select_name': ('Select %s', '选择 %s'),
    'gui.enchantment_library.select_help': ('Check multiple enchantments, then apply them together. Changing a slider also checks its enchantment.', '可勾选多个附魔后一次应用；调整滑条也会自动勾选对应附魔。'),
    'gui.enchantment_library.slider_help': ('Level %s / %s. Drag to choose a level; arrow keys adjust by one, or five with Shift.', '等级 %s／%s。左右拖动选择；方向键逐级调整，按住 Shift 每次调整 5 级。'),
    'gui.enchantment_library.batch_help': ('Selections are kept while searching and scrolling. The entire selection is applied in one operation.', '搜索与滚动时保留全部勾选（包括隐藏条目），一次操作应用全部选项。'),
    'gui.enchantment_library.deposit_slot': ('Insert an enchanted book or equipment to extract its enchantments.', '放入附魔书或装备，点击存入以提取其附魔。'),
    'gui.enchantment_library.target_slot': ('Insert equipment to enchant, or ordinary books to withdraw enchanted books.', '放入要附魔的装备，或放入空白书以取出附魔书。'),
    'gui.enchantment_library.output_slot': ('Clean equipment, returned books and withdrawn enchanted books appear here.', '取回无附魔装备、返还的空白书及取出的附魔书。'),
}
for index, locale in enumerate(('en_us', 'zh_cn')):
    write(f'assets/enchantment_library/lang/{locale}.json', {key: values[index] for key, values in translations.items()})
print('Generated block assets, recipe, loot table, tags, advancement, and English/Chinese translations.')
