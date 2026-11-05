# The mobs that come with the mod, written as design files.
#   python tools/make_starters.py
import base64
import json
import os
import struct

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data', 'charmobmaker', 'charmobmaker_starters')


class Mob:
    def __init__(self, id, name):
        self.id = id
        self.name = name
        self.parts = []
        self.clips = {}
        self.paints = []
        self.stats = {}
        self.next = 1

    def part(self, name, kind, pos, parent=None, **extra):
        pid = 'p%d' % self.next
        self.next += 1
        p = {'id': pid, 'name': name, 'kind': kind, 'pos': list(pos)}
        if parent:
            p['parent'] = parent
        p.update(extra)
        self.parts.append(p)
        return pid

    def box(self, name, size, pos, parent=None, colour='FFFFFFFF', **extra):
        return self.part(name, 'cube', pos, parent, size=list(size), color=colour, **extra)

    def block(self, name, block, pos, parent=None, **extra):
        return self.part(name, 'block', pos, parent, block=block, **extra)

    def bone(self, name, mob, bone, pos, parent=None, **extra):
        extra.setdefault('solo', True)
        return self.part(name, 'mob', pos, parent, mob=mob, bone=bone, **extra)

    def clip(self, name, length, loop='loop', speed=1):
        self.clips[name] = {'name': name, 'length': length, 'loop': loop, 'speed': speed, 'tracks': {}}
        return name

    def key(self, clip, part, channel, t, x, y, z, ease='smooth'):
        tracks = self.clips[clip]['tracks']
        tracks.setdefault((part, channel), []).append([t, x, y, z, ease])

    def swing(self, clip, part, length, amount, first=True):
        a = amount if first else -amount
        self.key(clip, part, 'rot', 0, a, 0, 0)
        self.key(clip, part, 'rot', length / 2, -a, 0, 0)

    def write(self):
        clips = []
        for c in self.clips.values():
            tracks = [{'part': k[0], 'channel': k[1], 'keys': sorted(v, key=lambda e: e[0])} for k, v in c['tracks'].items()]
            clips.append({'name': c['name'], 'length': c['length'], 'speed': c['speed'], 'loop': c['loop'], 'tracks': tracks})
        data = {'format': 1, 'id': self.id, 'name': self.name, 'author': 'Char', 'parts': self.parts, 'clips': clips, 'stats': self.stats}
        if self.paints:
            data['paints'] = self.paints
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(OUT, self.id + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, separators=(',', ':'))
            f.write('\n')


def cell(rows, colours):
    data = b''
    for row in rows:
        for c in row:
            data += struct.pack('>I', colours[c])
    return base64.b64encode(data).decode()


