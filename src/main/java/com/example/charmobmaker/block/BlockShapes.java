package com.example.charmobmaker.block;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.model.BlockBoxes;
import com.example.charmobmaker.model.BlockInfo;
import com.example.charmobmaker.model.Design;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The hit and walk-into shapes of made blocks, worked out once per design and turned for each way it can face.
public final class BlockShapes {
    private record Pair(VoxelShape hit, VoxelShape solid) {}

    private static final VoxelShape SMALL = Block.box(4, 0, 4, 12, 8, 12);
    private static final Pair DEFAULT = new Pair(Shapes.block(), Shapes.block());
    private static final Map<String, Pair[]> SERVER = new ConcurrentHashMap<>(), CLIENT = new ConcurrentHashMap<>();

    private BlockShapes() {}

    public static void forget(String id) {
        SERVER.remove(id);
        CLIENT.remove(id);
    }

    public static void clear() {
        SERVER.clear();
        CLIENT.clear();
    }

    public static VoxelShape hit(BlockGetter level, BlockPos pos, BlockState state) {
        return shapes(level, pos, state).hit();
    }

    public static VoxelShape solid(BlockGetter level, BlockPos pos, BlockState state) {
        return shapes(level, pos, state).solid();
    }

    private static Pair shapes(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof CustomBlockEntity be) || be.designId().isEmpty()) return DEFAULT;
        boolean client = !(level instanceof Level l) || l.isClientSide;
        String id = be.designId();
        Design d = (client ? Designs.CLIENT : Designs.SERVER).get(id);
        if (d == null) return DEFAULT;
        Map<String, Pair[]> cache = client ? CLIENT : SERVER;
        Pair[] turned = cache.computeIfAbsent(id, k -> new Pair[4]);
        int quarter = state.getValue(CustomBlock.FACING).get2DDataValue();
        Pair p = turned[quarter];
        if (p == null) {
            p = make(d, quarter);
            turned[quarter] = p;
        }
        return p;
    }

    private static Pair make(Design d, int quarter) {
        if (d.block.shape == BlockInfo.Shape.FULL) return DEFAULT;
        List<float[]> boxes = BlockBoxes.of(d);
        VoxelShape parts = Shapes.empty();
        for (float[] b : boxes) {
            float[] t = BlockBoxes.turned(b, quarter);
            parts = Shapes.joinUnoptimized(parts, Block.box(t[0], t[1], t[2], t[3], t[4], t[5]), net.minecraft.world.phys.shapes.BooleanOp.OR);
        }
        parts = parts.optimize();
        if (parts.isEmpty()) parts = SMALL;
        return new Pair(parts, d.block.shape == BlockInfo.Shape.NONE ? Shapes.empty() : parts);
    }
}
