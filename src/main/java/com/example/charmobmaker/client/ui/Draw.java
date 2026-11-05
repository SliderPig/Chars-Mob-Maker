package com.example.charmobmaker.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

// Soft-edged shapes: every outline gets a thin fade so corners and lines look smooth at any size, nothing blocky.
public final class Draw {
    private static final float[] PX = new float[256], PY = new float[256], NX = new float[256], NY = new float[256];
    private GuiGraphics g;
    private Matrix4f m;
    private VertexConsumer vc;
    private float px = 0.5f; // one real pixel, in layout units

    public void begin(GuiGraphics g, float onePixel) {
        this.g = g;
        this.px = onePixel;
        this.vc = null;
    }

    private VertexConsumer vc() {
        // asked for every time because text and items in between end the batch
        vc = g.bufferSource().getBuffer(RenderType.gui());
        m = g.pose().last().pose();
        return vc;
    }

    private void v(float x, float y, int c) {
        vc.vertex(m, x, y, 0f).color(c).endVertex();
    }

    private static int fade(int c, float a) {
        return ((int) ((c >>> 24) * a) << 24) | (c & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private int outline(float x0, float y0, float x1, float y1, float r) {
        r = Math.max(0f, Math.min(r, Math.min(x1 - x0, y1 - y0) / 2f));
        int seg = r <= 0.01f ? 1 : Math.min(10, Math.max(3, (int) (r / px / 1.5f)));
        int n = 0;
        float[][] corners = {{x0 + r, y0 + r, 270f}, {x0 + r, y1 - r, 180f}, {x1 - r, y1 - r, 90f}, {x1 - r, y0 + r, 0f}};
        for (float[] c : corners) {
            for (int i = 0; i <= seg; i++) {
                double a = Math.toRadians(c[2] - 90.0 * i / seg);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                PX[n] = c[0] + cos * r;
                PY[n] = c[1] + sin * r;
                NX[n] = cos;
                NY[n] = sin;
                n++;
            }
        }
        return n;
    }

    public void rect(float x0, float y0, float x1, float y1, float r, int colour) {
        grad(x0, y0, x1, y1, r, colour, colour);
    }

    // Rounded box fading from one colour at the top to another at the bottom.
    public void grad(float x0, float y0, float x1, float y1, float r, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0) return;
        vc();
        int n = outline(x0, y0, x1, y1, r);
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f, h = Math.max(0.001f, y1 - y0);
        int mid = mix(top, bottom, 0.5f);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int ci = mix(top, bottom, (PY[i] - y0) / h), cj = mix(top, bottom, (PY[j] - y0) / h);
            v(cx, cy, mid);
            v(PX[i], PY[i], ci);
            v(PX[j], PY[j], cj);
            v(PX[j], PY[j], cj);
            v(PX[i] + NX[i] * px, PY[i] + NY[i] * px, ci & 0xFFFFFF);
            v(PX[j] + NX[j] * px, PY[j] + NY[j] * px, cj & 0xFFFFFF);
            v(PX[j], PY[j], cj);
            v(PX[i], PY[i], ci);
        }
    }

