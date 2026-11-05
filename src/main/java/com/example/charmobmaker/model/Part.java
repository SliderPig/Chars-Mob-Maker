package com.example.charmobmaker.model;

// One piece of a mob. Positions are in pixels (16 = one block), turning in degrees, Y is up and +Z is the way the mob faces.
public final class Part {
    public enum Kind {
        CUBE, BLOCK, MOB, ITEM;

        public static Kind of(String s) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s)) return k;
            return CUBE;
        }
    }

    public static final int FACES = 6; // north south east west up down
    public static final String[] FACE_NAMES = {"N", "S", "E", "W", "U", "D"};

    public String id = "";
    public String name = "Part";
    public String parent = ""; // "" = hangs off the mob itself
    public Kind kind = Kind.CUBE;
    public float[] pos = {0, 0, 0};
    public float[] rot = {0, 0, 0};
    public float[] scale = {1, 1, 1};
    public float[] pivot = {0, 0, 0}; // where it turns and grows from, in its own space
    public boolean glow;   // bright even in the dark
    public boolean head;   // turns to look at things
    public boolean hidden; // not drawn, handy while building

    // CUBE
    public float[] size = {8, 8, 8};
    public int color = 0xFFFFFFFF;
    public String texture = ""; // a block sprite like minecraft:block/stone, "" = plain colour
    public int paint = -1;      // a painted cell of the design, -1 = none
    public int[] facePaint;     // null = every face uses paint, else one cell per face, -1 = use paint

    // BLOCK
    public String block = ""; // minecraft:oak_stairs[facing=east]

    // MOB
    public String mob = "";         // minecraft:zombie
    public String bone = "";        // "" = the whole mob, else a path like head or body/left_wing
    public boolean solo;            // just this bone, without the bones hanging off it
    public String mobTexture = "";  // "" = the mob's own skin

    // ITEM
    public String item = "";
    public String display = "third_person_right_hand";

    public Part copy() {
        Part p = new Part();
        p.id = id;
        p.name = name;
        p.parent = parent;
        p.kind = kind;
        p.pos = pos.clone();
        p.rot = rot.clone();
        p.scale = scale.clone();
        p.pivot = pivot.clone();
        p.glow = glow;
        p.head = head;
        p.hidden = hidden;
        p.size = size.clone();
        p.color = color;
        p.texture = texture;
        p.paint = paint;
        p.facePaint = facePaint == null ? null : facePaint.clone();
        p.block = block;
        p.mob = mob;
        p.bone = bone;
        p.solo = solo;
        p.mobTexture = mobTexture;
        p.item = item;
        p.display = display;
        return p;
    }
}
