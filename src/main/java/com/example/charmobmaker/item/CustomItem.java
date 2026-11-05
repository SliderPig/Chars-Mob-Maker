package com.example.charmobmaker.item;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.client.render.CustomItemRenderer;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.ItemInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Consumer;

// The one real item behind every made item. What it looks like and what using it does comes from its design: a fixed list of things, never code.
public final class CustomItem extends Item {
    private static final String COOL = "charmobmaker.cool";

    public CustomItem(Properties properties) {
        super(properties);
    }

    private static Design design(ItemStack stack, Level level) {
        String id = DesignStacks.idOf(stack);
        return level != null ? Designs.of(level, id) : Designs.CLIENT.getOrDefault(id, Designs.SERVER.get(id));
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = stack.hasTag() ? stack.getTag().getString(DesignStacks.NAME) : "";
        return name.isEmpty() ? super.getName(stack) : Component.literal(name);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        Design d = design(stack, null);
        return d == null ? 64 : d.item.stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        Design d = design(stack, null);
        if (d == null) return UseAnim.NONE;
        return switch (d.item.use) {
            case DRINK -> UseAnim.DRINK;
            case EAT -> UseAnim.EAT;
            default -> UseAnim.NONE;
        };
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        Design d = design(stack, null);
        if (d == null || d.item.use == ItemInfo.Use.NONE || d.item.use == ItemInfo.Use.INSTANT) return 0;
        return Math.max(1, Math.round(d.item.useSeconds * 20f));
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_EAT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Design d = design(stack, level);
        if (d == null || d.item.use == ItemInfo.Use.NONE) return InteractionResultHolder.pass(stack);
        long left = coolingFor(player, d.id, level);
        if (left > 0L) {
            if (level.isClientSide) player.displayClientMessage(Component.literal("Ready again in " + (left / 20L + 1L) + " s"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (d.item.use == ItemInfo.Use.INSTANT) {
            ItemStack after = applyUse(stack, level, player, d);
            return InteractionResultHolder.sidedSuccess(after, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
        Design d = design(stack, level);
        if (d == null) return stack;
        return applyUse(stack, level, who, d);
    }

    private static long coolingFor(Player player, String id, Level level) {
        CompoundTag tag = player.getPersistentData().getCompound(COOL);
        return tag.getLong(id) - level.getGameTime();
    }

    // Does everything the design lists. Both sides run this so the cooldown and the item change agree; only the server hands out the real effects.
    private static ItemStack applyUse(ItemStack stack, Level level, LivingEntity who, Design d) {
        ItemInfo info = d.item;
        Player player = who instanceof Player p ? p : null;
        boolean creative = player != null && player.getAbilities().instabuild;
        if (player != null && info.cooldown > 0f) {
            CompoundTag tag = player.getPersistentData().getCompound(COOL);
            tag.putLong(d.id, level.getGameTime() + Math.round(info.cooldown * 20f));
            player.getPersistentData().put(COOL, tag);
        }
        if (!level.isClientSide) serverEffects(level, who, player, d);
        if (!info.consume || creative) return stack;
        stack.shrink(1);
        if (info.returns.isEmpty()) return stack;
        ItemStack back = DesignStacks.ofRef(info.returns, 1, level.isClientSide ? Designs.CLIENT : Designs.SERVER);
        if (back.isEmpty()) return stack;
        if (stack.isEmpty()) return back;
        if (player != null && !level.isClientSide && !player.getInventory().add(back)) player.drop(back, false);
        return stack;
    }

    private static void serverEffects(Level level, LivingEntity who, Player player, Design d) {
        ItemInfo info = d.item;
        if (info.clearEffects) who.removeAllEffects();
        for (ItemInfo.Effect e : info.effects) {
            ResourceLocation rl = ResourceLocation.tryParse(e.id);
            if (rl == null || !ForgeRegistries.MOB_EFFECTS.containsKey(rl)) continue;
            who.addEffect(new MobEffectInstance(ForgeRegistries.MOB_EFFECTS.getValue(rl), Math.max(1, Math.round(e.seconds * 20f)), e.level - 1));
        }
        if (info.heal > 0f) who.heal(info.heal);
        if (player != null) {
            if (info.food > 0) player.getFoodData().eat(info.food, info.saturation);
            if (info.xp > 0) player.giveExperiencePoints(info.xp);
        }
        if (info.launch > 0f) {
            Vec3 v = who.getDeltaMovement();
            who.setDeltaMovement(v.x, info.launch, v.z);
            who.hurtMarked = true;
        }
        if (!info.sound.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(info.sound);
            if (rl != null && ForgeRegistries.SOUND_EVENTS.containsKey(rl)) {
                level.playSound(null, who.getX(), who.getY(), who.getZ(), ForgeRegistries.SOUND_EVENTS.getValue(rl), SoundSource.PLAYERS, 1f, 1f);
            }
        }
        if (!info.particle.isEmpty() && level instanceof ServerLevel server) {
            ResourceLocation rl = ResourceLocation.tryParse(info.particle);
            ParticleType<?> type = rl == null ? null : ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            if (type instanceof SimpleParticleType simple) sendParticles(server, simple, who, info.particleCount);
        }
    }

    private static void sendParticles(ServerLevel level, ParticleOptions particle, LivingEntity who, int count) {
        level.sendParticles(particle, who.getX(), who.getY() + who.getBbHeight() * 0.6, who.getZ(), count, 0.4, 0.5, 0.4, 0.04);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        Design d = design(stack, level);
        if (d == null) {
            lines.add(Component.literal("That item design is gone").withStyle(ChatFormatting.RED));
            return;
        }
        ItemInfo info = d.item;
        for (String l : info.lines) lines.add(Component.literal(l).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (ItemInfo.Effect e : info.effects) {
            ResourceLocation rl = ResourceLocation.tryParse(e.id);
            if (rl == null || !ForgeRegistries.MOB_EFFECTS.containsKey(rl)) continue;
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            MutableComponent line = Component.translatable(effect.getDescriptionId());
            if (e.level > 1) line = Component.translatable("potion.withAmplifier", line, Component.translatable("potion.potency." + Mth.clamp(e.level - 1, 0, 5)));
            MobEffectInstance timed = new MobEffectInstance(effect, Math.round(e.seconds * 20f));
            line = Component.translatable("potion.withDuration", line, MobEffectUtil.formatDuration(timed, 1f));
            lines.add(line.withStyle(effect.getCategory().getTooltipFormatting()));
        }
        if (info.heal > 0f) lines.add(Component.literal("Heals " + trim(info.heal / 2f) + " hearts").withStyle(ChatFormatting.RED));
        if (info.food > 0) lines.add(Component.literal("Fills " + trim(info.food / 2f) + " hunger").withStyle(ChatFormatting.GOLD));
        if (info.clearEffects) lines.add(Component.literal("Clears other effects").withStyle(ChatFormatting.GRAY));
        if (info.cooldown > 0f) lines.add(Component.literal("Can be used every " + trim(info.cooldown) + " s").withStyle(ChatFormatting.GRAY));
    }

    private static String trim(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CustomItemRenderer.get();
            }
        });
    }
}
