package com.example.charmobmaker;

import com.example.charmobmaker.item.CustomBlockItem;
import com.example.charmobmaker.item.CustomItem;
import com.example.charmobmaker.item.MobEggItem;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.ItemRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

// Turns designs and item refs into real item stacks, and back. Every made item and block is one real item with the design's id written in it.
public final class DesignStacks {
    public static final String ID = "Design", NAME = "DesignName";

    private DesignStacks() {}

    public static ItemStack of(Design d) {
        return switch (d.type) {
            case MOB -> MobEggItem.of(d);
            case BLOCK -> tagged(new ItemStack(ModContent.CUSTOM_BLOCK_ITEM.get()), d);
            case ITEM -> tagged(new ItemStack(ModContent.CUSTOM_ITEM.get()), d);
            case SCREEN -> ItemStack.EMPTY;
        };
    }

    private static ItemStack tagged(ItemStack stack, Design d) {
        stack.getOrCreateTag().putString(ID, d.id);
        stack.getOrCreateTag().putString(NAME, d.name);
        return stack;
    }

    public static String idOf(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(ID) : "";
    }

    // Empty when it can't be made: an unknown item or a design that's gone.
    public static ItemStack ofRef(String ref, int count, Map<String, Design> designs) {
        ItemStack stack = ItemStack.EMPTY;
        if (ItemRef.isDesign(ref)) {
            Design d = designs.get(ItemRef.designId(ref));
            if (d != null) stack = of(d);
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(ref);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != Items.AIR) stack = new ItemStack(item);
            }
        }
        if (!stack.isEmpty()) stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), count)));
        return stack;
    }

    public static String refOf(ItemStack stack) {
        if (stack.isEmpty()) return "";
        if (stack.getItem() instanceof CustomItem || stack.getItem() instanceof CustomBlockItem || stack.getItem() instanceof MobEggItem) return ItemRef.ofDesign(idOf(stack));
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl == null ? "" : rl.toString();
    }
}
