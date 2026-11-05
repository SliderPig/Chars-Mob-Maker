package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// A whole mob: plain data, so there's only ever one real entity type and new mobs need no restart.
public final class Design {
    public static final int FORMAT = 1;

    // The same part tree and animation tools make all of these; a screen has no parts, just its layout and recipes.
    public enum Type {
        MOB("Mob"), BLOCK("Block"), ITEM("Item"), SCREEN("Screen");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        public static Type of(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return MOB;
        }
    }

    public Type type = Type.MOB;

    public String id = "";     // given by the server when first saved, safe as a file name
    public String name = "New Mob";
    public String author = "";
    public String owner = "";  // UUID of who made it, used when everyone is allowed to edit
    public final List<Part> parts = new ArrayList<>();
    public final List<Clip> clips = new ArrayList<>();
    public final List<int[]> paints = new ArrayList<>(); // each cell is 16 x 16 ARGB
    public Stats stats = new Stats();
    public BlockInfo block = new BlockInfo();
    public ItemInfo item = new ItemInfo();
    public ScreenInfo screen = new ScreenInfo();

    public Part part(String partId) {
        for (Part p : parts) if (p.id.equals(partId)) return p;
        return null;
    }

    public Clip clip(String clipName) {
        for (Clip c : clips) if (c.name.equals(clipName)) return c;
        return null;
    }

    public Design copy() {
        Design d = new Design();
        d.id = id;
        d.name = name;
        d.author = author;
        d.owner = owner;
        for (Part p : parts) d.parts.add(p.copy());
        for (Clip c : clips) d.clips.add(c.copy());
        for (int[] cell : paints) d.paints.add(cell.clone());
        d.stats = stats.copy();
        d.type = type;
        d.block = block.copy();
        d.item = item.copy();
        d.screen = screen.copy();
        return d;
    }
}
