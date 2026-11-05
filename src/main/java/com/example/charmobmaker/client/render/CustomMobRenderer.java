package com.example.charmobmaker.client.render;

import com.example.charmobmaker.entity.CustomMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class CustomMobRenderer extends EntityRenderer<CustomMob> {
    private static final double FAR = 48.0 * 48.0; // past this nobody sees the animation, so it isn't worked out

    public CustomMobRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CustomMob mob) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(CustomMob mob, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        shadowRadius = Math.min(4f, mob.getBbWidth() * 0.6f);
        Minecraft mc = Minecraft.getInstance();
        if (!mob.isInvisibleTo(mc.player)) {
            Compiled c = RenderCache.of(mob.designId());
            if (c == null) c = RenderCache.placeholder();
            float body = Mth.rotLerp(partial, mob.yBodyRotO, mob.yBodyRot);
            float head = Mth.rotLerp(partial, mob.yHeadRotO, mob.yHeadRot) - body;
            float pitch = Mth.lerp(partial, mob.xRotO, mob.getXRot());
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-body));
            if (mob.deathTime > 0 && !mob.hasDeathClip()) {
                float fall = (mob.deathTime + partial - 1f) / 20f * 1.6f;
                ps.mulPose(Axis.ZP.rotationDegrees(Math.min(1f, Mth.sqrt(fall)) * 90f));
            }
            ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
            boolean near = mc.getEntityRenderDispatcher().distanceToSqr(mob) < FAR;
            if (near) {
                float now = (mob.tickCount + partial) / 20f;
                float death = mob.deathTime > 0 ? (mob.deathTime + partial) / 20f : -1f;
                mob.anim.pose(c.rig, now, mob.walkAnimation.position(partial), mob.walkAnimation.speed(partial), death, c.out);
                c.rig.pose(c.out, Mth.wrapDegrees(head), pitch, c.world);
            } else {
                c.rig.pose(null, 0f, 0f, c.world);
            }
            int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(mob.hurtTime > 0 || mob.deathTime > 0));
            c.draw(ps, buffers, light, overlay, partial, mob);
            ps.popPose();
        }
        super.render(mob, yaw, partial, ps, buffers, light);
    }
}
