package com.example.charmobmaker.entity;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.model.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.function.Predicate;

// Puts a mob's brain together out of its stats. Called again whenever the design changes.
final class MobGoals {
    private MobGoals() {}

    static void build(CustomMob m, Stats s) {
        // running ones have to be stopped first, or they'd keep hold of moving and looking for good
        m.goalSelector.getRunningGoals().toList().forEach(WrappedGoal::stop);
        m.targetSelector.getRunningGoals().toList().forEach(WrappedGoal::stop);
        m.goalSelector.removeAllGoals(g -> true);
        m.targetSelector.removeAllGoals(g -> true);
        if (s.swims && !s.flying) m.goalSelector.addGoal(0, new FloatGoal(m));
        if (s.mood == Stats.Mood.PASSIVE) m.goalSelector.addGoal(1, new PanicGoal(m, 1.4));
        boolean fights = s.mood != Stats.Mood.PASSIVE;
        if (fights) {
            if (s.leap) m.goalSelector.addGoal(1, new LeapGoal(m, s));
            if (s.teleport) m.goalSelector.addGoal(1, new TeleportGoal(m, s));
            if (s.summon) m.goalSelector.addGoal(1, new SummonGoal(m, s));
            int rate = Math.max(5, Math.round(s.attackRate * 20f));
            switch (s.attack) {
                case MELEE -> m.goalSelector.addGoal(2, new Melee(m, rate));
                case ARROW, FIREBALL -> m.goalSelector.addGoal(2, new RangedAttackGoal(m, 1.0, rate, rate + 10, 16f));
                case NONE -> {
                }
            }
        }
        if (s.flying) m.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(m, 1.0));
        else m.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(m, 0.8));
        m.goalSelector.addGoal(6, new LookAtPlayerGoal(m, Player.class, 8f));
        m.goalSelector.addGoal(7, new RandomLookAroundGoal(m));

        if (fights) m.targetSelector.addGoal(1, new HurtByTargetGoal(m));
        if (s.mood == Stats.Mood.HOSTILE) {
            int order = 2;
            String own = m.designId();
            for (String t : s.targets) {
                Class<? extends LivingEntity> kind = kindOf(t);
                Predicate<LivingEntity> also = notSame(own);
                if (kind == LivingEntity.class) {
                    ResourceLocation rl = ResourceLocation.tryParse(t);
                    EntityType<?> type = rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
                    if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) continue;
                    also = also.and(e -> e.getType() == type);
                }
                m.targetSelector.addGoal(order++, new NearestAttackableTargetGoal<>(m, kind, 10, true, false, also));
            }
        }
    }

    private static Predicate<LivingEntity> notSame(String design) {
        return e -> !(e instanceof CustomMob c && design.equals(c.designId()));
    }

    private static Class<? extends LivingEntity> kindOf(String target) {
        return switch (target) {
            case "player" -> Player.class;
            case "villager" -> AbstractVillager.class;
            case "animal" -> Animal.class;
            case "monster" -> Monster.class;
            case "golem" -> AbstractGolem.class;
            default -> LivingEntity.class;
        };
    }

    private static final class Melee extends MeleeAttackGoal {
        private final int rate;

        Melee(CustomMob m, int rate) {
            super(m, 1.2, false);
            this.rate = rate;
        }

        @Override
        protected int getAttackInterval() {
            return adjustedTickDelay(rate);
        }
    }

    // A jump at the target from a distance.
    private static final class LeapGoal extends Goal {
        private final CustomMob m;
        private final int cooldown;
        private int wait;

        LeapGoal(CustomMob m, Stats s) {
            this.m = m;
            cooldown = Math.round(s.abilityCooldown * 20f);
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--wait > 0) return false;
            LivingEntity t = m.getTarget();
            if (t == null || !m.onGround()) return false;
            double d = m.distanceToSqr(t);
            return d >= 9.0 && d <= 64.0 && m.getSensing().hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            wait = cooldown;
            Vec3 way = new Vec3(t.getX() - m.getX(), 0.0, t.getZ() - m.getZ());
            if (way.lengthSqr() > 1.0e-7) way = way.normalize().scale(0.7);
            m.setDeltaMovement(way.x, 0.55, way.z);
            m.level().broadcastEntityEvent(m, CustomMob.EV_LEAP);
        }
    }

    // Pops up beside the target.
    private static final class TeleportGoal extends Goal {
        private final CustomMob m;
        private final int cooldown;
        private int wait;

        TeleportGoal(CustomMob m, Stats s) {
            this.m = m;
            cooldown = Math.round(s.abilityCooldown * 20f);
        }

        @Override
        public boolean canUse() {
            if (--wait > 0) return false;
            LivingEntity t = m.getTarget();
            return t != null && m.distanceToSqr(t) > 25.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            wait = cooldown;
            for (int i = 0; i < 12; i++) {
                double angle = m.getRandom().nextDouble() * Math.PI * 2.0;
                double dist = 2.0 + m.getRandom().nextDouble() * 3.0;
                if (m.randomTeleport(t.getX() + Math.cos(angle) * dist, t.getY() + m.getRandom().nextInt(3) - 1, t.getZ() + Math.sin(angle) * dist, true)) {
                    m.level().broadcastEntityEvent(m, CustomMob.EV_TELEPORT);
                    m.playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 1f, 1f);
                    break;
                }
            }
        }
    }

    // Calls helpers, but not endlessly, and never bosses.
    private static final class SummonGoal extends Goal {
        private static final int MAX_NEARBY = 6;
        private final CustomMob m;
        private final Stats s;
        private final int cooldown;
        private int wait;

        SummonGoal(CustomMob m, Stats s) {
            this.m = m;
            this.s = s;
            cooldown = Math.round(s.abilityCooldown * 20f);
        }

        @Override
        public boolean canUse() {
            if (--wait > 0) return false;
            return m.getTarget() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            wait = cooldown;
            if (!(m.level() instanceof ServerLevel level)) return;
            String tag = "charmobmaker.summoned";
            int near = level.getEntitiesOfClass(Mob.class, new AABB(m.blockPosition()).inflate(24.0), e -> e.getTags().contains(tag)).size();
            if (near >= MAX_NEARBY) return;
            boolean made = false;
            for (int i = 0; i < s.summonCount && near + i < MAX_NEARBY; i++) {
                BlockPos at = m.blockPosition().offset(m.getRandom().nextInt(5) - 2, 0, m.getRandom().nextInt(5) - 2);
                Mob helper = make(level, at);
                if (helper == null) break;
                helper.addTag(tag);
                if (m.getTarget() != null) helper.setTarget(m.getTarget());
                made = true;
            }
            if (made) m.level().broadcastEntityEvent(m, CustomMob.EV_SUMMON);
        }

        private Mob make(ServerLevel level, BlockPos at) {
            ResourceLocation rl = ResourceLocation.tryParse(s.summonMob);
            if (rl == null) return null;
            if (rl.getNamespace().equals("charmobmaker")) {
                String id = rl.getPath();
                if (Designs.SERVER.get(id) == null) return null;
                return CustomMob.spawnAt(level, at, id);
            }
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return null;
            Entity e = ForgeRegistries.ENTITY_TYPES.getValue(rl).create(level);
            if (!(e instanceof Mob mob) || e instanceof EnderDragon || e instanceof WitherBoss) {
                if (e != null) e.discard();
                return null;
            }
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, m.getRandom().nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(mob);
            return mob;
        }
    }
}
