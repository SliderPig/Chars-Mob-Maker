# Pixel art and the plain data files of the mod: bench textures and model, the black cat icon, the white sprite, lang, recipe, tags.
#   python tools/make_art.py
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'charmobmaker')
DATA = os.path.join(ROOT, 'data')

BLACK = (22, 16, 24, 255)
BLACK2 = (42, 30, 46, 255)
PINK = (255, 143, 199, 255)
PINK_SOFT = (255, 182, 220, 255)
PINK_DEEP = (214, 85, 156, 255)
PLUM = (86, 54, 100, 255)
PLUM_DARK = (62, 38, 74, 255)
PLUM_LIGHT = (112, 74, 128, 255)
CLEAR = (0, 0, 0, 0)
YELLOW = (255, 224, 102, 255)


def save(img, path):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def write_json(root, path, data):
    full = os.path.join(root, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def planks(base, dark, light):
    img = Image.new('RGBA', (16, 16), base)
    px = img.load()
    for y in (3, 7, 11, 15):
        for x in range(16):
            px[x, y] = dark
    for y, x in [(1, 4), (5, 11), (9, 2), (13, 8)]:
        px[x, y] = light
        px[(x + 1) % 16, y] = light
    for y, x in [(0, 12), (4, 1), (8, 7), (12, 13)]:
        px[x, y] = dark
    return img


def paw(img, cx, cy, colour):
    px = img.load()
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (1, 1), (-1, 1), (0, -1), (1, -1), (-1, -1), (2, 0), (-2, 0)]:
        px[cx + dx, cy + dy + 2] = colour
    for dx in (-3, -1, 1, 3):
        px[cx + dx, cy - 1] = colour
        if dx in (-1, 1):
            px[cx + dx, cy - 2] = colour


def cat_icon():
    rows = [
        '................',
        '..K.........K...',
        '..KK.......KK...',
        '..KPK.....KPK...',
        '..KPKKKKKKKPK...',
        '..KKKKKKKKKKK...',
        '.KKKKKKKKKKKKK..',
        '.KKYYKKKKKYYKK..',
        '.KKYKKKKKKKYKK..',
        '.KKKKKKPKKKKKK..',
        '.KKKKKPKPKKKKK..',
        '..KKKKKKKKKKK...',
        '...KKKKKKKKK....',
        '....KKKKKKK.....',
        '................',
        '................',
    ]
    colours = {'.': CLEAR, 'K': BLACK, 'P': PINK, 'Y': YELLOW}
    img = Image.new('RGBA', (16, 16), CLEAR)
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            px[x, y] = colours[c]
    # a soft pink outline so it shows on the dark bar
    out = img.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 0:
                        opx[x, y] = (255, 143, 199, 110)
    return out


