package com.example.charmobmaker.client.render;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.Design;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Made items and blocks as items: the real 3D thing, in the hand, on the ground and in slots.
public final class CustomItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CustomItemRenderer instance;

    private CustomItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CustomItemRenderer get() {
        if (instance == null) instance = new CustomItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        String id = DesignStacks.idOf(stack);
        Design d = Designs.CLIENT.get(id);
        Compiled c = d == null ? RenderCache.placeholder() : RenderCache.of(id);
        if (c == null) return;
        float[] b = c.bounds();
        float span = Math.max(1f, Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2])));
        boolean inHand = ctx.firstPerson() || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.HEAD;
        // in slots and on the ground it fits the space; in the hand it's its real size times what the design asks
        float scale = inHand && d != null && d.type == Design.Type.ITEM ? d.item.handScale / 16f : Math.min(1f, 16f / span) / 16f;
        if (ctx == ItemDisplayContext.GUI && d != null && d.type == Design.Type.ITEM) scale = 14f / span / 16f;
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        ps.scale(scale, scale, scale);
        ps.translate(-(b[0] + b[3]) / 2f, -(b[1] + b[4]) / 2f - (inHand && d != null && d.type == Design.Type.ITEM ? d.item.handGrip : 0f), -(b[2] + b[5]) / 2f);
        pose(c, d);
        c.draw(ps, buffers, light, overlay, 0f, null);
        ps.popPose();
    }

    private static void pose(Compiled c, Design d) {
        var clip = d == null ? null : c.rig.clip("idle");
        if (clip == null || clip.clip.tracks.isEmpty()) {
            c.rig.pose(null, 0f, 0f, c.world);
            return;
        }
        Clip k = clip.clip;
        var level = Minecraft.getInstance().level;
        float now = level == null ? 0f : level.getGameTime() / 20f;
        clip.sample(k.wrapTime(now * k.speed), c.out);
        c.rig.pose(c.out, 0f, 0f, c.world);
    }
}
