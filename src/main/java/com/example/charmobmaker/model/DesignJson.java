package com.example.charmobmaker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

// Designs as JSON. Reading never trusts the text: anything missing or of the wrong type just gets its default, and DesignRules tidies the rest.
public final class DesignJson {
    private DesignJson() {}

    public static final class BadDesign extends Exception {
        public BadDesign(String message) {
            super(message);
        }
    }

    // ---------- writing ----------

    public static String write(Design d) {
        JsonObject o = new JsonObject();
        o.addProperty("format", Design.FORMAT);
        o.addProperty("id", d.id);
        o.addProperty("name", d.name);
        if (!d.author.isEmpty()) o.addProperty("author", d.author);
        if (!d.owner.isEmpty()) o.addProperty("owner", d.owner);
        JsonArray parts = new JsonArray();
        for (Part p : d.parts) parts.add(writePart(p));
        o.add("parts", parts);
        JsonArray clips = new JsonArray();
        for (Clip c : d.clips) clips.add(writeClip(c));
        o.add("clips", clips);
        if (!d.paints.isEmpty()) {
            JsonArray paints = new JsonArray();
            for (int[] cell : d.paints) paints.add(writeCell(cell));
            o.add("paints", paints);
        }
        o.add("stats", writeStats(d.stats));
        return o.toString();
    }

    private static JsonObject writePart(Part p) {
        JsonObject o = new JsonObject();
        o.addProperty("id", p.id);
        o.addProperty("name", p.name);
        if (!p.parent.isEmpty()) o.addProperty("parent", p.parent);
        o.addProperty("kind", p.kind.name().toLowerCase(Locale.ROOT));
        o.add("pos", floats(p.pos));
        if (nonZero(p.rot)) o.add("rot", floats(p.rot));
        if (p.scale[0] != 1f || p.scale[1] != 1f || p.scale[2] != 1f) o.add("scale", floats(p.scale));
        if (nonZero(p.pivot)) o.add("pivot", floats(p.pivot));
        if (p.glow) o.addProperty("glow", true);
        if (p.head) o.addProperty("head", true);
        if (p.hidden) o.addProperty("hidden", true);
        switch (p.kind) {
            case CUBE -> {
                o.add("size", floats(p.size));
                o.addProperty("color", String.format(Locale.ROOT, "%08X", p.color));
                if (!p.texture.isEmpty()) o.addProperty("texture", p.texture);
                if (p.paint >= 0) o.addProperty("paint", p.paint);
                if (p.facePaint != null) {
                    JsonArray a = new JsonArray();
                    for (int f : p.facePaint) a.add(f);
                    o.add("facePaint", a);
                }
            }
            case BLOCK -> o.addProperty("block", p.block);
            case MOB -> {
                o.addProperty("mob", p.mob);
                if (!p.bone.isEmpty()) o.addProperty("bone", p.bone);
                if (p.solo) o.addProperty("solo", true);
                if (!p.mobTexture.isEmpty()) o.addProperty("mobTexture", p.mobTexture);
            }
            case ITEM -> {
                o.addProperty("item", p.item);
                o.addProperty("display", p.display);
            }
        }
        return o;
    }

    private static JsonObject writeClip(Clip c) {
        JsonObject o = new JsonObject();
        o.addProperty("name", c.name);
        o.addProperty("length", round(c.length));
        if (c.speed != 1f) o.addProperty("speed", round(c.speed));
        o.addProperty("loop", c.loop.name().toLowerCase(Locale.ROOT));
        JsonArray tracks = new JsonArray();
        for (Track t : c.tracks) {
            if (t.keys.isEmpty()) continue;
            JsonObject to = new JsonObject();
            to.addProperty("part", t.part);
            to.addProperty("channel", t.channel.name().toLowerCase(Locale.ROOT));
            JsonArray keys = new JsonArray();
            for (Key k : t.keys) {
                JsonArray ka = new JsonArray();
                ka.add(round(k.t));
                ka.add(round(k.x));
                ka.add(round(k.y));
                ka.add(round(k.z));
                ka.add(k.ease.name().toLowerCase(Locale.ROOT));
                keys.add(ka);
            }
            to.add("keys", keys);
            tracks.add(to);
        }
        o.add("tracks", tracks);
        return o;
    }

