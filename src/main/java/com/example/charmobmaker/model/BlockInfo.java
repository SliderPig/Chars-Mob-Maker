package com.example.charmobmaker.model;

// What a made block is like in the world, when a design is a block.
public final class BlockInfo {
    public enum Shape {
        PARTS("Its parts"), FULL("Full block"), NONE("Walk through");

        public final String label;

        Shape(String label) {
            this.label = label;
        }

        public static Shape of(String s) {
            for (Shape v : values()) if (v.name().equalsIgnoreCase(s)) return v;
            return PARTS;
        }
    }

    public static final String[] SOUNDS = {"stone", "wood", "wool", "metal", "glass", "grass", "sand"};

    public float hardness = 1.5f;
    public float blast = 6f;
    public int light = 0;
    public Shape shape = Shape.PARTS;
    public String sound = "stone";
    public String screen = ""; // the id of a screen design this block opens, "" = just a block
    public int stack = 64;

    public BlockInfo copy() {
        BlockInfo b = new BlockInfo();
        b.hardness = hardness;
        b.blast = blast;
        b.light = light;
        b.shape = shape;
        b.sound = sound;
        b.screen = screen;
        b.stack = stack;
        return b;
    }
}
