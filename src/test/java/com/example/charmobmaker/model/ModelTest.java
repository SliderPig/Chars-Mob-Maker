package com.example.charmobmaker.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    private static Part cube(String id, String name, String parent, float x, float y, float z) {
        Part p = new Part();
        p.id = id;
        p.name = name;
        p.parent = parent;
        p.pos = new float[] {x, y, z};
        return p;
    }

    private static Design sample() {
        Design d = new Design();
        d.name = "Test";
        d.parts.add(cube("body", "Body", "", 0, 12, 0));
        d.parts.add(cube("head", "Head", "body", 0, 8, 2));
        d.parts.add(cube("leg_l", "Left leg", "body", 3, -6, 0));
        d.parts.add(cube("leg_r", "Right leg", "body", -3, -6, 0));
        Part block = cube("b", "Block", "head", 0, 0, 0);
        block.kind = Part.Kind.BLOCK;
        block.block = "minecraft:oak_stairs[facing=east,half=top]";
        d.parts.add(block);
        Part mob = cube("m", "Zombie head", "", 0, 0, 0);
        mob.kind = Part.Kind.MOB;
        mob.mob = "minecraft:zombie";
        mob.bone = "head";
        mob.solo = true;
        d.parts.add(mob);
        Part item = cube("i", "Sword", "", 0, 0, 0);
        item.kind = Part.Kind.ITEM;
        item.item = "minecraft:iron_sword";
        d.parts.add(item);
        Clip c = new Clip("walk", 2f, Clip.Loop.LOOP);
        ClipTools.setKey(c, "leg_l", Channel.ROT, 0f, 30, 0, 0, Ease.SMOOTH);
        ClipTools.setKey(c, "leg_l", Channel.ROT, 1f, -30, 0, 0, Ease.LINEAR);
        ClipTools.setKey(c, "body", Channel.SCALE, 0.5f, 1, 2, 1, Ease.STEP);
        d.clips.add(c);
        d.paints.add(new int[256]);
        d.paints.get(0)[17] = 0xFFFF00FF;
        d.parts.get(0).paint = 0;
        d.stats.drops.add(new Stats.Drop());
        d.stats.ambientSound = "minecraft:entity.cat.ambient";
        return d;
    }

    @Test
    void jsonRoundTripKeepsEverything() throws Exception {
        Design d = sample();
        String text = DesignJson.write(d);
        Design back = DesignJson.read(text);
        assertEquals(text, DesignJson.write(back));
        assertEquals("minecraft:oak_stairs[facing=east,half=top]", back.part("b").block);
        assertEquals(Part.Kind.MOB, back.part("m").kind);
        assertEquals("head", back.part("m").bone);
        assertTrue(back.part("m").solo);
        assertEquals(0xFFFF00FF, back.paints.get(0)[17]);
        assertEquals(2, back.clip("walk").track("leg_l", Channel.ROT, false).keys.size());
        assertEquals(Ease.LINEAR, back.clip("walk").track("leg_l", Channel.ROT, false).keys.get(1).ease);
        assertEquals("minecraft:entity.cat.ambient", back.stats.ambientSound);
    }

    @Test
    void junkTextIsRefusedNotCrashed() {
        assertThrows(DesignJson.BadDesign.class, () -> DesignJson.read("not json {{{"));
        assertThrows(DesignJson.BadDesign.class, () -> DesignJson.read("[1,2,3]"));
        assertThrows(DesignJson.BadDesign.class, () -> DesignJson.read("{\"hello\":1}"));
        assertThrows(DesignJson.BadDesign.class, () -> DesignJson.read("[".repeat(200000)));
    }

    @Test
    void wrongTypesFallBackToDefaults() throws Exception {
        Design d = DesignJson.read("{\"name\":{\"x\":1},\"parts\":[{\"pos\":\"oops\",\"kind\":5,\"size\":[1,\"a\",3]},7],\"stats\":{\"health\":\"lots\",\"targets\":[{\"a\":1}]},\"clips\":[{\"tracks\":[{\"keys\":[[\"a\",1,2,3],[0.5]]}]}]}");
        DesignRules.tidy(d);
        assertEquals("New Mob", d.name);
        assertEquals(1, d.parts.size());
        assertEquals(0f, d.parts.get(0).pos[0]);
        assertEquals(20f, d.stats.health);
    }

    @Test
    void tidyClampsEverything() {
        Design d = sample();
        d.name = "§cEvil\nname that is far too long to ever be allowed in here at all";
        Part p = d.part("body");
        p.pos[0] = 1e9f;
        p.pos[1] = Float.NaN;
        p.scale[0] = 1e9f;
        p.size[0] = -5f;
        p.block = "../../evil";
        p.texture = "minecraft:../../../secret";
        p.mob = "NOT VALID";
        d.stats.health = 1e9f;
        d.stats.speed = -3f;
        d.stats.width = 1e5f;
        d.stats.targets.add("rm -rf");
        d.stats.summonMob = "x";
        DesignRules.tidy(d);
        assertTrue(d.name.length() <= Limits.MAX_NAME);
        assertFalse(d.name.contains("§"));
        assertFalse(d.name.contains("\n"));
        assertEquals(Limits.MAX_OFFSET, p.pos[0]);
        assertEquals(0f, p.pos[1]);
        assertEquals(Limits.MAX_SCALE, p.scale[0]);
        assertEquals(0f, p.size[0]);
        assertEquals("", p.block);
        assertEquals("", p.texture);
        assertEquals("", p.mob);
        assertEquals(Limits.MAX_HEALTH, d.stats.health);
        assertEquals(0f, d.stats.speed);
        assertEquals(Limits.MAX_WIDTH, d.stats.width);
        assertEquals(List.of("player"), d.stats.targets);
        assertEquals("minecraft:silverfish", d.stats.summonMob);
    }

    @Test
    void tooManyPartsAndKeysAreCut() {
        Design d = new Design();
        for (int i = 0; i < 200; i++) d.parts.add(cube("p" + i, "P", "", 0, 0, 0));
        Clip c = new Clip("x", 60f, Clip.Loop.LOOP);
        for (int i = 0; i < 500; i++) c.track("p0", Channel.ROT, true).keys.add(new Key(i * 0.1f, 0, 0, 0, Ease.LINEAR));
        d.clips.add(c);
        for (int i = 0; i < 40; i++) d.clips.add(new Clip("clip" + i, 1f, Clip.Loop.ONCE));
        for (int i = 0; i < 30; i++) d.paints.add(new int[256]);
        DesignRules.tidy(d);
        assertEquals(Limits.MAX_PARTS, d.parts.size());
        assertEquals(Limits.MAX_KEYS_PER_TRACK, d.clip("x").track("p0", Channel.ROT, false).keys.size());
        assertEquals(Limits.MAX_CLIPS, d.clips.size());
        assertEquals(Limits.MAX_PAINTS, d.paints.size());
    }

    @Test
    void parentLoopsAreBroken() {
        Design d = new Design();
        d.parts.add(cube("a", "A", "c", 0, 0, 0));
        d.parts.add(cube("b", "B", "a", 0, 0, 0));
        d.parts.add(cube("c", "C", "b", 0, 0, 0));
        d.parts.add(cube("s", "Self", "s", 0, 0, 0));
        d.parts.add(cube("x", "Lost", "nobody", 0, 0, 0));
        DesignRules.tidy(d);
        assertEquals(d.parts.size(), PartTree.rows(d, null).size());
        assertEquals(d.parts.size(), Rig.of(d).size);
        assertEquals("", d.part("s").parent);
        assertEquals("", d.part("x").parent);
    }

    @Test
    void idsCanNeverLeaveTheFolder() {
        assertEquals("pink_cat", Ids.slug("Pink Cat!"));
        assertEquals("etc_passwd", Ids.slug("../../etc/passwd"));
        assertEquals("mob", Ids.slug("../.."));
        assertEquals("mob", Ids.slug(""));
        assertEquals("mob_con", Ids.slug("CON"));
        assertTrue(Ids.valid("pink_cat"));
        assertFalse(Ids.valid("../evil"));
        assertFalse(Ids.valid("a/b"));
        assertFalse(Ids.valid("a\\b"));
        assertFalse(Ids.valid("a.b"));
        assertFalse(Ids.valid("nul"));
        assertFalse(Ids.valid(""));
        assertFalse(Ids.resource("minecraft:../../x"));
        assertTrue(Ids.resource("minecraft:block/stone"));
        assertTrue(Ids.blockState("minecraft:oak_stairs[facing=east,half=top]"));
        assertFalse(Ids.blockState("minecraft:stone[a=b"));
        assertFalse(Ids.blockState("minecraft:stone[a=\"]"));
    }

    @Test
    void keysInterpolateWithEases() {
        Track t = new Track("p", Channel.ROT);
        t.keys.add(new Key(0f, 0, 10, 0, Ease.LINEAR));
        t.keys.add(new Key(1f, 10, 20, 0, Ease.STEP));
        t.keys.add(new Key(2f, 20, 30, 0, Ease.SMOOTH));
        t.keys.add(new Key(3f, 0, 0, 0, Ease.LINEAR));
        float[] v = new float[3];
        t.sample(0.5f, 4f, false, v, 0);
        assertEquals(5f, v[0], 1e-4);
        assertEquals(15f, v[1], 1e-4);
        t.sample(1.5f, 4f, false, v, 0);
        assertEquals(10f, v[0], 1e-4); // snap holds until the next key
        t.sample(2.5f, 4f, false, v, 0);
        assertEquals(10f, v[0], 1e-4); // smooth is half way at half time
        t.sample(-1f, 4f, false, v, 0);
        assertEquals(0f, v[0], 1e-4);
        t.sample(9f, 4f, false, v, 0);
        assertEquals(0f, v[0], 1e-4);
    }

    @Test
    void loopingClipsTravelBackToTheStart() {
        Track t = new Track("p", Channel.ROT);
        t.keys.add(new Key(0f, 0, 0, 0, Ease.LINEAR));
        t.keys.add(new Key(1f, 10, 0, 0, Ease.LINEAR));
        float[] v = new float[3];
        t.sample(1.5f, 2f, true, v, 0);
        assertEquals(5f, v[0], 1e-4);
        t.sample(1.5f, 2f, false, v, 0);
        assertEquals(10f, v[0], 1e-4);
        Clip c = new Clip("x", 2f, Clip.Loop.LOOP);
        assertEquals(0.5f, c.wrapTime(2.5f), 1e-4);
        assertEquals(1.5f, c.wrapTime(-0.5f), 1e-4);
        c.loop = Clip.Loop.HOLD;
        assertEquals(2f, c.wrapTime(9f), 1e-4);
    }

    @Test
    void easesStayInRange() {
        for (Ease e : Ease.values()) {
            assertEquals(0f, e.apply(0f), 1e-4);
            assertEquals(1f, e.apply(1f), 1e-4);
            for (float a = 0.01f; a < 1f; a += 0.01f) {
                float f = e.apply(a);
                assertTrue(f >= -0.01f && f <= 1.01f, e + " " + a + " " + f);
            }
        }
    }

    @Test
    void rigOrdersParentsFirstAndAddsUpMoves() {
        Design d = new Design();
        d.parts.add(cube("kid", "Kid", "root", 1, 0, 0));
        d.parts.add(cube("root", "Root", "", 0, 10, 0));
        d.parts.get(1).rot[2] = 90f;
        Rig rig = Rig.of(d);
        assertEquals("root", rig.parts[0].id);
        assertEquals(0, rig.parent[1]);
        Matrix4f[] m = rig.newMatrices();
        rig.pose(null, 0f, 0f, m);
        Vector3f v = new Vector3f();
        m[1].getTranslation(v);
        assertEquals(0f, v.x, 1e-4);
        assertEquals(11f, v.y, 1e-4);
    }

    @Test
    void pivotIsWhereItTurns() {
        Design d = new Design();
        Part arm = cube("arm", "Arm", "", 0, 0, 0);
        arm.pivot = new float[] {0, 6, 0};
        arm.rot[0] = 90f;
        d.parts.add(arm);
        Rig rig = Rig.of(d);
        Matrix4f[] m = rig.newMatrices();
        rig.pose(null, 0f, 0f, m);
        Vector3f shoulder = m[0].transformPosition(new Vector3f(0, 6, 0));
        assertEquals(6f, shoulder.y, 1e-4); // stays put
        Vector3f hand = m[0].transformPosition(new Vector3f(0, -6, 0));
        assertEquals(6f, hand.y, 1e-4);
        assertEquals(-12f, hand.z, 1e-3);
    }

    @Test
    void poseAddsTurnAndMultipliesSize() {
        Design d = new Design();
        Part p = cube("a", "A", "", 1, 2, 3);
        d.parts.add(p);
        Clip c = new Clip("x", 1f, Clip.Loop.ONCE);
        ClipTools.setKey(c, "a", Channel.POS, 0f, 1, 1, 1, Ease.LINEAR);
        ClipTools.setKey(c, "a", Channel.SCALE, 0f, 2, 2, 2, Ease.LINEAR);
        d.clips.add(c);
        Rig rig = Rig.of(d);
        PoseOut pose = new PoseOut(rig.size);
        rig.clip("x").sample(0f, pose);
        Matrix4f[] m = rig.newMatrices();
        rig.pose(pose, 0f, 0f, m);
        Vector3f v = new Vector3f();
        m[0].getTranslation(v);
        assertEquals(2f, v.x, 1e-4);
        m[0].getScale(v);
        assertEquals(2f, v.x, 1e-4);
    }

    @Test
    void oneShotsOnlyMoveTheirOwnParts() {
        Design d = new Design();
        d.parts.add(cube("leg", "Leg", "", 0, 0, 0));
        d.parts.add(cube("arm", "Arm", "", 0, 0, 0));
        Clip walk = new Clip("walk", 1f, Clip.Loop.LOOP);
        ClipTools.setKey(walk, "leg", Channel.ROT, 0f, 40, 0, 0, Ease.LINEAR);
        ClipTools.setKey(walk, "arm", Channel.ROT, 0f, 40, 0, 0, Ease.LINEAR);
        Clip attack = new Clip("attack", 1f, Clip.Loop.ONCE);
        ClipTools.setKey(attack, "arm", Channel.ROT, 0f, -90, 0, 0, Ease.LINEAR);
        d.clips.add(walk);
        d.clips.add(attack);
        Rig rig = Rig.of(d);
        AnimState a = new AnimState();
        PoseOut out = new PoseOut(rig.size);
        a.play("attack", 0f, true);
        a.pose(rig, 0.5f, 0f, 1f, -1f, out);
        assertEquals(40f, out.rot[rig.indexOf("leg") * 3], 1e-3);
        assertEquals(-90f, out.rot[rig.indexOf("arm") * 3], 1e-3);
        // after it ends and fades, the arm walks again and the clip is forgotten
        a.pose(rig, 2f, 0f, 1f, -1f, out);
        assertEquals(40f, out.rot[rig.indexOf("arm") * 3], 1e-3);
        assertEquals("", a.playing());
    }

    @Test
    void walkBlendsInWithSpeedAndPlaysFasterWhenFaster() {
        Design d = new Design();
        d.parts.add(cube("leg", "Leg", "", 0, 0, 0));
        Clip walk = new Clip("walk", 10f, Clip.Loop.LOOP);
        ClipTools.setKey(walk, "leg", Channel.ROT, 0f, 10, 0, 0, Ease.LINEAR);
        d.clips.add(walk);
        Rig rig = Rig.of(d);
        AnimState a = new AnimState();
        PoseOut out = new PoseOut(rig.size);
        a.pose(rig, 0f, 0f, 0f, -1f, out);
        assertEquals(0f, out.rot[0], 1e-4);
        a.pose(rig, 0f, 0f, 1f, -1f, out);
        assertEquals(10f, out.rot[0], 1e-4);
        assertEquals(1f, AnimState.walkTime(12f), 1e-4);
    }

    @Test
    void partTreeAddDuplicateMirrorDelete() {
        Design d = sample();
        Part copy = PartTree.duplicate(d, "body");
        assertNotNull(copy);
        assertEquals(d.part("head").pos[1], PartTree.subtree(d, copy.id).get(1).pos[1]);
        assertEquals(12, d.parts.size()); // 7 + the 5 that hang together
        assertNotNull(d.clip("walk").track(PartTree.subtree(d, copy.id).get(2).id, Channel.ROT, false));
        Part left = d.part("leg_l");
        Part right = PartTree.mirror(d, left.id);
        assertEquals("Right leg", right.name);
        assertEquals(-left.pos[0], right.pos[0]);
        PartTree.remove(d, copy.id);
        assertNull(d.part(copy.id));
        assertNull(d.clip("walk").track(copy.id, Channel.SCALE, false));
        PartTree.remove(d, "body");
        assertNull(d.part("head"));
        assertNull(d.clip("walk").track("leg_l", Channel.ROT, false));
    }

    @Test
    void partLimitIsKept() {
        Design d = new Design();
        for (int i = 0; i < Limits.MAX_PARTS; i++) assertNotNull(PartTree.add(d, new Part(), ""));
        assertNull(PartTree.add(d, new Part(), ""));
        assertNull(PartTree.duplicate(d, "p1"));
    }

    @Test
    void reparentKeepsThePartWhereItIs() {
        Design d = new Design();
        Part a = cube("a", "A", "", 5, 10, 0);
        a.rot = new float[] {0, 90, 0};
        a.scale = new float[] {2, 2, 2};
        Part b = cube("b", "B", "", 3, 4, 5);
        b.rot = new float[] {10, 20, 30};
        b.pivot = new float[] {1, 2, 3};
        d.parts.add(a);
        d.parts.add(b);
        Rig before = Rig.of(d);
        Matrix4f[] m = before.newMatrices();
        before.pose(null, 0f, 0f, m);
        Vector3f p1 = m[before.indexOf("b")].transformPosition(new Vector3f(1, 1, 1));
        assertTrue(PartTree.reparent(d, "b", "a"));
        assertEquals("a", d.part("b").parent);
        Rig after = Rig.of(d);
        Matrix4f[] n = after.newMatrices();
        after.pose(null, 0f, 0f, n);
        Vector3f p2 = n[after.indexOf("b")].transformPosition(new Vector3f(1, 1, 1));
        assertEquals(p1.x, p2.x, 1e-2);
        assertEquals(p1.y, p2.y, 1e-2);
        assertEquals(p1.z, p2.z, 1e-2);
    }

    @Test
    void cantReparentIntoItsOwnChild() {
        Design d = sample();
        assertFalse(PartTree.canReparent(d, "body", "head"));
        assertFalse(PartTree.canReparent(d, "body", "body"));
        assertTrue(PartTree.canReparent(d, "head", ""));
        assertFalse(PartTree.reparent(d, "body", "b"));
    }

    @Test
    void packUnpackAndBombProtection() {
        String s = "hello ".repeat(1000);
        assertEquals(s, Packer.unpack(Packer.pack(s)));
        byte[] bomb = Packer.pack("x".repeat(Limits.MAX_JSON_BYTES + 10));
        assertNull(Packer.unpack(bomb));
        assertNull(Packer.unpack(new byte[] {1, 2, 3}));
        assertNull(Packer.unpack(new byte[Limits.MAX_DESIGN_GZ_BYTES + 1]));
        List<byte[]> pieces = Packer.split(new byte[70000], 30000);
        assertEquals(3, pieces.size());
        assertEquals(10000, pieces.get(2).length);
        assertEquals(1, Packer.split(new byte[0], 30000).size());
    }

    @Test
    void keyTools() {
        Design d = sample();
        Clip c = d.clip("walk");
        ClipTools.mirrorOnto(c, "leg_l", "leg_r", 1f);
        Track r = c.track("leg_r", Channel.ROT, false);
        assertEquals(2, r.keys.size());
        assertEquals(0f, r.keys.get(0).t, 1e-4); // the key at 1 wrapped to 0
        assertEquals(-30f, r.keys.get(0).x, 1e-4);
        assertEquals(d.part("leg_r"), ClipTools.opposite(d, d.part("leg_l")));

        ClipTools.Board b = ClipTools.copyPart(c, "leg_l", 0f);
        ClipTools.paste(c, "head", b, 0.5f);
        assertNotNull(c.track("head", Channel.ROT, false).at(1.5f));

        Track l = c.track("leg_l", Channel.ROT, false);
        ClipTools.moveKey(c, l, l.keys.get(0), 1f);
        assertEquals(1, l.keys.size());
        ClipTools.setLength(c, 0.5f);
        assertEquals(0.5f, c.track("head", Channel.ROT, false).keys.get(0).t, 1e-4);
        assertEquals(0.5f, ClipTools.snap(0.51f), 1e-4);
    }

    @Test
    void quickWalkMovesOppositeLegsOppositeWays() {
        Design d = sample();
        Clip walk = ClipTools.makeWalk(d);
        float l = walk.track("leg_l", Channel.ROT, false).keys.get(0).x;
        float r = walk.track("leg_r", Channel.ROT, false).keys.get(0).x;
        assertEquals(-l, r, 1e-4);
        assertNotEquals(0f, l);
    }

    @Test
    void historyUndoRedo() {
        History h = new History("a");
        assertFalse(h.push("a"));
        assertTrue(h.push("b"));
        h.push("c");
        assertEquals("b", h.undo());
        assertEquals("a", h.undo());
        assertNull(h.undo());
        assertEquals("b", h.redo());
        h.push("d");
        assertFalse(h.canRedo());
        assertEquals("b", h.undo());
    }

    @Test
    void paintFillStaysInsideItsColour() {
        int[] cell = new int[256];
        for (int i = 0; i < 16; i++) cell[8 * 16 + i] = 0xFF000000; // a line across the middle
        Paints.fillFrom(cell, 0, 0, 0xFFFF0000);
        assertEquals(0xFFFF0000, cell[0]);
        assertEquals(0xFF000000, cell[8 * 16]);
        assertEquals(0, cell[15 * 16]);
    }
}
