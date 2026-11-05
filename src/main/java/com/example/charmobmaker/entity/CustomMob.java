package com.example.charmobmaker.entity;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.model.AnimState;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Rig;
import com.example.charmobmaker.model.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

// The one real mob. Everything about it comes from its design: looks on the PC, stats and brain on the server.
public class CustomMob extends PathfinderMob implements RangedAttackMob {
    private static final EntityDataAccessor<String> DESIGN = SynchedEntityData.defineId(CustomMob.class, EntityDataSerializers.STRING);
    private static final Stats NO_STATS = new Stats();
    public static final byte EV_ATTACK = 70, EV_RANGED = 71, EV_LEAP = 72, EV_TELEPORT = 73, EV_SUMMON = 74;

    public final AnimState anim = new AnimState();
    private Design design;
    private boolean natural;
    private float cullReach = 1f;

    public CustomMob(EntityType<? extends CustomMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ---------- making them ----------

    public static CustomMob spawnAt(ServerLevel level, BlockPos pos, String id) {
        CustomMob m = ModContent.CUSTOM.get().create(level);
        if (m == null) return null;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        m.yBodyRot = m.getYRot();
        m.yHeadRot = m.getYRot();
        m.setDesign(id);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(m);
        return m;
    }

    // Somewhere free a little way from a spot, for spawn commands and buttons.
    public static CustomMob spawnNear(ServerLevel level, Vec3 at, String id, double radius) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        BlockPos pos = BlockPos.containing(at.x + Math.cos(angle) * radius, at.y, at.z + Math.sin(angle) * radius);
        for (int i = 0; i < 4 && !level.noCollision(new AABB(pos)); i++) pos = pos.above();
        CustomMob m = spawnAt(level, pos, id);
        if (m != null) m.setPersistenceRequired();
        return m;
    }

    public void markNatural() {
        natural = true;
    }

    public boolean isNatural() {
        return natural;
    }

    // ---------- the design ----------

    public String designId() {
        return entityData.get(DESIGN);
    }

    public void setDesign(String id) {
        entityData.set(DESIGN, id == null ? "" : id);
        applyDesign();
    }

    public Design design() {
        return design;
    }

    private Stats stats() {
        return design == null ? NO_STATS : design.stats;
    }