    private static String writeCell(int[] cell) {
        ByteBuffer b = ByteBuffer.allocate(cell.length * 4);
        for (int v : cell) b.putInt(v);
        return Base64.getEncoder().encodeToString(b.array());
    }

    private static JsonObject writeStats(Stats s) {
        JsonObject o = new JsonObject();
        o.addProperty("health", round(s.health));
        o.addProperty("speed", round(s.speed));
        o.addProperty("damage", round(s.damage));
        o.addProperty("armor", round(s.armor));
        o.addProperty("knockbackResist", round(s.knockbackResist));
        o.addProperty("width", round(s.width));
        o.addProperty("height", round(s.height));
        o.addProperty("xp", s.xp);
        o.addProperty("mood", s.mood.name().toLowerCase(Locale.ROOT));
        o.addProperty("flying", s.flying);
        o.addProperty("fireImmune", s.fireImmune);
        o.addProperty("swims", s.swims);
        o.add("targets", strings(s.targets));
        JsonArray drops = new JsonArray();
        for (Stats.Drop d : s.drops) {
            JsonObject od = new JsonObject();
            od.addProperty("item", d.item);
            od.addProperty("min", d.min);
            od.addProperty("max", d.max);
            od.addProperty("chance", round(d.chance));
            drops.add(od);
        }
        o.add("drops", drops);
        JsonObject sounds = new JsonObject();
        if (!s.ambientSound.isEmpty()) sounds.addProperty("ambient", s.ambientSound);
        if (!s.hurtSound.isEmpty()) sounds.addProperty("hurt", s.hurtSound);
        if (!s.deathSound.isEmpty()) sounds.addProperty("death", s.deathSound);
        if (!s.attackSound.isEmpty()) sounds.addProperty("attack", s.attackSound);
        if (!s.stepSound.isEmpty()) sounds.addProperty("step", s.stepSound);
        o.add("sounds", sounds);
        o.addProperty("attack", s.attack.name().toLowerCase(Locale.ROOT));
        o.addProperty("attackRate", round(s.attackRate));
        o.addProperty("leap", s.leap);
        o.addProperty("teleport", s.teleport);
        o.addProperty("summon", s.summon);
        o.addProperty("abilityCooldown", round(s.abilityCooldown));
        o.addProperty("summonMob", s.summonMob);
        o.addProperty("summonCount", s.summonCount);
        o.addProperty("spawnNaturally", s.spawnNaturally);
        o.add("spawnBiomes", strings(s.spawnBiomes));
        o.addProperty("spawnRate", s.spawnRate);
        o.addProperty("spawnGroup", s.spawnGroup);
        o.addProperty("spawnMaxNearby", s.spawnMaxNearby);
        o.addProperty("spawnNightOnly", s.spawnNightOnly);
        o.addProperty("eggBase", String.format(Locale.ROOT, "%08X", s.eggBase));
        o.addProperty("eggSpots", String.format(Locale.ROOT, "%08X", s.eggSpots));
        return o;
    }

    private static boolean nonZero(float[] v) {
        return v[0] != 0f || v[1] != 0f || v[2] != 0f;
    }

    private static JsonArray floats(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(round(f));
        return a;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }

    // three decimals is plenty and keeps files small
    private static Number round(float f) {
        float r = Math.round(f * 1000f) / 1000f;
        if (r == (int) r) return (int) r;
        return r;
    }

    // ---------- reading ----------

