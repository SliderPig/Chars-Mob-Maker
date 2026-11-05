package com.example.charmobmaker.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BbModelTest {
    private static final String FILE = """
            {"name":"tester","elements":[
              {"name":"body","uuid":"e1","from":[-4,12,-2],"to":[4,24,2],"origin":[0,24,0],"color":3},
              {"name":"arm","uuid":"e2","from":[4,12,-2],"to":[8,24,2],"origin":[6,22,0],"rotation":[0,0,10]}
            ],
            "outliner":[{"name":"root","uuid":"g1","origin":[0,0,0],"children":["e1",{"name":"arm_group","uuid":"g2","origin":[6,22,0],"rotation":[-20,0,0],"children":["e2"]}]}],
            "animations":[{"name":"animation.tester.walk","length":1.5,"loop":"loop","animators":{"g2":{"name":"arm_group","type":"bone","keyframes":[
              {"channel":"rotation","time":0,"interpolation":"linear","data_points":[{"x":"10","y":"0","z":"0"}]},
              {"channel":"rotation","time":0.75,"interpolation":"catmullrom","data_points":[{"x":"-10","y":"math.sin(q.anim_time)","z":"0"}]},
              {"channel":"position","time":0,"data_points":[{"x":2,"y":1,"z":3}]}
            ]}}}]}
            """;

    @Test
    void readsBoxesFoldersAndKeys() throws Exception {
        Design d = BbModel.read(FILE, "fallback");
        assertEquals("tester", d.name);
        assertEquals(4, d.parts.size());
        Part root = d.parts.stream().filter(p -> p.name.equals("root")).findFirst().orElseThrow();
        Part body = d.parts.stream().filter(p -> p.name.equals("body")).findFirst().orElseThrow();
        Part group = d.parts.stream().filter(p -> p.name.equals("arm_group")).findFirst().orElseThrow();
        Part arm = d.parts.stream().filter(p -> p.name.equals("arm")).findFirst().orElseThrow();
        assertEquals(root.id, body.parent);
        assertEquals(group.id, arm.parent);
        assertEquals(0f, root.size[0]); // folders draw nothing
        assertEquals(8f, body.size[0]);
        assertEquals(12f, body.size[1]);
        assertEquals(18f, body.pos[1]); // the middle of the box
        Clip walk = d.clip("walk");
        assertNotNull(walk);
        assertEquals(1.5f, walk.length, 1e-4);
        assertEquals(Clip.Loop.LOOP, walk.loop);
        Track rot = walk.track(group.id, Channel.ROT, false);
        assertEquals(2, rot.keys.size());
        assertEquals(-10f, rot.keys.get(0).x, 1e-4); // turned half round on the way in
        assertEquals(Ease.LINEAR, rot.keys.get(0).ease);
        assertEquals(Ease.SMOOTH, rot.keys.get(1).ease);
        assertEquals(0f, rot.keys.get(1).y, 1e-4); // a formula can't be used
        Track pos = walk.track(group.id, Channel.POS, false);
        assertEquals(-2f, pos.keys.get(0).x, 1e-4);
        assertEquals(1f, pos.keys.get(0).y, 1e-4);
        assertEquals(-3f, pos.keys.get(0).z, 1e-4);
    }

    @Test
    void boxesLandWhereBlockbenchHadThem() throws Exception {
        Design d = BbModel.read(FILE, "x");
        Rig rig = Rig.of(d);
        Matrix4f[] m = rig.newMatrices();
        // take the turns away to check just the places
        for (Part p : d.parts) p.rot = new float[3];
        rig.pose(null, 0f, 0f, m);
        int arm = rig.indexOf(d.parts.stream().filter(p -> p.name.equals("arm")).findFirst().orElseThrow().id);
        Vector3f middle = m[arm].transformPosition(new Vector3f());
        assertEquals(-6f, middle.x, 1e-3); // +6 in Blockbench, which faces the other way
        assertEquals(18f, middle.y, 1e-3);
        assertEquals(0f, middle.z, 1e-3);
    }

    @Test
    void notABlockbenchFileIsRefused() {
        assertThrows(DesignJson.BadDesign.class, () -> BbModel.read("{}", "x"));
        assertThrows(DesignJson.BadDesign.class, () -> BbModel.read("nonsense", "x"));
        assertThrows(DesignJson.BadDesign.class, () -> BbModel.read("{\"elements\":5,\"outliner\":7}", "x"));
    }

    @Test
    void tooManyPartsStopAtTheLimit() throws Exception {
        StringBuilder els = new StringBuilder(), kids = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                els.append(',');
                kids.append(',');
            }
            els.append("{\"name\":\"c").append(i).append("\",\"uuid\":\"u").append(i).append("\",\"from\":[0,0,0],\"to\":[1,1,1]}");
            kids.append("\"u").append(i).append('"');
        }
        Design d = BbModel.read("{\"elements\":[" + els + "],\"outliner\":[" + kids + "]}", "x");
        assertEquals(Limits.MAX_PARTS, d.parts.size());
    }
}
