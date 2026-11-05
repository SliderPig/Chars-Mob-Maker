package com.example.charmobmaker.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Everything you can do to the tree of parts: add, copy, delete, move under another part. No game code, so it can all be tested.
public final class PartTree {
    public record Row(Part part, int depth, boolean hasChildren) {}

    private PartTree() {}

    public static List<Row> rows(Design d, Set<String> collapsed) {
        Map<String, List<Part>> kids = kids(d);
        List<Row> out = new ArrayList<>();
        addRows(kids, "", 0, collapsed, out);
        return out;
    }

    private static void addRows(Map<String, List<Part>> kids, String id, int depth, Set<String> collapsed, List<Row> out) {
        List<Part> list = kids.get(id);
        if (list == null) return;
        for (Part p : list) {
            boolean has = kids.containsKey(p.id);
            out.add(new Row(p, depth, has));
            if (has && (collapsed == null || !collapsed.contains(p.id))) addRows(kids, p.id, depth + 1, collapsed, out);
        }
    }

    private static Map<String, List<Part>> kids(Design d) {
        Map<String, List<Part>> kids = new HashMap<>();
        for (Part p : d.parts) kids.computeIfAbsent(p.parent, k -> new ArrayList<>()).add(p);
        return kids;
    }

    public static String newId(Design d) {
        Set<String> used = new HashSet<>();
        for (Part p : d.parts) used.add(p.id);
        for (int i = 1; ; i++) if (!used.contains("p" + i)) return "p" + i;
    }

    // Null when the mob is already full.
    public static Part add(Design d, Part p, String parent) {
        if (d.parts.size() >= Limits.MAX_PARTS) return null;
        p.id = newId(d);
        p.parent = parent == null ? "" : parent;
        d.parts.add(p);
        return p;
    }

    public static List<Part> subtree(Design d, String id) {
        List<Part> out = new ArrayList<>();
        Part root = d.part(id);
        if (root == null) return out;
        out.add(root);
        for (int i = 0; i < out.size(); i++) {
            String at = out.get(i).id;
            for (Part p : d.parts) if (p.parent.equals(at)) out.add(p);
        }
        return out;
    }

    public static void remove(Design d, String id) {
        Set<String> gone = new HashSet<>();
        for (Part p : subtree(d, id)) gone.add(p.id);
        d.parts.removeIf(p -> gone.contains(p.id));
        for (Clip c : d.clips) c.tracks.removeIf(t -> gone.contains(t.part));
    }

    // A copy of a part and everything hanging off it, keys included. Null when it wouldn't fit.
    public static Part duplicate(Design d, String id) {
        List<Part> tree = subtree(d, id);
        if (tree.isEmpty() || d.parts.size() + tree.size() > Limits.MAX_PARTS) return null;
        Map<String, String> ids = new HashMap<>();
        Part first = null;
        for (Part src : tree) {
            Part c = src.copy();
            c.id = newId(d);
            ids.put(src.id, c.id);
            c.parent = ids.getOrDefault(src.parent, src.parent);
            d.parts.add(c);
            if (first == null) first = c;
        }
        for (Clip clip : d.clips) {
            List<Track> extra = new ArrayList<>();
            for (Track t : clip.tracks) {
                String to = ids.get(t.part);
                if (to != null) {
                    Track c = t.copy();
                    c.part = to;
                    extra.add(c);
                }
            }
            clip.tracks.addAll(extra);
        }
        return first;
    }

    // Copy on the other side of the mob: left arm to right arm, keys mirrored too.
    public static Part mirror(Design d, String id) {
        Part copy = duplicate(d, id);
        if (copy == null) return null;
        for (Part p : subtree(d, copy.id)) {
            boolean top = p == copy;
            p.name = DesignRules.cleanName(flipName(p.name), 32, p.name);
            if (top || !p.parent.isEmpty()) {
                p.pos[0] = -p.pos[0];
                p.pivot[0] = -p.pivot[0];
                p.rot[1] = -p.rot[1];
                p.rot[2] = -p.rot[2];
            }
            for (Clip c : d.clips) {
                for (Track t : c.tracks) {
                    if (t.part.equals(p.id)) ClipTools.flipKeys(t);
                }
            }
        }
        return copy;
    }