def cat(mid, name, wool, ear, eye, nose, face_bg, health, sounds_quiet=False):
    m = Mob(mid, name)
    m.paints.append(cell([
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBEEEBBBBBBEEEBB',
        'BBEWKBBBBBBEWKBB',
        'BBEKKBBBBBBEKKBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBNNBBBBBBB',
        'BDBBBBBBNBBBBBDB',
        'BBDBBBBDBDBBBDBB',
        'BBBBBBBBDBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
        'BBBBBBBBBBBBBBBB',
    ], {'B': face_bg, 'E': eye, 'W': 0xFFFFFFFF, 'K': 0xFF16121A, 'N': nose, 'D': 0xFF4A3550}))
    tex = 'minecraft:block/' + wool
    body = m.box('Body', (6.5, 6.5, 14), (0, 8.75, 0), texture=tex)
    head = m.box('Head', (8, 7.5, 7), (0, 5, 8.5), body, texture=tex, head=True, facePaint=[0, -1, -1, -1, -1, -1], pivot=[0, -3, -3])
    m.box('Left ear', (2.6, 2.6, 1.2), (2.6, 4.9, -0.8), head, colour=ear)
    m.box('Right ear', (2.6, 2.6, 1.2), (-2.6, 4.9, -0.8), head, colour=ear)
    legs = {}
    for label, x, z in (('Left front leg', 2.1, 5.2), ('Right front leg', -2.1, 5.2), ('Left hind leg', 2.1, -5.2), ('Right hind leg', -2.1, -5.2)):
        legs[label] = m.box(label, (2.3, 6, 2.3), (x, -5.75, z), body, texture=tex, pivot=[0, 2.6, 0])
    tail = m.box('Tail', (1.8, 1.8, 9), (0, 2.5, -10.5), body, texture=tex, pivot=[0, 0, 4.2], rot=[40, 0, 0])
    m.clip('idle', 3.0)
    m.key('idle', tail, 'rot', 0, 0, -16, 0)
    m.key('idle', tail, 'rot', 1.5, 0, 16, 0)
    m.key('idle', head, 'rot', 0, 0, 0, 0)
    m.key('idle', head, 'rot', 1.5, 3, 0, 0)
    m.key('idle', body, 'scale', 0, 1, 1, 1)
    m.key('idle', body, 'scale', 1.5, 1.01, 1.03, 1.01)
    m.clip('walk', 0.7)
    m.swing('walk', legs['Left front leg'], 0.7, 34, True)
    m.swing('walk', legs['Right hind leg'], 0.7, 34, True)
    m.swing('walk', legs['Right front leg'], 0.7, 34, False)
    m.swing('walk', legs['Left hind leg'], 0.7, 34, False)
    m.key('walk', tail, 'rot', 0, 0, -22, 0)
    m.key('walk', tail, 'rot', 0.35, 0, 22, 0)
    m.key('walk', body, 'pos', 0, 0, 0, 0)
    m.key('walk', body, 'pos', 0.175, 0, 0.5, 0)
    m.key('walk', body, 'pos', 0.35, 0, 0, 0)
    m.key('walk', body, 'pos', 0.525, 0, 0.5, 0)
    m.clip('attack', 0.45, 'once')
    m.key('attack', head, 'pos', 0, 0, 0, 0)
    m.key('attack', head, 'pos', 0.12, 0, -1, 4, 'out')
    m.key('attack', head, 'pos', 0.45, 0, 0, 0)
    m.key('attack', legs['Left front leg'], 'rot', 0, 0, 0, 0)
    m.key('attack', legs['Left front leg'], 'rot', 0.12, -70, 0, 0, 'out')
    m.key('attack', legs['Left front leg'], 'rot', 0.45, 0, 0, 0)
    m.clip('hurt', 0.35, 'once')
    m.key('hurt', body, 'rot', 0, 0, 0, 0)
    m.key('hurt', body, 'rot', 0.08, 0, 14, 0, 'out')
    m.key('hurt', body, 'rot', 0.2, 0, -8, 0)
    m.key('hurt', body, 'rot', 0.35, 0, 0, 0)
    m.key('hurt', head, 'rot', 0, 0, 0, 0)
    m.key('hurt', head, 'rot', 0.1, -24, 0, 0, 'out')
    m.key('hurt', head, 'rot', 0.35, 0, 0, 0)
    m.clip('death', 1.2, 'hold')
    m.key('death', body, 'rot', 0, 0, 0, 0, 'in')
    m.key('death', body, 'rot', 0.8, 0, 0, 90, 'bounce')
    m.key('death', body, 'pos', 0, 0, 0, 0, 'in')
    m.key('death', body, 'pos', 0.8, 0, -7, 0)
    m.key('death', head, 'rot', 0.3, 0, 0, 0)
    m.key('death', head, 'rot', 1.0, 30, 0, 0)
    m.stats = {'health': health, 'speed': 0.3, 'damage': 2, 'width': 0.7, 'height': 0.95, 'xp': 3, 'mood': 'neutral', 'targets': ['player'],
               'attack': 'melee', 'attackRate': 1, 'leap': True, 'abilityCooldown': 6,
               'drops': [{'item': 'minecraft:' + wool, 'min': 0, 'max': 2, 'chance': 1}, {'item': 'minecraft:string', 'min': 0, 'max': 1, 'chance': 0.5}],
               'sounds': {'ambient': 'minecraft:entity.cat.ambient', 'hurt': 'minecraft:entity.cat.hurt', 'death': 'minecraft:entity.cat.death', 'attack': 'minecraft:entity.cat.hiss', 'step': 'minecraft:block.wool.step'},
               'eggBase': 'FF%06X' % (0xFF8FC7 if wool.startswith('pink') else 0x16121A), 'eggSpots': 'FF%06X' % (0x2A1B33 if wool.startswith('pink') else 0xFF8FC7)}
    m.write()


