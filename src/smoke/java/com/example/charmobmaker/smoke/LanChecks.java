package com.example.charmobmaker.smoke;

import com.example.charmobmaker.CharMobMaker;
import com.example.charmobmaker.Config;
import com.example.charmobmaker.Designs;
import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.editor.EditorScreen;
import com.example.charmobmaker.client.editor.EditorState;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.item.MobEggItem;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.Packer;
import com.example.charmobmaker.model.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.function.BooleanSupplier;

// Two real games over a real connection, like a friend joining: python tools/run_lan_test.py. The guest isn't an op, so it tries everything it shouldn't be able to do.
@Mod.EventBusSubscriber(modid = CharMobMaker.MODID, value = Dist.CLIENT)
public final class LanChecks {
    private static final boolean HOST = Boolean.getBoolean("charmobmaker.lanhost"), GUEST = Boolean.getBoolean("charmobmaker.languest");
    private static final int PORT = 25601;
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean titleSeen, started, scripted;
    private static int wait, problems;

    private record Step(int after, Runnable what, BooleanSupplier until) {}

    private LanChecks() {}

    private static void later(int ticks, Runnable what) {
        STEPS.add(new Step(ticks, what, null));
    }

    private static void when(BooleanSupplier until, Runnable what) {
        STEPS.add(new Step(0, what, until));
    }

    private static void check(int ticks, String what, BooleanSupplier ok) {
        later(ticks, () -> {
            boolean good = false;
            try {
                good = ok.getAsBoolean();
            } catch (Throwable t) {
                CharMobMaker.LOG.error("[LanCheck] check threw", t);
            }
            if (!good) problems++;
            CharMobMaker.LOG.info("[LanCheck] {} {}", good ? "OK  " : "FAIL", what);
        });
    }

    private static Path marker(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("run-lan").resolve(name);
    }

    private static void mark(String name) {
        try {
            Files.createDirectories(marker(name).getParent());
            Files.writeString(marker(name), "x");
        } catch (IOException e) {
            CharMobMaker.LOG.error("[LanCheck] marker", e);
        }
    }

    private static void shot(int ticks, String name) {
        later(ticks, () -> Screenshot.grab(Minecraft.getInstance().gameDirectory, name + ".png", Minecraft.getInstance().getMainRenderTarget(), c -> {}));
    }

    private static Design bigDesign() {
        Design d = new Design();
        d.name = "Big Upload";
        Random r = new Random(7L);
        for (int c = 0; c < 16; c++) {
            int[] cell = new int[256];
            for (int i = 0; i < 256; i++) cell[i] = 0xFF000000 | r.nextInt(0x1000000);
            d.paints.add(cell);
        }
        for (int i = 0; i < 64; i++) {
            Part p = new Part();
            p.id = "p" + i;
            p.name = "Part " + i;
            p.pos = new float[] {(i % 8) * 6f - 21f, (i / 8) * 6f + 3f, 0f};
            p.size = new float[] {5f, 5f, 5f};
            p.paint = i % 16;
            d.parts.add(p);
        }
        // a clip full of odd numbers, which don't squeeze well, so the design needs several packets
        com.example.charmobmaker.model.Clip clip = new com.example.charmobmaker.model.Clip("noise", 30f, com.example.charmobmaker.model.Clip.Loop.LOOP);
        for (int i = 0; i < 64; i++) {
            for (com.example.charmobmaker.model.Channel ch : new com.example.charmobmaker.model.Channel[] {com.example.charmobmaker.model.Channel.ROT, com.example.charmobmaker.model.Channel.POS}) {
                for (int k = 0; k < 30; k++) {
                    com.example.charmobmaker.model.ClipTools.setKey(clip, "p" + i, ch, k + r.nextFloat() * 0.9f, r.nextFloat() * 90f, r.nextFloat() * 90f, r.nextFloat() * 90f, com.example.charmobmaker.model.Ease.LINEAR);
                }
            }
        }
        d.clips.add(clip);
        d.stats.width = 2f;
        d.stats.height = 3f;
        return d;
    }

