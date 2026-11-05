package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Editing keys: set, move, copy and mirror them, plus a quick walk or idle made from the names of the parts.
public final class ClipTools {
    public static final float SNAP = 0.05f;

    private ClipTools() {}

    public static float snap(float time) {
        return Math.round(time / SNAP) * SNAP;
    }

    // Sets the key at this time, or makes one. Keeps the ease of a key that was already there.
    public static Key setKey(Clip clip, String part, Channel channel, float t, float x, float y, float z, Ease ease) {
        Track track = clip.track(part, channel, true);
        float time = Math.max(0f, Math.min(clip.length, t));
        Key k = track.at(time);
        if (k == null) {
            if (track.keys.size() >= Limits.MAX_KEYS_PER_TRACK) return null;
            k = new Key(time, x, y, z, ease == null ? Ease.SMOOTH : ease);
            track.keys.add(k);
            track.sort();
        } else {
            k.x = x;
            k.y = y;
            k.z = z;
            if (ease != null) k.ease = ease;
        }
        return k;
    }

    public static void removeKey(Clip clip, Track track, Key key) {
        track.keys.remove(key);
        if (track.keys.isEmpty()) clip.tracks.remove(track);
    }

    public static void moveKey(Clip clip, Track track, Key key, float newTime) {
        float time = Math.max(0f, Math.min(clip.length, newTime));
        for (Key other : new ArrayList<>(track.keys)) {
            if (other != key && Math.abs(other.t - time) < 0.002f) track.keys.remove(other);
        }
        key.t = time;
        track.sort();
    }

    // Changing the length keeps keys where they are, ones past the new end get pulled in to the end.
    public static void setLength(Clip clip, float length) {
        clip.length = Math.max(Limits.MIN_CLIP, Math.min(Limits.MAX_CLIP, length));
        for (Track t : clip.tracks) {
            for (Key k : t.keys) if (k.t > clip.length) k.t = clip.length;
            t.sort();
            for (int i = t.keys.size() - 1; i > 0; i--) if (t.keys.get(i).t - t.keys.get(i - 1).t < 0.001f) t.keys.remove(i - 1);
        }
    }

    // Keys of a track as seen in a mirror across the mob.
    public static void flipKeys(Track t) {
        for (Key k : t.keys) {
            if (t.channel == Channel.ROT) {
                k.y = -k.y;
                k.z = -k.z;
            } else if (t.channel == Channel.POS) {
                k.x = -k.x;
            }
        }
    }

    // The clipboard for keys: everything one part does in a clip.
    public record Board(List<Track> tracks, float from) {}

    public static Board copyPart(Clip clip, String part, float from) {
        List<Track> out = new ArrayList<>();
        for (Track t : clip.tracks) if (t.part.equals(part)) out.add(t.copy());
        return new Board(out, from);
    }

    // Pastes with the first copied moment landing at the given time.
    public static void paste(Clip clip, String part, Board board, float at) {
        for (Track src : board.tracks()) {
            for (Key k : src.keys) {
                float t = k.t - board.from() + at;
                if (t < -0.001f || t > clip.length + 0.001f) continue;
                setKey(clip, part, src.channel, t, k.x, k.y, k.z, k.ease);
            }
        }
    }

    // left leg takes on what right leg does, half a cycle later when asked: the usual way to make a walk.
    public static void mirrorOnto(Clip clip, String from, String to, float timeShift) {
        if (from.equals(to)) return;
        clip.tracks.removeIf(t -> t.part.equals(to));
        List<Track> made = new ArrayList<>();
        for (Track t : clip.tracks) {
            if (!t.part.equals(from)) continue;
            Track c = t.copy();
            c.part = to;
            flipKeys(c);
            if (timeShift != 0f) {
                for (Key k : c.keys) {
                    float shifted = (k.t + timeShift) % clip.length;
                    k.t = shifted < 0f ? shifted + clip.length : shifted;
                }
                c.sort();
            }
            made.add(c);
        }
        clip.tracks.addAll(made);
    }

    // The part on the other side of the mob going by names, or null.
    public static Part opposite(Design d, Part p) {
        String flipped = PartTree.flipName(p.name);
        if (flipped.equals(p.name)) return null;
        for (Part o : d.parts) if (o != p && o.name.equalsIgnoreCase(flipped)) return o;
        return null;
    }

