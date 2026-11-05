package com.example.charmobmaker.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;

// A ready-made list of quads, in pixels around the part's origin. Baked once and just sent out each frame, nothing gets worked out twice.
public final class Mesh {
    private static final int PER_VERTEX = 5, PER_QUAD = 4 * PER_VERTEX + 3;
    private final float[] data; // 4 x (x y z u v) then nx ny nz
    private final int[] colours; // per quad, 0 means white
    public final int quads;
    public final float minX, minY, minZ, maxX, maxY, maxZ;

    private Mesh(float[] data, int[] colours, int quads) {
        this.data = data;
        this.colours = colours;
        this.quads = quads;
        float a = Float.MAX_VALUE, b = Float.MAX_VALUE, c = Float.MAX_VALUE, d = -Float.MAX_VALUE, e = -Float.MAX_VALUE, f = -Float.MAX_VALUE;
        for (int q = 0; q < quads; q++) {
            for (int v = 0; v < 4; v++) {
                int at = q * PER_QUAD + v * PER_VERTEX;
                a = Math.min(a, data[at]);
                b = Math.min(b, data[at + 1]);
                c = Math.min(c, data[at + 2]);
                d = Math.max(d, data[at]);
                e = Math.max(e, data[at + 1]);
                f = Math.max(f, data[at + 2]);
            }
        }
        if (quads == 0) a = b = c = d = e = f = 0f;
        minX = a;
        minY = b;
        minZ = c;
        maxX = d;
        maxY = e;
        maxZ = f;
    }

    public boolean isEmpty() {
        return quads == 0;
    }

    // tint multiplies every quad's own colour. flip = the part is mirrored, so the quads turn over to stay facing outwards.
    public void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, int tint, int overlay, int light, boolean flip) {
        float ta = (tint >>> 24) / 255f, tr = ((tint >> 16) & 255) / 255f, tg = ((tint >> 8) & 255) / 255f, tb = (tint & 255) / 255f;
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12(), m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float[] d = data;
        for (int q = 0; q < quads; q++) {
            int at = q * PER_QUAD;
            float nx = d[at + 20], ny = d[at + 21], nz = d[at + 22];
            float wx = n.m00() * nx + n.m10() * ny + n.m20() * nz;
            float wy = n.m01() * nx + n.m11() * ny + n.m21() * nz;
            float wz = n.m02() * nx + n.m12() * ny + n.m22() * nz;
            float len = wx * wx + wy * wy + wz * wz;
            if (len > 1e-12f) {
                len = 1f / (float) Math.sqrt(len);
                wx *= len;
                wy *= len;
                wz *= len;
            }
            float r = tr, g = tg, b = tb, a = ta;
            if (colours != null && colours[q] != 0) {
                int c = colours[q];
                r *= ((c >> 16) & 255) / 255f;
                g *= ((c >> 8) & 255) / 255f;
                b *= (c & 255) / 255f;
            }
            for (int i = 0; i < 4; i++) {
                int v = at + (flip ? 3 - i : i) * PER_VERTEX;
                float x = d[v], y = d[v + 1], z = d[v + 2];
                vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                        r, g, b, a, d[v + 3], d[v + 4], overlay, light, wx, wy, wz);
            }
        }
    }

    public static final class Builder {
        private float[] data = new float[PER_QUAD * 6];
        private int[] colours;
        private int quads;

        // Four corners going anti-clockwise as seen from outside
        public Builder quad(float x0, float y0, float z0, float u0, float v0,
                            float x1, float y1, float z1, float u1, float v1,
                            float x2, float y2, float z2, float u2, float v2,
                            float x3, float y3, float z3, float u3, float v3,
                            float nx, float ny, float nz, int colour) {
            if ((quads + 1) * PER_QUAD > data.length) data = Arrays.copyOf(data, data.length * 2);
            int at = quads * PER_QUAD;
            float[] d = data;
            d[at] = x0;
            d[at + 1] = y0;
            d[at + 2] = z0;
            d[at + 3] = u0;
            d[at + 4] = v0;
            d[at + 5] = x1;
            d[at + 6] = y1;
            d[at + 7] = z1;
            d[at + 8] = u1;
            d[at + 9] = v1;
            d[at + 10] = x2;
            d[at + 11] = y2;
            d[at + 12] = z2;
            d[at + 13] = u2;
            d[at + 14] = v2;
            d[at + 15] = x3;
            d[at + 16] = y3;
            d[at + 17] = z3;
            d[at + 18] = u3;
            d[at + 19] = v3;
            d[at + 20] = nx;
            d[at + 21] = ny;
            d[at + 22] = nz;
            if (colour != 0) {
                if (colours == null) colours = new int[data.length / PER_QUAD + 1];
                if (colours.length <= quads) colours = Arrays.copyOf(colours, data.length / PER_QUAD + 1);
                colours[quads] = colour;
            }
            quads++;
            return this;
        }

        public boolean isEmpty() {
            return quads == 0;
        }

        public Mesh build() {
            return new Mesh(Arrays.copyOf(data, quads * PER_QUAD), colours == null ? null : Arrays.copyOf(colours, quads), quads);
        }
    }
}
