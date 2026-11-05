package com.example.charmobmaker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

// Made blocks, items, screens and machines.
class ThingsTest {
    private static Design drink() {
        Design d = new Design();
        d.type = Design.Type.ITEM;
        d.name = "Energy Drink";
        d.item.use = ItemInfo.Use.DRINK;
        d.item.useSeconds = 2f;
        d.item.returns = "minecraft:iron_nugget";
        d.item.food = 2;
        d.item.cooldown = 10f;
        ItemInfo.Effect e = new ItemInfo.Effect();
        e.id = "minecraft:speed";
        e.level = 2;
        e.seconds = 60f;
        d.item.effects.add(e);
        d.item.lines.add("Tastes pink");
        return d;
    }

    private static Design machine() {
        Design d = new Design();
        d.type = Design.Type.SCREEN;
        d.name = "Machine";
        ScreenInfo s = d.screen;
        for (int i = 0; i < 3; i++) {
            ScreenInfo.Widget w = new ScreenInfo.Widget();
            w.kind = ScreenInfo.Widget.Kind.SLOT;
            w.x = 10 + i * 20;
            w.y = 20;
            w.output = i == 2;
            s.widgets.add(w);
        }
        ScreenInfo.Recipe r = new ScreenInfo.Recipe();
        r.name = "Mash";
        r.inputs.add(new ScreenInfo.Ingredient("minecraft:sugar", 2));
        r.inputs.add(new ScreenInfo.Ingredient("minecraft:sweet_berries", 1));
        r.output = "design:mash";
        r.seconds = 4f;
        s.recipes.add(r);
        return d;
    }

    @Test
    void itemsRoundTrip() throws Exception {
        Design d = drink();
        String text = DesignJson.write(d);
        Design back = DesignJson.read(text);
        assertEquals(text, DesignJson.write(back));
        assertEquals(Design.Type.ITEM, back.type);
        assertEquals("minecraft:iron_nugget", back.item.returns);
        assertEquals(2, back.item.effects.get(0).level);
        assertEquals("Tastes pink", back.item.lines.get(0));
    }

    @Test
    void blocksRoundTrip() throws Exception {
        Design d = new Design();
        d.type = Design.Type.BLOCK;
        d.block.light = 7;
        d.block.shape = BlockInfo.Shape.NONE;
        d.block.screen = "machine";
        Design back = DesignJson.read(DesignJson.write(d));
        assertEquals(Design.Type.BLOCK, back.type);
        assertEquals(7, back.block.light);
        assertEquals(BlockInfo.Shape.NONE, back.block.shape);
        assertEquals("machine", back.block.screen);
    }

    @Test
    void screensRoundTrip() throws Exception {
        Design d = machine();
        String text = DesignJson.write(d);
        Design back = DesignJson.read(text);
        assertEquals(text, DesignJson.write(back));
        assertEquals(3, back.screen.slots().size());
        assertTrue(back.screen.slots().get(2).output);
        assertEquals("design:mash", back.screen.recipes.get(0).output);
        assertEquals(2, back.screen.recipes.get(0).inputs.get(0).count);
    }

    @Test
    void oldMobFilesStillReadAsMobs() throws Exception {
        Design back = DesignJson.read("{\"name\":\"Old\",\"parts\":[]}");
        assertEquals(Design.Type.MOB, back.type);
    }

    @Test
    void tidyKeepsItemsSane() {
        Design d = drink();
        d.item.stack = 500;
        d.item.useSeconds = 1e9f;
        d.item.returns = "../../evil";
        d.item.food = 9999;
        d.item.heal = Float.NaN;
        d.item.effects.get(0).level = 500;
        d.item.effects.get(0).seconds = 1e9f;
        ItemInfo.Effect bad = new ItemInfo.Effect();
        bad.id = "not an id";
        d.item.effects.add(bad);
        for (int i = 0; i < 20; i++) d.item.lines.add("§cline " + i);
        DesignRules.tidy(d);
        assertEquals(64, d.item.stack);
        assertEquals(30f, d.item.useSeconds);
        assertEquals("", d.item.returns);
        assertEquals(40, d.item.food);
        assertEquals(0f, d.item.heal);
        assertEquals(1, d.item.effects.size());
        assertEquals(Limits.MAX_EFFECT_LEVEL, d.item.effects.get(0).level);
        assertEquals(Limits.MAX_EFFECT_SECONDS, d.item.effects.get(0).seconds);
        assertEquals(Limits.MAX_LINES, d.item.lines.size());
        assertFalse(d.item.lines.get(1).contains("§"));
    }

