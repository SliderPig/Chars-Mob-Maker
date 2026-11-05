# {C} Char's Mob Maker

Make your own mobs, blocks, items and machines inside the game, no restart, no code. Build them from other mobs' body parts, blocks, items and boxes you paint yourself, animate them on a timeline like in Blockbench, and even make the screens of your machines.

An example of all of it comes with the mod: the **Energy Drink**. A 3D can you really drink, a Drink Machine block with its own screen, three steps (Mash, Brew, Can), and effects on the can.

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

## Blocks, items and screens

**Mobs** at the top has **New: Mob, Block, Item, Screen**. Blocks and items use the same Build and Animate tabs as mobs; only the second tab changes.

### Made blocks

- In Build you see the block space as a thin frame. A 16 pixel box at height 8 fills it exactly. The block's front (+Z) faces you when you place it.
- The **Block** tab: how solid it is (its own parts, a full block, or walk-through; the green boxes show it), how tough, how bright, its sound, and which screen it opens.
- A block with no clips goes straight into the chunk like any normal block, so build whole houses with them. Paintings, mob parts and items on it, and blocks with clips, cost a little more each.
- Clips: **idle** plays all the time, **working** plays while its machine runs.

### Made items

- 3D in the hand, in slots and on the ground.
- The **Item** tab: drink, eat, instant or nothing; how long it takes; a wait before it can be used again; whether it's used up and what it leaves behind (the can gives back a nugget); potion effects with level and time; healing, food, XP and a bounce; a sound and sparkles; extra tooltip lines. The tooltip shows it all, like a potion.

### Made screens

- **Layout**: add panels, words, slots, buttons, progress arrows or bars, pictures (any item, or paint your own) and a **Steps** strip, then drag them about. The white dot sizes them. What you see is what the game shows. The player's bag is always there; you can move it.
- **Steps**: what the machine makes. Each step lists what goes in, how long it takes and what comes out. Items you made count too. A result in an output slot can feed the next step without moving it.
- Save the screen, then on a block's **Block** tab pick it under **Opens a screen**.
- Buttons can start whatever can go, run one step, or stop. Hover a Steps chip in the game to see what it needs. Or turn on **Starts by itself**.
- Slots are real server slots, the same as a furnace's, so nothing can be duped or forged from a PC. Hoppers work: in to the input slots, out of the output slots. Words can show `{step}` and `{percent}`.

### The Energy Drink, step by step

1. `/mobmaker give drink_machine`, place it, right click it.
2. **Mash**: 2 sweet berries + 1 sugar in the slots, press Start.
3. **Brew**: leave the mash where it is, put in 1 glowstone dust, press Start.
4. **Can**: leave the brew where it is, put in 1 iron nugget, press Start.
5. Take your drink, hold right click. Speed II, Haste II, Jump Boost, some healing and food, and the crushed can comes back as a nugget.

Only ops can make or change any of these on a server (same as mobs); anyone can use the machines and items.

## Blocks, items and screens

**Mobs** at the top has **New: Mob, Block, Item, Screen**. Blocks and items use the same Build and Animate tabs as mobs; only the second tab changes.

### Made blocks

- In Build you see the block space as a thin frame. A 16 pixel box at height 8 fills it exactly. The block's front (+Z) faces you when you place it.
- The **Block** tab: how solid it is (its own parts, a full block, or walk-through; the green boxes show it), how tough, how bright, its sound, and which screen it opens.
- A block with no clips goes straight into the chunk like any normal block, so build whole houses with them. Paintings, mob parts and items on it, and blocks with clips, cost a little more each.
- Clips: **idle** plays all the time, **working** plays while its machine runs.

### Made items

- 3D in the hand, in slots and on the ground.
- The **Item** tab: drink, eat, instant or nothing; how long it takes; a wait before it can be used again; whether it's used up and what it leaves behind (the can gives back a nugget); potion effects with level and time; healing, food, XP and a bounce; a sound and sparkles; extra tooltip lines. The tooltip shows it all, like a potion.

