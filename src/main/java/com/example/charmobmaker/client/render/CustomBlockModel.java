package com.example.charmobmaker.client.render;

import com.example.charmobmaker.block.CustomBlock;
import com.example.charmobmaker.block.CustomBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

// What the chunk builder sees for a made block: the design's baked quads, picked by the design id the block entity hands it.
public final class CustomBlockModel implements IDynamicBakedModel {
    private static final ChunkRenderTypeSet TYPES = ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent());
    private final BakedModel base;

    public CustomBlockModel(BakedModel base) {
        this.base = base;
    }

    private static BlockBakes.Baked bakedOf(BlockState state, ModelData data) {
        String id = data.get(CustomBlockEntity.DESIGN);
        if (id == null || id.isEmpty()) return null;
        int quarter = state == null ? 0 : state.getValue(CustomBlock.FACING).get2DDataValue();
        return BlockBakes.get(id, quarter);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
        if (side != null) return List.of();
        BlockBakes.Baked b = bakedOf(state, data);
        if (b == null) return List.of();
        if (type == RenderType.cutout()) return b.solid();
        if (type == RenderType.translucent()) return b.glass();
        if (type != null) return List.of();
        List<BakedQuad> all = new ArrayList<>(b.solid());
        all.addAll(b.glass());
        return all;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return TYPES;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return base.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockBakes.Baked b = bakedOf(null, data);
        return b == null ? base.getParticleIcon() : b.particle();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
