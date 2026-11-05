package com.example.charmobmaker.block;

import com.example.charmobmaker.Access;
import com.example.charmobmaker.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Mob Maker's Bench: a little workshop table with a black cat on it. Right click opens the editor.
public final class BenchBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 11, 0, 16, 14, 16),
            Block.box(1, 0, 1, 4, 11, 4),
            Block.box(12, 0, 1, 15, 11, 4),
            Block.box(1, 0, 12, 4, 11, 15),
            Block.box(12, 0, 12, 15, 11, 15));

    public BenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer p) {
            if (Access.canEdit(p)) Net.sendOpen(p, "");
            else p.displayClientMessage(Component.literal(Access.refusal()), true);
        }
        return InteractionResult.CONSUME;
    }
}
