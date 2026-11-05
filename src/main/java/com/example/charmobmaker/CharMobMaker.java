package com.example.charmobmaker;

import com.example.charmobmaker.client.ClientSetup;
import com.example.charmobmaker.entity.CustomMob;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CharMobMaker.MODID)
public final class CharMobMaker {
    public static final String MODID = "charmobmaker";
    public static final Logger LOG = LogUtils.getLogger();

    public CharMobMaker() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        bus.addListener(this::commonSetup);
        bus.addListener(this::attributes);
        var game = MinecraftForge.EVENT_BUS;
        game.addListener(this::serverStarted);
        game.addListener(this::serverStopped);
        game.addListener(this::commands);
        game.addListener(this::loggedIn);
        game.addListener(this::loggedOut);
        game.addListener(this::serverTick);
        game.addListener(this::sleep);
        game.addListener(this::joinLevel);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(bus));
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::register);
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(ModContent.CUSTOM.get(), CustomMob.createAttributes().build());
    }

    private void serverStarted(ServerStartedEvent e) {
        DesignStore.start(e.getServer());
    }

    private void serverStopped(ServerStoppedEvent e) {
        DesignStore.stop();
    }

    private void commands(RegisterCommandsEvent e) {
        MobMakerCommand.register(e.getDispatcher());
    }

    private void loggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) DesignStore.sendAll(p);
    }

    private void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Net.forget(e.getEntity().getUUID());
    }

    private void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) NaturalSpawns.tick(e.getServer());
    }

    // Hostile made mobs nearby stop you sleeping, same as monsters do.
    private void sleep(PlayerSleepInBedEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide || p.isCreative() || e.getResultStatus() != null) return;
        var near = p.level().getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(8.0, 5.0, 8.0), CustomMob::isHostile);
        if (!near.isEmpty()) e.setResult(Player.BedSleepingProblem.NOT_SAFE);
    }

    // Iron golems go after hostile made mobs like any other monster, but leave the friendly ones alone.
    private void joinLevel(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof IronGolem golem) {
            golem.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(golem, CustomMob.class, 5, false, false, m -> ((CustomMob) m).isHostile()));
        }
    }
}
