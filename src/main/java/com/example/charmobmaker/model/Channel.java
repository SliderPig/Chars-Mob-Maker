package com.example.charmobmaker.model;

public enum Channel {
    ROT("Turn"), POS("Move"), SCALE("Size");

    public final String label;

    Channel(String label) {
        this.label = label;
    }

    public static Channel of(String s) {
        for (Channel c : values()) if (c.name().equalsIgnoreCase(s)) return c;
        return ROT;
    }

    // the value that means "no change"
    public float rest() {
        return this == SCALE ? 1f : 0f;
    }
}
