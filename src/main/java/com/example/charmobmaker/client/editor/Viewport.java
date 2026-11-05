package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.render.Compiled;
import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Channel;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.PartTree;
import com.example.charmobmaker.model.Stats;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

// The big 3D picture behind the panels: orbit by dragging, zoom with the wheel, click a part to pick it, drag the coloured arrows to move it.
final class Viewport {
    private static final float FOV = (float) Math.toRadians(32.0);
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final int[] AXIS_COLOURS = {Ui.RED, Ui.GREEN, Ui.BLUE};
    private static final Vector3f[] AXES = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};

    float yaw = -28f, pitch = 16f, dist = 5f;
    float tx, ty = 0.9f, tz;
    private final Matrix4f proj = new Matrix4f(), view = new Matrix4f(), viewProj = new Matrix4f(), inverse = new Matrix4f();
    private float w, h, zoom = 1f;
    private boolean orbiting, panning, moved;
    private float lastX, lastY;
    private int grabAxis = -1, hoverAxis = -1;
    private float grabX, grabY;
    private float[] grabValues;
    private final Vector4f tmp = new Vector4f();
    private final Matrix4f model = new Matrix4f();
    private final float[] p0 = new float[3], p1 = new float[3];

    // freeHeight: how much of the window the mob really has, so it shrinks to stay in view when the timeline comes up.
    void setup(Ui ui, float freeCx, float freeCy, float freeHeight) {
        w = ui.width;
        h = ui.height;
        zoom = Math.min(1f, freeHeight / h);
        float ndcX = freeCx / w * 2f - 1f, ndcY = 1f - freeCy / h * 2f;
        proj.identity().translate(ndcX, ndcY, 0f).scale(zoom, zoom, 1f).perspective(FOV, w / h, 0.05f, 300f);
        view.identity().translate(0f, 0f, -dist).rotateX(pitch * DEG).rotateY(yaw * DEG).translate(-tx, -ty, -tz);
        proj.mul(view, viewProj);
        viewProj.invert(inverse);
    }

    // Blocks in the mob's space to layout units on screen. out[2] is below 0 when behind the camera.
    private void project(float x, float y, float z, float[] out) {
        tmp.set(x, y, z, 1f);
        viewProj.transform(tmp);
        out[2] = tmp.w;
        float ww = Math.abs(tmp.w) < 1e-5f ? 1e-5f : tmp.w;
        out[0] = (tmp.x / ww + 1f) / 2f * w;
        out[1] = (1f - tmp.y / ww) / 2f * h;
    }

    void aimAt(Compiled c) {
        float[] b = c.bounds();
        tx = (b[0] + b[3]) / 32f;
        ty = (b[1] + b[4]) / 32f;
        tz = (b[2] + b[5]) / 32f;
        dist = Math.max(2f, fitDistance(c));
    }

    private static float fitDistance(Compiled c) {
        float[] b = c.bounds();
        float dx = (b[3] - b[0]) / 16f, dy = (b[4] - b[1]) / 16f, dz = (b[5] - b[2]) / 16f;
        float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 2f;
        return radius / (float) Math.sin(FOV / 2f) * 1.15f;
    }

    // After adding something big the camera backs off, but never moves in on its own.
    void backOffFor(Compiled c) {
        if (fitDistance(c) > dist) aimAt(c);
    }

    void view(float yawDeg, float pitchDeg) {
        yaw = yawDeg;
        pitch = pitchDeg;
    }

    // ---------- what's behind the mob: floor and grid ----------

    void backdrop(Ui ui) {
        Draw d = ui.draw;
        float[][] ring = new float[32][3];
        int on = 0;
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2.0 * i / 32.0;
            project((float) Math.cos(a) * 3.2f, 0f, (float) Math.sin(a) * 3.2f, ring[i]);
            if (ring[i][2] > 0f) on++;
        }
        project(0f, 0f, 0f, p0);
        if (on == 32 && p0[2] > 0f) {
            int inner = 0x30FF8FC7;
            for (int i = 0; i < 32; i++) {
                float[] a = ring[i], b = ring[(i + 1) % 32];
                fanPiece(d, p0[0], p0[1], a[0], a[1], b[0], b[1], inner);
            }
        }
        int radius = 4;
        for (int i = -radius; i <= radius; i++) {
            gridLine(d, i, -radius, i, radius);
            gridLine(d, -radius, i, radius, i);
        }
    }

    private void fanPiece(Draw d, float cx, float cy, float ax, float ay, float bx, float by, int inner) {
        d.fan(cx, cy, ax, ay, bx, by, inner, inner & 0xFFFFFF);
    }

    private void gridLine(Draw d, float x0, float z0, float x1, float z1) {
        int steps = 8;
        for (int s = 0; s < steps; s++) {
            float a = s / (float) steps, b = (s + 1f) / steps;
            float ax = x0 + (x1 - x0) * a, az = z0 + (z1 - z0) * a, bx = x0 + (x1 - x0) * b, bz = z0 + (z1 - z0) * b;
            project(ax, 0f, az, p0);
            project(bx, 0f, bz, p1);
            if (p0[2] <= 0f || p1[2] <= 0f) continue;
            d.lineFade(p0[0], p0[1], p1[0], p1[1], 0.5f, gridColour(ax, az), gridColour(bx, bz));
        }
    }

    private static int gridColour(float x, float z) {
        float r = (float) Math.sqrt(x * x + z * z) / 4.2f;
        float a = Math.max(0f, 1f - r * r);
        return ((int) (a * 70f) << 24) | 0xFFC8E6;
    }

    // ---------- the mob itself ----------

    void render3D(Ui ui, EditorState s, int glowPart) {
        GuiGraphics g = ui.g;
        ui.flush();
        Compiled c = s.compiled();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShaderLights(new Vector3f(0.25f, 1f, 0.75f).normalize(), new Vector3f(-0.6f, 0.4f, 0.6f).normalize());
        PoseStack ps = new PoseStack();
        ps.last().pose().set(view);
        ps.last().normal().set(new Matrix3f(view));
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        c.draw(ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ui.partial, null);
        buffers.endBatch();
        if (glowPart >= 0) {
            float pulse = 0.28f + 0.14f * (float) Math.sin(System.currentTimeMillis() / 240.0);
            int a = 255, r = 255, gr = (int) (120f * pulse * 2f), b = (int) (190f * pulse * 2f);
            int colour = (a << 24) | ((int) (r * pulse * 1.6f) << 16) | (gr << 8) | b;
            RenderSystem.depthMask(false);
            c.drawGlow(ps, buffers, glowPart, colour);
            buffers.endBatch();
            RenderSystem.depthMask(true);
        }
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
    }

    void hitbox(Ui ui, Stats st, int colour) {
        float a = st.width / 2f, b = st.height;
        box(ui.draw, null, -a, 0f, -a, a, b, a, 0.75f, colour);
    }

    // For a made block: the block space it sits in, and its solid boxes on its settings tab.
    void blockFrame(Ui ui, com.example.charmobmaker.model.Design d, boolean showSolid) {
        box(ui.draw, null, -0.5f, 0f, -0.5f, 0.5f, 1f, 0.5f, 0.75f, 0x90FFC8E6);
        if (!showSolid || d.block.shape == com.example.charmobmaker.model.BlockInfo.Shape.FULL) return;
        for (float[] b : com.example.charmobmaker.model.BlockBoxes.of(d)) {
            box(ui.draw, null, b[0] / 16f - 0.5f, b[1] / 16f, b[2] / 16f - 0.5f, b[3] / 16f - 0.5f, b[4] / 16f, b[5] / 16f - 0.5f, 0.75f, 0xB08CF0D8);
        }
    }

    // The outline of a box in a part's space, or in the mob's own when no matrix is given.
    private void box(Draw d, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float width, int colour) {
        float[][] c = new float[8][3];
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            v.set((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
            if (m != null) {
                m.transformPosition(v);
                v.mul(1f / 16f);
            }
            project(v.x, v.y, v.z, c[i]);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            if (c[e[0]][2] > 0f && c[e[1]][2] > 0f) d.line(c[e[0]][0], c[e[0]][1], c[e[1]][0], c[e[1]][1], width, colour);
        }
    }

    // ---------- the selected part: outline and arrows ----------

    void selection(Ui ui, EditorState s) {
        Compiled c = s.compiled();
        int i = c.rig.indexOf(s.selected);
        hoverAxis = -1;
        if (i < 0) return;
        Draw d = ui.draw;
        Part part = c.rig.parts[i];
        float[] b = c.box[i];
        box(d, c.world[i], b[0], b[1], b[2], b[3], b[4], b[5], 0.75f, 0xC0FF8FC7);
        if (s.tab == EditorState.Tab.STATS) return;
        Vector3f o = new Vector3f(part.pivot[0], part.pivot[1], part.pivot[2]);
        c.world[i].transformPosition(o);
        o.mul(1f / 16f);
        project(o.x, o.y, o.z, p0);
        if (p0[2] <= 0f) return;
        float ox = p0[0], oy = p0[1];
        Matrix4f frame = c.rig.parent[i] >= 0 ? c.world[c.rig.parent[i]] : new Matrix4f();
        if (s.tool == EditorState.Tool.SIZE) frame = c.world[i];
        float perBlock = h * zoom / (2f * (float) Math.tan(FOV / 2f) * p0[2]);
        float length = 42f / perBlock;
        boolean free = grabAxis < 0;
        float[][] ends = new float[3][3];
        for (int a = 0; a < 3; a++) {
            Vector3f dir = frame.transformDirection(new Vector3f(AXES[a])).normalize();
            project(o.x + dir.x * length, o.y + dir.y * length, o.z + dir.z * length, ends[a]);
        }
        if (s.tool == EditorState.Tool.TURN) {
            turnRings(ui, frame, o, length, free, ox, oy);
            return;
        }
        for (int a = 0; a < 3; a++) {
            if (ends[a][2] <= 0f) continue;
            if (free && !ui.overPanels() && !ui.blocked && segDist(ui.mx, ui.my, ox, oy, ends[a][0], ends[a][1]) < 6f) hoverAxis = a;
        }
        for (int a = 0; a < 3; a++) {
            if (ends[a][2] <= 0f) continue;
            boolean hot = hoverAxis == a || grabAxis == a;
            int colour = hot ? 0xFFFFFFFF : AXIS_COLOURS[a];
            float width = hot ? 2.5f : 1.75f;
            d.line(ox, oy, ends[a][0], ends[a][1], width, colour);
            float dx = ends[a][0] - ox, dy = ends[a][1] - oy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) continue;
            dx /= len;
            dy /= len;
            if (s.tool == EditorState.Tool.SIZE) {
                d.rect(ends[a][0] - 3f, ends[a][1] - 3f, ends[a][0] + 3f, ends[a][1] + 3f, 1.5f, colour);
            } else {
                float tipX = ends[a][0] + dx * 7f, tipY = ends[a][1] + dy * 7f;
                d.triangle(tipX, tipY, ends[a][0] - dy * 3.5f, ends[a][1] + dx * 3.5f, ends[a][0] + dy * 3.5f, ends[a][1] - dx * 3.5f, colour);
            }
        }
        d.circle(ox, oy, 2.5f, 0xFFFFFFFF);
    }

    private void turnRings(Ui ui, Matrix4f frame, Vector3f o, float length, boolean free, float ox, float oy) {
        Draw d = ui.draw;
        float[][][] rings = new float[3][40][3];
        for (int a = 0; a < 3; a++) {
            Vector3f axis = AXES[a], u = AXES[(a + 1) % 3], v = AXES[(a + 2) % 3];
            for (int i = 0; i < 40; i++) {
                double t = Math.PI * 2.0 * i / 40.0;
                Vector3f p = new Vector3f(u).mul((float) Math.cos(t)).add(new Vector3f(v).mul((float) Math.sin(t)));
                frame.transformDirection(p).normalize().mul(length).add(o);
                project(p.x, p.y, p.z, rings[a][i]);
            }
            if (free && !ui.overPanels() && !ui.blocked) {
                for (int i = 0; i < 40; i++) {
                    float[] p = rings[a][i], q = rings[a][(i + 1) % 40];
                    if (p[2] > 0f && q[2] > 0f && segDist(ui.mx, ui.my, p[0], p[1], q[0], q[1]) < 4.5f) hoverAxis = a;
                }
            }
        }
        for (int a = 0; a < 3; a++) {
            boolean hot = hoverAxis == a || grabAxis == a;
            int colour = hot ? 0xFFFFFFFF : AXIS_COLOURS[a];
            for (int i = 0; i < 40; i++) {
                float[] p = rings[a][i], q = rings[a][(i + 1) % 40];
                if (p[2] > 0f && q[2] > 0f) d.line(p[0], p[1], q[0], q[1], hot ? 2.25f : 1.25f, colour);
            }
        }
        d.circle(ox, oy, 2.5f, 0xFFFFFFFF);
    }

    private static float segDist(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float l2 = dx * dx + dy * dy;
        float t = l2 < 1e-4f ? 0f : Math.max(0f, Math.min(1f, ((px - x0) * dx + (py - y0) * dy) / l2));
        float ex = px - (x0 + dx * t), ey = py - (y0 + dy * t);
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    // ---------- the mouse ----------

    // Called after all the panels had their turn, so it only ever gets clicks nothing else wanted.
    void input(Ui ui, EditorState s) {
        Minecraft mc = Minecraft.getInstance();
        boolean onView = !ui.overPanels() && !ui.blocked;
        if (onView && ui.wheel != 0f) {
            dist = Math.max(0.8f, Math.min(60f, dist * (float) Math.pow(0.88, ui.wheel)));
            ui.wheel = 0f;
        }
        if (ui.pressed && onView && ui.active.isEmpty()) {
            ui.pressed = false;
            lastX = ui.mx;
            lastY = ui.my;
            moved = false;
            if (hoverAxis >= 0) {
                startGrab(ui, s);
            } else {
                orbiting = true;
                ui.active = "viewport";
            }
        }
        if (ui.rightPressed && onView) {
            panning = true;
            lastX = ui.mx;
            lastY = ui.my;
        }
        if (orbiting) {
            float dx = ui.mx - lastX, dy = ui.my - lastY;
            if (Math.abs(dx) + Math.abs(dy) > 0.01f) {
                if (Math.abs(dx) + Math.abs(dy) > 0.5f) moved = true;
                yaw += dx * 0.6f;
                pitch = Math.max(-89f, Math.min(89f, pitch + dy * 0.6f));
                lastX = ui.mx;
                lastY = ui.my;
            }
            if (!ui.mouseIsDown()) {
                orbiting = false;
                if (!moved) {
                    s.selected = pick(ui.mx, ui.my, s);
                    s.lastAdded = "";
                    s.key = null;
                    s.keyTrack = null;
                }
            }
        }
        if (panning) {
            float dx = ui.mx - lastX, dy = ui.my - lastY;
            float perBlock = h * zoom / (2f * (float) Math.tan(FOV / 2f) * dist);
            Vector3f right = new Vector3f(1, 0, 0).rotateY(-yaw * DEG), up = new Vector3f(0, 1, 0).rotateX(-pitch * DEG).rotateY(-yaw * DEG);
            tx -= (dx * right.x - dy * up.x) / perBlock;
            ty -= (dx * right.y - dy * up.y) / perBlock;
            tz -= (dx * right.z - dy * up.z) / perBlock;
            lastX = ui.mx;
            lastY = ui.my;
            if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) panning = false;
        }
        if (grabAxis >= 0) {
            dragGrab(ui, s);
            if (!ui.mouseIsDown()) grabAxis = -1;
        }
    }

    private static Channel channelOf(EditorState.Tool tool) {
        return tool == EditorState.Tool.MOVE ? Channel.POS : tool == EditorState.Tool.TURN ? Channel.ROT : Channel.SCALE;
    }

    private void startGrab(Ui ui, EditorState s) {
        Part p = s.selectedPart();
        if (p == null) return;
        grabAxis = hoverAxis;
        grabX = ui.mx;
        grabY = ui.my;
        grabValues = s.values(p, channelOf(s.tool));
        ui.active = "gizmo";
        s.playing = false;
    }

    private void dragGrab(Ui ui, EditorState s) {
        Compiled c = s.compiled();
        Part part = s.selectedPart();
        if (part == null) return;
        int i = c.rig.indexOf(part.id);
        if (i < 0) return;
        Matrix4f frame = c.rig.parent[i] >= 0 ? c.world[c.rig.parent[i]] : new Matrix4f();
        if (s.tool == EditorState.Tool.SIZE) frame = c.world[i];
        Vector3f o = new Vector3f(part.pivot[0], part.pivot[1], part.pivot[2]);
        c.world[i].transformPosition(o);
        o.mul(1f / 16f);
        project(o.x, o.y, o.z, p0);
        Vector3f dir = frame.transformDirection(new Vector3f(AXES[grabAxis]));
        boolean shift = (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS);
        float dmx = ui.mx - grabX, dmy = ui.my - grabY;
        float[] v = grabValues.clone();
        switch (s.tool) {
            case MOVE -> {
                project(o.x + dir.x / 16f, o.y + dir.y / 16f, o.z + dir.z / 16f, p1);
                float sx = p1[0] - p0[0], sy = p1[1] - p0[1];
                float l2 = sx * sx + sy * sy;
                if (l2 < 1e-5f) return;
                float move = (dmx * sx + dmy * sy) / l2;
                v[grabAxis] = snap(grabValues[grabAxis] + move, shift ? 1f : 0.25f);
            }
            case SIZE -> {
                Vector3f n = new Vector3f(dir).normalize();
                project(o.x + n.x, o.y + n.y, o.z + n.z, p1);
                float sx = p1[0] - p0[0], sy = p1[1] - p0[1];
                float l = (float) Math.sqrt(sx * sx + sy * sy);
                if (l < 1e-3f) return;
                float amount = (dmx * sx + dmy * sy) / l / 50f;
                v[grabAxis] = snap(grabValues[grabAxis] + amount, shift ? 0.25f : 0.01f);
            }
            case TURN -> {
                // how far round: drag along the ring where you grabbed it
                float rx = grabX - p0[0], ry = grabY - p0[1];
                float r = Math.max(10f, (float) Math.sqrt(rx * rx + ry * ry));
                float ax = (rx * dmy - ry * dmx) / (r * r);
                Vector3f camAxis = new Vector3f(dir).normalize();
                Matrix3f toView = new Matrix3f(view);
                toView.transform(camAxis);
                float angle = ax * (camAxis.z >= 0f ? -1f : 1f) * (180f / (float) Math.PI);
                float[] base = s.animating() ? part.rot : new float[3];
                Quaternionf start = new Quaternionf().rotationZYX((base[2] + grabValues[2]) * DEG, (base[1] + grabValues[1]) * DEG, (base[0] + grabValues[0]) * DEG);
                Quaternionf extra = new Quaternionf().rotationAxis(snap(angle, shift ? 15f : 1f) * DEG, AXES[grabAxis]);
                Vector3f e = PartTree.eulerDegrees(extra.mul(start));
                v[0] = e.x - base[0];
                v[1] = e.y - base[1];
                v[2] = e.z - base[2];
            }
        }
        s.setValues(part, channelOf(s.tool), v);
    }

    private static float snap(float v, float step) {
        return Math.round(v / step) * step;
    }

    // ---------- clicking on parts ----------

    private String pick(float mx, float my, EditorState s) {
        Compiled c = s.compiled();
        Vector3f near = unproject(mx, my, -1f), far = unproject(mx, my, 1f);
        near.mul(16f);
        far.mul(16f);
        float best = Float.MAX_VALUE;
        String found = "";
        Matrix4f inv = new Matrix4f();
        Vector3f a = new Vector3f(), b = new Vector3f();
        for (int i = 0; i < c.rig.size; i++) {
            if (c.rig.parts[i].hidden) continue;
            c.world[i].invert(inv);
            inv.transformPosition(near, a);
            inv.transformPosition(far, b);
            float[] box = c.box[i];
            float t = slab(a, b, box);
            if (t >= 0f && t < best) {
                best = t;
                found = c.rig.parts[i].id;
            }
        }
        return found;
    }

    private Vector3f unproject(float mx, float my, float ndcZ) {
        tmp.set(mx / w * 2f - 1f, 1f - my / h * 2f, ndcZ, 1f);
        inverse.transform(tmp);
        return new Vector3f(tmp.x / tmp.w, tmp.y / tmp.w, tmp.z / tmp.w);
    }

    private static float slab(Vector3f a, Vector3f b, float[] box) {
        float t0 = 0f, t1 = 1f;
        float[] from = {a.x, a.y, a.z}, way = {b.x - a.x, b.y - a.y, b.z - a.z};
        for (int i = 0; i < 3; i++) {
            float lo = Math.min(box[i], box[i + 3]) - 0.2f, hi = Math.max(box[i], box[i + 3]) + 0.2f;
            if (Math.abs(way[i]) < 1e-6f) {
                if (from[i] < lo || from[i] > hi) return -1f;
                continue;
            }
            float t2 = (lo - from[i]) / way[i], t3 = (hi - from[i]) / way[i];
            if (t2 > t3) {
                float x = t2;
                t2 = t3;
                t3 = x;
            }
            t0 = Math.max(t0, t2);
            t1 = Math.min(t1, t3);
            if (t0 > t1) return -1f;
        }
        return t0;
    }

    // Used by the tests to find where a part shows up on screen.
    float[] screenOf(EditorState s, String partId) {
        Compiled c = s.compiled();
        int i = c.rig.indexOf(partId);
        if (i < 0) return null;
        Part p = c.rig.parts[i];
        Vector3f o = c.world[i].transformPosition(new Vector3f(p.pivot[0], p.pivot[1], p.pivot[2]));
        o.mul(1f / 16f);
        float[] out = new float[3];
        project(o.x, o.y, o.z, out);
        return out;
    }
}