    @Test
    void tidyKeepsScreensSane() {
        Design d = machine();
        ScreenInfo s = d.screen;
        s.width = 100000;
        s.height = -5;
        for (int i = 0; i < 100; i++) {
            ScreenInfo.Widget w = new ScreenInfo.Widget();
            w.kind = ScreenInfo.Widget.Kind.SLOT;
            w.x = 99999;
            w.y = -99999;
            s.widgets.add(w);
        }
        ScreenInfo.Recipe r = s.recipes.get(0);
        r.seconds = -3f;
        r.inputs.get(0).count = 9999;
        r.output = "nonsense";
        r.inputs.add(new ScreenInfo.Ingredient("../evil", 1));
        for (int i = 0; i < 40; i++) s.recipes.add(new ScreenInfo.Recipe());
        DesignRules.tidy(d);
        assertEquals(Limits.MAX_SCREEN_W, s.width);
        assertEquals(Limits.MIN_SCREEN_H, s.height);
        assertTrue(s.slots().size() <= Limits.MAX_SLOTS);
        assertEquals(Limits.MAX_RECIPES, s.recipes.size());
        for (ScreenInfo.Widget w : s.widgets) {
            assertTrue(w.x >= 0 && w.x + w.w <= s.width, "x " + w.x);
            assertTrue(w.y >= 0 && w.y + w.h <= s.height, "y " + w.y);
        }
        assertEquals(0.05f, r.seconds, 1e-4);
        assertEquals(64, r.inputs.get(0).count);
        assertEquals(2, r.inputs.size());
        assertEquals("minecraft:air", r.output);
    }

    @Test
    void itemRefs() {
        assertTrue(ItemRef.valid("minecraft:sugar"));
        assertTrue(ItemRef.valid("design:energy_drink"));
        assertFalse(ItemRef.valid("design:../evil"));
        assertFalse(ItemRef.valid("design:"));
        assertFalse(ItemRef.valid(""));
        assertTrue(ItemRef.isDesign("design:a"));
        assertEquals("a", ItemRef.designId("design:a"));
    }

    private static MachineRules.Held held(String ref, int count) {
        return new MachineRules.Held(ref, count, 64);
    }

    private static final ToIntFunction<String> SIXTY_FOUR = r -> 64;

