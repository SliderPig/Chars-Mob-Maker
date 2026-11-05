package com.example.charmobmaker.model;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A design made ready to pose: parts in an order where every parent comes before its children, so one pass works out all the matrices.
public final class Rig {
    public final Design design;
    public final int size;
    public final Part[] parts;
    public final int[] parent;
    public final int[] depth;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, RigClip> clips = new HashMap<>();
    private final float deg = (float) (Math.PI / 180.0);
    private final Matrix4f local = new Matrix4f();

    // A clip with its tracks already pointing at part numbers.
    public static final class RigClip {
        public final Clip clip;
        final int[] partOf;
        final Track[] tracks;

        RigClip(Clip clip, Map<String, Integer> index) {
            this.clip = clip;
            List<Track> ok = new ArrayList<>();
            for (Track t : clip.tracks) if (index.containsKey(t.part) && !t.keys.isEmpty()) ok.add(t);
            tracks = ok.toArray(new Track[0]);
            partOf = new int[tracks.length];
            for (int i = 0; i < tracks.length; i++) partOf[i] = index.get(tracks[i].part);
        }

        // Clears out and fills it with this clip at this time (already wrapped by the caller).
        public void sample(float time, PoseOut out) {
            out.clear();
            boolean wrap = clip.loop == Clip.Loop.LOOP;
            for (int i = 0; i < tracks.length; i++) {
                Track t = tracks[i];
                int p = partOf[i];
                switch (t.channel) {
                    case ROT -> {
                        t.sample(time, clip.length, wrap, out.rot, p * 3);
                        out.rotSet[p] = true;
                    }
                    case POS -> {
                        t.sample(time, clip.length, wrap, out.pos, p * 3);
                        out.posSet[p] = true;
                    }
                    case SCALE -> {
                        t.sample(time, clip.length, wrap, out.scale, p * 3);
                        out.scaleSet[p] = true;
                    }
                }
            }
        }
    }

    private Rig(Design d) {
        design = d;
        List<Part> order = new ArrayList<>();
        List<Integer> parents = new ArrayList<>(), depths = new ArrayList<>();
        Map<String, List<Part>> kids = new HashMap<>();
        for (Part p : d.parts) kids.computeIfAbsent(p.parent, k -> new ArrayList<>()).add(p);
        walk(kids, "", -1, 0, order, parents, depths);
        size = order.size();
        parts = order.toArray(new Part[0]);
        parent = new int[size];
        depth = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = parents.get(i);
            depth[i] = depths.get(i);
            index.put(parts[i].id, i);
        }
        for (Clip c : d.clips) clips.put(c.name, new RigClip(c, index));
    }

    private static void walk(Map<String, List<Part>> kids, String id, int parentIndex, int depth, List<Part> order, List<Integer> parents, List<Integer> depths) {
        List<Part> list = kids.get(id);
        if (list == null) return;
        for (Part p : list) {
            int me = order.size();
            order.add(p);
            parents.add(parentIndex);
            depths.add(depth);
            if (!p.id.equals(id)) walk(kids, p.id, me, depth + 1, order, parents, depths);
        }
    }

    public static Rig of(Design d) {
        return new Rig(d);
    }

    public int indexOf(String partId) {
        Integer i = index.get(partId);
        return i == null ? -1 : i;
    }

    public RigClip clip(String name) {
        return clips.get(name);
    }

    public Matrix4f[] newMatrices() {
        Matrix4f[] m = new Matrix4f[size];
        for (int i = 0; i < size; i++) m[i] = new Matrix4f();
        return m;
    }

    // Works out where every part is, in pixels from the mob's feet. headYaw and headPitch are degrees for parts that look at things.
    public void pose(PoseOut pose, float headYaw, float headPitch, Matrix4f[] out) {
        for (int i = 0; i < size; i++) {
            localMatrix(i, pose, headYaw, headPitch, local);
            if (parent[i] < 0) out[i].set(local);
            else out[parent[i]].mul(local, out[i]);
        }
    }

    public void localMatrix(int i, PoseOut pose, float headYaw, float headPitch, Matrix4f m) {
        Part p = parts[i];
        int j = i * 3;
        float rx = p.rot[0], ry = p.rot[1], rz = p.rot[2];
        float px = p.pos[0], py = p.pos[1], pz = p.pos[2];
        float sx = p.scale[0], sy = p.scale[1], sz = p.scale[2];
        if (pose != null) {
            rx += pose.rot[j];
            ry += pose.rot[j + 1];
            rz += pose.rot[j + 2];
            px += pose.pos[j];
            py += pose.pos[j + 1];
            pz += pose.pos[j + 2];
            sx *= pose.scale[j];
            sy *= pose.scale[j + 1];
            sz *= pose.scale[j + 2];
        }
        if (p.head) {
            rx += headPitch;
            ry -= headYaw;
        }
        m.translation(px + p.pivot[0], py + p.pivot[1], pz + p.pivot[2])
                .rotateZYX(rz * deg, ry * deg, rx * deg)
                .scale(sx, sy, sz)
                .translate(-p.pivot[0], -p.pivot[1], -p.pivot[2]);
    }

    // Roughly how far the mob reaches from its feet, in pixels, so it's not culled too soon.
    public float reach() {
        Matrix4f[] m = newMatrices();
        pose(null, 0f, 0f, m);
        float best = 16f;
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (int i = 0; i < size; i++) {
            m[i].getTranslation(v);
            Part p = parts[i];
            float own = switch (p.kind) {
                case CUBE -> (float) Math.sqrt(p.size[0] * p.size[0] + p.size[1] * p.size[1] + p.size[2] * p.size[2]) * 0.5f;
                case BLOCK -> 14f;
                case MOB -> 32f;
                case ITEM -> 16f;
            };
            float scale = Math.max(Math.abs(p.scale[0]), Math.max(Math.abs(p.scale[1]), Math.abs(p.scale[2])));
            best = Math.max(best, v.length() + own * scale);
        }
        return best;
    }
}