    // A soft shadow or glow spreading out from a rounded box.
    public void halo(float x0, float y0, float x1, float y1, float r, int colour, float spread) {
        vc();
        int n = outline(x0, y0, x1, y1, r);
        int clear = colour & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            v(PX[i] + NX[i] * spread, PY[i] + NY[i] * spread, clear);
            v(PX[j] + NX[j] * spread, PY[j] + NY[j] * spread, clear);
            v(PX[j], PY[j], colour);
            v(PX[i], PY[i], colour);
        }
    }

    // A thin line round a rounded box.
    public void border(float x0, float y0, float x1, float y1, float r, float w, int colour) {
        vc();
        int n = outline(x0, y0, x1, y1, r);
        int clear = colour & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ix = PX[i] - NX[i] * w, iy = PY[i] - NY[i] * w, jx = PX[j] - NX[j] * w, jy = PY[j] - NY[j] * w;
            v(ix, iy, colour);
            v(PX[i], PY[i], colour);
            v(PX[j], PY[j], colour);
            v(jx, jy, colour);
            v(PX[i] + NX[i] * px, PY[i] + NY[i] * px, clear);
            v(PX[j] + NX[j] * px, PY[j] + NY[j] * px, clear);
            v(PX[j], PY[j], colour);
            v(PX[i], PY[i], colour);
            v(ix, iy, colour);
            v(jx, jy, colour);
            v(jx - NX[j] * px, jy - NY[j] * px, clear);
            v(ix - NX[i] * px, iy - NY[i] * px, clear);
        }
    }

    public void circle(float cx, float cy, float r, int colour) {
        rect(cx - r, cy - r, cx + r, cy + r, r, colour);
    }

    public void ring(float cx, float cy, float r, float w, int colour) {
        border(cx - r, cy - r, cx + r, cy + r, r, w, colour);
    }

    // A straight line with soft sides.
    public void line(float x0, float y0, float x1, float y1, float w, int colour) {
        lineFade(x0, y0, x1, y1, w, colour, colour);
    }

    public void lineFade(float x0, float y0, float x1, float y1, float w, int from, int to) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) return;
        vc();
        float nx = -dy / len, ny = dx / len;
        float h = w / 2f, f = h + px;
        v(x0 + nx * h, y0 + ny * h, from);
        v(x1 + nx * h, y1 + ny * h, to);
        v(x1 - nx * h, y1 - ny * h, to);
        v(x0 - nx * h, y0 - ny * h, from);
        int fromClear = from & 0xFFFFFF, toClear = to & 0xFFFFFF;
        v(x0 + nx * f, y0 + ny * f, fromClear);
        v(x1 + nx * f, y1 + ny * f, toClear);
        v(x1 + nx * h, y1 + ny * h, to);
        v(x0 + nx * h, y0 + ny * h, from);
        v(x0 - nx * h, y0 - ny * h, from);
        v(x1 - nx * h, y1 - ny * h, to);
        v(x1 - nx * f, y1 - ny * f, toClear);
        v(x0 - nx * f, y0 - ny * f, fromClear);
    }

    // One slice of a soft pool of light: bright at the middle, gone by the edge.
    public void fan(float cx, float cy, float ax, float ay, float bx, float by, int inner, int outer) {
        vc();
        v(cx, cy, inner);
        v(ax, ay, outer);
        v(bx, by, outer);
        v(bx, by, outer);
    }

    // A filled triangle; the corners can come in any order.
    public void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int colour) {
        vc();
        v(x0, y0, colour);
        v(x1, y1, colour);
        v(x2, y2, colour);
        v(x2, y2, colour);
        int clear = colour & 0xFFFFFF;
        edge(x0, y0, x1, y1, colour, clear, (x0 + x1 + x2) / 3f, (y0 + y1 + y2) / 3f);
        edge(x1, y1, x2, y2, colour, clear, (x0 + x1 + x2) / 3f, (y0 + y1 + y2) / 3f);
        edge(x2, y2, x0, y0, colour, clear, (x0 + x1 + x2) / 3f, (y0 + y1 + y2) / 3f);
    }

    private void edge(float x0, float y0, float x1, float y1, int c, int clear, float cx, float cy) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) return;
        float nx = -dy / len, ny = dx / len;
        if ((x0 - cx) * nx + (y0 - cy) * ny < 0f) {
            nx = -nx;
            ny = -ny;
        }
        v(x0 + nx * px, y0 + ny * px, clear);
        v(x1 + nx * px, y1 + ny * px, clear);
        v(x1, y1, c);
        v(x0, y0, c);
    }

    public static int alpha(int colour, float a) {
        return fade(colour, a);
    }
}