    public static Design read(String text) throws BadDesign {
        if (text == null || text.length() > Limits.MAX_JSON_BYTES) throw new BadDesign("too big");
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException | StackOverflowError e) {
            throw new BadDesign("not valid JSON");
        }
        if (!root.isJsonObject()) throw new BadDesign("not a design");
        JsonObject o = root.getAsJsonObject();
        if (!o.has("parts")) throw new BadDesign("not a design: it has no parts list");
        try {
            Design d = new Design();
            d.id = str(o, "id", "");
            d.name = str(o, "name", "New Mob");
            d.author = str(o, "author", "");
            d.owner = str(o, "owner", "");
            for (JsonElement e : arr(o, "parts")) if (e.isJsonObject()) d.parts.add(readPart(e.getAsJsonObject()));
            for (JsonElement e : arr(o, "clips")) if (e.isJsonObject()) d.clips.add(readClip(e.getAsJsonObject()));
            for (JsonElement e : arr(o, "paints")) d.paints.add(readCell(e));
            if (o.has("stats") && o.get("stats").isJsonObject()) d.stats = readStats(o.getAsJsonObject("stats"));
            return d;
        } catch (RuntimeException e) {
            throw new BadDesign("the design is damaged");
        }
    }

    private static Part readPart(JsonObject o) {
        Part p = new Part();
        p.id = str(o, "id", "");
        p.name = str(o, "name", "Part");
        p.parent = str(o, "parent", "");
        p.kind = Part.Kind.of(str(o, "kind", "cube"));
        p.pos = vec(o, "pos", 0f);
        p.rot = vec(o, "rot", 0f);
        p.scale = vec(o, "scale", 1f);
        p.pivot = vec(o, "pivot", 0f);
        p.glow = bool(o, "glow", false);
        p.head = bool(o, "head", false);
        p.hidden = bool(o, "hidden", false);
        p.size = o.has("size") ? vec(o, "size", 8f) : p.size;
        p.color = colour(o, "color", 0xFFFFFFFF);
        p.texture = str(o, "texture", "");
        p.paint = integer(o, "paint", -1);
        JsonArray faces = arr(o, "facePaint");
        if (faces.size() == Part.FACES) {
            p.facePaint = new int[Part.FACES];
            for (int i = 0; i < Part.FACES; i++) p.facePaint[i] = faces.get(i).isJsonPrimitive() ? faces.get(i).getAsInt() : -1;
        }
        p.block = str(o, "block", "");
        p.mob = str(o, "mob", "");
        p.bone = str(o, "bone", "");
        p.solo = bool(o, "solo", false);
        p.mobTexture = str(o, "mobTexture", "");
        p.item = str(o, "item", "");
        p.display = str(o, "display", "third_person_right_hand");
        return p;
    }

    private static Clip readClip(JsonObject o) {
        Clip c = new Clip();
        c.name = str(o, "name", "clip");
        c.length = num(o, "length", 1f);
        c.speed = num(o, "speed", 1f);
        c.loop = Clip.Loop.of(str(o, "loop", "loop"));
        for (JsonElement e : arr(o, "tracks")) {
            if (!e.isJsonObject()) continue;
            JsonObject to = e.getAsJsonObject();
            Track t = new Track(str(to, "part", ""), Channel.of(str(to, "channel", "rot")));
            for (JsonElement ke : arr(to, "keys")) {
                if (!ke.isJsonArray()) continue;
                JsonArray a = ke.getAsJsonArray();
                if (a.size() < 4) continue;
                t.keys.add(new Key(fin(a.get(0)), fin(a.get(1)), fin(a.get(2)), fin(a.get(3)), a.size() > 4 && a.get(4).isJsonPrimitive() ? Ease.of(a.get(4).getAsString()) : Ease.SMOOTH));
            }
            c.tracks.add(t);
        }
        return c;
    }

    private static int[] readCell(JsonElement e) {
        int[] cell = new int[256];
        if (!e.isJsonPrimitive()) return cell;
        try {
            byte[] raw = Base64.getDecoder().decode(e.getAsString());
            if (raw.length != 1024) return cell;
            ByteBuffer b = ByteBuffer.wrap(raw);
            for (int i = 0; i < 256; i++) cell[i] = b.getInt();
        } catch (IllegalArgumentException ignored) {
            // a damaged painting stays blank
        }
        return cell;
    }

    private static Stats readStats(JsonObject o) {
        Stats s = new Stats();
        s.health = num(o, "health", s.health);
        s.speed = num(o, "speed", s.speed);
        s.damage = num(o, "damage", s.damage);
        s.armor = num(o, "armor", s.armor);
        s.knockbackResist = num(o, "knockbackResist", s.knockbackResist);
        s.width = num(o, "width", s.width);
        s.height = num(o, "height", s.height);
        s.xp = integer(o, "xp", s.xp);
        s.mood = Stats.Mood.of(str(o, "mood", "neutral"));
        s.flying = bool(o, "flying", false);
        s.fireImmune = bool(o, "fireImmune", false);
        s.swims = bool(o, "swims", true);
        if (o.has("targets")) {
            s.targets.clear();
            for (JsonElement e : arr(o, "targets")) if (e.isJsonPrimitive()) s.targets.add(e.getAsString());
        }
        for (JsonElement e : arr(o, "drops")) {
            if (!e.isJsonObject()) continue;
            JsonObject od = e.getAsJsonObject();
            Stats.Drop d = new Stats.Drop();
            d.item = str(od, "item", d.item);
            d.min = integer(od, "min", d.min);
            d.max = integer(od, "max", d.max);
            d.chance = num(od, "chance", d.chance);
            s.drops.add(d);
        }
        if (o.has("sounds") && o.get("sounds").isJsonObject()) {
            JsonObject so = o.getAsJsonObject("sounds");
            s.ambientSound = str(so, "ambient", "");
            s.hurtSound = str(so, "hurt", "");
            s.deathSound = str(so, "death", "");
            s.attackSound = str(so, "attack", "");
            s.stepSound = str(so, "step", "");
        }
        s.attack = Stats.Attack.of(str(o, "attack", "melee"));
        s.attackRate = num(o, "attackRate", s.attackRate);
        s.leap = bool(o, "leap", false);
        s.teleport = bool(o, "teleport", false);
        s.summon = bool(o, "summon", false);
        s.abilityCooldown = num(o, "abilityCooldown", s.abilityCooldown);
        s.summonMob = str(o, "summonMob", s.summonMob);
        s.summonCount = integer(o, "summonCount", s.summonCount);
        s.spawnNaturally = bool(o, "spawnNaturally", false);
        for (JsonElement e : arr(o, "spawnBiomes")) if (e.isJsonPrimitive()) s.spawnBiomes.add(e.getAsString());
        s.spawnRate = integer(o, "spawnRate", s.spawnRate);
        s.spawnGroup = integer(o, "spawnGroup", s.spawnGroup);
        s.spawnMaxNearby = integer(o, "spawnMaxNearby", s.spawnMaxNearby);
        s.spawnNightOnly = bool(o, "spawnNightOnly", false);
        s.eggBase = colour(o, "eggBase", s.eggBase);
        s.eggSpots = colour(o, "eggSpots", s.eggSpots);
        return s;
    }

    private static final JsonArray NONE = new JsonArray();

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : NONE;
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    private static float num(JsonObject o, String key, float fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        float f = e.getAsFloat();
        return Float.isFinite(f) ? f : fallback;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        return e.getAsInt();
    }

    private static float fin(JsonElement e) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return 0f;
        float f = e.getAsFloat();
        return Float.isFinite(f) ? f : 0f;
    }

    private static float[] vec(JsonObject o, String key, float fallback) {
        float[] v = {fallback, fallback, fallback};
        JsonArray a = arr(o, key);
        if (a.size() == 3) for (int i = 0; i < 3; i++) if (a.get(i).isJsonPrimitive() && a.get(i).getAsJsonPrimitive().isNumber()) v[i] = fin(a.get(i));
        return v;
    }

    private static int colour(JsonObject o, String key, int fallback) {
        String s = str(o, key, "");
        if (s.isEmpty()) return fallback;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