    @Test
    void aStepNeedsAllItsInputsAndRoom() {
        ScreenInfo.Recipe r = machine().screen.recipes.get(0);
        boolean[] out = {false, false, true};
        // not enough sugar
        assertNull(MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 1), held("minecraft:sweet_berries", 1), held("", 0)}, out, 64));
        // enough
        MachineRules.Plan p = MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 3), held("minecraft:sweet_berries", 1), held("", 0)}, out, 64);
        assertNotNull(p);
        assertArrayEquals(new int[] {2, 1, 0}, p.take());
        assertEquals(2, p.resultSlot());
        // the output slot is holding something else
        assertNull(MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 3), held("minecraft:sweet_berries", 1), held("minecraft:dirt", 1)}, out, 64));
        // the same thing stacks up
        assertEquals(2, MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 3), held("minecraft:sweet_berries", 1), held("design:mash", 5)}, out, 64).resultSlot());
        // but not past a full stack
        assertNull(MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 3), held("minecraft:sweet_berries", 1), held("design:mash", 64)}, out, 64));
    }

    @Test
    void aResultCanFeedTheNextStepWithoutMovingIt() {
        ScreenInfo.Recipe brew = new ScreenInfo.Recipe();
        brew.inputs.add(new ScreenInfo.Ingredient("design:mash", 1));
        brew.inputs.add(new ScreenInfo.Ingredient("minecraft:glowstone_dust", 1));
        brew.output = "design:brew";
        boolean[] out = {false, false, true};
        MachineRules.Plan p = MachineRules.plan(brew, new MachineRules.Held[] {held("minecraft:glowstone_dust", 1), held("", 0), held("design:mash", 1)}, out, 64);
        assertNotNull(p);
        assertArrayEquals(new int[] {1, 0, 1}, p.take());
        assertEquals(2, p.resultSlot()); // the mash is used up, so the brew fits where it was
    }

    @Test
    void inputsAcrossSlotsAddUp() {
        ScreenInfo.Recipe r = new ScreenInfo.Recipe();
        r.inputs.add(new ScreenInfo.Ingredient("minecraft:sugar", 5));
        r.output = "minecraft:cake";
        boolean[] out = {false, false, true};
        MachineRules.Plan p = MachineRules.plan(r, new MachineRules.Held[] {held("minecraft:sugar", 2), held("minecraft:sugar", 4), held("", 0)}, out, 64);
        assertArrayEquals(new int[] {2, 3, 0}, p.take());
    }

    @Test
    void firstRunnablePicksInOrderAndSkipsEmptyRecipes() {
        ScreenInfo.Recipe empty = new ScreenInfo.Recipe();
        ScreenInfo.Recipe a = new ScreenInfo.Recipe();
        a.inputs.add(new ScreenInfo.Ingredient("minecraft:stone", 1));
        a.output = "minecraft:dirt";
        ScreenInfo.Recipe b = new ScreenInfo.Recipe();
        b.inputs.add(new ScreenInfo.Ingredient("minecraft:sugar", 1));
        b.output = "minecraft:dirt";
        boolean[] out = {false, true};
        assertEquals(2, MachineRules.firstRunnable(List.of(empty, a, b), new MachineRules.Held[] {held("minecraft:sugar", 1), held("", 0)}, out, SIXTY_FOUR));
        assertEquals(-1, MachineRules.firstRunnable(List.of(empty, a, b), new MachineRules.Held[] {held("minecraft:dirt", 1), held("", 0)}, out, SIXTY_FOUR));
    }

    @Test
    void blockBoxesComeFromTheParts() {
        Design d = new Design();
        d.type = Design.Type.BLOCK;
        Part base = new Part();
        base.id = "a";
        base.pos = new float[] {0, 2, 0};
        base.size = new float[] {16, 4, 16};
        d.parts.add(base);
        Part post = new Part();
        post.id = "b";
        post.parent = "a";
        post.pos = new float[] {0, 6, 0};
        post.size = new float[] {4, 8, 4};
        d.parts.add(post);
        Part mob = new Part();
        mob.id = "c";
        mob.kind = Part.Kind.MOB;
        d.parts.add(mob);
        List<float[]> boxes = BlockBoxes.of(d);
        assertEquals(2, boxes.size());
        assertArrayEquals(new float[] {0, 0, 0, 16, 4, 16}, boxes.get(0), 1e-3f);
        assertArrayEquals(new float[] {6, 4, 6, 10, 12, 10}, boxes.get(1), 1e-3f);
    }

    @Test
    void blockBoxesStayInsideTheBlockAndTurn() {
        Design d = new Design();
        Part big = new Part();
        big.id = "a";
        big.pos = new float[] {0, 8, 4};
        big.size = new float[] {4, 100, 4};
        d.parts.add(big);
        float[] b = BlockBoxes.of(d).get(0);
        assertEquals(0f, b[1], 1e-3);
        assertEquals(16f, b[4], 1e-3);
        // x 6-10, z 10-14: turned west (front pointing -X) the far side comes round
        float[] w = BlockBoxes.turned(b, 1);
        assertArrayEquals(new float[] {2, 0, 6, 6, 16, 10}, w, 1e-3f);
        float[] n = BlockBoxes.turned(b, 2);
        assertArrayEquals(new float[] {6, 0, 2, 10, 16, 6}, n, 1e-3f);
        float[] e = BlockBoxes.turned(b, 3);
        assertArrayEquals(new float[] {10, 0, 6, 14, 16, 10}, e, 1e-3f);
    }

    @Test
    void aScreenHasNoParts() {
        Design d = machine();
        d.parts.add(new Part());
        d.clips.add(new Clip("idle", 1f, Clip.Loop.LOOP));
        DesignRules.tidy(d);
        assertTrue(d.parts.isEmpty());
        assertTrue(d.clips.isEmpty());
    }
}
