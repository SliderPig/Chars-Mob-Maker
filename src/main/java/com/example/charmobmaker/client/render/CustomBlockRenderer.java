package com.example.charmobmaker.client.render;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.block.CustomBlock;
import com.example.charmobmaker.block.CustomBlockEntity;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Rig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

// Only draws what couldn't be baked into the chunk: paintings, other mobs' parts, items, and whole blocks that animate.
public final class CustomBlockRenderer implements BlockEntityRenderer<CustomBlockEntity> {
    public CustomBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CustomBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        String id = be.designId();
        Design d = Designs.CLIENT.get(id);
        if (d == null) return;
        boolean animated = BlockBakes.animated(d);
        Compiled c = animated ? RenderCache.of(id) : RenderCache.leftovers(id);
        if (c == null || c.nothingToDraw()) return;
        float yaw = be.getBlockState().getValue(CustomBlock.FACING).get2DDataValue() * 90f;
        ps.pushPose();
        ps.translate(0.5f, 0f, 0.5f);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        if (animated) {
            float now = be.getLevel() == null ? 0f : (be.getLevel().getGameTime() + partial) / 20f;
            sample(c.rig, d, be.running(), now, c);
            c.rig.pose(c.out, 0f, 0f, c.world);
        } else {
            c.rig.pose(null, 0f, 0f, c.world);
        }
        c.draw(ps, buffers, light, overlay, partial, null);
        ps.popPose();
    }

    private static void sample(Rig rig, Design d, boolean running, float now, Compiled c) {
        Rig.RigClip clip = running && CustomBlockEntity.hasClip(d, "working") ? rig.clip("working") : rig.clip("idle");
        if (clip == null) {
            c.out.clear();
            return;
        }
        Clip k = clip.clip;
        clip.sample(k.wrapTime(now * k.speed), c.out);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
