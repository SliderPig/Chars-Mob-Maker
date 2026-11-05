package com.example.charmobmaker.model;

// Everything a design may be. The server checks each upload against these, so nobody can send it something silly.
public final class Limits {
    public static final int MAX_PARTS = 64;
    public static final int MAX_DEPTH = 24;
    public static final int MAX_CLIPS = 24;
    public static final int MAX_KEYS_PER_TRACK = 96;
    public static final int MAX_KEYS_TOTAL = 4000;
    public static final int MAX_PAINTS = 16;
    public static final int MAX_DROPS = 8;
    public static final int MAX_TARGETS = 8;
    public static final int MAX_BIOMES = 16;
    public static final int MAX_NAME = 32;
    public static final int MAX_JSON_BYTES = 256 * 1024;
    public static final int MAX_DESIGN_GZ_BYTES = 128 * 1024;
    public static final float MAX_OFFSET = 1024f;   // pixels
    public static final float MAX_SIZE = 512f;
    public static final float MAX_SCALE = 32f;
    public static final float MIN_CLIP = 0.05f, MAX_CLIP = 60f;

    public static final float MAX_HEALTH = 1000f, MAX_SPEED = 1.0f, MAX_DAMAGE = 200f, MAX_ARMOR = 30f;
    public static final float MIN_WIDTH = 0.1f, MAX_WIDTH = 8f, MIN_HEIGHT = 0.1f, MAX_HEIGHT = 12f;
    public static final int MAX_XP = 1000;
    public static final float MIN_RATE = 0.25f, MAX_RATE = 30f;
    public static final float MIN_COOLDOWN = 1f, MAX_COOLDOWN = 300f;
    public static final int MAX_SUMMON = 4;

    public static final int MAX_WIDGETS = 64;
    public static final int MAX_SLOTS = 27;
    public static final int MAX_RECIPES = 16;
    public static final int MAX_INPUTS = 6;
    public static final int MIN_SCREEN_W = 164, MIN_SCREEN_H = 78, MAX_SCREEN_W = 420, MAX_SCREEN_H = 300;
    public static final float MAX_RECIPE_SECONDS = 600f;
    public static final int MAX_EFFECTS = 8;
    public static final int MAX_LINES = 4;
    public static final int MAX_EFFECT_LEVEL = 10;
    public static final float MAX_EFFECT_SECONDS = 3600f;

    private Limits() {}
}
