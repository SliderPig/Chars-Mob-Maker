package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// A screen made in the editor: its look, its slots and buttons, and what the machine behind it can make.
public final class ScreenInfo {
    public static final class Widget {
        public enum Kind {
            PANEL("Panel"), TEXT("Text"), SLOT("Slot"), BUTTON("Button"), PROGRESS("Progress"), IMAGE("Picture"), STEPS("Steps");

            public final String label;

            Kind(String label) {
                this.label = label;
            }

            public static Kind of(String s) {
                for (Kind v : values()) if (v.name().equalsIgnoreCase(s)) return v;
                return PANEL;
            }
        }

        public Kind kind = Kind.PANEL;
        public int x = 8, y = 8, w = 40, h = 20;
        public int colour = 0xFFFF8FC7;
        public String text = "";
        public boolean output;          // SLOT: the machine puts things here, you can't
        public int action = ACTION_START; // BUTTON: start, stop, or a step's number
        public boolean bar;             // PROGRESS: a bar rather than an arrow
        public boolean centred;         // TEXT
        public String item = "";        // IMAGE: an item ref
        public int paint = -1;          // IMAGE: a painted cell

        public static final int ACTION_START = -1, ACTION_STOP = -2;

        public Widget copy() {
            Widget c = new Widget();
            c.kind = kind;
            c.x = x;
            c.y = y;
            c.w = w;
            c.h = h;
            c.colour = colour;
            c.text = text;
            c.output = output;
            c.action = action;
            c.bar = bar;
            c.centred = centred;
            c.item = item;
            c.paint = paint;
            return c;
        }
    }

    public static final class Ingredient {
        public String item = "minecraft:sugar";
        public int count = 1;

        public Ingredient() {}

        public Ingredient(String item, int count) {
            this.item = item;
            this.count = count;
        }

        public Ingredient copy() {
            return new Ingredient(item, count);
        }
    }

    // One step of the process: these go in, this much time passes, that comes out.
    public static final class Recipe {
        public String name = "Step";
        public final List<Ingredient> inputs = new ArrayList<>();
        public String output = "minecraft:sugar";
        public int outputCount = 1;
        public float seconds = 3f;

        public Recipe copy() {
            Recipe r = new Recipe();
            r.name = name;
            for (Ingredient i : inputs) r.inputs.add(i.copy());
            r.output = output;
            r.outputCount = outputCount;
            r.seconds = seconds;
            return r;
        }
    }

    public int width = 176, height = 166;
    public int colourTop = 0xFF3A2547, colourBottom = 0xFF281A32;
    public int invX = 8, invY = 84;
    public boolean auto;  // starts by itself as soon as it can, rather than waiting for a button
    public final List<Widget> widgets = new ArrayList<>();
    public final List<Recipe> recipes = new ArrayList<>();

    public ScreenInfo copy() {
        ScreenInfo s = new ScreenInfo();
        s.width = width;
        s.height = height;
        s.colourTop = colourTop;
        s.colourBottom = colourBottom;
        s.invX = invX;
        s.invY = invY;
        s.auto = auto;
        for (Widget w : widgets) s.widgets.add(w.copy());
        for (Recipe r : recipes) s.recipes.add(r.copy());
        return s;
    }

    // The slots in order: a slot's number is its place in this list.
    public List<Widget> slots() {
        List<Widget> out = new ArrayList<>();
        for (Widget w : widgets) if (w.kind == Widget.Kind.SLOT) out.add(w);
        return out;
    }

    public void addDefaults() {
        Widget title = new Widget();
        title.kind = Widget.Kind.TEXT;
        title.x = 8;
        title.y = 6;
        title.w = 160;
        title.h = 10;
        title.text = "My Machine";
        title.colour = 0xFFF8EAF3;
        widgets.add(title);
    }
}