### Made screens

- **Layout**: add panels, words, slots, buttons, progress arrows or bars, pictures (any item, or paint your own) and a **Steps** strip, then drag them about. The white dot sizes them. What you see is what the game shows. The player's bag is always there; you can move it.
- **Steps**: what the machine makes. Each step lists what goes in, how long it takes and what comes out. Items you made count too. A result in an output slot can feed the next step without moving it.
- Save the screen, then on a block's **Block** tab pick it under **Opens a screen**.
- Buttons can start whatever can go, run one step, or stop. Hover a Steps chip in the game to see what it needs. Or turn on **Starts by itself**.
- Slots are real server slots, the same as a furnace's, so nothing can be duped or forged from a PC. Hoppers work: in to the input slots, out of the output slots. Words can show `{step}` and `{percent}`.

### The Energy Drink, step by step

1. `/mobmaker give drink_machine`, place it, right click it.
2. **Mash**: 2 sweet berries + 1 sugar in the slots, press Start.
3. **Brew**: leave the mash where it is, put in 1 glowstone dust, press Start.
4. **Can**: leave the brew where it is, put in 1 iron nugget, press Start.
5. Take your drink, hold right click. Speed II, Haste II, Jump Boost, some healing and food, and the crushed can comes back as a nugget.

Only ops can make or change any of these on a server (same as mobs); anyone can use the machines and items.

## Commands

| Command | What it does |
|---|---|
| `/mobmaker` | opens the editor |
| `/mobmaker open <name>` | opens it on that mob |
| `/mobmaker list` | lists the mobs |
| `/mobmaker spawn <name> [count]` | spawns some nearby |
| `/mobmaker give <name> [count]` | gives eggs, made blocks or made items |
| `/mobmaker delete <name>` | deletes a design of any kind |
| `/mobmaker play <mobs> <clip>` | plays a clip on mobs in the world |
| `/mobmaker reload` | reads the design files again |

`/summon charmobmaker:custom ~ ~ ~ {Design:"pink_cat"}` works too.

## What comes with it

Mobs: Pink Cat, Inky the Black Cat, Block Golem, Chicken-Headed Cow, Bone Knight and Magma Eye. Blocks: Drink Machine and Cat Lamp. Items: Energy Drink, Berry Mash, Energy Brew. Screen: Drink Machine Screen. They arrive once per world. Open them to see how they're made, change them, or delete them for good.

## Limits

64 parts, 24 clips, 96 keys per part and channel, 16 painted cells, 64 things on a screen, 27 slots, 16 steps, 8 effects on an item, 200 designs per world (config). The server checks every upload against these and tidies anything out of range, so nobody can send it anything silly. A design is only ever data, never code, and its file name comes from letters and numbers only.

## Where things live

- Designs: `<world>/charmobmaker/designs/<name>.json`. Plain text. Edit by hand and run `/mobmaker reload` if you want.
- Config: `config/charmobmaker-common.toml`: `allowEveryone`, `maxDesigns`, `maxSpawnCount`, `naturalSpawns`, `maxNaturalPerPlayer`.

## For making changes

- `gradlew test`: the plain logic (designs, tree, keys, limits).
- `gradlew runGameTestServer -Psmoke`: a real headless server spawns mobs and watches them act.
- `gradlew build -Psmoke -x test`, then `python tools/run_real_client.py [embeddium] [only=things]`: the real game walks through the editor pressing its real buttons and saves screenshots to `run-real/screenshots`; `only=things` does the blocks, items, screens and the Energy Drink. Build without `-Psmoke` before using the jar for real.
- `python tools/run_lan_test.py`: two real games over a real connection; the guest (not an op) tries everything it shouldn't be able to do.
- `tools/make_art.py` makes the textures and data files, `tools/make_starters.py` the starter mobs.

MIT licence, see LICENSE.
