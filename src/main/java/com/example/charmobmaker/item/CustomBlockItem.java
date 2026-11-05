package com.example.charmobmaker.item;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.block.CustomBlockEntity;
import com.example.charmobmaker.client.render.CustomItemRenderer;
import com.example.charmobmaker.model.Design;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

// The item form of a made block, truly 3D in the hand and in slots.
public final class CustomBlockItem extends BlockItem {
    public CustomBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack stackFor(CustomBlockEntity be) {
        Design d = be.designNow();
        return d == null ? ItemStack.EMPTY : DesignStacks.of(d);
    }

    private static Design design(ItemStack stack, Level level) {
        String id = DesignStacks.idOf(stack);
        return level != null ? Designs.of(level, id) : Designs.CLIENT.getOrDefault(id, Designs.SERVER.get(id));
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = stack.hasTag() ? stack.getTag().getString(DesignStacks.NAME) : "";
        return name.isEmpty() ? super.getName(stack) : Component.literal(name);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        Design d = design(stack, null);
        return d == null ? 64 : d.block.stack;
    }

    @Override
    public InteractionResult place(BlockPlaceContext c) {
        if (Designs.of(c.getLevel(), DesignStacks.idOf(c.getItemInHand())) == null) {
            if (c.getPlayer() != null && c.getLevel().isClientSide) c.getPlayer().displayClientMessage(Component.literal("That block design is gone"), true);
            return InteractionResult.FAIL;
        }
        return super.place(c);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CustomBlockEntity be) be.setDesign(DesignStacks.idOf(stack));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        Design d = design(stack, level);
        if (d == null) {
            lines.add(Component.literal("That block design is gone").withStyle(ChatFormatting.RED));
            return;
        }
        if (!d.block.screen.isEmpty()) lines.add(Component.literal("A machine: right click to use it").withStyle(ChatFormatting.LIGHT_PURPLE));
        else lines.add(Component.literal("Made in the Mob Maker").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CustomItemRenderer.get();
            }
        });
    }
}
