package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// What a made item does when you use it. Only ever this list of things: it's data, never code.
public final class ItemInfo {
    public enum Use {
        DRINK("Drink"), EAT("Eat"), INSTANT("Instant"), NONE("Nothing");

        public final String label;

        Use(String label) {
            this.label = label;
        }

        public static Use of(String s) {
            for (Use v : values()) if (v.name().equalsIgnoreCase(s)) return v;
            return DRINK;
        }
    }

    public static final class Effect {
        public String id = "minecraft:speed";
        public int level = 1;
        public float seconds = 30f;

        public Effect copy() {
            Effect e = new Effect();
            e.id = id;
            e.level = level;
            e.seconds = seconds;
            return e;
        }
    }

    public int stack = 16;
    public Use use = Use.DRINK;
    public float useSeconds = 1.6f;
    public float cooldown = 0f;
    public boolean consume = true;
    public String returns = "";  // an item ref left behind, like an empty can
    public float heal = 0f;
    public int food = 0;
    public float saturation = 0.3f;
    public boolean clearEffects;
    public float launch = 0f;     // bounces you up, in blocks per tick
    public int xp = 0;
    public String sound = "";
    public String particle = "";
    public int particleCount = 12;
    public final List<Effect> effects = new ArrayList<>();
    public final List<String> lines = new ArrayList<>(); // extra tooltip lines
    public float handScale = 1f;
    public float handGrip = 0f; // pixels: moves where the hand holds it, along its height

    public ItemInfo copy() {
        ItemInfo i = new ItemInfo();
        i.stack = stack;
        i.use = use;
        i.useSeconds = useSeconds;
        i.cooldown = cooldown;
        i.consume = consume;
        i.returns = returns;
        i.heal = heal;
        i.food = food;
        i.saturation = saturation;
        i.clearEffects = clearEffects;
        i.launch = launch;
        i.xp = xp;
        i.sound = sound;
        i.particle = particle;
        i.particleCount = particleCount;
        for (Effect e : effects) i.effects.add(e.copy());
        i.lines.addAll(lines);
        i.handScale = handScale;
        i.handGrip = handGrip;
        return i;
    }
}
