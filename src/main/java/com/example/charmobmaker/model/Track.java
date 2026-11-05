package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// All the keys of one part and one channel, always sorted by time.
public final class Track {
    public String part = "";
    public Channel channel = Channel.ROT;
    public final List<Key> keys = new ArrayList<>();

    public Track() {}

    public Track(String part, Channel channel) {
        this.part = part;
        this.channel = channel;
    }

    public Track copy() {
        Track t = new Track(part, channel);
        for (Key k : keys) t.keys.add(k.copy());
        return t;
    }

    public void sort() {
        keys.sort((a, b) -> Float.compare(a.t, b.t));
    }

    // Writes the value at this time into out (3 numbers from offset). When wrap is on, the last key travels back to the first by the end of the clip.
    public void sample(float time, float length, boolean wrap, float[] out, int offset) {
        int n = keys.size();
        if (n == 0) {
            float r = channel.rest();
            out[offset] = r;
            out[offset + 1] = r;
            out[offset + 2] = r;
            return;
        }
        Key first = keys.get(0), last = keys.get(n - 1);
        Key a, b;
        float span, into;
        if (time < first.t) {
            if (!wrap || n == 1) {
                write(first, out, offset);
                return;
            }
            a = last;
            b = first;
            span = length - last.t + first.t;
            into = length - last.t + time;
        } else if (time >= last.t) {
            if (!wrap || n == 1) {
                write(last, out, offset);
                return;
            }
            a = last;
            b = first;
            span = length - last.t + first.t;
            into = time - last.t;
        } else {
            int lo = 0, hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (keys.get(mid).t <= time) lo = mid;
                else hi = mid;
            }
            a = keys.get(lo);
            b = keys.get(hi);
            span = b.t - a.t;
            into = time - a.t;
        }
        float f = span <= 1e-5f ? 1f : a.ease.apply(into / span);
        out[offset] = a.x + (b.x - a.x) * f;
        out[offset + 1] = a.y + (b.y - a.y) * f;
        out[offset + 2] = a.z + (b.z - a.z) * f;
    }

    private static void write(Key k, float[] out, int offset) {
        out[offset] = k.x;
        out[offset + 1] = k.y;
        out[offset + 2] = k.z;
    }

    // The key sitting at this time (within a hair), or null.
    public Key at(float time) {
        for (Key k : keys) if (Math.abs(k.t - time) < 0.002f) return k;
        return null;
    }
}
