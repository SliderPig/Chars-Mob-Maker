package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// One named animation. idle, walk, attack, ranged, hurt, death, leap, teleport and summon get played by the mob itself, any other name by /mobmaker play.
public final class Clip {
    public enum Loop {
        LOOP("Loops"), ONCE("Plays once"), HOLD("Stays at the end");

        public final String label;

        Loop(String label) {
            this.label = label;
        }

        public static Loop of(String s) {
            for (Loop l : values()) if (l.name().equalsIgnoreCase(s)) return l;
            return LOOP;
        }
    }

    public String name = "idle";
    public float length = 1f;
    public float speed = 1f;
    public Loop loop = Loop.LOOP;
    public final List<Track> tracks = new ArrayList<>();

    public Clip() {}

    public Clip(String name, float length, Loop loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    public Clip copy() {
        Clip c = new Clip(name, length, loop);
        c.speed = speed;
        for (Track t : tracks) c.tracks.add(t.copy());
        return c;
    }

    public Track track(String part, Channel channel, boolean make) {
        for (Track t : tracks) if (t.channel == channel && t.part.equals(part)) return t;
        if (!make) return null;
        Track t = new Track(part, channel);
        tracks.add(t);
        return t;
    }

    // Puts a time into the clip according to its loop setting.
    public float wrapTime(float time) {
        if (length <= 0f) return 0f;
        if (loop == Loop.LOOP) {
            float t = time % length;
            return t < 0f ? t + length : t;
        }
        return Math.max(0f, Math.min(length, time));
    }

    public int keyCount() {
        int n = 0;
        for (Track t : tracks) n += t.keys.size();
        return n;
    }
}
