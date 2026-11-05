package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Tidies a design so it's always safe to keep, send and draw: every number clamped, every name checked, the part tree really a tree.
// Runs on the server for every upload (a PC can send anything) and on the PC for everything it receives or imports.
public final class DesignRules {
    public static final Set<String> TARGET_GROUPS = Set.of("player", "villager", "animal", "monster", "golem");

    private DesignRules() {}

    public static void tidy(Design d) {
        d.name = cleanName(d.name, Limits.MAX_NAME, "New Mob");
        d.author = cleanName(d.author, 32, "");
        if (!d.owner.matches("[0-9a-fA-F-]{0,36}")) d.owner = "";
        if (!d.id.isEmpty() && !Ids.valid(d.id)) d.id = "";
        tidyPaints(d);
        tidyParts(d);
        tidyClips(d);
        tidyStats(d.stats);
        tidyBlock(d);
        tidyItem(d.item);
        tidyScreen(d);
        if (d.type == null) d.type = Design.Type.MOB;
        if (d.type == Design.Type.SCREEN) {
            d.parts.clear();
            d.clips.clear();
        }
    }

    private static void tidyBlock(Design d) {
        BlockInfo b = d.block;
        b.hardness = range(b.hardness, 0f, 100f, 1.5f);
        b.blast = range(b.blast, 0f, 1200f, 6f);
        b.light = Math.max(0, Math.min(15, b.light));
        if (b.shape == null) b.shape = BlockInfo.Shape.PARTS;
        boolean known = false;
        for (String s : BlockInfo.SOUNDS) known |= s.equals(b.sound);
        if (!known) b.sound = "stone";
        if (!b.screen.isEmpty() && !Ids.valid(b.screen)) b.screen = "";
        b.stack = Math.max(1, Math.min(64, b.stack));
    }

    private static void tidyItem(ItemInfo i) {
        i.stack = Math.max(1, Math.min(64, i.stack));
        if (i.use == null) i.use = ItemInfo.Use.DRINK;
        i.useSeconds = range(i.useSeconds, 0.1f, 30f, 1.6f);
        i.cooldown = range(i.cooldown, 0f, 3600f, 0f);
        if (!i.returns.isEmpty() && !ItemRef.valid(i.returns)) i.returns = "";
        i.heal = range(i.heal, 0f, 200f, 0f);
        i.food = Math.max(0, Math.min(40, i.food));
        i.saturation = range(i.saturation, 0f, 2f, 0.3f);
        i.launch = range(i.launch, 0f, 4f, 0f);
        i.xp = Math.max(0, Math.min(Limits.MAX_XP, i.xp));
        i.sound = sound(i.sound);
        i.particle = sound(i.particle);
        i.particleCount = Math.max(0, Math.min(64, i.particleCount));
        i.effects.removeIf(e -> !Ids.resource(e.id));
        while (i.effects.size() > Limits.MAX_EFFECTS) i.effects.remove(i.effects.size() - 1);
        for (ItemInfo.Effect e : i.effects) {
            e.level = Math.max(1, Math.min(Limits.MAX_EFFECT_LEVEL, e.level));
            e.seconds = range(e.seconds, 0.05f, Limits.MAX_EFFECT_SECONDS, 30f);
        }
        while (i.lines.size() > Limits.MAX_LINES) i.lines.remove(i.lines.size() - 1);
        i.lines.replaceAll(l -> cleanName(l, 60, ""));
        i.handScale = range(i.handScale, 0.1f, 4f, 1f);
        i.handGrip = range(i.handGrip, -32f, 32f, 0f);
    }

