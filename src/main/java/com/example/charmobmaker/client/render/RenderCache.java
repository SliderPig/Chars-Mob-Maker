package com.example.charmobmaker.client.render;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Part;

import java.util.HashMap;
import java.util.Map;

// One Compiled per design the PC knows, made when first needed. Thrown away when the design changes or textures reload.
public final class RenderCache {
    private static final Map<String, Compiled> CACHE = new HashMap<>();
    private static final Map<String, Compiled> LEFTOVERS = new HashMap<>();
    private static Compiled placeholder;

    private RenderCache() {}

    public static Compiled of(String id) {
        Compiled c = CACHE.get(id);
        if (c != null) return c;
        Design d = Designs.CLIENT.get(id);
        if (d == null) return null;
        c = new Compiled(d);
        CACHE.put(id, c);
        return c;
    }

    // Just the bits of a placed block that couldn't go in the chunk.
    public static Compiled leftovers(String id) {
        Compiled c = LEFTOVERS.get(id);
        if (c != null) return c;
        Design d = Designs.CLIENT.get(id);
        if (d == null) return null;
        c = new Compiled(d, true);
        LEFTOVERS.put(id, c);
        return c;
    }

    public static void forget(String id) {
        Compiled c = CACHE.remove(id);
        if (c != null) c.release();
        c = LEFTOVERS.remove(id);
        if (c != null) c.release();
    }

    public static void clear() {
        for (Compiled c : CACHE.values()) c.release();
        CACHE.clear();
        for (Compiled c : LEFTOVERS.values()) c.release();
        LEFTOVERS.clear();
        if (placeholder != null) placeholder.release();
        placeholder = null;
    }

    // Shown for the moment before a design has arrived: a little pink box with ears.
    public static Compiled placeholder() {
        if (placeholder == null) {
            Design d = new Design();
            d.parts.add(box("a", 0, 6, 0, 10, 12, 10));
            d.parts.add(box("b", -3, 13.5f, 0, 2.5f, 3, 2.5f));
            d.parts.add(box("c", 3, 13.5f, 0, 2.5f, 3, 2.5f));
            placeholder = new Compiled(d);
        }
        return placeholder;
    }

    private static Part box(String id, float x, float y, float z, float sx, float sy, float sz) {
        Part p = new Part();
        p.id = id;
        p.pos = new float[] {x, y, z};
        p.size = new float[] {sx, sy, sz};
        p.color = 0xFFFF8FC7;
        return p;
    }
}
