package com.example.charmobmaker.model;

import java.util.ArrayList;
import java.util.List;

// How the mob behaves in the world.
public final class Stats {
    public enum Mood {
        HOSTILE("Hostile"), NEUTRAL("Neutral"), PASSIVE("Passive");

        public final String label;

        Mood(String label) {
            this.label = label;
        }

        public static Mood of(String s) {
            for (Mood m : values()) if (m.name().equalsIgnoreCase(s)) return m;
            return NEUTRAL;
        }
    }

    public enum Attack {
        NONE("None"), MELEE("Melee"), ARROW("Arrows"), FIREBALL("Fireballs");

        public final String label;

        Attack(String label) {
            this.label = label;
        }

        public static Attack of(String s) {
            for (Attack a : values()) if (a.name().equalsIgnoreCase(s)) return a;
            return MELEE;
        }
    }

    public static final class Drop {
        public String item = "minecraft:bone";
        public int min = 0, max = 1;
        public float chance = 1f;

        public Drop copy() {
            Drop d = new Drop();
            d.item = item;
            d.min = min;
            d.max = max;
            d.chance = chance;
            return d;
        }
    }

    public float health = 20f;
    public float speed = 0.25f;
    public float damage = 3f;
    public float armor = 0f;
    public float knockbackResist = 0f;
    public float width = 0.6f, height = 1.8f;
    public int xp = 5;
    public Mood mood = Mood.NEUTRAL;
    public boolean flying, fireImmune;
    public boolean swims = true; // bobs up in water rather than walking along the bottom
    public final List<String> targets = new ArrayList<>(List.of("player"));
    public final List<Drop> drops = new ArrayList<>();
    public String ambientSound = "", hurtSound = "", deathSound = "", attackSound = "", stepSound = "";
    public Attack attack = Attack.MELEE;
    public float attackRate = 1.5f; // seconds between hits or shots

    public boolean leap, teleport, summon;
    public float abilityCooldown = 10f;
    public String summonMob = "minecraft:silverfish";
    public int summonCount = 2;

    public boolean spawnNaturally;
    public final List<String> spawnBiomes = new ArrayList<>(); // empty = anywhere in the Overworld
    public int spawnRate = 10;     // 1 to 100, how eager
    public int spawnGroup = 1;
    public int spawnMaxNearby = 4;
    public boolean spawnNightOnly;

    public int eggBase = 0xFFFF8FC7, eggSpots = 0xFF2A1B33;

    public Stats copy() {
        Stats s = new Stats();
        s.health = health;
        s.speed = speed;
        s.damage = damage;
        s.armor = armor;
        s.knockbackResist = knockbackResist;
        s.width = width;
        s.height = height;
        s.xp = xp;
        s.mood = mood;
        s.flying = flying;
        s.fireImmune = fireImmune;
        s.swims = swims;
        s.targets.clear();
        s.targets.addAll(targets);
        for (Drop d : drops) s.drops.add(d.copy());
        s.ambientSound = ambientSound;
        s.hurtSound = hurtSound;
        s.deathSound = deathSound;
        s.attackSound = attackSound;
        s.stepSound = stepSound;
        s.attack = attack;
        s.attackRate = attackRate;
        s.leap = leap;
        s.teleport = teleport;
        s.summon = summon;
        s.abilityCooldown = abilityCooldown;
        s.summonMob = summonMob;
        s.summonCount = summonCount;
        s.spawnNaturally = spawnNaturally;
        s.spawnBiomes.addAll(spawnBiomes);
        s.spawnRate = spawnRate;
        s.spawnGroup = spawnGroup;
        s.spawnMaxNearby = spawnMaxNearby;
        s.spawnNightOnly = spawnNightOnly;
        s.eggBase = eggBase;
        s.eggSpots = eggSpots;
        return s;
    }
}