    public boolean isHostile() {
        return design != null && design.stats.mood == Stats.Mood.HOSTILE;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DESIGN, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DESIGN.equals(key) && level().isClientSide) applyDesign();
        super.onSyncedDataUpdated(key);
    }

    // Reads the design again: called when the mob appears, and whenever the design gets changed in the editor, while mobs are out in the world.
    public void applyDesign() {
        design = Designs.of(level(), designId());
        cullReach = design == null ? 1f : Rig.of(design).reach() / 16f;
        refreshDimensions();
        if (level().isClientSide) return;
        Stats s = stats();
        float oldMax = getMaxHealth();
        float fraction = oldMax > 0f ? getHealth() / oldMax : 1f;
        base(Attributes.MAX_HEALTH, s.health);
        base(Attributes.MOVEMENT_SPEED, s.speed);
        base(Attributes.FLYING_SPEED, s.speed * 1.6);
        base(Attributes.ATTACK_DAMAGE, s.damage);
        base(Attributes.ARMOR, s.armor);
        base(Attributes.KNOCKBACK_RESISTANCE, s.knockbackResist);
        if (!isDeadOrDying()) setHealth(Math.max(1f, s.health * fraction));
        xpReward = s.xp;
        setMovement(s.flying);
        MobGoals.build(this, s);
    }

    private void base(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance a = getAttribute(attribute);
        if (a != null) a.setBaseValue(value);
    }

    private void setMovement(boolean flying) {
        if (flying) {
            if (!(moveControl instanceof FlyingMoveControl)) {
                moveControl = new FlyingMoveControl(this, 20, true);
                FlyingPathNavigation nav = new FlyingPathNavigation(this, level());
                nav.setCanOpenDoors(false);
                nav.setCanFloat(true);
                nav.setCanPassDoors(true);
                navigation = nav;
            }
        } else if (moveControl instanceof FlyingMoveControl) {
            moveControl = new MoveControl(this);
            navigation = createNavigation(level());
        }
        setNoGravity(flying);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Design", designId());
        tag.putBoolean("Natural", natural);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        natural = tag.getBoolean("Natural");
        setDesign(tag.getString("Design"));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Stats s = stats();
        return EntityDimensions.scalable(s.width, s.height);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(cullReach);
    }

    @Override
    protected Component getTypeName() {
        return design == null ? Component.literal("Made Mob") : Component.literal(design.name);
    }

    @Override
    public boolean fireImmune() {
        return stats().fireImmune;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return isHostile();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return natural;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !stats().flying && super.causeFallDamage(distance, multiplier, source);
    }

    // ---------- sounds ----------

    private static SoundEvent sound(String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(rl);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return sound(stats().ambientSound);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        SoundEvent s = sound(stats().hurtSound);
        return s != null ? s : SoundEvents.GENERIC_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        SoundEvent s = sound(stats().deathSound);
        return s != null ? s : SoundEvents.GENERIC_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (stats().flying) return;
        SoundEvent s = sound(stats().stepSound);
        if (s != null) playSound(s, 0.2f, 1f);
        else super.playStepSound(pos, state);
    }

    // ---------- fighting ----------

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        level().broadcastEntityEvent(this, EV_ATTACK);
        SoundEvent s = sound(stats().attackSound);
        if (s != null) playSound(s, 1f, 1f);
        return hit;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        level().broadcastEntityEvent(this, EV_RANGED);
        Stats s = stats();
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (s.attack == Stats.Attack.FIREBALL) {
            double dy = target.getY(0.5) - getEyeY();
            SmallFireball ball = new SmallFireball(level(), this, dx, dy, dz);
            ball.setPos(getX(), getEyeY() - 0.1, getZ());
            level().addFreshEntity(ball);
            playSound(SoundEvents.BLAZE_SHOOT, 1f, 1f);
        } else {
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), power);
            arrow.setBaseDamage(Math.max(0.5, s.damage / 2.0));
            double dy = target.getY(1.0 / 3.0) - arrow.getY();
            double flat = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + flat * 0.2, dz, 1.6f, 14 - level().getDifficulty().getId() * 4);
            level().addFreshEntity(arrow);
            playSound(SoundEvents.SKELETON_SHOOT, 1f, 1f / (getRandom().nextFloat() * 0.4f + 0.8f));
        }
        SoundEvent extra = sound(s.attackSound);
        if (extra != null) playSound(extra, 1f, 1f);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (Stats.Drop d : stats().drops) {
            if (getRandom().nextFloat() > d.chance) continue;
            ResourceLocation rl = ResourceLocation.tryParse(d.item);
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) continue;
            int count = d.min + (d.max > d.min ? getRandom().nextInt(d.max - d.min + 1) : 0);
            if (count > 0) spawnAtLocation(new ItemStack(ForgeRegistries.ITEMS.getValue(rl), count));
        }
    }

    // A death clip plays out in full (up to 5 seconds) before the mob vanishes.
    private int deathTicks() {
        Clip c = design == null ? null : design.clip("death");
        if (c == null) return 20;
        return Mth.clamp(Mth.ceil(c.length / c.speed * 20f), 20, 100);
    }

    public boolean hasDeathClip() {
        return design != null && design.clip("death") != null;
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= deathTicks() && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    // ---------- animations on the PC ----------

    private float animNow() {
        return tickCount / 20f;
    }

    public void playClip(String clip, boolean once) {
        anim.play(clip, animNow(), once);
    }

    private void playIfThere(String... clips) {
        if (design == null) return;
        for (String c : clips) {
            if (design.clip(c) != null) {
                playClip(c, true);
                return;
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EV_ATTACK -> playIfThere("attack");
            case EV_RANGED -> playIfThere("ranged", "attack");
            case EV_LEAP -> playIfThere("leap");
            case EV_TELEPORT -> playIfThere("teleport");
            case EV_SUMMON -> playIfThere("summon");
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void handleDamageEvent(DamageSource source) {
        super.handleDamageEvent(source);
        playIfThere("hurt");
    }
}