    public static String flipName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("left")) return swapWord(name, "left", "right");
        if (lower.contains("right")) return swapWord(name, "right", "left");
        return name;
    }

    private static String swapWord(String name, String from, String to) {
        int at = name.toLowerCase(Locale.ROOT).indexOf(from);
        String piece = name.substring(at, at + from.length());
        String swapped = Character.isUpperCase(piece.charAt(0)) ? Character.toUpperCase(to.charAt(0)) + to.substring(1) : to;
        return name.substring(0, at) + swapped + name.substring(at + from.length());
    }

    public static boolean canReparent(Design d, String id, String newParent) {
        if (id.equals(newParent) || d.part(id) == null) return false;
        if (newParent == null || newParent.isEmpty()) return true;
        if (d.part(newParent) == null) return false;
        for (Part p : subtree(d, id)) if (p.id.equals(newParent)) return false;
        Part at = d.part(newParent);
        int depth = 1;
        while (at != null && !at.parent.isEmpty()) {
            at = d.part(at.parent);
            depth++;
        }
        return depth + subtreeDepth(d, id) < Limits.MAX_DEPTH;
    }

    private static int subtreeDepth(Design d, String id) {
        int best = 1;
        for (Part p : d.parts) if (p.parent.equals(id)) best = Math.max(best, 1 + subtreeDepth(d, p.id));
        return best;
    }

    // Moves a part under a new parent. It stays exactly where it is on screen: its numbers are worked out again for the new parent.
    public static boolean reparent(Design d, String id, String newParent) {
        String target = newParent == null ? "" : newParent;
        if (!canReparent(d, id, target)) return false;
        Part p = d.part(id);
        if (p.parent.equals(target)) return true;
        Rig rig = Rig.of(d);
        Matrix4f[] world = rig.newMatrices();
        rig.pose(null, 0f, 0f, world);
        Matrix4f mine = world[rig.indexOf(id)];
        int parentIndex = target.isEmpty() ? -1 : rig.indexOf(target);
        Matrix4f local = parentIndex < 0 ? new Matrix4f(mine) : new Matrix4f(world[parentIndex]).invert().mul(mine);
        Vector3f scale = new Vector3f();
        local.getScale(scale);
        Quaternionf q = new Quaternionf();
        local.getUnnormalizedRotation(q);
        q.normalize();
        Vector3f euler = eulerDegrees(q);
        Vector3f t = new Vector3f();
        local.getTranslation(t);
        // local = T(pos + pivot) R S T(-pivot), so pos comes from where the pivot point ends up
        Vector3f pivot = new Vector3f(p.pivot[0], p.pivot[1], p.pivot[2]);
        Vector3f turned = new Vector3f(pivot).mul(scale).rotate(q);
        p.pos[0] = t.x + turned.x - pivot.x;
        p.pos[1] = t.y + turned.y - pivot.y;
        p.pos[2] = t.z + turned.z - pivot.z;
        p.rot[0] = euler.x;
        p.rot[1] = euler.y;
        p.rot[2] = euler.z;
        p.scale[0] = keepSign(p.scale[0], scale.x);
        p.scale[1] = keepSign(p.scale[1], scale.y);
        p.scale[2] = keepSign(p.scale[2], scale.z);
        p.parent = target;
        return true;
    }

    // Turn numbers for a rotation, in degrees. Goes through a matrix because the quaternion's own version gets X slightly wrong.
    public static Vector3f eulerDegrees(Quaternionf q) {
        Vector3f e = new Matrix4f().rotation(q).getEulerAnglesZYX(new Vector3f());
        return e.mul((float) (180.0 / Math.PI));
    }

    private static float keepSign(float old, float size) {
        return Math.abs(size - Math.abs(old)) < 1e-4f ? old : size;
    }
}
