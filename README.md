# {C} Char's Mob Maker

Make your own mobs inside the game, no restart, no code. Build them from other mobs' body parts, blocks, items and boxes you paint yourself, then animate them on a timeline like in Blockbench.

For Minecraft 1.20.1 with Forge. It goes on the server **and** on every player's PC. It needs no other mods.

## Getting in

Any of these opens the editor:

- `/mobmaker`
- the **Mob Maker's Bench** block (crafted from 1 crafting table + 1 pink dye), right click it
- the `\` key (change it in Controls, under Mob Maker)

In singleplayer you can always use it. On a server only ops can, unless `allowEveryone = true` in `config/charmobmaker-common.toml`; then everyone can make mobs but only change their own.

## The editor

The mob stands in the middle. Drag on empty space to look around, **mouse wheel** to zoom, **right mouse drag** to slide the view. Front, Side, Top and Fit at the bottom put the camera back where you want it.

### Build tab

- **Add a part** gives you four kinds:
  - **Box**: size it, colour it, give it a block's texture, or paint it yourself (16 x 16 cells, a different one per side if you like).
  - **Block**: any block, with its real model. Stairs, fences, glass, chests, anything.
  - **Mob part**: pick a mob, then a body part with a live picture of it: the zombie's head, the cow's body, the chicken's legs. **Whole mob** adds the whole thing; **Add all its parts, separately** lays out every part in place so you can swap one out (that's how the Chicken-Headed Cow got made).
  - **Item**: a sword, a torch, whatever. Hang it from an arm and press **Set it up like held in a hand**.
- The list on the left is the tree of parts. New parts hang from the selected one (the Add window says where); right after adding one, the next goes beside it. **Drag a row onto another** to hang it there (it stays where it is on screen), or onto the empty bit of the list to free it. The little eye hides a part while you work. Click the small arrow to fold a branch.
- **Copy** duplicates a part with everything hanging off it. **Mirror** makes a copy on the other side of the mob (Left arm becomes Right arm). **Delete** deletes.
- Click a part on the mob itself to select it; it glows pink. Drag the coloured arrows to move it; the Move, Turn and Size buttons above the mob (or keys 1, 2, 3) change what the handles do. Hold Shift for neat steps.
- The right panel has every number: where it sits, turn, size, and the **pivot**, which is the point it turns from (the shoulder of an arm). Type a number or use the slider.
- **Looks at things** makes a part turn to face whatever the mob looks at, for heads. **Bright in the dark** makes it glow.

16 pixels = 1 block. The mob's feet are at 0, and +Z is the way it faces.

### Stats tab

Health, armour, speed, damage, how often it attacks, XP, the hitbox (shown as a green box), flying, fire immunity. Hostile, Neutral (fights back) or Passive (runs away). What it goes after: players, villagers, animals, monsters, golems or one kind of mob. Melee, arrows or fireballs. Three abilities: leaping, teleporting and calling helpers. Drops, sounds (searchable, press the arrow to hear one), egg colours, and whether it spawns on its own (which biomes, how often, in the dark only).

### Animate tab

The timeline is along the bottom. Each clip has a name; these get played by themselves:

| Clip | When |
|---|---|
| idle | standing around |
| walk | moving, faster when the mob is faster, blending smoothly with idle |
| attack | when it hits something |
| ranged | when it shoots (falls back to attack) |
| hurt | when it takes a hit |
| death | as it dies, up to 5 seconds, and it waits for the clip to finish |
| leap, teleport, summon | when it uses that ability |

Press **+** for clips of your own, and play those with `/mobmaker play @e[type=charmobmaker:custom,distance=..10] dance` (the clip name `stop` ends one).

To animate: pick a part, move the playhead by clicking the ruler, then pose it with the handles or the numbers. A key appears on its own at that moment. The circle next to Turn, Move and Size adds or removes a key right there. On the timeline: click a key to pick it (then choose how it travels on to the next one: Linear, Smooth, Slow start, Slow stop, Snap, Bounce), drag it to move it, right click to delete it, double click an empty line to add one. **Space** plays and pauses, **K** sets a key.

Handy buttons: **Copy** and **Paste** a part's keys, and **Flip**, which copies the part's keys to the part on the other side half a cycle later: animate one leg and you have two. **Make a walk for me** and **Make an idle for me** build a clip from the names of your parts (anything called leg, arm, tail, head, body).

A clip only moves the parts it has keys for, so an attack with just arm keys swings the arm while the legs keep walking.

### Saving and sharing

- **Save** (or Ctrl+S) stores the mob in the world. Mobs already out in the world change straight away, for everyone. Your work is kept if you close the screen without saving, until you open another mob.
- **Undo** and **Redo** (Ctrl+Z, Ctrl+Y) go back through every change.
- **Spawn** puts one next to you, **Egg** gives you a spawn egg. Eggs for every mob are in the Mob Maker creative tab.
- **Mobs** lists every mob in this world, and has **New**, **Save a copy**, **To a file**, **Copy text** and **Paste text**. Files go in `<game folder>/charmobmaker/designs` for sharing with anyone. Put a Blockbench `.bbmodel` file there and it opens too: boxes, folders and keyframes come through, textures don't (boxes get a plain colour).

## Commands

| Command | What it does |
|---|---|
| `/mobmaker` | opens the editor |
| `/mobmaker open <name>` | opens it on that mob |
| `/mobmaker list` | lists the mobs |
| `/mobmaker spawn <name> [count]` | spawns some nearby |
| `/mobmaker give <name> [count]` | gives eggs |
| `/mobmaker delete <name>` | deletes a design |
| `/mobmaker play <mobs> <clip>` | plays a clip on mobs in the world |
| `/mobmaker reload` | reads the design files again |

`/summon charmobmaker:custom ~ ~ ~ {Design:"pink_cat"}` works too.

## Mobs that come with it

Pink Cat, Inky the Black Cat, Block Golem, Chicken-Headed Cow, Bone Knight and Magma Eye. They arrive once per world. Open them to see how they're made, change them, or delete them for good.

## Limits

64 parts, 24 clips, 96 keys per part and channel, 16 painted cells, 200 mobs per world (config). The server checks every upload against these and tidies anything out of range, so nobody can send it anything silly. A design is only ever data, never code, and its file name comes from letters and numbers only.

## Where things live

- Designs: `<world>/charmobmaker/designs/<name>.json`. Plain text. Edit by hand and run `/mobmaker reload` if you want.
- Config: `config/charmobmaker-common.toml`: `allowEveryone`, `maxDesigns`, `maxSpawnCount`, `naturalSpawns`, `maxNaturalPerPlayer`.

## For making changes

- `gradlew test`: the plain logic (designs, tree, keys, limits).
- `gradlew runGameTestServer -Psmoke`: a real headless server spawns mobs and watches them act.
- `gradlew build -Psmoke -x test`, then `python tools/run_real_client.py [embeddium]`: the real game walks through the editor pressing its real buttons and saves screenshots to `run-real/screenshots`. Build without `-Psmoke` before using the jar for real.
- `tools/make_art.py` makes the textures and data files, `tools/make_starters.py` the starter mobs.

MIT licence, see LICENSE.
