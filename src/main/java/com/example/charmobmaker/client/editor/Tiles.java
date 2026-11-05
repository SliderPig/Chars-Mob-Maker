package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.render.Compiled;
import com.example.charmobmaker.client.render.Mesh;
import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.ui.Ui;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// The little live 3D pictures in lists: a mob's body part or a whole design, slowly turning.
final class Tiles {
    private Tiles() {}

    private static float spin() {
        return (System.currentTimeMillis() % 7200L) / 20f;
    }

    private static PoseStack place(Ui ui, float cx, float cy, float size, float yaw) {
        PoseStack ps = ui.g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 120f);
        ps.scale(size, -size, size);
        ps.mulPose(Axis.XP.rotationDegrees(18f));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        return ps;
    }

    static void mesh(Ui ui, float cx, float cy, float box, Mesh mesh, ResourceLocation texture) {
        if (mesh == null || mesh.isEmpty()) return;
        ui.later(() -> {
            float spanX = mesh.maxX - mesh.minX, spanY = mesh.maxY - mesh.minY, spanZ = mesh.maxZ - mesh.minZ;
            float span = Math.max(spanX, Math.max(spanY, spanZ));
            if (span < 0.01f) return;
            float scale = box / (span * 1.5f);
            PoseStack ps = place(ui, cx, cy, scale, 215f + spin());
            ps.translate(-(mesh.minX + mesh.maxX) / 2f, -(mesh.minY + mesh.maxY) / 2f, -(mesh.minZ + mesh.maxZ) / 2f);
            Lighting.setupForEntityInInventory();
            var buffers = ui.g.bufferSource();
            Matrix4f m = ps.last().pose();
            Matrix3f n = new Matrix3f();
            m.normal(n);
            mesh.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), m, n, 0xFFFFFFFF, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, false);
            ui.g.flush();
            ps.popPose();
            Lighting.setupFor3DItems();
        });
    }

    static void wholeMob(Ui ui, float cx, float cy, float box, MobLibrary.Model m) {
        if (m == null || m.dummy == null) return;
        ui.later(() -> {
            float span = Math.max(m.height, m.width) * 16f;
            float scale = box / (span * 1.25f);
            PoseStack ps = place(ui, cx, cy, scale, 215f + spin());
            ps.translate(0f, -m.height * 8f, 0f);
            ps.scale(16f, 16f, 16f);
            Lighting.setupForEntityInInventory();
            MobLibrary.drawWhole(m, ps, ui.g.bufferSource(), LightTexture.FULL_BRIGHT, ui.partial, null);
            ui.g.flush();
            ps.popPose();
            Lighting.setupFor3DItems();
        });
    }

    static void design(Ui ui, float cx, float cy, float box, Compiled c) {
        if (c == null) return;
        ui.later(() -> {
            float reach = Math.max(8f, c.rig.reach());
            float h = c.design.stats.height * 16f;
            float scale = box / (Math.max(reach, h) * 1.3f);
            PoseStack ps = place(ui, cx, cy, scale, 215f + spin());
            ps.translate(0f, -Math.min(reach, h) * 0.5f, 0f);
            Lighting.setupForEntityInInventory();
            c.rig.pose(null, 0f, 0f, c.world);
            c.draw(ps, ui.g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ui.partial, null);
            ui.g.flush();
            ps.popPose();
            Lighting.setupFor3DItems();
        });
    }
}
