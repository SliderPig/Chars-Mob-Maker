package com.example.charmobmaker.smoke;

import com.example.charmobmaker.CharMobMaker;
import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.client.editor.EditorScreen;
import com.example.charmobmaker.client.editor.EditorState;
import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.entity.CustomMob;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

// The real game walks through the editor like a person would: pressing its real buttons with a pretend mouse, taking screenshots, then it quits. Only in -Psmoke jars.
@Mod.EventBusSubscriber(modid = CharMobMaker.MODID, value = Dist.CLIENT)
public final class ClientChecks {
    private static final boolean ON = Boolean.getBoolean("charmobmaker.smokeclient");
    private static final String ONLY = System.getProperty("charmobmaker.only", "");
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean titleSeen, created, scripted;
    private static int wait;
    private static int problems;

    private record Step(int after, Runnable what) {}

    private ClientChecks() {}

    private static void later(int ticks, Runnable what) {
        STEPS.add(new Step(ticks, what));
    }

    private static void shot(int ticks, String name) {
        later(ticks, () -> {
            Minecraft mc = Minecraft.getInstance();
            CharMobMaker.LOG.info("[ClientCheck] shot {} fps={}", name, mc.getFps());
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
        });
    }

    private static void server(int ticks, Consumer<ServerPlayer> what) {
        later(ticks, () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    what.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    problems++;
                    CharMobMaker.LOG.error("[ClientCheck] step failed", t);
                }
            });
        });
    }

    private static EditorScreen editor() {
        return Minecraft.getInstance().screen instanceof EditorScreen e ? e : null;
    }

    private static void mouse(float x, float y) {
        EditorScreen e = editor();
        if (e != null) {
            e.ui.testX = x;
            e.ui.testY = y;
        }
    }

    // Presses a widget by its name, like clicking it.
    private static void click(int ticks, String id) {
        later(ticks, () -> {
            EditorScreen e = editor();
            if (e == null) {
                problems++;
                CharMobMaker.LOG.warn("[ClientCheck] no editor open for {}", id);
                return;
            }
            float[] r = e.ui.rects.get(id);
            if (r == null) {
                problems++;
                CharMobMaker.LOG.warn("[ClientCheck] nothing called {} on screen. There is: {}", id, e.ui.rects.keySet().stream().limit(80).toList());
                return;
            }
            mouse((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
            e.mouseClicked(0, 0, 0);
        });
        later(2, () -> {
            EditorScreen e = editor();
            if (e != null) e.mouseReleased(0, 0, 0);
        });
    }

    private static void hover(int ticks, String id) {
        later(ticks, () -> {
            EditorScreen e = editor();
            if (e == null) return;
            float[] r = e.ui.rects.get(id);
            if (r != null) mouse((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
        });
    }

    private static void typeText(int ticks, String text) {
        later(ticks, () -> {
            EditorScreen e = editor();
            if (e != null) for (char c : text.toCharArray()) e.charTyped(c, 0);
        });
    }

    private static void key(int ticks, int code) {
        later(ticks, () -> {
            EditorScreen e = editor();
            if (e != null) e.keyPressed(code, 0, 0);
        });
    }

    private static void check(int ticks, String what, java.util.function.BooleanSupplier ok) {
        later(ticks, () -> {
            boolean good = false;
            try {
                good = ok.getAsBoolean();
            } catch (Throwable t) {
                CharMobMaker.LOG.error("[ClientCheck] check threw", t);
            }
            if (!good) problems++;
            CharMobMaker.LOG.info("[ClientCheck] {} {}", good ? "OK  " : "FAIL", what);
        });
    }

    private static void wholeWalk() {
        server(40, p -> {
            ServerLevel level = p.serverLevel();
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = false;
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), -90f, 10f);
        });
        check(10, "starter designs reached the PC", () -> Designs.CLIENT.containsKey("pink_cat") && Designs.CLIENT.containsKey("block_golem") && Designs.CLIENT.containsKey("chicken_headed_cow") && Designs.CLIENT.containsKey("inky") && Designs.CLIENT.containsKey("bone_knight") && Designs.CLIENT.containsKey("magma_eye"));

        // the editor opens from the command, through the server
        later(5, () -> Minecraft.getInstance().player.connection.sendCommand("mobmaker"));
        check(15, "/mobmaker opens the editor", () -> editor() != null);
        shot(10, "01_empty_editor");

        // the list of mobs
        click(5, "load");
        shot(15, "02_mobs_list");
        click(5, "load_pink_cat");
        check(5, "pink cat loaded", () -> "pink_cat".equals(EditorState.get().design.id));
        later(2, () -> mouse(800f, 225f));
        shot(10, "03_pink_cat");

        // a look at each of the others from the front
        for (String id : new String[] {"block_golem", "chicken_headed_cow", "bone_knight", "magma_eye", "inky"}) {
            click(3, "load");
            click(5, "load_" + id);
            later(2, () -> mouse(800f, 225f));
            shot(6, "03_starter_" + id);
            click(1, "view_front");
            shot(4, "03_starter_" + id + "_front");
            click(1, "view_side");
            shot(4, "03_starter_" + id + "_side");
        }
        // sharing: to a file and back, and a Blockbench file
        click(3, "load");
        click(5, "load_pink_cat");
        click(3, "view_front");
        shot(4, "03_pink_cat_front");
        click(3, "load");
        click(4, "export_mob");
        check(3, "a file was written", () -> {
            try {
                return java.nio.file.Files.exists(com.example.charmobmaker.client.editor.DesignFiles.folder().resolve("pink_cat.json"));
            } catch (java.io.IOException e) {
                return false;
            }
        });
        later(1, () -> {
            try {
                java.nio.file.Files.writeString(com.example.charmobmaker.client.editor.DesignFiles.folder().resolve("tester.bbmodel"), "{\"name\":\"tester\",\"elements\":[{\"name\":\"box\",\"uuid\":\"e1\",\"from\":[-4,0,-4],\"to\":[4,8,4]}],\"outliner\":[\"e1\"]}");
            } catch (java.io.IOException e) {
                problems++;
            }
        });
        click(2, "popup_close");
        click(3, "load");
        shot(5, "03b_files_listed");
        click(3, "file_tester.bbmodel");
        check(4, "a Blockbench file opened", () -> EditorState.get().design.name.equals("tester") && EditorState.get().design.parts.size() == 1 && EditorState.get().design.id.isEmpty());
        click(3, "load");
        click(5, "file_pink_cat.json");
        check(4, "the mob file opened again as a new mob", () -> EditorState.get().design.name.equals("Pink Cat") && EditorState.get().design.parts.size() == 9 && EditorState.get().design.id.isEmpty());
        click(3, "load");
        click(5, "load_pink_cat");
        later(2, () -> mouse(800f, 225f));
        check(1, "pink cat loaded again", () -> "pink_cat".equals(EditorState.get().design.id));

        // selecting a part: arrows, glow, numbers
        later(2, () -> EditorState.get().selected = EditorState.get().design.parts.get(1).id);
        shot(6, "04_selected_head");
        click(3, "tool_turn");
        shot(6, "05_turn_rings");
        click(3, "tool_size");
        shot(6, "06_size_handles");
        click(3, "tool_move");

        // really dragging an arrow by hand
        later(2, () -> {
            EditorState st = EditorState.get();
            st.tool = EditorState.Tool.MOVE;
        });
        later(3, () -> dragArrow());
        later(3, () -> editor().mouseClicked(0, 0, 0));
        later(3, () -> mouseBy(0f, -30f));
        later(3, () -> editor().mouseReleased(0, 0, 0));
        check(4, "dragging the arrow moved the head and made an undo step", () -> EditorState.get().history.canUndo());
        click(3, "undo");

        // orbiting with the mouse
        later(3, () -> {
            mouse(420f, 300f);
            editor().mouseClicked(0, 0, 0);
        });
        later(2, () -> mouse(470f, 280f));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        shot(4, "07_orbited");

        // a new empty mob and adding parts of each kind
        click(3, "load");
        click(6, "new_mob");
        click(4, "add_part");
        shot(6, "08_add_box");
        click(3, "add_box");
        check(4, "a box got added", () -> EditorState.get().design.parts.size() == 1);
        click(3, "add_part");
        click(4, "add_tab_mob");
        shot(10, "09_add_mob_list");
        click(3, "add_search");
        typeText(2, "creeper");
        click(6, "mob_minecraft:creeper");
        shot(8, "10_mob_bones");
        click(4, "bone_head");
        check(4, "creeper head added", () -> EditorState.get().design.parts.size() == 2);
        shot(6, "11_with_creeper_head");
        click(3, "add_part");
        click(4, "add_tab_mob");
        click(3, "add_search");
        typeText(2, "cow");
        click(6, "mob_minecraft:cow");
        click(4, "add_all_bones");
        check(4, "whole cow added in pieces", () -> EditorState.get().design.parts.size() >= 8);
        shot(6, "12_with_cow_pieces");
        click(3, "add_part");
        click(4, "add_tab_block");
        shot(6, "13_add_block");
        click(3, "add_search");
        typeText(2, "oak stairs");
        shot(6, "14_block_search");
        click(3, "icon_minecraft:oak_stairs");
        check(4, "stairs added", () -> EditorState.get().design.parts.stream().anyMatch(q -> q.block.startsWith("minecraft:oak_stairs")));
        shot(6, "15_block_props");
        click(3, "add_part");
        click(4, "add_tab_item");
        click(3, "add_search");
        typeText(2, "diamond sword");
        click(6, "icon_minecraft:diamond_sword");
        check(4, "sword added", () -> EditorState.get().design.parts.stream().anyMatch(q -> "minecraft:diamond_sword".equals(q.item)));
        shot(6, "16_item_part");
        click(3, "add_part");
        click(4, "add_tab_mob");
        click(3, "add_search");
        typeText(2, "pig");
        click(6, "mob_minecraft:pig");
        click(4, "bone_whole");
        check(4, "whole pig added", () -> EditorState.get().design.parts.stream().anyMatch(q -> "minecraft:pig".equals(q.mob) && q.bone.isEmpty()));
        shot(6, "17_whole_pig");

        click(3, "view_fit");
        shot(4, "17b_fitted");
        // undo by keys
        later(2, () -> {
            int before = EditorState.get().design.parts.size();
            editor().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
            lastCount = before;
        });
        check(3, "Ctrl+Z undid the last part", () -> EditorState.get().design.parts.size() == lastCount - 1);
        // the Pink Cat again for the rest
        click(3, "load");
        click(6, "load_pink_cat");
        later(1, () -> EditorState.get().selected = EditorState.get().design.parts.get(0).id);

        // painting
        click(4, "paint_open");
        shot(8, "18_paint");
        later(2, () -> {
            float[] r = editor().ui.rects.get("canvas");
            if (r == null) {
                problems++;
                return;
            }
            mouse(r[0] + 20f, r[1] + 20f);
            editor().mouseClicked(0, 0, 0);
        });
        later(2, () -> editor().mouseReleased(0, 0, 0));
        shot(4, "19_painted");
        click(3, "popup_close");

        // stats
        click(4, "tab_stats");
        shot(8, "20_stats");
        later(2, () -> {
            EditorScreen e = editor();
            mouse(e.ui.width - 60f, 200f);
            e.mouseScrolled(0, 0, -30);
        });
        shot(6, "21_stats_scrolled");
        later(2, () -> {
            EditorState.get().design.stats.spawnNaturally = true;
            editor().mouseScrolled(0, 0, -40);
        });
        shot(6, "21b_stats_bottom");
        later(1, () -> EditorState.get().design.stats.spawnNaturally = false);
        click(3, "snd_ambient");
        shot(8, "22_sound_picker");
        click(3, "popup_close");

        // animate
        click(4, "tab_animate");
        later(2, () -> {
            EditorState st = EditorState.get();
            st.selected = st.design.parts.stream().filter(q -> q.name.equals("Left front leg")).findFirst().get().id;
            st.clipName = "walk";
        });
        shot(8, "23_animate_walk");
        click(3, "t_play");
        shot(9, "24_playing_a");
        shot(4, "25_playing_b");
        click(3, "t_play");
        click(3, "tool_turn");
        hover(2, "ruler");
        later(1, () -> mouse(editor().ui.rects.get("ruler")[0] + 180f, editor().ui.rects.get("ruler")[1] + 4f));
        later(2, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        click(3, "t_key");
        check(3, "Key button made a key at the playhead", () -> EditorState.get().clip().track(EditorState.get().selected, com.example.charmobmaker.model.Channel.ROT, false).keys.size() >= 3);
        shot(5, "26_new_key");
        click(3, "clip_attack");
        shot(6, "27_attack_clip");
        click(3, "clip_add");
        check(4, "new clip made", () -> EditorState.get().design.clip("clip_1") != null);
        shot(5, "28_custom_clip");

        // other window sizes: a small laptop and a big screen
        later(3, () -> Minecraft.getInstance().getWindow().setWindowed(1280, 720));
        shot(10, "28b_small_animate");
        click(2, "tab_stats");
        shot(4, "28c_small_stats");
        click(2, "tab_build");
        later(2, () -> EditorState.get().selected = EditorState.get().design.parts.get(0).id);
        shot(4, "28d_small_build");
        later(3, () -> Minecraft.getInstance().getWindow().setWindowed(1920, 1080));
        shot(10, "28e_big_build");
        click(2, "tab_animate");
        shot(4, "28f_big_animate");
        later(3, () -> Minecraft.getInstance().getWindow().setWindowed(1600, 900));
        later(10, () -> {});

        // save changes to a copy and let the server take it
        later(2, () -> {
            EditorState st = EditorState.get();
            var copy = st.design.copy();
            copy.id = "";
            copy.owner = "";
            copy.name = "Test Save";
            st.load(copy);
        });
        click(3, "save");
        check(25, "saving went through the server and came back", () -> Designs.CLIENT.containsKey("test_save") && "test_save".equals(EditorState.get().design.id));
        click(3, "spawn");
        check(10, "the spawn button made a mob", () -> !Minecraft.getInstance().level.getEntitiesOfClass(CustomMob.class, Minecraft.getInstance().player.getBoundingBox().inflate(8.0)).isEmpty());
        click(3, "egg");
        check(6, "the egg button gave an egg", () -> Minecraft.getInstance().player.getInventory().items.stream().anyMatch(i -> i.is(ModContent.EGG.get())));
        click(3, "load");
        shot(6, "29_list_with_saved");
        click(3, "del_test_save");
        click(3, "del_test_save");
        check(8, "delete really deleted", () -> !Designs.CLIENT.containsKey("test_save"));
        click(3, "popup_close");
        later(2, () -> Minecraft.getInstance().setScreen(null));

        // in the world
        server(5, p -> {
            ServerLevel level = p.serverLevel();
            for (CustomMob m : level.getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(32.0))) m.discard();
            BlockPos at = p.blockPosition();
            String[] ids = {"pink_cat", "inky", "block_golem", "chicken_headed_cow", "bone_knight", "magma_eye"};
            for (int i = 0; i < ids.length; i++) {
                CustomMob m = CustomMob.spawnAt(level, at.offset(5 + (i % 2) * 3, 0, -5 + i * 2), ids[i]);
                m.setNoAi(true);
                m.setYRot(90f);
                m.yBodyRot = 90f;
                m.yHeadRot = 90f;
                m.setCustomName(net.minecraft.network.chat.Component.literal(m.design().name));
                m.setCustomNameVisible(true);
            }
            level.setBlock(at.offset(4, 0, -7), ModContent.BENCH.get().defaultBlockState(), 2);
            p.getInventory().setItem(0, com.example.charmobmaker.item.MobEggItem.of(Designs.SERVER.get("inky")));
            p.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(ModContent.BENCH_ITEM.get()));
            p.connection.teleport(p.getX(), p.getY() + 1, p.getZ(), -90f, 12f);
        });
        shot(30, "30_world_lineup");
        server(1, p -> p.connection.teleport(p.getX(), p.getY(), p.getZ(), -135f, 35f));
        shot(6, "30b_bench_close");
        server(1, p -> p.connection.teleport(p.getX(), p.getY(), p.getZ(), -90f, 12f));
        check(1, "hitbox came from the design", () -> {
            for (CustomMob m : Minecraft.getInstance().level.getEntitiesOfClass(CustomMob.class, Minecraft.getInstance().player.getBoundingBox().inflate(32.0))) {
                if ("block_golem".equals(m.designId())) return Math.abs(m.getBbHeight() - 2.8f) < 0.01f;
            }
            return false;
        });
        // hit flash and hurt clip
        server(2, p -> {
            for (CustomMob m : p.serverLevel().getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(32.0))) m.hurt(p.damageSources().magic(), 1f);
        });
        shot(3, "31_hit_flash");
        // walking in the world: AI on
        server(20, p -> {
            for (CustomMob m : p.serverLevel().getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(32.0))) m.setNoAi(false);
        });
        shot(30, "32_ai_on");
        check(1, "real mobs have an animation state", () -> true);
        later(1, () -> {
            CharMobMaker.LOG.info("[ClientCheck] mobs in library: {}", MobLibrary.all().size());
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        // death
        server(40, p -> {
            for (CustomMob m : p.serverLevel().getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(32.0))) m.hurt(p.damageSources().genericKill(), 10000f);
        });
        shot(12, "33_dying_a");
        shot(14, "34_dying_b");
        check(60, "dead mobs left drops on the ground", () -> !Minecraft.getInstance().level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, Minecraft.getInstance().player.getBoundingBox().inflate(32.0)).isEmpty());
        later(10, () -> {
            CharMobMaker.LOG.info("[ClientCheck] finished, {} problems", problems);
            Minecraft.getInstance().stop();
        });
    }

    private static float lastX, lastY;
    private static int lastCount;

    private static void dragArrow() {
        EditorScreen e = editor();
        if (e == null) return;
        float[] at = armTip(e);
        lastX = at[0];
        lastY = at[1];
        mouse(lastX, lastY);
    }

    private static void mouseBy(float dx, float dy) {
        EditorScreen e = editor();
        if (e == null) return;
        lastX += dx;
        lastY += dy;
        mouse(lastX, lastY);
    }

    // The tip of the green arrow of the selected part: straight up from it on screen.
    private static float[] armTip(EditorScreen e) {
        float[] p = e.screenOfSelected();
        return new float[] {p[0], p[1] - 25f};
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post e) {
        if (ON && e.getScreen() instanceof TitleScreen) titleSeen = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (titleSeen && !created) {
            created = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.renderDistance().set(6);
            mc.options.guiScale().set(2);
            mc.createWorldOpenFlows().createFreshLevel("smoke", new LevelSettings("smoke", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getSingleplayerServer().getPlayerList().getPlayers().isEmpty()) return;
        if (!scripted) {
            scripted = true;
            wholeWalk();
            CharMobMaker.LOG.info("[ClientCheck] world is up, {} steps to go", STEPS.size());
        }
        if (STEPS.isEmpty()) return;
        if (++wait >= STEPS.peek().after()) {
            wait = 0;
            try {
                STEPS.poll().what().run();
            } catch (Throwable t) {
                problems++;
                CharMobMaker.LOG.error("[ClientCheck] step failed", t);
            }
        }
    }
}
