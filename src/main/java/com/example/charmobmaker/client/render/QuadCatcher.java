package com.example.charmobmaker.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

// Stands in for a real vertex buffer while a mob's model "draws" itself once, and keeps its quads for a Mesh.
// Model space has Y down and the front at -Z, so everything is turned half round the X axis into ours (Y up, front at +Z), and blown up from blocks to pixels.
final class QuadCatcher implements VertexConsumer {
    final Mesh.Builder out;
    private final float[] q = new float[20];
    private int at;

    QuadCatcher(Mesh.Builder out) {
        this.out = out;
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        int i = at * 5;
        q[i] = x * 16f;
        q[i + 1] = -y * 16f;
        q[i + 2] = -z * 16f;
        q[i + 3] = u;
        q[i + 4] = v;
        if (++at == 4) {
            at = 0;
            out.quad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], q[12], q[13], q[14], q[15], q[16], q[17], q[18], q[19], nx, -ny, -nz, 0);
        }
    }

    // Models only ever use the call above, but a mod could send it the long way round.
    private float lx, ly, lz, lu, lv, lnx, lny, lnz;

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        lx = (float) x;
        ly = (float) y;
        lz = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        lu = u;
        lv = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        lnx = x;
        lny = y;
        lnz = z;
        return this;
    }

    @Override
    public void endVertex() {
        vertex(lx, ly, lz, 1f, 1f, 1f, 1f, lu, lv, 0, 0, lnx, lny, lnz);
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