def golem():
    m = Mob('block_golem', 'Block Golem')
    body = m.block('Body', 'minecraft:stone', (0, 24, 0), scale=[1.5, 1.5, 1.0])
    head = m.block('Head', 'minecraft:chiseled_stone_bricks', (0, 13, 0), body, scale=[0.62, 0.62, 0.95], head=True, pivot=[0, -8, 0])
    m.box('Left eye', (2.4, 1.6, 1), (3, 1.5, 8.4), head, colour='FFFF8FC7', glow=True)
    m.box('Right eye', (2.4, 1.6, 1), (-3, 1.5, 8.4), head, colour='FFFF8FC7', glow=True)
    larm = m.block('Left arm', 'minecraft:mossy_cobblestone', (10.8, -1, 0), body, scale=[0.42, 1.25, 0.65], pivot=[0, 8, 0])
    rarm = m.block('Right arm', 'minecraft:mossy_cobblestone', (-10.8, -1, 0), body, scale=[0.42, 1.25, 0.65], pivot=[0, 8, 0])
    lleg = m.block('Left leg', 'minecraft:stone_bricks', (3.6, -14, 0), body, scale=[0.42, 0.68, 0.72], pivot=[0, 8, 0])
    rleg = m.block('Right leg', 'minecraft:stone_bricks', (-3.6, -14, 0), body, scale=[0.42, 0.68, 0.72], pivot=[0, 8, 0])
    m.clip('idle', 4.0)
    m.key('idle', larm, 'rot', 0, 3, 0, 2)
    m.key('idle', larm, 'rot', 2, -3, 0, 4)
    m.key('idle', rarm, 'rot', 0, -3, 0, -2)
    m.key('idle', rarm, 'rot', 2, 3, 0, -4)
    m.key('idle', head, 'rot', 0, 0, 0, 0)
    m.key('idle', head, 'rot', 2, -3, 0, 0)
    m.clip('walk', 1.2)
    m.swing('walk', lleg, 1.2, 24, True)
    m.swing('walk', rleg, 1.2, 24, False)
    m.swing('walk', larm, 1.2, 20, False)
    m.swing('walk', rarm, 1.2, 20, True)
    m.key('walk', body, 'rot', 0, 0, 0, 2.5)
    m.key('walk', body, 'rot', 0.6, 0, 0, -2.5)
    m.key('walk', body, 'pos', 0, 0, 0, 0)
    m.key('walk', body, 'pos', 0.3, 0, 0.8, 0)
    m.key('walk', body, 'pos', 0.6, 0, 0, 0)
    m.key('walk', body, 'pos', 0.9, 0, 0.8, 0)
    m.clip('attack', 0.7, 'once')
    for arm in (larm, rarm):
        m.key('attack', arm, 'rot', 0, 0, 0, 0, 'out')
        m.key('attack', arm, 'rot', 0.25, -150, 0, 0, 'in')
        m.key('attack', arm, 'rot', 0.4, -40, 0, 0, 'out')
        m.key('attack', arm, 'rot', 0.7, 0, 0, 0)
    m.key('attack', body, 'rot', 0, 0, 0, 0)
    m.key('attack', body, 'rot', 0.25, -8, 0, 0, 'in')
    m.key('attack', body, 'rot', 0.4, 12, 0, 0)
    m.key('attack', body, 'rot', 0.7, 0, 0, 0)
    m.clip('hurt', 0.4, 'once')
    m.key('hurt', body, 'pos', 0, 0, 0, 0)
    m.key('hurt', body, 'pos', 0.1, 0, 0, -1.5, 'out')
    m.key('hurt', body, 'pos', 0.4, 0, 0, 0)
    m.clip('death', 2.0, 'hold')
    m.key('death', body, 'rot', 0, 0, 0, 0, 'in')
    m.key('death', body, 'rot', 1.2, -90, 0, 0, 'bounce')
    m.key('death', body, 'pos', 0, 0, 0, 0, 'in')
    m.key('death', body, 'pos', 1.2, 0, -14, 0)
    m.key('death', head, 'pos', 0.8, 0, 0, 0)
    m.key('death', head, 'pos', 1.6, 0, 12, -14, 'bounce')
    m.stats = {'health': 60, 'speed': 0.2, 'damage': 7, 'armor': 6, 'knockbackResist': 0.6, 'width': 1.3, 'height': 2.8, 'xp': 15, 'mood': 'hostile',
               'targets': ['player', 'monster'], 'attack': 'melee', 'attackRate': 1.6,
               'drops': [{'item': 'minecraft:cobblestone', 'min': 2, 'max': 8, 'chance': 1}, {'item': 'minecraft:moss_block', 'min': 0, 'max': 1, 'chance': 0.4}],
               'sounds': {'hurt': 'minecraft:entity.iron_golem.hurt', 'death': 'minecraft:entity.iron_golem.death', 'attack': 'minecraft:entity.iron_golem.attack', 'step': 'minecraft:entity.iron_golem.step'},
               'eggBase': 'FF7D7D7D', 'eggSpots': 'FFFF8FC7'}
    m.write()


