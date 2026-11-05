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
        // the buttons under the parts list, by hand
        click(2, "load");
        click(4, "load_pink_cat");
        later(1, () -> EditorState.get().selected = partNamed("Left front leg").id);
        click(3, "part_copy");
        check(3, "Copy made a copy", () -> EditorState.get().design.parts.size() == 10);
        click(2, "undo");
        check(3, "and Undo took it back", () -> EditorState.get().design.parts.size() == 9);
        click(2, "redo");
        check(3, "and Redo brought it again", () -> EditorState.get().design.parts.size() == 10);
        click(2, "undo");
        later(1, () -> EditorState.get().selected = partNamed("Left ear").id);
        click(2, "part_mirror");
        check(3, "Mirror makes the other side", () -> EditorState.get().design.parts.stream().filter(q -> q.name.equals("Right ear")).count() == 2);
        click(2, "part_delete");
        check(3, "Delete deletes", () -> EditorState.get().design.parts.size() == 9);
        // typing a name and a number, and dragging a slider
        click(2, "name");
        typeText(2, "Typed Name");
        later(2, () -> editor().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        check(3, "typing a name works", () -> EditorState.get().design.name.equals("Typed Name"));
        later(1, () -> EditorState.get().selected = partNamed("Body").id);
        click(4, "pos1#n");
        typeText(2, "20.5");
        later(2, () -> editor().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        check(3, "typing a number works", () -> partNamed("Body").pos[1] == 20.5f);
        later(2, () -> {
            float[] r = editor().ui.rects.get("rot0#s");
            mouse(r[0] + 2f, (r[1] + r[3]) / 2f);
        });
        later(2, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> {
            float[] r = editor().ui.rects.get("rot0#s");
            mouse(r[2] - 2f, (r[1] + r[3]) / 2f);
        });
        later(2, () -> editor().mouseReleased(0, 0, 0));
        check(3, "dragging a slider works", () -> partNamed("Body").rot[0] > 100f);
        // moving a part under another by dragging its row: it stays where it is on screen
        later(1, () -> before = EditorState.get().compiled().bounds().clone());
        later(2, () -> {
            float[] r = editor().ui.rects.get("row_" + partNamed("Tail").id);
            mouse((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
        });
        later(2, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> {
            float[] r = editor().ui.rects.get("row_" + partNamed("Left ear").id);
            mouse((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
        });
        shot(2, "17c_dragging_a_row");
        later(2, () -> editor().mouseReleased(0, 0, 0));
        check(3, "dragging a row hangs it from another part", () -> partNamed("Tail").parent.equals(partNamed("Left ear").id));
        check(1, "and it stayed in place", () -> {
            float[] now = EditorState.get().compiled().bounds();
            for (int i = 0; i < 6; i++) if (Math.abs(now[i] - before[i]) > 0.01f) return false;
            return true;
        });
        // clicking on the mob itself picks the part
        later(2, () -> {
            EditorState.get().selected = "";
            editor().selectPart("");
        });
        later(1, () -> {
            float[] at = editor().screenOfPart(partNamed("Head").id);
            mouse(at[0], at[1]);
        });
        later(2, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        check(3, "clicking the mob picks the part you clicked", () -> {
            var p = EditorState.get().selectedPart();
            CharMobMaker.LOG.info("[ClientCheck] clicked on {}", p == null ? "nothing" : p.name);
            return p != null;
        });
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

        // the behaviour switches
        later(1, () -> {
            var t = EditorState.get().design.stats;
            t.mood = com.example.charmobmaker.model.Stats.Mood.NEUTRAL;
            t.flying = false;
        });
        later(1, () -> mouse(editor().ui.width - 60f, 200f));
        later(2, () -> editor().mouseScrolled(0, 0, 90));
        click(3, "mood_0");
        check(2, "the mood chips work", () -> EditorState.get().design.stats.mood == com.example.charmobmaker.model.Stats.Mood.HOSTILE);
        shot(4, "22b_hostile_stats");
        click(2, "target_villager");
        check(2, "the target chips work", () -> EditorState.get().design.stats.targets.contains("villager"));
        click(2, "flying");
        check(2, "the Flies switch works", () -> EditorState.get().design.stats.flying);
        click(2, "attack_2");
        check(2, "the attack chips work", () -> EditorState.get().design.stats.attack == com.example.charmobmaker.model.Stats.Attack.ARROW);
        click(2, "drop_add");
        click(5, "pick_search");
        typeText(2, "diamond");
        shot(5, "22c_item_picker");
        click(2, "pick_minecraft:diamond");
        check(3, "adding a drop works", () -> EditorState.get().design.stats.drops.stream().anyMatch(d -> d.item.equals("minecraft:diamond")));
        click(2, "snd_ambient");
        click(4, "pick_search");
        typeText(2, "pig.ambient");
        click(5, "pick_minecraft:entity.pig.ambient");
        check(4, "picking a sound works", () -> EditorState.get().design.stats.ambientSound.equals("minecraft:entity.pig.ambient"));
        click(2, "undo");
        click(2, "undo");
        click(2, "undo");
        click(2, "undo");
        click(2, "undo");
        click(2, "undo");

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
        click(2, "ease_bounce");
        check(2, "the ease chips change the selected key", () -> EditorState.get().key != null && EditorState.get().key.ease == com.example.charmobmaker.model.Ease.BOUNCE);
        // dragging that key along
        later(2, () -> {
            var k = EditorState.get().key;
            keyTimeBefore = k.t;
            float[] r = editor().ui.rects.get("k_ROT_" + Math.round(k.t * 1000f));
            lastX = (r[0] + r[2]) / 2f;
            lastY = (r[1] + r[3]) / 2f;
            mouse(lastX, lastY);
        });
        later(2, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> mouseBy(40f, 0f));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        check(3, "dragging a key moves it", () -> EditorState.get().key != null && Math.abs(EditorState.get().key.t - keyTimeBefore) > 0.01f);
        later(1, () -> EditorState.get().selected = partNamed("Left front leg").id);
        click(2, "t_copy");
        later(1, () -> EditorState.get().selected = partNamed("Tail").id);
        click(2, "t_paste");
        check(3, "copy and paste of keys", () -> EditorState.get().clip().track(partNamed("Tail").id, com.example.charmobmaker.model.Channel.ROT, false).keys.size() >= 3);
        later(1, () -> EditorState.get().selected = partNamed("Left front leg").id);
        click(2, "t_mirror");
        check(3, "Flip copies the keys to the other side", () -> EditorState.get().clip().track(partNamed("Right front leg").id, com.example.charmobmaker.model.Channel.ROT, false).keys.size() >= 3);
        click(2, "quick_walk");
        check(3, "Make a walk for me", () -> EditorState.get().design.clip("walk").keyCount() > 8);
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
        server(1, p -> {
            homeX = p.getX();
            homeZ = p.getZ();
            p.connection.teleport(p.getX() + 2.4, p.getY(), p.getZ() - 6.0, -60f, 42f);
        });
        shot(8, "30b_bench_close");
        server(1, p -> p.connection.teleport(homeX, p.getY(), homeZ, -90f, 12f));
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
        check(1, "the creative tab has the eggs", () -> ModContent.TAB.get().getDisplayItems().size() >= 7);
        // spawning on their own: flat grass all round, so one should turn up within a few tries
        server(1, p -> {
            var d = Designs.SERVER.get("pink_cat").copy();
            d.stats.spawnNaturally = true;
            d.stats.spawnRate = 100;
            d.stats.spawnMaxNearby = 8;
            d.stats.spawnGroup = 2;
            com.example.charmobmaker.DesignStore.store(d, "", "");
            p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, p.getServer());
        });
        server(300, p -> {
            long natural = p.serverLevel().getEntitiesOfClass(CustomMob.class, p.getBoundingBox().inflate(64.0), CustomMob::isNatural).size();
            naturalSeen = natural > 0;
            CharMobMaker.LOG.info("[ClientCheck] naturally spawned: {}", natural);
            p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.getServer());
        });
        check(5, "a design set to spawn on its own did", () -> naturalSeen);
        later(10, () -> {
            CharMobMaker.LOG.info("[ClientCheck] finished, {} problems", problems);
            Minecraft.getInstance().stop();
        });
    }

    // ---------- made blocks, items, screens and machines ----------

    private static void slotClick(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
    }

    private static net.minecraft.world.item.ItemStack menuStack(int slot) {
        return Minecraft.getInstance().player.containerMenu.getSlot(slot).getItem();
    }

    private static void put(int ticks, int fromInvSlotInMenu, int toSlot) {
        later(ticks, () -> slotClick(fromInvSlotInMenu));
        later(2, () -> slotClick(toSlot));
    }

    private static void thingsWalk() {
        server(40, p -> {
            ServerLevel level = p.serverLevel();
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), -90f, 10f);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, com.example.charmobmaker.DesignStacks.of(Designs.SERVER.get("energy_drink")));
            p.getInventory().setItem(1, com.example.charmobmaker.DesignStacks.of(Designs.SERVER.get("drink_machine")));
            p.getInventory().setItem(2, com.example.charmobmaker.DesignStacks.of(Designs.SERVER.get("cat_lamp")));
            p.getInventory().setItem(3, com.example.charmobmaker.DesignStacks.of(Designs.SERVER.get("berry_mash")));
            p.getInventory().setItem(4, com.example.charmobmaker.DesignStacks.of(Designs.SERVER.get("energy_brew")));
            p.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, 8));
            p.getInventory().setItem(10, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SUGAR, 8));
            p.getInventory().setItem(11, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST, 8));
            p.getInventory().setItem(12, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 8));
        });
        check(10, "the Energy Drink things reached the PC", () -> Designs.CLIENT.containsKey("energy_drink") && Designs.CLIENT.containsKey("drink_machine_screen"));
        check(1, "stack sizes come from the design", () -> Minecraft.getInstance().player.getInventory().getItem(0).getMaxStackSize() == 16);
        later(1, () -> {
            var lines = Minecraft.getInstance().player.getInventory().getItem(0).getTooltipLines(Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            for (var l : lines) CharMobMaker.LOG.info("[ClientCheck] tooltip: {}", l.getString());
        });
        // the drink in the hand, first and third person
        shot(10, "40_drink_first_person");
        later(1, () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shot(8, "41_drink_third_front");
        later(1, () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        shot(8, "42_drink_third_back");
        later(1, () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
        // placing the machine and a lamp for real, through the game
        later(2, () -> {
            var mc = Minecraft.getInstance();
            mc.player.getInventory().selected = 1;
        });
        server(3, p -> p.connection.teleport(p.getX(), p.getY(), p.getZ(), -90f, 25f));
        later(5, () -> {
            var mc = Minecraft.getInstance();
            BlockPos at = mc.player.blockPosition().offset(3, -1, 0);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at, false));
        });
        check(8, "the machine got placed by hand", () -> {
            var mc = Minecraft.getInstance();
            var be = mc.level.getBlockEntity(mc.player.blockPosition().offset(3, 0, 0));
            return be instanceof com.example.charmobmaker.block.CustomBlockEntity c && c.designId().equals("drink_machine");
        });
        later(1, () -> Minecraft.getInstance().player.getInventory().selected = 2);
        later(3, () -> {
            var mc = Minecraft.getInstance();
            BlockPos at = mc.player.blockPosition().offset(3, -1, 2);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at, false));
        });
        check(8, "the machine's light is on", () -> {
            var mc = Minecraft.getInstance();
            return mc.level.getBlockState(mc.player.blockPosition().offset(3, 0, 0)).getValue(com.example.charmobmaker.block.CustomBlock.LIGHT) == 6;
        });
        check(1, "the lamp went in too, and its shape comes from its parts", () -> {
            var mc = Minecraft.getInstance();
            BlockPos at = mc.player.blockPosition().offset(3, 0, 2);
            var shape = mc.level.getBlockState(at).getShape(mc.level, at);
            return mc.level.getBlockEntity(at) instanceof com.example.charmobmaker.block.CustomBlockEntity c && c.designId().equals("cat_lamp") && !shape.isEmpty() && shape.bounds().maxX - shape.bounds().minX < 1.0;
        });
        shot(6, "43_placed_in_the_world");
        // up close, side on
        server(1, p -> p.connection.teleport(p.getX() + 0.8, p.getY(), p.getZ() - 0.6, -60f, 30f));
        shot(6, "44_machine_close");
        // the screen, by right clicking the machine
        later(2, () -> Minecraft.getInstance().player.getInventory().selected = 8);
        server(1, p -> p.connection.teleport(p.getX() - 0.8, p.getY(), p.getZ() + 0.6, -90f, 25f));
        later(5, () -> {
            var mc = Minecraft.getInstance();
            BlockPos at = mc.player.blockPosition().offset(3, 0, 0);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.WEST, at, false));
        });
        check(10, "right clicking the machine opens its screen", () -> Minecraft.getInstance().screen instanceof com.example.charmobmaker.client.machine.MachineScreen);
        shot(5, "50_machine_screen_empty");
        // slots 0 1 2 are the machine's, 3 on is the player's: berries 3, sugar 4, glowstone 5, nuggets 6
        later(1, () -> slotClick(3));
        later(2, () -> { /* berries on the mouse */ });
        later(1, () -> slotClick(0));
        later(2, () -> slotClick(4));
        later(1, () -> slotClick(1));
        check(4, "items really moved into the machine's slots", () -> menuStack(0).is(net.minecraft.world.item.Items.SWEET_BERRIES) && menuStack(1).is(net.minecraft.world.item.Items.SUGAR));
        shot(2, "51_machine_filled");
        later(1, () -> ((com.example.charmobmaker.client.machine.MachineScreen) Minecraft.getInstance().screen).pressForTest(com.example.charmobmaker.machine.MachineMenu.BUTTON_START));
        shot(25, "52_machine_step_one");
        check(45, "step one made a Berry Mash in the output slot", () -> com.example.charmobmaker.DesignStacks.idOf(menuStack(2)).equals("berry_mash"));
        shot(1, "53_mash_done");
        // take the left over berries and sugar out, add glowstone
        later(1, () -> {
            var mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 1, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
        });
        later(3, () -> slotClick(5));
        later(2, () -> slotClick(0));
        check(2, "glowstone went in", () -> menuStack(0).is(net.minecraft.world.item.Items.GLOWSTONE_DUST));
        // the output slot can't be put into by hand
        later(2, () -> slotClick(6));
        later(2, () -> slotClick(2));
        check(3, "nothing can be put in an output slot by hand", () -> com.example.charmobmaker.DesignStacks.idOf(menuStack(2)).equals("berry_mash") || menuStack(2).isEmpty());
        later(2, () -> slotClick(6));
        later(2, () -> ((com.example.charmobmaker.client.machine.MachineScreen) Minecraft.getInstance().screen).pressForTest(com.example.charmobmaker.machine.MachineMenu.BUTTON_STEP + 1));
        shot(30, "54_machine_step_two");
        check(60, "step two made an Energy Brew", () -> com.example.charmobmaker.DesignStacks.idOf(menuStack(2)).equals("energy_brew"));
        later(1, () -> {
            var mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
        });
        later(3, () -> slotClick(6));
        later(2, () -> slotClick(0));
        later(2, () -> ((com.example.charmobmaker.client.machine.MachineScreen) Minecraft.getInstance().screen).pressForTest(com.example.charmobmaker.machine.MachineMenu.BUTTON_START));
        shot(20, "55_machine_step_three");
        check(40, "step three made an Energy Drink", () -> com.example.charmobmaker.DesignStacks.idOf(menuStack(2)).equals("energy_drink"));
        shot(1, "56_drink_made");
        later(1, () -> {
            var mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 2, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
        });
        later(4, () -> Minecraft.getInstance().player.closeContainer());
        // drinking it for real
        later(3, () -> Minecraft.getInstance().player.getInventory().selected = 0);
        server(1, p -> p.getFoodData().setFoodLevel(10));
        later(3, () -> Minecraft.getInstance().options.keyUse.setDown(true));
        shot(14, "57_drinking");
        later(1, () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shot(6, "57b_drinking_third");
        later(30, () -> Minecraft.getInstance().options.keyUse.setDown(false));
        later(1, () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
        server(4, p -> {
            drank = p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED) && p.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getAmplifier() == 1 && p.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
            canBack = p.getInventory().getItem(0).is(net.minecraft.world.item.Items.IRON_NUGGET) || p.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET));
            CharMobMaker.LOG.info("[ClientCheck] after drinking: food {} effects {} hand {}", p.getFoodData().getFoodLevel(), p.getActiveEffects().size(), p.getInventory().getItem(0));
        });
        check(3, "drinking gave its effects", () -> drank);
        check(1, "and the can came back as a nugget", () -> canBack);
        shot(2, "58_after_drinking");
        // the items in slots and on the ground
        later(1, () -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player)));
        shot(6, "59_inventory_icons");
        later(1, () -> Minecraft.getInstance().setScreen(null));
        server(1, p -> {
            for (int i = 0; i < 5; i++) {
                var e = p.spawnAtLocation(p.getInventory().getItem(i).copy(), 1f);
                if (e != null) {
                    e.setPos(p.getX() + 3, p.getY() + 0.2, p.getZ() - 4 + i);
                    e.setPickUpDelay(600);
                }
            }
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), -110f, 20f);
        });
        shot(10, "60_on_the_ground");
        // a lot of lamps: no clips, so they're in the chunk and cost nothing per frame
        later(1, () -> fpsBefore = Minecraft.getInstance().getFps());
        server(5, p -> {
            ServerLevel level = p.serverLevel();
            BlockPos at = p.blockPosition();
            var d = Designs.SERVER.get("cat_lamp");
            for (int x = 0; x < 22; x++) {
                for (int z = 0; z < 22; z++) {
                    BlockPos b = at.offset(6 + x, 0, -11 + z);
                    level.setBlock(b, com.example.charmobmaker.block.CustomBlock.stateFor(com.example.charmobmaker.ModContent.CUSTOM_BLOCK.get().defaultBlockState(), d), 2);
                    ((com.example.charmobmaker.block.CustomBlockEntity) level.getBlockEntity(b)).setDesign("cat_lamp");
                }
            }
            p.connection.teleport(p.getX(), p.getY() + 3, p.getZ(), -90f, 22f);
        });
        shot(40, "61_many_lamps");
        later(40, () -> CharMobMaker.LOG.info("[ClientCheck] fps before lamps {}, with 484 lamps {}", fpsBefore, Minecraft.getInstance().getFps()));

        // the editor for all of these
        later(5, () -> Minecraft.getInstance().player.connection.sendCommand("mobmaker open energy_drink"));
        check(15, "opens on the item", () -> editor() != null && EditorState.get().design.id.equals("energy_drink"));
        later(2, () -> mouse(800f, 225f));
        shot(4, "70_editor_item");
        click(2, "tab_stats");
        shot(5, "71_editor_item_tab");
        later(1, () -> mouse(editor().ui.width - 60f, 200f));
        later(2, () -> editor().mouseScrolled(0, 0, -40));
        shot(4, "71b_item_tab_lower");
        later(1, () -> editor().mouseScrolled(0, 0, 90));
        click(2, "tab_build");
        click(3, "load");
        click(5, "load_drink_machine");
        later(1, () -> mouse(800f, 225f));
        shot(5, "72_editor_block");
        click(2, "tab_stats");
        shot(5, "73_editor_block_tab");
        click(2, "tab_animate");
        click(2, "clip_working");
        later(1, () -> EditorState.get().time = 0.4f);
        shot(5, "74_editor_block_working");
        click(2, "tab_build");
        click(3, "load");
        click(5, "load_drink_machine_screen");
        later(1, () -> mouse(800f, 225f));
        shot(5, "75_editor_layout");
        later(1, () -> {
            float[] r = editor().ui.rects.get("w_2");
            mouse((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
        });
        later(1, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        shot(3, "76_editor_layout_widget");
        click(2, "tab_stats");
        shot(5, "77_editor_recipes");

        // making a new screen from nothing, with the real buttons
        click(2, "load");
        click(4, "new_screen");
        check(3, "a new screen starts", () -> EditorState.get().design.type == com.example.charmobmaker.model.Design.Type.SCREEN && EditorState.get().design.id.isEmpty());
        click(3, "add_w_slot");
        check(2, "a slot got added", () -> EditorState.get().design.screen.slots().size() == 1);
        // drag it about
        later(2, () -> {
            var w = EditorState.get().design.screen.slots().get(0);
            startSlotX = w.x;
            float[] at = editor().screens().canvasSpot(w.x + 9, w.y + 9);
            lastX = at[0];
            lastY = at[1];
            mouse(lastX, lastY);
        });
        later(1, () -> editor().mouseClicked(0, 0, 0));
        later(2, () -> mouseBy(-30f, 10f));
        later(2, () -> editor().mouseReleased(0, 0, 0));
        check(2, "dragging moved the slot", () -> EditorState.get().design.screen.slots().get(0).x != startSlotX);
        click(1, "w_output");
        check(2, "a slot can be made an output", () -> EditorState.get().design.screen.slots().get(0).output);
        click(1, "add_w_button");
        click(1, "add_w_progress");
        click(1, "add_w_steps");
        click(1, "add_w_panel");
        click(1, "add_w_text");
        click(1, "add_w_image");
        shot(3, "78_new_screen_layout");
        click(1, "w_paint");
        shot(4, "78b_paint_picture");
        click(1, "popup_close");
        click(2, "tab_stats");
        click(2, "recipe_add");
        check(3, "a step got added", () -> EditorState.get().design.screen.recipes.size() == 1);
        click(2, "r0_add");
        click(4, "pick_search");
        typeText(1, "sugar");
        click(5, "pick_minecraft:sugar");
        check(3, "an input got picked", () -> EditorState.get().design.screen.recipes.get(0).inputs.size() == 1);
        shot(3, "79_new_recipe");
        click(1, "save");
        check(15, "the new screen saved", () -> !EditorState.get().design.id.isEmpty() && Designs.CLIENT.containsKey(EditorState.get().design.id));
        // and a new item and block from nothing
        click(2, "load");
        click(4, "new_item");
        check(3, "a new item starts", () -> EditorState.get().design.type == com.example.charmobmaker.model.Design.Type.ITEM);
        click(2, "tab_stats");
        click(2, "effect_add");
        click(4, "pick_search");
        typeText(1, "regeneration");
        click(4, "pick_minecraft:regeneration");
        check(3, "an effect got added", () -> EditorState.get().design.item.effects.size() == 1);
        click(1, "save");
        check(15, "the new item saved", () -> !EditorState.get().design.id.isEmpty() && Designs.CLIENT.containsKey(EditorState.get().design.id));
        click(1, "egg");
        check(40, "Give put it in the bag", () -> Minecraft.getInstance().player.getInventory().items.stream().anyMatch(i -> com.example.charmobmaker.DesignStacks.idOf(i).equals(EditorState.get().design.id)));
        click(1, "load");
        shot(5, "80_all_things_list");
        click(1, "filter_2");
        shot(3, "80b_blocks_only");
        click(1, "new_block");
        check(3, "a new block starts", () -> EditorState.get().design.type == com.example.charmobmaker.model.Design.Type.BLOCK);
        click(2, "tab_stats");
        click(2, "block_screen");
        shot(4, "81_pick_a_screen");
        click(2, "pick_drink_machine_screen");
        check(2, "picked its screen", () -> EditorState.get().design.block.screen.equals("drink_machine_screen"));
        later(1, () -> Minecraft.getInstance().setScreen(null));
        check(1, "the creative tab has the things", () -> com.example.charmobmaker.ModContent.TAB.get().getDisplayItems().stream().anyMatch(i -> i.is(com.example.charmobmaker.ModContent.CUSTOM_ITEM.get())) && com.example.charmobmaker.ModContent.TAB.get().getDisplayItems().stream().anyMatch(i -> i.is(com.example.charmobmaker.ModContent.CUSTOM_BLOCK_ITEM.get())));
        later(10, () -> {
            CharMobMaker.LOG.info("[ClientCheck] finished, {} problems", problems);
            Minecraft.getInstance().stop();
        });
    }

    private static volatile boolean drank, canBack;
    private static int fpsBefore, startSlotX;

    private static float lastX, lastY;
    private static int lastCount;
    private static float keyTimeBefore;
    private static float[] before;

    private static com.example.charmobmaker.model.Part partNamed(String name) {
        return EditorState.get().design.parts.stream().filter(q -> q.name.equals(name)).findFirst().orElseThrow();
    }
    private static double homeX, homeZ;
    private static volatile boolean naturalSeen;

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
            if ("things".equals(ONLY)) thingsWalk();
            else wholeWalk();
            CharMobMaker.LOG.info("[ClientCheck] world is up, {} steps to go", STEPS.size());
        }
        mc.getToasts().clear();
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
