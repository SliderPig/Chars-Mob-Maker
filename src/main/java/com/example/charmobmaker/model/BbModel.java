package com.example.charmobmaker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

// Reads the simple side of a Blockbench .bbmodel file: boxes, folders and keyframes. Textures aren't read, boxes get a colour from their Blockbench colour marker.
// Blockbench mobs face -Z, ours face +Z, so everything is turned half round on the way in.
public final class BbModel {
    private static final int[] MARKERS = {0xFF6DD6FF, 0xFFFFC56D, 0xFFFF6D6D, 0xFFE27DFF, 0xFF7DFF9B, 0xFF7DA7FF, 0xFFFF8FC7, 0xFFC4C4C4};

    private BbModel() {}

    public static Design read(String text, String fallbackName) throws DesignJson.BadDesign {
        if (text == null || text.length() > Limits.MAX_JSON_BYTES * 4) throw new DesignJson.BadDesign("too big");
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException | StackOverflowError e) {
            throw new DesignJson.BadDesign("not a Blockbench file");
        }
        if (!root.has("elements") || !root.has("outliner")) throw new DesignJson.BadDesign("not a Blockbench model");
        Design d = new Design();
        d.name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : fallbackName;
        try {
            Map<String, JsonObject> elements = new HashMap<>();
            for (JsonElement e : root.getAsJsonArray("elements")) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("uuid")) elements.put(o.get("uuid").getAsString(), o);
            }
            Map<String, String> partOfUuid = new HashMap<>();
            for (JsonElement e : root.getAsJsonArray("outliner")) walk(d, e, "", new float[3], elements, partOfUuid);
            if (root.has("animations") && root.get("animations").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("animations")) readClip(d, e.getAsJsonObject(), partOfUuid);
            }
        } catch (RuntimeException e) {
            throw new DesignJson.BadDesign("the Blockbench file has something unexpected in it");
        }
        DesignRules.tidy(d);
        return d;
    }

    private static void walk(Design d, JsonElement e, String parent, float[] parentOrigin, Map<String, JsonObject> elements, Map<String, String> partOfUuid) {
        if (d.parts.size() >= Limits.MAX_PARTS) return;
        if (e.isJsonPrimitive()) {
            JsonObject el = elements.get(e.getAsString());
            if (el != null) cube(d, el, parent, parentOrigin);
            return;
        }
        JsonObject group = e.getAsJsonObject();
        float[] origin = group.has("origin") ? vec(group.getAsJsonArray("origin")) : new float[3];
        Part p = new Part();
        p.name = group.has("name") ? group.get("name").getAsString() : "Group";
        p.size = new float[3];
        p.pos = turned(origin[0] - parentOrigin[0], origin[1] - parentOrigin[1], origin[2] - parentOrigin[2]);
        if (group.has("rotation")) p.rot = turnedAngles(vec(group.getAsJsonArray("rotation")));
        PartTree.add(d, p, parent);
        if (group.has("uuid")) partOfUuid.put(group.get("uuid").getAsString(), p.id);
        if (group.has("children") && group.get("children").isJsonArray()) {
            for (JsonElement kid : group.getAsJsonArray("children")) walk(d, kid, p.id, origin, elements, partOfUuid);
        }
    }

    private static void cube(Design d, JsonObject el, String parent, float[] parentOrigin) {
        if (!el.has("from") || !el.has("to")) return;
        float[] from = vec(el.getAsJsonArray("from")), to = vec(el.getAsJsonArray("to"));
        float[] origin = el.has("origin") ? vec(el.getAsJsonArray("origin")) : new float[] {(from[0] + to[0]) / 2f, (from[1] + to[1]) / 2f, (from[2] + to[2]) / 2f};
        float cx = (from[0] + to[0]) / 2f, cy = (from[1] + to[1]) / 2f, cz = (from[2] + to[2]) / 2f;
        Part p = new Part();
        p.name = el.has("name") ? el.get("name").getAsString() : "Box";
        p.size = new float[] {Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])};
        p.pos = turned(cx - parentOrigin[0], cy - parentOrigin[1], cz - parentOrigin[2]);
        p.pivot = turned(origin[0] - cx, origin[1] - cy, origin[2] - cz);
        if (el.has("rotation")) p.rot = turnedAngles(vec(el.getAsJsonArray("rotation")));
        int marker = el.has("color") && el.get("color").isJsonPrimitive() ? el.get("color").getAsInt() : 0;
        p.color = MARKERS[Math.floorMod(marker, MARKERS.length)];
        PartTree.add(d, p, parent);
    }

    private static void readClip(Design d, JsonObject a, Map<String, String> partOfUuid) {
        Clip c = new Clip();
        c.name = DesignRules.cleanClipName(a.has("name") ? a.get("name").getAsString().replaceAll("^animation\\.[a-z0-9_]*\\.", "") : "clip");
        c.length = a.has("length") ? Math.max(Limits.MIN_CLIP, a.get("length").getAsFloat()) : 1f;
        String loop = a.has("loop") && a.get("loop").isJsonPrimitive() ? a.get("loop").getAsString() : "loop";
        c.loop = loop.equals("once") ? Clip.Loop.ONCE : loop.equals("hold") ? Clip.Loop.HOLD : Clip.Loop.LOOP;
        if (a.has("animators") && a.get("animators").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : a.getAsJsonObject("animators").entrySet()) {
                String part = partOfUuid.get(entry.getKey());
                JsonObject animator = entry.getValue().getAsJsonObject();
                if (part == null || !animator.has("keyframes")) continue;
                for (JsonElement ke : animator.getAsJsonArray("keyframes")) keyframe(c, part, ke.getAsJsonObject());
            }
        }
        d.clips.add(c);
    }

    private static void keyframe(Clip c, String part, JsonObject k) {
        String ch = k.has("channel") ? k.get("channel").getAsString() : "rotation";
        Channel channel = ch.equals("position") ? Channel.POS : ch.equals("scale") ? Channel.SCALE : Channel.ROT;
        if (!k.has("data_points") || k.getAsJsonArray("data_points").isEmpty()) return;
        JsonObject p = k.getAsJsonArray("data_points").get(0).getAsJsonObject();
        float x = point(p, "x", channel.rest()), y = point(p, "y", channel.rest()), z = point(p, "z", channel.rest());
        String how = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
        Ease ease = how.equals("step") ? Ease.STEP : how.equals("catmullrom") || how.equals("bezier") ? Ease.SMOOTH : Ease.LINEAR;
        float t = k.has("time") ? k.get("time").getAsFloat() : 0f;
        if (channel == Channel.ROT) {
            float[] a = turnedAngles(new float[] {x, y, z});
            ClipTools.setKey(c, part, channel, t, a[0], a[1], a[2], ease);
        } else if (channel == Channel.POS) {
            float[] a = turned(x, y, z);
            ClipTools.setKey(c, part, channel, t, a[0], a[1], a[2], ease);
        } else {
            ClipTools.setKey(c, part, channel, t, x, y, z, ease);
        }
    }

    private static float point(JsonObject p, String key, float fallback) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive()) return fallback;
        try {
            return Float.parseFloat(p.get(key).getAsString().trim());
        } catch (NumberFormatException e) {
            return fallback; // a formula, can't use those here
        }
    }

    private static float[] turned(float x, float y, float z) {
        return new float[] {-x, y, -z};
    }

    private static float[] turnedAngles(float[] a) {
        return new float[] {-a[0], a[1], -a[2]};
    }

    private static float[] vec(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