def chicken_cow():
    m = Mob('chicken_headed_cow', 'Chicken-Headed Cow')
    cow = 'minecraft:cow'
    chicken = 'minecraft:chicken'
    # numbers are the cow's own: its model has Y down and its front at -Z, so x, 24 - y, -z
    body = m.bone('Body', cow, 'body', (0, 19, -2), rot=[90, 0, 0])
    legs = {}
    for label, bone, x, z in (('Right hind leg', 'right_hind_leg', -4, -7), ('Left hind leg', 'left_hind_leg', 4, -7), ('Right front leg', 'right_front_leg', -4, 6), ('Left front leg', 'left_front_leg', 4, 6)):
        legs[label] = m.bone(label, cow, bone, (x, 12, z))
    neck = m.box('Neck', (3, 3, 3), (0, 19, 8), None, texture='minecraft:block/white_wool', pivot=[0, 0, 0], colour='FFFFFFFF')
    head = m.bone('Head', chicken, 'head', (0, 21, 10), scale=[1.6, 1.6, 1.6], head=True)
    m.bone('Beak', chicken, 'beak', (0, 0, 0), head)
    m.bone('Wattle', chicken, 'red_thing', (0, 0, 0), head)
    lwing = m.bone('Left wing', chicken, 'left_wing', (5.5, 22, 3), scale=[1.3, 1.3, 1.3])
    rwing = m.bone('Right wing', chicken, 'right_wing', (-5.5, 22, 3), scale=[1.3, 1.3, 1.3])
    m.clip('idle', 2.4)
    m.key('idle', head, 'rot', 0, 0, 0, 0)
    m.key('idle', head, 'rot', 0.3, 0, 0, 0)
    m.key('idle', head, 'rot', 0.45, 40, 0, 0)
    m.key('idle', head, 'rot', 0.6, 0, 0, 0)
    m.key('idle', head, 'rot', 0.75, 40, 0, 0)
    m.key('idle', head, 'rot', 0.9, 0, 0, 0)
    m.key('idle', lwing, 'rot', 0, 0, 0, 0)
    m.key('idle', rwing, 'rot', 0, 0, 0, 0)
    m.key('idle', lwing, 'rot', 1.4, 0, 0, 0)
    m.key('idle', rwing, 'rot', 1.4, 0, 0, 0)
    for i, (t, a) in enumerate([(1.55, 50), (1.7, 0), (1.85, 50), (2.0, 0)]):
        m.key('idle', lwing, 'rot', t, 0, 0, -a)
        m.key('idle', rwing, 'rot', t, 0, 0, a)
    m.clip('walk', 0.9)
    m.swing('walk', legs['Left front leg'], 0.9, 30, True)
    m.swing('walk', legs['Right hind leg'], 0.9, 30, True)
    m.swing('walk', legs['Right front leg'], 0.9, 30, False)
    m.swing('walk', legs['Left hind leg'], 0.9, 30, False)
    m.key('walk', head, 'pos', 0, 0, 0, 0)
    m.key('walk', head, 'pos', 0.225, 0, 0, 2)
    m.key('walk', head, 'pos', 0.45, 0, 0, 0)
    m.key('walk', head, 'pos', 0.675, 0, 0, 2)
    m.clip('hurt', 0.5, 'once')
    for t, a in [(0, 0), (0.1, 70), (0.2, 10), (0.3, 70), (0.5, 0)]:
        m.key('hurt', lwing, 'rot', t, 0, 0, -a)
        m.key('hurt', rwing, 'rot', t, 0, 0, a)
    m.clip('death', 1.5, 'hold')
    m.key('death', body, 'rot', 0, 0, 0, 0, 'in')
    m.key('death', body, 'rot', 0.8, 0, 0, 0)
    for label, leg in legs.items():
        m.key('death', leg, 'rot', 0, 0, 0, 0, 'in')
        m.key('death', leg, 'pos', 0, 0, 0, 0, 'in')
        m.key('death', leg, 'pos', 0.8, 0, 10, 0, 'bounce')
    m.key('death', body, 'pos', 0, 0, 0, 0, 'in')
    m.key('death', body, 'pos', 0.8, 0, -11, 0, 'bounce')
    m.key('death', head, 'pos', 0, 0, 0, 0, 'in')
    m.key('death', head, 'pos', 0.8, 0, -13, 0, 'bounce')
    m.key('death', neck, 'pos', 0, 0, 0, 0, 'in')
    m.key('death', neck, 'pos', 0.8, 0, -11, 0, 'bounce')
    m.stats = {'health': 14, 'speed': 0.22, 'damage': 0, 'width': 0.9, 'height': 1.5, 'xp': 2, 'mood': 'passive', 'attack': 'none', 'targets': [],
               'drops': [{'item': 'minecraft:leather', 'min': 0, 'max': 2, 'chance': 1}, {'item': 'minecraft:feather', 'min': 1, 'max': 3, 'chance': 1}, {'item': 'minecraft:egg', 'min': 0, 'max': 1, 'chance': 0.3}],
               'sounds': {'ambient': 'minecraft:entity.chicken.ambient', 'hurt': 'minecraft:entity.cow.hurt', 'death': 'minecraft:entity.chicken.death', 'step': 'minecraft:entity.cow.step'},
               'eggBase': 'FFEAE0D0', 'eggSpots': 'FF6B4A2B'}
    m.write()


