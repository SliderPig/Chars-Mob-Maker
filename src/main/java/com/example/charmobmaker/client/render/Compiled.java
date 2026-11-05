package com.example.charmobmaker.client.render;

import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Paints;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.PoseOut;
import com.example.charmobmaker.model.Rig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// A design turned into something quick to draw: every part's mesh found once and sorted by what texture it needs, so a mob with 60 parts is still only a few draw calls.
public final class Compiled {
    private static final ResourceLocation WHITE = new ResourceLocation("charmobmaker", "block/white");

    public static final class Entry {
        final int part;
        final Mesh mesh;
        final int tint;

        Entry(int part, Mesh mesh, int tint) {
            this.part = part;
            this.mesh = mesh;
            this.tint = tint;
        }
    }

    public static final class Batch {
        final RenderType type;
        final ResourceLocation texture;
        final List<Entry> entries = new ArrayList<>();

        Batch(RenderType type, ResourceLocation texture) {
            this.type = type;
            this.texture = texture;
        }
    }

    private interface Live {
        void draw(PoseStack ps, MultiBufferSource buffers, int light, int overlay, float partial, LivingEntity host);
    }

    public final Design design;
    public final Rig rig;
    public final PoseOut out;
    public final Matrix4f[] world;
    // pixels around each part's own origin: x0 y0 z0 x1 y1 z1
    public final float[][] box;
    private final List<Batch> batches = new ArrayList<>();
    private final Live[] live;
    private final boolean[] hidden;
    private final boolean[] glow;
    private final PaintAtlas paint;
    private final Matrix4f combined = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();
    private final PoseStack liveStack = new PoseStack();
    private float[] bounds;

    public Compiled(Design d) {
        design = d;
        rig = Rig.of(d);
        out = new PoseOut(rig.size);
        world = rig.newMatrices();
        box = new float[rig.size][];
        live = new Live[rig.size];
        hidden = new boolean[rig.size];
        glow = new boolean[rig.size];
        paint = d.paints.isEmpty() ? null : new PaintAtlas(d);
        Map<String, Batch> byKey = new LinkedHashMap<>();
        for (int i = 0; i < rig.size; i++) {
            Part p = rig.parts[i];
            hidden[i] = p.hidden;
            glow[i] = p.glow;
            box[i] = new float[] {-1, -1, -1, 1, 1, 1};
            switch (p.kind) {
                case CUBE -> cube(i, p, byKey);
                case BLOCK -> block(i, p, byKey);
                case MOB -> mob(i, p, byKey);
                case ITEM -> item(i, p);
            }
        }
        batches.addAll(byKey.values());
    }

    public void release() {
        if (paint != null) paint.release();
    }

    public void repaint(Design d) {
        if (paint != null) paint.update(d);
    }

    private Batch batch(Map<String, Batch> byKey, String key, RenderType type, ResourceLocation texture) {
        return byKey.computeIfAbsent(key, k -> new Batch(type, texture));
    }

    private void add(Map<String, Batch> byKey, int part, Mesh mesh, int tint) {
        batch(byKey, "solid", Sheets.cutoutBlockSheet(), TextureAtlas.LOCATION_BLOCKS).entries.add(new Entry(part, mesh, tint));
    }

