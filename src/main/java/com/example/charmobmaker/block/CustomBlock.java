package com.example.charmobmaker.block;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.item.CustomBlockItem;
import com.example.charmobmaker.model.BlockInfo;
import com.example.charmobmaker.model.Design;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

// The one real block behind every made block. What it looks like and how it acts comes from the design its block entity names.
public final class CustomBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    public static final BooleanProperty MACHINE = BooleanProperty.create("machine");

    public CustomBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PINK)
                .strength(1.5f, 6f)
                .noOcclusion()
                .dynamicShape()
                .lightLevel(s -> s.getValue(LIGHT))
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIGHT, 0).setValue(MACHINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LIGHT, MACHINE);
    }

    private static Design designAt(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CustomBlockEntity be)) return null;
        return level instanceof Level l ? Designs.of(l, be.designId()) : Designs.CLIENT.get(be.designId());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        Design d = Designs.of(c.getLevel(), DesignStacks.idOf(c.getItemInHand()));
        return d == null ? s : stateFor(s, d);
    }

    public static BlockState stateFor(BlockState s, Design d) {
        return s.setValue(LIGHT, d.block.light).setValue(MACHINE, !d.block.screen.isEmpty());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BlockShapes.hit(level, pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BlockShapes.solid(level, pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1f;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomBlockEntity(pos, state);
    }

    // Only machines tick, so a house full of decoration costs nothing here.
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(MACHINE)) return null;
        return createTickerHelper(type, ModContent.CUSTOM_BE.get(), CustomBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CustomBlockEntity be) || be.screenDesign() == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer p) NetworkHooks.openScreen(p, be, be::writeOpen);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CustomBlockEntity be) {
                var items = be.items();
                for (int i = 0; i < items.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i));
            }
            super.onRemove(state, level, pos, next, moved);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof CustomBlockEntity c) {
            ItemStack stack = CustomBlockItem.stackFor(c);
            if (!stack.isEmpty()) return List.of(stack);
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof CustomBlockEntity c ? CustomBlockItem.stackFor(c) : ItemStack.EMPTY;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        Design d = designAt(level, pos);
        float hardness = d == null ? 1.5f : d.block.hardness;
        if (hardness <= 0f) return 1f;
        return player.getDigSpeed(state, pos) / hardness / 30f;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        Design d = designAt(level, pos);
        return d == null ? 6f : d.block.blast;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        Design d = designAt(level, pos);
        return d == null ? SoundType.STONE : soundOf(d.block);
    }

    private static SoundType soundOf(BlockInfo b) {
        return switch (b.sound) {
            case "wood" -> SoundType.WOOD;
            case "wool" -> SoundType.WOOL;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "grass" -> SoundType.GRASS;
            case "sand" -> SoundType.SAND;
            default -> SoundType.STONE;
        };
    }
}
