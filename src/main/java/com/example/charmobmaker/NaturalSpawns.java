package com.example.charmobmaker;

import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

// Mobs that spawn on their own, if their design says so. Every 10 seconds, near each player, one try per design. Cheap, and it never touches the normal mob caps.
public final class NaturalSpawns {
    private static final int EVERY = 200;
    private static int clock;

    private NaturalSpawns() {}

    public static void tick(MinecraftServer server) {
        if (++clock < EVERY) return;
        clock = 0;
        if (!Config.NATURAL_SPAWNS.get() || Designs.SERVER.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            ServerLevel level = p.serverLevel();
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) continue;
            if (nearbyNatural(level, p) >= Config.MAX_NATURAL_PER_PLAYER.get()) continue;
            for (Design d : Designs.SERVER.values()) {
                if (d.type == Design.Type.MOB && d.stats.spawnNaturally) tryOne(level, p, d);
            }
        }
    }

    private static int nearbyNatural(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(CustomMob.class, new AABB(p.blockPosition()).inflate(64.0), CustomMob::isNatural).size();
    }

    private static void tryOne(ServerLevel level, ServerPlayer p, Design d) {
        Stats s = d.stats;
        RandomSource r = level.getRandom();
        if (r.nextInt(100) >= s.spawnRate) return;
        if (s.mood == Stats.Mood.HOSTILE && level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (s.spawnBiomes.isEmpty() && level.dimension() != Level.OVERWORLD) return;
        double angle = r.nextDouble() * Math.PI * 2.0;
        double dist = 28.0 + r.nextDouble() * 28.0;
        int x = (int) (p.getX() + Math.cos(angle) * dist), z = (int) (p.getZ() + Math.sin(angle) * dist);
        if (!level.hasChunkAt(new BlockPos(x, 64, z))) return;
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!matchesBiome(level, at, s)) return;
        if (s.flying) at = at.above(4 + r.nextInt(8));
        if (s.spawnNightOnly || s.mood == Stats.Mood.HOSTILE) {
            if (level.getBrightness(LightLayer.SKY, at) - level.getSkyDarken() > 7 || level.getBrightness(LightLayer.BLOCK, at) > 7) return;
        } else if (level.getBlockState(at).liquid()) {
            return;
        }
        if (level.getBlockState(at.below()).isAir() && !s.flying) return;
        if (level.getEntitiesOfClass(CustomMob.class, new AABB(at).inflate(48.0), m -> d.id.equals(m.designId())).size() >= s.spawnMaxNearby) return;
        if (level.hasNearbyAlivePlayer(at.getX(), at.getY(), at.getZ(), 20.0)) return;
        for (int i = 0; i < s.spawnGroup; i++) {
            BlockPos spot = i == 0 ? at : at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            CustomMob m = CustomMob.spawnAt(level, spot, d.id);
            if (m != null) m.markNatural();
        }
    }

    private static boolean matchesBiome(ServerLevel level, BlockPos at, Stats s) {
        if (s.spawnBiomes.isEmpty()) return true;
        Holder<Biome> here = level.getBiome(at);
        for (String id : s.spawnBiomes) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && here.is(ResourceKey.create(Registries.BIOME, rl))) return true;
        }
        return false;
    }
}