def bone_knight():
    m = Mob('bone_knight', 'Bone Knight')
    sk = 'minecraft:skeleton'
    body = m.bone('Body', sk, 'body', (0, 24, 0))
    head = m.bone('Head', sk, 'head', (0, 24, 0), head=True)
    helmet = m.block('Helmet', 'minecraft:gold_block', (0, 4.5, 0), head, scale=[0.56, 0.56, 0.56])
    larm = m.bone('Left arm', sk, 'left_arm', (5, 22, 0))
    rarm = m.bone('Right arm', sk, 'right_arm', (-5, 22, 0))
    lleg = m.bone('Left leg', sk, 'left_leg', (2, 12, 0))
    rleg = m.bone('Right leg', sk, 'right_leg', (-2, 12, 0))
    sword = m.part('Sword', 'item', (1, -10, 2), rarm, item='minecraft:diamond_sword', display='third_person_right_hand', rot=[-90, 180, 0])
    m.clip('idle', 3.0)
    m.key('idle', rarm, 'rot', 0, -8, 0, 0)
    m.key('idle', rarm, 'rot', 1.5, -12, 0, 0)
    m.key('idle', larm, 'rot', 0, 4, 0, 0)
    m.key('idle', larm, 'rot', 1.5, -4, 0, 0)
    m.clip('walk', 0.8)
    m.swing('walk', lleg, 0.8, 36, True)
    m.swing('walk', rleg, 0.8, 36, False)
    m.swing('walk', larm, 0.8, 28, False)
    m.key('walk', rarm, 'rot', 0, -24, 0, 0)
    m.key('walk', rarm, 'rot', 0.4, -8, 0, 0)
    m.clip('attack', 0.5, 'once')
    m.key('attack', rarm, 'rot', 0, -20, 0, 0, 'out')
    m.key('attack', rarm, 'rot', 0.15, -140, 0, 20, 'in')
    m.key('attack', rarm, 'rot', 0.28, -30, 0, -10, 'out')
    m.key('attack', rarm, 'rot', 0.5, -12, 0, 0)
    m.key('attack', body, 'rot', 0, 0, 0, 0)
    m.key('attack', body, 'rot', 0.15, 0, -14, 0, 'in')
    m.key('attack', body, 'rot', 0.28, 0, 18, 0)
    m.key('attack', body, 'rot', 0.5, 0, 0, 0)
    m.clip('hurt', 0.35, 'once')
    m.key('hurt', head, 'rot', 0, 0, 0, 0)
    m.key('hurt', head, 'rot', 0.1, -24, 8, 0, 'out')
    m.key('hurt', head, 'rot', 0.35, 0, 0, 0)
    m.clip('death', 2.0, 'hold')
    for part, x, z, spin in ((head, 3, 4, 40), (larm, 6, 2, 90), (rarm, -6, 2, -90), (body, 0, -2, 30), (lleg, 3, -3, 90), (rleg, -3, -3, -90)):
        m.key('death', part, 'pos', 0, 0, 0, 0, 'in')
        h = {head: 24, larm: 20, rarm: 20, body: 16, lleg: 9, rleg: 9}[part]
        m.key('death', part, 'pos', 0.7, x, -h, z, 'bounce')
        m.key('death', part, 'rot', 0, 0, 0, 0, 'in')
        m.key('death', part, 'rot', 0.7, spin, 0, spin / 2, 'bounce')
    m.stats = {'health': 30, 'speed': 0.27, 'damage': 6, 'armor': 3, 'width': 0.6, 'height': 2.0, 'xp': 10, 'mood': 'hostile', 'targets': ['player', 'villager'], 'attack': 'melee', 'attackRate': 1.2, 'leap': True, 'abilityCooldown': 8,
               'drops': [{'item': 'minecraft:bone', 'min': 1, 'max': 3, 'chance': 1}, {'item': 'minecraft:gold_nugget', 'min': 0, 'max': 3, 'chance': 0.6}],
               'sounds': {'ambient': 'minecraft:entity.skeleton.ambient', 'hurt': 'minecraft:entity.skeleton.hurt', 'death': 'minecraft:entity.skeleton.death', 'attack': 'minecraft:entity.player.attack.sweep', 'step': 'minecraft:entity.skeleton.step'},
               'eggBase': 'FFC9C9C9', 'eggSpots': 'FFE5B53A'}
    m.write()