    private static void tidyScreen(Design d) {
        ScreenInfo s = d.screen;
        s.width = Math.max(Limits.MIN_SCREEN_W, Math.min(Limits.MAX_SCREEN_W, s.width));
        s.height = Math.max(Limits.MIN_SCREEN_H, Math.min(Limits.MAX_SCREEN_H, s.height));
        s.invX = Math.max(0, Math.min(s.width - 162, s.invX));
        s.invY = Math.max(0, Math.min(s.height - 76, s.invY));
        while (s.widgets.size() > Limits.MAX_WIDGETS) s.widgets.remove(s.widgets.size() - 1);
        int slots = 0;
        for (Iterator<ScreenInfo.Widget> it = s.widgets.iterator(); it.hasNext(); ) {
            ScreenInfo.Widget w = it.next();
            if (w.kind == null) w.kind = ScreenInfo.Widget.Kind.PANEL;
            if (w.kind == ScreenInfo.Widget.Kind.SLOT) {
                if (++slots > Limits.MAX_SLOTS) {
                    it.remove();
                    continue;
                }
                w.w = 18;
                w.h = 18;
            }
            w.w = Math.max(1, Math.min(s.width, w.w));
            w.h = Math.max(1, Math.min(s.height, w.h));
            w.x = Math.max(0, Math.min(s.width - Math.min(w.w, s.width), w.x));
            w.y = Math.max(0, Math.min(s.height - Math.min(w.h, s.height), w.y));
            w.text = cleanName(w.text, 80, "");
            if (w.action < ScreenInfo.Widget.ACTION_STOP || w.action >= Limits.MAX_RECIPES) w.action = ScreenInfo.Widget.ACTION_START;
            if (!w.item.isEmpty() && !ItemRef.valid(w.item)) w.item = "";
            if (w.paint >= d.paints.size() || w.paint < -1) w.paint = -1;
        }
        while (s.recipes.size() > Limits.MAX_RECIPES) s.recipes.remove(s.recipes.size() - 1);
        for (ScreenInfo.Recipe r : s.recipes) {
            r.name = cleanName(r.name, 24, "Step");
            r.inputs.removeIf(i -> !ItemRef.valid(i.item));
            while (r.inputs.size() > Limits.MAX_INPUTS) r.inputs.remove(r.inputs.size() - 1);
            for (ScreenInfo.Ingredient i : r.inputs) i.count = Math.max(1, Math.min(64, i.count));
            if (!ItemRef.valid(r.output)) r.output = "minecraft:air";
            r.outputCount = Math.max(1, Math.min(64, r.outputCount));
            r.seconds = range(r.seconds, 0.05f, Limits.MAX_RECIPE_SECONDS, 3f);
        }
    }

