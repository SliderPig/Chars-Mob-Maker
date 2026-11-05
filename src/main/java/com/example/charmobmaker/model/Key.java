package com.example.charmobmaker.model;

// A keyframe: at this time the part should have these values. The ease says how it travels on to the NEXT key.
public final class Key {
    public float t;
    public float x, y, z;
    public Ease ease = Ease.SMOOTH;

    public Key() {}

    public Key(float t, float x, float y, float z, Ease ease) {
        this.t = t;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ease = ease;
    }

    public Key copy() {
        return new Key(t, x, y, z, ease);
    }
}