    // The whole clip played the other way round, left for right.
    public static void mirrorWhole(Design d, Clip clip) {
        List<Track> swapped = new ArrayList<>();
        for (Track t : clip.tracks) {
            Part p = d.part(t.part);
            Part other = p == null ? null : opposite(d, p);
            Track c = t.copy();
            if (other != null) c.part = other.id;
            flipKeys(c);
            swapped.add(c);
        }
        clip.tracks.clear();
        clip.tracks.addAll(swapped);
    }

    private static boolean has(Part p, String word) {
        return p.name.toLowerCase(Locale.ROOT).contains(word);
    }

    // Makes a walk from what the parts are called: anything named leg or arm swings, left and right in turns, diagonal pairs for four legs.
    public static Clip makeWalk(Design d) {
        Clip clip = new Clip("walk", 0.8f, Clip.Loop.LOOP);
        List<Part> legs = new ArrayList<>(), arms = new ArrayList<>();
        for (Part p : d.parts) {
            if (has(p, "leg") || has(p, "foot") || has(p, "paw")) legs.add(p);
            else if (has(p, "arm")) arms.add(p);
        }
        float swing = 32f;
        for (Part p : legs) {
            boolean left = side(p);
            boolean back = has(p, "hind") || has(p, "back") || has(p, "rear");
            swingKeys(clip, p, left != back ? swing : -swing);
        }
        for (Part p : arms) swingKeys(clip, p, side(p) ? -swing * 0.7f : swing * 0.7f);
        Part body = bodyPart(d);
        if (body != null) {
            ClipTools.setKey(clip, body.id, Channel.POS, 0f, 0f, 0f, 0f, Ease.SMOOTH);
            ClipTools.setKey(clip, body.id, Channel.POS, 0.2f, 0f, 0.6f, 0f, Ease.SMOOTH);
            ClipTools.setKey(clip, body.id, Channel.POS, 0.4f, 0f, 0f, 0f, Ease.SMOOTH);
            ClipTools.setKey(clip, body.id, Channel.POS, 0.6f, 0f, 0.6f, 0f, Ease.SMOOTH);
        }
        for (Part p : d.parts) {
            if (has(p, "tail")) {
                ClipTools.setKey(clip, p.id, Channel.ROT, 0f, 0f, -14f, 0f, Ease.SMOOTH);
                ClipTools.setKey(clip, p.id, Channel.ROT, 0.4f, 0f, 14f, 0f, Ease.SMOOTH);
            }
        }
        return clip;
    }

    private static boolean side(Part p) {
        if (has(p, "left")) return true;
        if (has(p, "right")) return false;
        return p.pos[0] > 0f;
    }

    private static void swingKeys(Clip clip, Part p, float swing) {
        setKey(clip, p.id, Channel.ROT, 0f, swing, 0f, 0f, Ease.SMOOTH);
        setKey(clip, p.id, Channel.ROT, clip.length * 0.5f, -swing, 0f, 0f, Ease.SMOOTH);
    }

    private static Part bodyPart(Design d) {
        for (Part p : d.parts) if (has(p, "body") || has(p, "torso")) return p;
        return null;
    }

    // A gentle breathing idle: the body rises and falls, heads and tails move a little.
    public static Clip makeIdle(Design d) {
        Clip clip = new Clip("idle", 3f, Clip.Loop.LOOP);
        Part body = bodyPart(d);
        if (body != null) {
            setKey(clip, body.id, Channel.SCALE, 0f, 1f, 1f, 1f, Ease.SMOOTH);
            setKey(clip, body.id, Channel.SCALE, 1.5f, 1.015f, 1.03f, 1.015f, Ease.SMOOTH);
        }
        for (Part p : d.parts) {
            if (has(p, "tail")) {
                setKey(clip, p.id, Channel.ROT, 0f, 0f, -10f, 0f, Ease.SMOOTH);
                setKey(clip, p.id, Channel.ROT, 1.5f, 0f, 10f, 0f, Ease.SMOOTH);
            } else if (has(p, "head")) {
                setKey(clip, p.id, Channel.ROT, 0f, 0f, 0f, 0f, Ease.SMOOTH);
                setKey(clip, p.id, Channel.ROT, 1.5f, 3f, 0f, 0f, Ease.SMOOTH);
            }
        }
        return clip;
    }
}
