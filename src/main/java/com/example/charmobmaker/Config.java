package com.example.charmobmaker;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_EVERYONE;
    public static final ForgeConfigSpec.IntValue MAX_DESIGNS;
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_COUNT;
    public static final ForgeConfigSpec.BooleanValue NATURAL_SPAWNS;
    public static final ForgeConfigSpec.IntValue MAX_NATURAL_PER_PLAYER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ALLOW_EVERYONE = b.comment("Let every player use the editor on a server. Off = ops only. In singleplayer you can always use it.",
                "When on, players can only change or delete mobs they made themselves; ops can change any.").define("allowEveryone", false);
        MAX_DESIGNS = b.comment("How many mob designs a world can hold.").defineInRange("maxDesigns", 200, 1, 1000);
        MAX_SPAWN_COUNT = b.comment("The most mobs one spawn command or button can make at a time.").defineInRange("maxSpawnCount", 32, 1, 256);
        NATURAL_SPAWNS = b.comment("Switch off to stop every design from spawning on its own, whatever its settings say.").define("naturalSpawns", true);
        MAX_NATURAL_PER_PLAYER = b.comment("Natural spawns stop when this many naturally spawned made mobs are near a player.").defineInRange("maxNaturalPerPlayer", 16, 0, 128);
        SPEC = b.build();
    }

    private Config() {}
}
