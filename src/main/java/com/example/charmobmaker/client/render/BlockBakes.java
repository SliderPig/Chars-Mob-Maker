package com.example.charmobmaker.client.render;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.block.CustomBlockEntity;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Paints;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.Rig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Made blocks that don't move get their boxes and block parts baked right into the chunk, like any normal block, so a house full of them costs nothing per frame.
// Anything that needs its own texture (painted boxes, other mobs' parts, items) or has a clip stays with the block entity renderer.
public final class BlockBakes {
    public record Baked(List<BakedQuad> solid, List<BakedQuad> glass, TextureAtlasSprite particle) {}

    private static final ResourceLocation WHITE = new ResourceLocation("charmobmaker", "block/white");
    private static final Map<String, Baked> CACHE = new ConcurrentHashMap<>();

    private BlockBakes() {}

    public static void clear() {
        CACHE.clear();
    }

    public static void forget(String id) {
        for (int q = 0; q < 4; q++) CACHE.remove(id + "|" + q);
    }

    // A block with an idle or working clip is drawn whole by its block entity renderer, every frame.
    public static boolean animated(Design d) {
        return CustomBlockEntity.hasClip(d, "idle") || CustomBlockEntity.hasClip(d, "working");
    }

    // Goes in the chunk: boxes without paint in a solid colour, and block parts with a model. The rest stays out.
    public static boolean bakesPart(Part p) {
        return switch (p.kind) {
            case CUBE -> (p.color >>> 24) >= 250;
            case BLOCK -> {
                BlockMeshes.Baked b = BlockMeshes.get(p.block);
                yield b != null && !b.live();
            }
            default -> false;
        };
    }

    public static Baked get(String id, int quarter) {
        String key = id + "|" + quarter;
        Baked b = CACHE.get(key);
        if (b != null) return b;
        Design d = Designs.CLIENT.get(id);
        b = d == null ? placeholder() : bake(d, quarter);
        if (d != null) CACHE.put(key, b);
        return b;
    }