def main():
    save(Image.new('RGBA', (16, 16), (255, 255, 255, 255)), 'textures/block/white.png')
    save(cat_icon(), 'textures/gui/cat.png')

    top = planks(PLUM, PLUM_DARK, PLUM_LIGHT)
    paw(top, 5, 6, PINK)
    px = top.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = PINK_DEEP
    save(top, 'textures/block/bench_top.png')
    side = planks(PLUM_DARK, (46, 28, 56, 255), PLUM)
    px = side.load()
    for x in range(16):
        px[x, 0] = PINK_DEEP
        px[x, 15] = PINK_DEEP
    save(side, 'textures/block/bench_side.png')
    leg = Image.new('RGBA', (16, 16), PLUM_DARK)
    px = leg.load()
    for y in range(16):
        px[0, y] = (46, 28, 56, 255)
        px[15, y] = PLUM
        if y % 5 == 2:
            px[7, y] = PLUM
    save(leg, 'textures/block/bench_leg.png')
    fur = Image.new('RGBA', (16, 16), BLACK)
    px = fur.load()
    for x, y in [(2, 3), (9, 1), (13, 6), (5, 9), (11, 12), (3, 14)]:
        px[x, y] = BLACK2
    save(fur, 'textures/block/bench_cat.png')
    ear = Image.new('RGBA', (16, 16), BLACK)
    px = ear.load()
    for y in range(4, 12):
        for x in range(5, 11):
            px[x, y] = PINK
    save(ear, 'textures/block/bench_cat_ear.png')
    face = Image.new('RGBA', (16, 16), BLACK)
    px = face.load()
    for x, y in [(2, 5), (3, 5), (2, 6), (3, 6), (12, 5), (13, 5), (12, 6), (13, 6)]:
        px[x, y] = YELLOW
    for x, y in [(7, 9), (8, 9)]:
        px[x, y] = PINK
    save(face, 'textures/block/bench_cat_face.png')

    def cube(a, b, side_tex, top_tex=None, bottom_tex=None, north=None):
        faces = {}
        for name in ('north', 'south', 'east', 'west', 'up', 'down'):
            tex = side_tex
            if name == 'up' and top_tex:
                tex = top_tex
            if name == 'down' and bottom_tex:
                tex = bottom_tex
            if name == 'north' and north:
                tex = north
            faces[name] = {'texture': '#' + tex}
        return {'from': a, 'to': b, 'faces': faces}

    model = {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': 'charmobmaker:block/bench_top',
            'top': 'charmobmaker:block/bench_top',
            'side': 'charmobmaker:block/bench_side',
            'leg': 'charmobmaker:block/bench_leg',
            'cat': 'charmobmaker:block/bench_cat',
            'ear': 'charmobmaker:block/bench_cat_ear',
            'face': 'charmobmaker:block/bench_cat_face',
        },
        'elements': [
            cube([0, 11, 0], [16, 14, 16], 'side', 'top', 'side'),
            cube([1, 0, 1], [4, 11, 4], 'leg'),
            cube([12, 0, 1], [15, 11, 4], 'leg'),
            cube([1, 0, 12], [4, 11, 15], 'leg'),
            cube([12, 0, 12], [15, 11, 15], 'leg'),
            # the cat sitting on the corner of the table
            cube([9, 14, 7], [13, 18, 12], 'cat'),
            cube([8.5, 18, 5], [13.5, 22, 9], 'cat', north='face'),
            cube([8.5, 22, 6.5], [10, 23.5, 8], 'cat', north='ear'),
            cube([12, 22, 6.5], [13.5, 23.5, 8], 'cat', north='ear'),
            cube([10.5, 14, 12], [11.5, 15, 15], 'cat'),
        ],
    }
    write_json(ASSETS, 'models/block/mob_makers_bench.json', model)
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v = {'model': 'charmobmaker:block/mob_makers_bench'}
        if y:
            v['y'] = y
        variants['facing=' + facing] = v
    write_json(ASSETS, 'blockstates/mob_makers_bench.json', {'variants': variants})
    write_json(ASSETS, 'models/item/mob_makers_bench.json', {'parent': 'charmobmaker:block/mob_makers_bench'})
    write_json(ASSETS, 'models/item/mob_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})
    # the one block behind all made blocks: its real look comes from CustomBlockModel
    write_json(ASSETS, 'models/block/custom_block.json', {'textures': {'particle': 'charmobmaker:block/white'}, 'elements': []})
    write_json(ASSETS, 'blockstates/custom_block.json', {'variants': {'': {'model': 'charmobmaker:block/custom_block'}}})
    write_json(ASSETS, 'models/item/custom_block.json', {
        'parent': 'builtin/entity',
        'gui_light': 'side',
        'textures': {'particle': 'charmobmaker:block/white'},
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        },
    })
    # the one item behind all made items, held upright like a can or a bottle
    write_json(ASSETS, 'models/item/custom_item.json', {
        'parent': 'builtin/entity',
        'gui_light': 'side',
        'textures': {'particle': 'charmobmaker:block/white'},
        'display': {
            'gui': {'rotation': [20, 200, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
            'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3.5, 1.5], 'scale': [0.7, 0.7, 0.7]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3.5, 1.5], 'scale': [0.7, 0.7, 0.7]},
            'firstperson_righthand': {'rotation': [0, 200, 0], 'translation': [0, 3.2, 0], 'scale': [0.7, 0.7, 0.7]},
            'firstperson_lefthand': {'rotation': [0, 160, 0], 'translation': [0, 3.2, 0], 'scale': [0.7, 0.7, 0.7]},
        },
    })
    write_json(ASSETS, 'lang/en_us.json', {
        'block.charmobmaker.mob_makers_bench': "Mob Maker's Bench",
        'item.charmobmaker.mob_egg': 'Mob Egg',
        'block.charmobmaker.custom_block': 'Made Block',
        'item.charmobmaker.custom_block': 'Made Block',
        'item.charmobmaker.custom_item': 'Made Item',
        'entity.charmobmaker.custom': 'Made Mob',
        'itemGroup.charmobmaker': 'Mob Maker',
        'key.charmobmaker.open': 'Open the Mob Maker',
        'key.categories.charmobmaker': 'Mob Maker',
    })
    # the white sprite for plain coloured boxes goes on the block sheet
    write_json(os.path.join(ROOT, 'assets'), 'minecraft/atlases/blocks.json', {'sources': [{'type': 'single', 'resource': 'charmobmaker:block/white'}]})

    write_json(DATA, 'charmobmaker/recipes/mob_makers_bench.json', {
        'type': 'minecraft:crafting_shapeless',
        'category': 'misc',
        'ingredients': [{'item': 'minecraft:crafting_table'}, {'item': 'minecraft:pink_dye'}],
        'result': {'item': 'charmobmaker:mob_makers_bench'},
    })
    write_json(DATA, 'charmobmaker/advancements/recipes/misc/mob_makers_bench.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_table': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ['minecraft:crafting_table']}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'charmobmaker:mob_makers_bench'}},
        },
        'requirements': [['has_table', 'has_the_recipe']],
        'rewards': {'recipes': ['charmobmaker:mob_makers_bench']},
    })
    write_json(DATA, 'charmobmaker/loot_tables/blocks/mob_makers_bench.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'charmobmaker:mob_makers_bench'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    })
    write_json(DATA, 'minecraft/tags/blocks/mineable/axe.json', {'replace': False, 'values': ['charmobmaker:mob_makers_bench']})
    print('art and data written')


if __name__ == '__main__':
    main()
