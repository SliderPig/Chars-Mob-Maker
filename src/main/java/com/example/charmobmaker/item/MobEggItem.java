package com.example.charmobmaker.item;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

// One egg item for every made mob: which mob it hatches is written in the item itself.
public final class MobEggItem extends Item {
    private static final String ID = "Design", NAME = "DesignName";

    public MobEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Design d) {
        ItemStack stack = new ItemStack(ModContent.EGG.get());
        stack.getOrCreateTag().putString(ID, d.id);
        stack.getOrCreateTag().putString(NAME, d.name);
        return stack;
    }

    public static String idOf(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(ID) : "";
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = stack.hasTag() ? stack.getTag().getString(NAME) : "";
        return name.isEmpty() ? super.getName(stack) : Component.literal(name + " Egg");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        String id = idOf(stack);
        if (id.isEmpty()) lines.add(Component.literal("Doesn't hatch anything yet").withStyle(ChatFormatting.GRAY));
        else if (level != null && Designs.of(level, id) == null) lines.add(Component.literal("That mob design is gone").withStyle(ChatFormatting.RED));
        else lines.add(Component.literal("Made in the Mob Maker").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level level = c.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        ItemStack stack = c.getItemInHand();
        Player player = c.getPlayer();
        String id = idOf(stack);
        if (Designs.of(level, id) == null) {
            if (player != null) player.displayClientMessage(Component.literal("That mob design is gone"), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = c.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(c.getClickedFace());
        CustomMob mob = CustomMob.spawnAt(server, at, id);
        if (mob != null) {
            mob.setPersistenceRequired();
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        }
        return InteractionResult.CONSUME;
    }
}
