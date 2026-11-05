package com.example.charmobmaker.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Block parts: the block's real model, its quads copied out once per block state. Blocks that draw themselves live (chests, signs) get drawn the usual way each frame.
public final class BlockMeshes {
    public record Baked(BlockState state, Mesh solid, Mesh translucent, boolean live) {}

    private static final Map<String, Baked> CACHE = new ConcurrentHashMap<>(); // the chunk builder threads ask too

    private BlockMeshes() {}

    public static void clear() {
        CACHE.clear();
    }

    public static BlockState parse(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            var lookup = mc.level != null ? mc.level.holderLookup(Registries.BLOCK) : BuiltInRegistries.BLOCK.asLookup();
            return BlockStateParser.parseForBlock(lookup, text, false).blockState();
        } catch (Exception e) {
            int open = text.indexOf('[');
            ResourceLocation rl = ResourceLocation.tryParse(open < 0 ? text : text.substring(0, open));
            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) return ForgeRegistries.BLOCKS.getValue(rl).defaultBlockState();
            return null;
        }
    }

    // Null when no such block exists here (a mod that's missing): the part then shows as a little box.
    public static Baked get(String text) {
        Baked b = CACHE.get(text);
        if (b != null) return b;
        BlockState state = parse(text);
        if (state == null) return null;
        b = bake(state);
        CACHE.put(text, b);
        return b;
    }

    private static Baked bake(BlockState state) {
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) return new Baked(state, null, null, true);
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        BlockColors colours = mc.getBlockColors();
        RandomSource rand = RandomSource.create(42L);
        Mesh.Builder solid = new Mesh.Builder(), glass = new Mesh.Builder();
        if (state.getRenderShape() != RenderShape.INVISIBLE && !state.isAir()) {
            for (RenderType type : model.getRenderTypes(state, rand, ModelData.EMPTY)) {
                Mesh.Builder into = type == RenderType.translucent() ? glass : solid;
                for (Direction d : Direction.values()) {
                    rand.setSeed(42L);
                    add(into, model.getQuads(state, d, rand, ModelData.EMPTY, type), state, colours);
                }
                rand.setSeed(42L);
                add(into, model.getQuads(state, null, rand, ModelData.EMPTY, type), state, colours);
            }
        }
        return new Baked(state, solid.isEmpty() ? null : solid.build(), glass.isEmpty() ? null : glass.build(), false);
    }

    private static void add(Mesh.Builder into, List<BakedQuad> quads, BlockState state, BlockColors colours) {
        for (BakedQuad q : quads) {
            into.sprite(q.getSprite());
            int[] v = q.getVertices();
            int stride = v.length / 4;
            int colour = 0;
            if (q.isTinted()) {
                int c = colours.getColor(state, null, null, q.getTintIndex());
                if (c != -1) colour = 0xFF000000 | c;
            }
            var n = q.getDirection().getNormal();
            into.quad(px(v[0]), py(v[1]), pz(v[2]), f(v[4]), f(v[5]),
                    px(v[stride]), py(v[stride + 1]), pz(v[stride + 2]), f(v[stride + 4]), f(v[stride + 5]),
                    px(v[stride * 2]), py(v[stride * 2 + 1]), pz(v[stride * 2 + 2]), f(v[stride * 2 + 4]), f(v[stride * 2 + 5]),
                    px(v[stride * 3]), py(v[stride * 3 + 1]), pz(v[stride * 3 + 2]), f(v[stride * 3 + 4]), f(v[stride * 3 + 5]),
                    n.getX(), n.getY(), n.getZ(), colour);
        }
    }

    private static float f(int bits) {
        return Float.intBitsToFloat(bits);
    }

    // block models run 0 to 1; our block parts sit centred on their origin, 16 pixels wide
    private static float px(int bits) {
        return (f(bits) - 0.5f) * 16f;
    }

    private static float py(int bits) {
        return (f(bits) - 0.5f) * 16f;
    }

    private static float pz(int bits) {
        return (f(bits) - 0.5f) * 16f;
    }

    // The block's texture, to use on a box.
    public static String textureOf(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY).contents().name().toString();
    }
}
