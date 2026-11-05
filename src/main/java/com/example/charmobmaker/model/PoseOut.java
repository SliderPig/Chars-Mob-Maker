package com.example.charmobmaker.model;

import java.util.Arrays;

// The pose of every part at one moment, as changes on top of the design's own numbers: degrees added, pixels added, size multiplied.
public final class PoseOut {
    public final int n;
    public final float[] rot, pos, scale;
    public final boolean[] rotSet, posSet, scaleSet; // which ones a clip actually moved

    public PoseOut(int n) {
        this.n = n;
        rot = new float[n * 3];
        pos = new float[n * 3];
        scale = new float[n * 3];
        rotSet = new boolean[n];
        posSet = new boolean[n];
        scaleSet = new boolean[n];
        clear();
    }

    public void clear() {
        Arrays.fill(rot, 0f);
        Arrays.fill(pos, 0f);
        Arrays.fill(scale, 1f);
        Arrays.fill(rotSet, false);
        Arrays.fill(posSet, false);
        Arrays.fill(scaleSet, false);
    }

    // Moves this pose towards other by the given weight: parts other doesn't touch stay put, unless everything is asked for.
    public void blend(PoseOut other, float w, boolean onlyWhatItMoved) {
        for (int i = 0; i < n; i++) {
            if (!onlyWhatItMoved || other.rotSet[i]) mix(rot, other.rot, i, w);
            if (!onlyWhatItMoved || other.posSet[i]) mix(pos, other.pos, i, w);
            if (!onlyWhatItMoved || other.scaleSet[i]) mix(scale, other.scale, i, w);
            rotSet[i] |= other.rotSet[i];
            posSet[i] |= other.posSet[i];
            scaleSet[i] |= other.scaleSet[i];
        }
    }

    private static void mix(float[] a, float[] b, int i, float w) {
        int j = i * 3;
        a[j] += (b[j] - a[j]) * w;
        a[j + 1] += (b[j + 1] - a[j + 1]) * w;
        a[j + 2] += (b[j + 2] - a[j + 2]) * w;
    }
}