    private static TextureAtlasSprite sprite(String name) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        ResourceLocation rl = name == null || name.isEmpty() ? WHITE : ResourceLocation.tryParse(name);
        TextureAtlasSprite s = rl == null ? null : atlas.getSprite(rl);
        if (s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation())) s = atlas.getSprite(WHITE);
        return s;
    }

    private void cube(int i, Part p, Map<String, Batch> byKey) {
        float x = p.size[0] / 2f, y = p.size[1] / 2f, z = p.size[2] / 2f;
        box[i] = new float[] {-x, -y, -z, x, y, z};
        if (p.size[0] <= 0f && p.size[1] <= 0f && p.size[2] <= 0f) return; // a pure joint, nothing to draw
        boolean painted = paint != null && Paints.usesPaint(p);
        TextureAtlasSprite tex = sprite(p.texture);
        Mesh.Builder atlas = new Mesh.Builder(), painting = new Mesh.Builder();
        for (int face = 0; face < Part.FACES; face++) {
            int cell = painted ? Paints.cellFor(p, face) : -1;
            if (cell >= 0 && cell < paint.cells()) {
                face(painting, face, x, y, z, paint.u(cell), paint.v(cell), paint.u(cell) + paint.cellSize(), paint.v(cell) + paint.cellSize());
            } else {
                face(atlas, face, x, y, z, tex.getU0(), tex.getV0(), tex.getU1(), tex.getV1());
            }
        }
        if (!atlas.isEmpty()) {
            boolean seeThrough = (p.color >>> 24) < 250;
            if (seeThrough) batch(byKey, "glass", Sheets.translucentCullBlockSheet(), TextureAtlas.LOCATION_BLOCKS).entries.add(new Entry(i, atlas.build(), p.color));
            else add(byKey, i, atlas.build(), p.color);
        }
        if (!painting.isEmpty()) {
            batch(byKey, "paint", RenderType.entityCutoutNoCull(paint.location()), paint.location()).entries.add(new Entry(i, painting.build(), p.color | 0xFF000000));
        }
    }

    // Each face's corners go anti-clockwise as seen from outside, with the picture the right way up on the sides.
    public static void face(Mesh.Builder b, int face, float x, float y, float z, float u0, float v0, float u1, float v1) {
        switch (face) {
            case 0 -> b.quad(-x, y, z, u0, v0, -x, -y, z, u0, v1, x, -y, z, u1, v1, x, y, z, u1, v0, 0, 0, 1, 0);
            case 1 -> b.quad(x, y, -z, u0, v0, x, -y, -z, u0, v1, -x, -y, -z, u1, v1, -x, y, -z, u1, v0, 0, 0, -1, 0);
            case 2 -> b.quad(x, y, z, u0, v0, x, -y, z, u0, v1, x, -y, -z, u1, v1, x, y, -z, u1, v0, 1, 0, 0, 0);
            case 3 -> b.quad(-x, y, -z, u0, v0, -x, -y, -z, u0, v1, -x, -y, z, u1, v1, -x, y, z, u1, v0, -1, 0, 0, 0);
            case 4 -> b.quad(-x, y, -z, u0, v0, -x, y, z, u0, v1, x, y, z, u1, v1, x, y, -z, u1, v0, 0, 1, 0, 0);
            default -> b.quad(-x, -y, z, u0, v0, -x, -y, -z, u0, v1, x, -y, -z, u1, v1, x, -y, z, u1, v0, 0, -1, 0, 0);
        }
    }

    private void missing(int i, Map<String, Batch> byKey) {
        Mesh.Builder b = new Mesh.Builder();
        TextureAtlasSprite tex = sprite("");
        for (int face = 0; face < Part.FACES; face++) face(b, face, 3f, 3f, 3f, tex.getU0(), tex.getV0(), tex.getU1(), tex.getV1());
        add(byKey, i, b.build(), 0xFFFF6FB5);
        box[i] = new float[] {-3, -3, -3, 3, 3, 3};
    }

    private void block(int i, Part p, Map<String, Batch> byKey) {
        BlockMeshes.Baked b = BlockMeshes.get(p.block);
        if (b == null) {
            missing(i, byKey);
            return;
        }
        box[i] = new float[] {-8, -8, -8, 8, 8, 8};
        if (b.live()) {
            BlockState state = b.state();
            live[i] = (ps, buffers, light, overlay, partial, host) -> {
                ps.scale(16f, 16f, 16f);
                ps.translate(-0.5f, -0.5f, -0.5f);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buffers, light, overlay, ModelData.EMPTY, null);
            };
            return;
        }
        if (b.solid() != null) {
            add(byKey, i, b.solid(), 0xFFFFFFFF);
            box[i] = bounds(b.solid());
        }
        if (b.translucent() != null) {
            batch(byKey, "glass", Sheets.translucentCullBlockSheet(), TextureAtlas.LOCATION_BLOCKS).entries.add(new Entry(i, b.translucent(), 0xFFFFFFFF));
            if (b.solid() == null) box[i] = bounds(b.translucent());
        }
    }

    private static float[] bounds(Mesh m) {
        return new float[] {m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ};
    }

    private void mob(int i, Part p, Map<String, Batch> byKey) {
        MobLibrary.Model m = MobLibrary.get(p.mob);
        if (m == null) {
            missing(i, byKey);
            return;
        }
        if (p.bone.isEmpty()) {
            float w = m.width * 8f, h = m.height * 16f;
            box[i] = new float[] {-w, 0, -w, w, h, w};
            live[i] = (ps, buffers, light, overlay, partial, host) -> {
                ps.scale(16f, 16f, 16f);
                MobLibrary.drawWhole(m, ps, buffers, light, partial, host);
            };
            return;
        }
        MobLibrary.Bone b = m.byPath.get(p.bone);
        if (b == null) {
            missing(i, byKey);
            return;
        }
        Mesh mesh = p.solo ? b.self : b.whole;
        ResourceLocation tex = p.mobTexture.isEmpty() ? m.texture : ResourceLocation.tryParse(p.mobTexture);
        if (tex == null) tex = m.texture;
        ResourceLocation texture = tex;
        batch(byKey, "mob|" + texture, RenderType.entityCutoutNoCull(texture), texture).entries.add(new Entry(i, mesh, 0xFFFFFFFF));
        box[i] = mesh.isEmpty() ? new float[] {-1, -1, -1, 1, 1, 1} : bounds(mesh);
    }

    private void item(int i, Part p) {
        ResourceLocation rl = ResourceLocation.tryParse(p.item);
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        ItemDisplayContext ctx = displayOf(p.display);
        box[i] = new float[] {-8, -8, -8, 8, 8, 8};
        live[i] = (ps, buffers, light, overlay, partial, host) -> {
            ps.scale(16f, 16f, 16f);
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(stack, ctx, light, overlay, ps, buffers, mc.level, 0);
        };
    }

    public static ItemDisplayContext displayOf(String name) {
        for (ItemDisplayContext c : ItemDisplayContext.values()) if (c.getSerializedName().equals(name) || c.name().equalsIgnoreCase(name)) return c;
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    // Draws it. pose comes with the mob's feet at the origin and in pixels. world must already be worked out with rig.pose.
    public void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float partial, LivingEntity host) {
        Matrix4f root = pose.last().pose();
        for (Batch b : batches) {
            VertexConsumer vc = buffers.getBuffer(b.type);
            for (Entry e : b.entries) {
                if (hidden[e.part]) continue;
                Matrix4f w = world[e.part];
                root.mul(w, combined);
                combined.normal(normal);
                e.mesh.emit(vc, combined, normal, e.tint, overlay, glow[e.part] ? LightTexture.FULL_BRIGHT : light, w.determinant3x3() < 0f);
            }
        }
        for (int i = 0; i < live.length; i++) {
            if (live[i] == null || hidden[i]) continue;
            root.mul(world[i], combined);
            combined.normal(normal);
            liveStack.pushPose();
            liveStack.last().pose().set(combined);
            liveStack.last().normal().set(normal);
            live[i].draw(liveStack, buffers, glow[i] ? LightTexture.FULL_BRIGHT : light, overlay, partial, host);
            liveStack.popPose();
        }
    }

    // The selected part shining in the editor: its own shape again, added on top.
    public void drawGlow(PoseStack pose, MultiBufferSource buffers, int part, int colour) {
        Matrix4f root = pose.last().pose();
        for (Batch b : batches) {
            VertexConsumer vc = null;
            for (Entry e : b.entries) {
                if (e.part != part) continue;
                if (vc == null) vc = buffers.getBuffer(RenderType.eyes(b.texture));
                Matrix4f w = world[e.part];
                root.mul(w, combined);
                combined.normal(normal);
                e.mesh.emit(vc, combined, normal, colour, 0, LightTexture.FULL_BRIGHT, w.determinant3x3() < 0f);
            }
        }
    }

    // How far the mob reaches in its resting pose, in pixels: x0 y0 z0 x1 y1 z1. Used to aim the camera and fit the little pictures.
    public float[] bounds() {
        if (bounds != null) return bounds;
        Matrix4f[] m = rig.newMatrices();
        rig.pose(null, 0f, 0f, m);
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f v = new Vector3f();
        boolean any = false;
        for (int i = 0; i < rig.size; i++) {
            Part p = rig.parts[i];
            if (p.hidden || p.kind == Part.Kind.CUBE && p.size[0] <= 0f && p.size[1] <= 0f && p.size[2] <= 0f) continue;
            any = true;
            float[] box = this.box[i];
            for (int c = 0; c < 8; c++) {
                v.set((c & 1) == 0 ? box[0] : box[3], (c & 2) == 0 ? box[1] : box[4], (c & 4) == 0 ? box[2] : box[5]);
                m[i].transformPosition(v);
                b[0] = Math.min(b[0], v.x);
                b[1] = Math.min(b[1], v.y);
                b[2] = Math.min(b[2], v.z);
                b[3] = Math.max(b[3], v.x);
                b[4] = Math.max(b[4], v.y);
                b[5] = Math.max(b[5], v.z);
            }
        }
        if (!any) b = new float[] {-8f, 0f, -8f, 8f, 16f, 8f};
        bounds = b;
        return b;
    }

    public boolean hasMesh(int part) {
        for (Batch b : batches) for (Entry e : b.entries) if (e.part == part) return true;
        return false;
    }
}