    private static void guestScript() {
        when(() -> Designs.CLIENT.size() >= 6, () -> CharMobMaker.LOG.info("[LanCheck] OK   guest received {} designs over the network", Designs.CLIENT.size()));
        later(40, () -> mark("guest_in"));
        when(() -> Files.exists(marker("mobs_out")), () -> {});
        check(20, "guest can see the mobs and they have their designs", () -> {
            var mobs = Minecraft.getInstance().level.getEntitiesOfClass(CustomMob.class, Minecraft.getInstance().player.getBoundingBox().inflate(32.0));
            return !mobs.isEmpty() && mobs.stream().allMatch(m -> m.design() != null);
        });
        shot(5, "lan_guest_sees_mobs");

        // not an op: all of this has to fail
        later(5, () -> Minecraft.getInstance().player.connection.sendCommand("mobmaker"));
        check(30, "the command doesn't open the editor for a guest", () -> !(Minecraft.getInstance().screen instanceof EditorScreen));
        later(1, () -> Net.action(Net.Act.OPEN, "", 0));
        check(30, "asking with the key doesn't either", () -> !(Minecraft.getInstance().screen instanceof EditorScreen));
        later(1, () -> {
            Design d = new Design();
            d.name = "Forged";
            Net.uploadToServer(DesignJson.write(d));
        });
        check(40, "a forged upload is refused", () -> !Designs.CLIENT.containsKey("forged") && EditorState.get().toastBad);
        later(1, () -> {
            Net.action(Net.Act.EGG, "pink_cat", 64);
            Net.action(Net.Act.DELETE, "pink_cat", 0);
            Net.action(Net.Act.SPAWN, "pink_cat", 200);
        });
        check(40, "forged eggs and deletes do nothing", () -> Designs.CLIENT.containsKey("pink_cat") && Minecraft.getInstance().player.getInventory().items.stream().noneMatch(i -> !MobEggItem.idOf(i).isEmpty()));

        // anyone can use a machine though, and everything in it stays on the server
        later(1, () -> mark("want_machine"));
        when(() -> Files.exists(marker("machine_out")), () -> {});
        later(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.core.BlockPos at = mc.player.blockPosition().offset(2, 0, 0);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.WEST, at, false));
        });
        check(20, "a guest can open a machine", () -> Minecraft.getInstance().screen instanceof com.example.charmobmaker.client.machine.MachineScreen);
        check(1, "and sees what the server put in it", () -> Minecraft.getInstance().player.containerMenu.getSlot(0).getItem().is(net.minecraft.world.item.Items.SWEET_BERRIES));
        later(1, () -> ((com.example.charmobmaker.client.machine.MachineScreen) Minecraft.getInstance().screen).pressForTest(com.example.charmobmaker.machine.MachineMenu.BUTTON_START));
        shot(20, "lan_guest_machine");
        check(60, "a guest's press runs the step on the server", () -> "berry_mash".equals(com.example.charmobmaker.DesignStacks.idOf(Minecraft.getInstance().player.containerMenu.getSlot(2).getItem())));
        later(1, () -> {
            // a made-up button press and a click in a slot that doesn't exist must do nothing
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 9999);
            mc.player.closeContainer();
        });
        later(10, () -> mark("machine_checked"));

        // now the host allows everyone
        later(1, () -> mark("please_allow"));
        when(() -> Files.exists(marker("allowed")), () -> {});
        later(5, () -> Minecraft.getInstance().player.connection.sendCommand("mobmaker"));
        check(30, "with allowEveryone the command opens the editor", () -> Minecraft.getInstance().screen instanceof EditorScreen);
        later(1, () -> {
            Design big = bigDesign();
            int packed = Packer.pack(DesignJson.write(big)).length;
            CharMobMaker.LOG.info("[LanCheck] big design is {} bytes squeezed, {} pieces", packed, packed / Net.PIECE + 1);
            if (packed <= Net.PIECE) {
                problems++;
                CharMobMaker.LOG.info("[LanCheck] FAIL the big design should need more than one piece");
            }
            EditorState.get().load(big);
            EditorState.get().save();
        });
        check(60, "a design in several pieces arrives, comes back whole, and the server gave it its id", () -> {
            Design back = Designs.CLIENT.get("big_upload");
            return back != null && back.parts.size() == 64 && back.paints.size() == 16 && back.paints.get(3)[100] == bigDesign().paints.get(3)[100] && "big_upload".equals(EditorState.get().design.id);
        });
        shot(5, "lan_guest_big_design");
        later(1, () -> {
            Design copy = Designs.CLIENT.get("pink_cat").copy();
            copy.name = "Hacked";
            EditorState.get().load(copy);
            EditorState.get().save();
        });
        check(40, "a guest can't overwrite someone else's mob", () -> Designs.CLIENT.get("pink_cat").name.equals("Pink Cat") && EditorState.get().toastBad);
        later(1, () -> {
            Design mine = Designs.CLIENT.get("big_upload").copy();
            mine.name = "Big Upload Two";
            EditorState.get().load(mine);
            EditorState.get().save();
        });
        check(40, "a guest can change their own", () -> Designs.CLIENT.get("big_upload").name.equals("Big Upload Two") && !EditorState.get().toastBad);
        later(1, () -> {
            StringBuilder junk = new StringBuilder("{\"parts\":[],\"name\":\"");
            Random r = new Random(3L);
            for (int i = 0; i < 240000; i++) junk.append((char) ('a' + r.nextInt(26)));
            junk.append("\"}");
            Net.uploadToServer(junk.toString());
        });
        check(40, "something far too big is refused", () -> EditorState.get().toastBad && Designs.CLIENT.keySet().stream().filter(k -> k.startsWith("a")).findAny().isEmpty());
        later(1, () -> Net.action(Net.Act.DELETE, "big_upload", 0));
        check(30, "a guest can delete their own", () -> !Designs.CLIENT.containsKey("big_upload"));
        later(10, () -> {
            CharMobMaker.LOG.info("[LanCheck] guest finished, {} problems", problems);
            mark("guest_done");
            Minecraft.getInstance().stop();
        });
    }

    private static void hostScript() {
        when(() -> Files.exists(marker("guest_in")), () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                ServerPlayer guest = server.getPlayerList().getPlayers().get(1);
                guest.setGameMode(GameType.SURVIVAL);
                guest.getAbilities().invulnerable = true;
                for (String id : new String[] {"pink_cat", "block_golem", "magma_eye"}) CustomMob.spawnNear(guest.serverLevel(), guest.position(), id, 4.0);
                CharMobMaker.LOG.info("[LanCheck] guest is an op: {}", guest.hasPermissions(2));
                mark("mobs_out");
            });
        });
        when(() -> Files.exists(marker("want_machine")), () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                ServerPlayer guest = server.getPlayerList().getPlayers().get(1);
                net.minecraft.core.BlockPos at = guest.blockPosition().offset(2, 0, 0);
                Design d = Designs.SERVER.get("drink_machine");
                guest.serverLevel().setBlock(at, com.example.charmobmaker.block.CustomBlock.stateFor(com.example.charmobmaker.ModContent.CUSTOM_BLOCK.get().defaultBlockState(), d), 3);
                var be = (com.example.charmobmaker.block.CustomBlockEntity) guest.serverLevel().getBlockEntity(at);
                be.setDesign("drink_machine");
                be.items().setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, 2));
                be.items().setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SUGAR, 1));
                mark("machine_out");
            });
        });
        when(() -> Files.exists(marker("please_allow")), () -> {
            Config.ALLOW_EVERYONE.set(true);
            mark("allowed");
        });
        when(() -> Files.exists(marker("guest_done")), () -> {});
        check(10, "nothing the guest wasn't allowed got through", () -> Designs.SERVER.containsKey("pink_cat") && Designs.SERVER.get("pink_cat").name.equals("Pink Cat") && !Designs.SERVER.containsKey("forged") && !Designs.SERVER.containsKey("big_upload"));
        later(5, () -> {
            Config.ALLOW_EVERYONE.set(false);
            CharMobMaker.LOG.info("[LanCheck] host finished, {} problems", problems);
            Minecraft.getInstance().stop();
        });
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post e) {
        if ((HOST || GUEST) && e.getScreen() instanceof TitleScreen) titleSeen = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!(HOST || GUEST) || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (titleSeen && !started) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            if (HOST) {
                try {
                    if (Files.isDirectory(marker("x").getParent())) {
                        try (var files = Files.list(marker("x").getParent())) {
                            files.forEach(f -> f.toFile().delete());
                        }
                    }
                } catch (IOException ex) {
                    CharMobMaker.LOG.warn("[LanCheck] old markers", ex);
                }
                mc.createWorldOpenFlows().createFreshLevel("smoke", new LevelSettings("smoke", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (HOST && !scripted && mc.player != null && mc.getSingleplayerServer() != null) {
            scripted = true;
            var server = mc.getSingleplayerServer();
            server.setUsesAuthentication(false);
            // no cheats for guests: the guest must not count as an op
            server.publishServer(GameType.SURVIVAL, false, PORT);
            server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            mark("host_up");
            CharMobMaker.LOG.info("[LanCheck] open on {}", PORT);
            hostScript();
        }
        if (GUEST && mc.player == null && mc.screen instanceof TitleScreen && Files.exists(marker("host_up")) && !scripted) {
            scripted = true;
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("localhost:" + PORT), new ServerData("lan", "localhost:" + PORT, true), false);
            guestScript();
        }
        if (STEPS.isEmpty() || (GUEST && mc.player == null)) return;
        Step s = STEPS.peek();
        boolean go = s.until() != null ? s.until().getAsBoolean() : ++wait >= s.after();
        if (go) {
            wait = 0;
            STEPS.poll();
            try {
                s.what().run();
            } catch (Throwable t) {
                problems++;
                CharMobMaker.LOG.error("[LanCheck] step failed", t);
            }
        }
    }
}