    public static String cleanName(String s, int max, String fallback) {
        if (s == null) return fallback;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != 127 && c != '§' && !Character.isSurrogate(c)) b.append(c);
        }
        String out = b.toString().trim();
        return out.isEmpty() ? fallback : out;
    }

    private static void tidyPaints(Design d) {
        while (d.paints.size() > Limits.MAX_PAINTS) d.paints.remove(d.paints.size() - 1);
        for (int i = 0; i < d.paints.size(); i++) if (d.paints.get(i) == null || d.paints.get(i).length != 256) d.paints.set(i, new int[256]);
    }

    private static void tidyParts(Design d) {
        while (d.parts.size() > Limits.MAX_PARTS) d.parts.remove(d.parts.size() - 1);
        Set<String> seen = new HashSet<>();
        int next = 1;
        for (Part p : d.parts) {
            if (!Ids.valid(p.id) || !seen.add(p.id)) {
                while (!seen.add("p" + next)) next++;
                p.id = "p" + next;
            }
        }
        for (Part p : d.parts) {
            p.name = cleanName(p.name, 32, "Part");
            if (p.kind == null) p.kind = Part.Kind.CUBE;
            clamp(p.pos, -Limits.MAX_OFFSET, Limits.MAX_OFFSET);
            clamp(p.rot, -3600f, 3600f);
            clamp(p.scale, -Limits.MAX_SCALE, Limits.MAX_SCALE);
            clamp(p.pivot, -Limits.MAX_OFFSET, Limits.MAX_OFFSET);
            clamp(p.size, 0f, Limits.MAX_SIZE);
            if (!p.texture.isEmpty() && !Ids.resource(p.texture)) p.texture = "";
            if (p.paint >= d.paints.size() || p.paint < -1) p.paint = -1;
            if (p.facePaint != null) {
                if (p.facePaint.length != Part.FACES) p.facePaint = null;
                else for (int i = 0; i < Part.FACES; i++) if (p.facePaint[i] >= d.paints.size() || p.facePaint[i] < -1) p.facePaint[i] = -1;
            }
            if (!p.block.isEmpty() && !Ids.blockState(p.block)) p.block = "";
            if (!p.mob.isEmpty() && !Ids.resource(p.mob)) p.mob = "";
            if (!p.bone.matches("[a-zA-Z0-9_/.-]{0,96}")) p.bone = "";
            if (!p.mobTexture.isEmpty() && !Ids.resource(p.mobTexture)) p.mobTexture = "";
            if (!p.item.isEmpty() && !Ids.resource(p.item)) p.item = "";
            if (!p.display.matches("[a-z_]{1,32}")) p.display = "third_person_right_hand";
            if (!seen.contains(p.parent) || p.parent.equals(p.id)) p.parent = "";
        }
        // break loops and cut trees that go silly deep
        Map<String, Part> byId = new HashMap<>();
        for (Part p : d.parts) byId.put(p.id, p);
        for (Part p : d.parts) {
            Set<String> path = new HashSet<>();
            Part at = p;
            int depth = 0;
            while (at != null && !at.parent.isEmpty()) {
                if (!path.add(at.id) || ++depth >= Limits.MAX_DEPTH) {
                    at.parent = "";
                    break;
                }
                at = byId.get(at.parent);
            }
        }
    }

    private static void tidyClips(Design d) {
        Set<String> parts = new HashSet<>();
        for (Part p : d.parts) parts.add(p.id);
        Set<String> names = new HashSet<>();
        int total = 0;
        for (Iterator<Clip> it = d.clips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            c.name = cleanClipName(c.name);
            if (d.clips.indexOf(c) >= Limits.MAX_CLIPS || !names.add(c.name)) {
                it.remove();
                continue;
            }
            c.length = range(c.length, Limits.MIN_CLIP, Limits.MAX_CLIP, 1f);
            c.speed = range(c.speed, 0.05f, 10f, 1f);
            if (c.loop == null) c.loop = Clip.Loop.LOOP;
            Set<String> trackKeys = new HashSet<>();
            for (Iterator<Track> ti = c.tracks.iterator(); ti.hasNext(); ) {
                Track t = ti.next();
                if (!parts.contains(t.part) || t.channel == null || !trackKeys.add(t.part + "|" + t.channel)) {
                    ti.remove();
                    continue;
                }
                for (Key k : t.keys) {
                    k.t = range(k.t, 0f, c.length, 0f);
                    float limit = t.channel == Channel.ROT ? 3600f : t.channel == Channel.POS ? Limits.MAX_OFFSET : Limits.MAX_SCALE;
                    k.x = range(k.x, -limit, limit, t.channel.rest());
                    k.y = range(k.y, -limit, limit, t.channel.rest());
                    k.z = range(k.z, -limit, limit, t.channel.rest());
                    if (k.ease == null) k.ease = Ease.SMOOTH;
                }
                t.sort();
                // two keys at the same moment make no sense, the later one wins
                for (int i = t.keys.size() - 1; i > 0; i--) if (t.keys.get(i).t - t.keys.get(i - 1).t < 0.001f) t.keys.remove(i - 1);
                while (t.keys.size() > Limits.MAX_KEYS_PER_TRACK) t.keys.remove(t.keys.size() - 1);
                while (total + t.keys.size() > Limits.MAX_KEYS_TOTAL && !t.keys.isEmpty()) t.keys.remove(t.keys.size() - 1);
                total += t.keys.size();
                if (t.keys.isEmpty()) ti.remove();
            }
        }
    }

    public static String cleanClipName(String s) {
        String lower = s == null ? "" : s.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lower.length() && b.length() < 24; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') b.append(c);
            else if (c == ' ' || c == '-') b.append('_');
        }
        return b.length() == 0 ? "clip" : b.toString();
    }

    public static void tidyStats(Stats s) {
        s.health = range(s.health, 1f, Limits.MAX_HEALTH, 20f);
        s.speed = range(s.speed, 0f, Limits.MAX_SPEED, 0.25f);
        s.damage = range(s.damage, 0f, Limits.MAX_DAMAGE, 3f);
        s.armor = range(s.armor, 0f, Limits.MAX_ARMOR, 0f);
        s.knockbackResist = range(s.knockbackResist, 0f, 1f, 0f);
        s.width = range(s.width, Limits.MIN_WIDTH, Limits.MAX_WIDTH, 0.6f);
        s.height = range(s.height, Limits.MIN_HEIGHT, Limits.MAX_HEIGHT, 1.8f);
        s.xp = Math.max(0, Math.min(Limits.MAX_XP, s.xp));
        if (s.mood == null) s.mood = Stats.Mood.NEUTRAL;
        if (s.attack == null) s.attack = Stats.Attack.MELEE;
        s.attackRate = range(s.attackRate, Limits.MIN_RATE, Limits.MAX_RATE, 1.5f);
        s.abilityCooldown = range(s.abilityCooldown, Limits.MIN_COOLDOWN, Limits.MAX_COOLDOWN, 10f);
        s.summonCount = Math.max(1, Math.min(Limits.MAX_SUMMON, s.summonCount));
        if (!isSummonName(s.summonMob)) s.summonMob = "minecraft:silverfish";
        List<String> targets = new ArrayList<>();
        for (String t : s.targets) {
            if ((TARGET_GROUPS.contains(t) || Ids.resource(t)) && !targets.contains(t) && targets.size() < Limits.MAX_TARGETS) targets.add(t);
        }
        s.targets.clear();
        s.targets.addAll(targets);
        s.drops.removeIf(d -> !Ids.resource(d.item));
        while (s.drops.size() > Limits.MAX_DROPS) s.drops.remove(s.drops.size() - 1);
        for (Stats.Drop d : s.drops) {
            d.min = Math.max(0, Math.min(64, d.min));
            d.max = Math.max(d.min, Math.min(64, d.max));
            d.chance = range(d.chance, 0f, 1f, 1f);
        }
        s.ambientSound = sound(s.ambientSound);
        s.hurtSound = sound(s.hurtSound);
        s.deathSound = sound(s.deathSound);
        s.attackSound = sound(s.attackSound);
        s.stepSound = sound(s.stepSound);
        List<String> biomes = new ArrayList<>();
        for (String b : s.spawnBiomes) if (Ids.resource(b) && !biomes.contains(b) && biomes.size() < Limits.MAX_BIOMES) biomes.add(b);
        s.spawnBiomes.clear();
        s.spawnBiomes.addAll(biomes);
        s.spawnRate = Math.max(1, Math.min(100, s.spawnRate));
        s.spawnGroup = Math.max(1, Math.min(8, s.spawnGroup));
        s.spawnMaxNearby = Math.max(1, Math.min(32, s.spawnMaxNearby));
    }

    private static boolean isSummonName(String s) {
        return Ids.resource(s);
    }

    private static String sound(String s) {
        return s != null && Ids.resource(s) ? s : "";
    }

    private static void clamp(float[] v, float lo, float hi) {
        for (int i = 0; i < v.length; i++) v[i] = range(v[i], lo, hi, Math.max(lo, Math.min(hi, 0f)));
    }

    private static float range(float f, float lo, float hi, float fallback) {
        if (!Float.isFinite(f)) return fallback;
        return Math.max(lo, Math.min(hi, f));
    }
}
