package com.example.charmobmaker.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

// The solid boxes of a made block, worked out from its parts so server and PC always agree. In pixels, 0 to 16, with the mob-style origin at the bottom middle of the block.
public final class BlockBoxes {
    public static final int MAX_BOXES = 24;

    private BlockBoxes() {}

    public static List<float[]> of(Design d) {
        List<float[]> out = new ArrayList<>();
        Rig rig = Rig.of(d);
        Matrix4f[] world = rig.newMatrices();
        rig.pose(null, 0f, 0f, world);
        Vector3f v = new Vector3f();
        for (int i = 0; i < rig.size && out.size() < MAX_BOXES; i++) {
            Part p = rig.parts[i];
            float hx, hy, hz;
            switch (p.kind) {
                case CUBE -> {
                    hx = p.size[0] / 2f;
                    hy = p.size[1] / 2f;
                    hz = p.size[2] / 2f;
                }
                case BLOCK -> hx = hy = hz = 8f;
                default -> {
                    continue; // only known on the PC, so they can't count
                }
            }
            if (hx < 0.5f && hy < 0.5f && hz < 0.5f) continue;
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int c = 0; c < 8; c++) {
                v.set((c & 1) == 0 ? -hx : hx, (c & 2) == 0 ? -hy : hy, (c & 4) == 0 ? -hz : hz);
                world[i].transformPosition(v);
                b[0] = Math.min(b[0], v.x);
                b[1] = Math.min(b[1], v.y);
                b[2] = Math.min(b[2], v.z);
                b[3] = Math.max(b[3], v.x);
                b[4] = Math.max(b[4], v.y);
                b[5] = Math.max(b[5], v.z);
            }
            float[] box = {clamp(b[0] + 8f), clamp(b[1]), clamp(b[2] + 8f), clamp(b[3] + 8f), clamp(b[4]), clamp(b[5] + 8f)};
            if (box[3] - box[0] >= 0.25f && box[4] - box[1] >= 0.25f && box[5] - box[2] >= 0.25f) out.add(box);
        }
        return out;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(16f, v));
    }

    // Turned to face a way: 0 south (as designed), 1 west, 2 north, 3 east.
    public static float[] turned(float[] b, int quarter) {
        float x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (quarter & 3) {
            case 1 -> new float[] {16f - z1, b[1], x0, 16f - z0, b[4], x1};
            case 2 -> new float[] {16f - x1, b[1], 16f - z1, 16f - x0, b[4], 16f - z0};
            case 3 -> new float[] {z0, b[1], 16f - x1, z1, b[4], 16f - x0};
            default -> b.clone();
        };
    }
}