def magma_eye():
    m = Mob('magma_eye', 'Magma Eye')
    core = m.block('Core', 'minecraft:magma_block', (0, 14, 0), glow=True, head=True, scale=[0.9, 0.9, 0.9])
    m.box('Eye', (9, 9, 1), (0, 0, 7.6), core, colour='FFFFE066', glow=True)
    m.box('Pupil', (4, 7, 1), (0, 0, 8.3), core, colour='FF16121A', glow=True)
    joint = m.box('Orbit', (0, 0, 0), (0, 14, 0))
    for i, (x, z, block) in enumerate(((13, 0, 'minecraft:blackstone'), (-13, 0, 'minecraft:blackstone'), (0, 13, 'minecraft:crying_obsidian'), (0, -13, 'minecraft:crying_obsidian'))):
        m.block('Rock %d' % (i + 1), block, (x, 0, z), joint, scale=[0.36, 0.36, 0.36])
    m.clip('idle', 4.0)
    m.key('idle', joint, 'rot', 0, 0, 0, 0, 'linear')
    m.key('idle', joint, 'rot', 4, 0, 360, 0, 'linear')
    m.key('idle', core, 'pos', 0, 0, 0, 0)
    m.key('idle', core, 'pos', 2, 0, 1.6, 0)
    m.key('idle', joint, 'pos', 0, 0, -1, 0)
    m.key('idle', joint, 'pos', 2, 0, 1, 0)
    m.clip('ranged', 0.5, 'once')
    m.key('ranged', core, 'scale', 0, 1, 1, 1)
    m.key('ranged', core, 'scale', 0.15, 1.35, 1.35, 1.35, 'out')
    m.key('ranged', core, 'scale', 0.5, 1, 1, 1)
    m.clip('hurt', 0.3, 'once')
    m.key('hurt', core, 'rot', 0, 0, 0, 0)
    m.key('hurt', core, 'rot', 0.1, 0, 20, 12, 'out')
    m.key('hurt', core, 'rot', 0.3, 0, 0, 0)
    m.clip('death', 1.5, 'hold')
    m.key('death', joint, 'scale', 0, 1, 1, 1, 'in')
    m.key('death', joint, 'scale', 1.0, 3, 0.2, 3)
    m.key('death', core, 'scale', 0, 1, 1, 1, 'bounce')
    m.key('death', core, 'scale', 1.2, 0.05, 0.05, 0.05)
    m.stats = {'health': 24, 'speed': 0.22, 'damage': 4, 'width': 1.0, 'height': 1.6, 'xp': 12, 'mood': 'hostile', 'flying': True, 'fireImmune': True, 'targets': ['player'], 'attack': 'fireball', 'attackRate': 2.5, 'teleport': True, 'abilityCooldown': 12,
               'drops': [{'item': 'minecraft:magma_cream', 'min': 0, 'max': 2, 'chance': 1}, {'item': 'minecraft:blaze_powder', 'min': 0, 'max': 1, 'chance': 0.4}],
               'sounds': {'ambient': 'minecraft:entity.blaze.ambient', 'hurt': 'minecraft:entity.blaze.hurt', 'death': 'minecraft:entity.blaze.death'},
               'spawnNaturally': False, 'eggBase': 'FF3A1408', 'eggSpots': 'FFFFA94D'}
    m.write()


def main():
    cat('pink_cat', 'Pink Cat', 'pink_wool', 'FFD6559C', 0xFF4DBFA8, 0xFFD6559C, 0xFFF08DBF, 14)
    cat('inky', 'Inky the Black Cat', 'black_wool', 'FFFF8FC7', 0xFFFFE066, 0xFFFF8FC7, 0xFF1A1620, 14)
    golem()
    chicken_cow()
    bone_knight()
    magma_eye()
    print('starters written to', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