    private static TextureAtlasSprite sprite(String name) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        ResourceLocation rl = name == null || name.isEmpty() ? WHITE : ResourceLocation.tryParse(name);
        TextureAtlasSprite s = rl == null ? null : atlas.getSprite(rl);
        if (s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation())) s = atlas.getSprite(WHITE);
        return s;
    }

    private static Baked placeholder() {
        Mesh.Builder b = new Mesh.Builder();
        TextureAtlasSprite white = sprite("");
        b.sprite(white);
        for (int f = 0; f < Part.FACES; f++) Compiled.face(b, f, 8f, 8f, 8f, white.getU0(), white.getV0(), white.getU1(), white.getV1());
        List<BakedQuad> quads = new ArrayList<>();
        toQuads(b.build(), new Matrix4f().translation(0.5f, 0.5f, 0.5f).scale(1f / 16f), 0xFFFF8FC7, quads);
        return new Baked(quads, List.of(), white);
    }

    private static Baked bake(Design d, int quarter) {
        TextureAtlasSprite white = sprite("");
        if (animated(d)) return new Baked(List.of(), List.of(), firstSprite(d, white));
        Rig rig = Rig.of(d);
        Matrix4f[] world = rig.newMatrices();
        rig.pose(null, 0f, 0f, world);
        float yaw = quarter * 90f;
        Matrix4f root = new Matrix4f().translation(0.5f, 0f, 0.5f).rotateY((float) Math.toRadians(-yaw)).scale(1f / 16f);
        List<BakedQuad> solid = new ArrayList<>(), glass = new ArrayList<>();
        Matrix4f m = new Matrix4f();
        for (int i = 0; i < rig.size; i++) {
            Part p = rig.parts[i];
            if (p.hidden || !bakesPart(p)) continue;
            root.mul(world[i], m);
            if (p.kind == Part.Kind.CUBE) {
                float x = p.size[0] / 2f, y = p.size[1] / 2f, z = p.size[2] / 2f;
                if (x <= 0f && y <= 0f && z <= 0f) continue;
                TextureAtlasSprite tex = sprite(p.texture);
                Mesh.Builder b = new Mesh.Builder();
                b.sprite(tex);
                for (int f = 0; f < Part.FACES; f++) {
                    if (d.paints.size() > 0 && Paints.cellFor(p, f) >= 0 && Paints.cellFor(p, f) < d.paints.size()) continue; // painted faces stay with the block entity renderer
                    Compiled.face(b, f, x, y, z, tex.getU0(), tex.getV0(), tex.getU1(), tex.getV1());
                }
                toQuads(b.build(), m, p.color, solid);
            } else {
                BlockMeshes.Baked block = BlockMeshes.get(p.block);
                if (block.solid() != null) toQuads(block.solid(), m, 0xFFFFFFFF, solid);
                if (block.translucent() != null) toQuads(block.translucent(), m, 0xFFFFFFFF, glass);
            }
        }
        return new Baked(solid, glass, firstSprite(d, white));
    }

    private static TextureAtlasSprite firstSprite(Design d, TextureAtlasSprite fallback) {
        for (Part p : d.parts) {
            if (p.kind == Part.Kind.CUBE && !p.texture.isEmpty()) return sprite(p.texture);
            if (p.kind == Part.Kind.BLOCK) {
                BlockMeshes.Baked b = BlockMeshes.get(p.block);
                if (b != null && b.state() != null) return Minecraft.getInstance().getBlockRenderer().getBlockModel(b.state()).getParticleIcon(net.minecraftforge.client.model.data.ModelData.EMPTY);
            }
        }
        return fallback;
    }

    private static void toQuads(Mesh mesh, Matrix4f m, int tint, List<BakedQuad> out) {
        Matrix3f n = new Matrix3f();
        m.normal(n);
        boolean flip = m.determinant3x3() < 0f;
        Vector3f v = new Vector3f(), nv = new Vector3f();
        float[] q = new float[23];
        for (int i = 0; i < mesh.quads; i++) {
            mesh.quad(i, q);
            nv.set(q[20], q[21], q[22]);
            n.transform(nv);
            if (nv.lengthSquared() > 1e-10f) nv.normalize();
            int colour = times(tint, mesh.colour(i));
            int nx = (int) (nv.x * 127f) & 255, ny = (int) (nv.y * 127f) & 255, nz = (int) (nv.z * 127f) & 255;
            int packedNormal = nx | (ny << 8) | (nz << 16);
            int[] data = new int[32];
            for (int c = 0; c < 4; c++) {
                int from = (flip ? 3 - c : c) * 5;
                v.set(q[from], q[from + 1], q[from + 2]);
                m.transformPosition(v);
                int at = c * 8;
                data[at] = Float.floatToRawIntBits(v.x);
                data[at + 1] = Float.floatToRawIntBits(v.y);
                data[at + 2] = Float.floatToRawIntBits(v.z);
                data[at + 3] = colour;
                data[at + 4] = Float.floatToRawIntBits(q[from + 3]);
                data[at + 5] = Float.floatToRawIntBits(q[from + 4]);
                data[at + 6] = 0;
                data[at + 7] = packedNormal;
            }
            TextureAtlasSprite s = mesh.sprite(i);
            Direction dir = Direction.getNearest(nv.x, nv.y, nv.z);
            out.add(new BakedQuad(data, -1, dir, s != null ? s : sprite(""), true, false));
        }
    }

    // Vertex colours in a baked quad are A B G R from the top byte down.
    private static int times(int tint, int quadColour) {
        int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255;
        if (quadColour != 0) {
            r = r * ((quadColour >> 16) & 255) / 255;
            g = g * ((quadColour >> 8) & 255) / 255;
            b = b * (quadColour & 255) / 255;
        }
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
