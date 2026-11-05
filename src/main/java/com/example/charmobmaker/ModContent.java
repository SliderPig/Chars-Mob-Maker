package com.example.charmobmaker;

import com.example.charmobmaker.block.BenchBlock;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.item.MobEggItem;
import com.example.charmobmaker.model.Design;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The only things registered: one entity type, one egg, one bench. Every mob made in the game is data on top of these.
public final class ModContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CharMobMaker.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CharMobMaker.MODID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CharMobMaker.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CharMobMaker.MODID);

    // MISC so made mobs don't use up the normal mob caps; natural spawns go through NaturalSpawns instead
    public static final RegistryObject<EntityType<CustomMob>> CUSTOM = ENTITIES.register("custom", () -> EntityType.Builder.<CustomMob>of(CustomMob::new, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(10)
            .build("custom"));

    public static final RegistryObject<Block> BENCH = BLOCKS.register("mob_makers_bench", () -> new BenchBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PINK)
            .strength(2.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()));

    public static final RegistryObject<Item> BENCH_ITEM = ITEMS.register("mob_makers_bench", () -> new BlockItem(BENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> EGG = ITEMS.register("mob_egg", () -> new MobEggItem(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.charmobmaker"))
            .icon(() -> new ItemStack(BENCH_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(BENCH_ITEM.get());
                List<Design> list = new ArrayList<>(Designs.CLIENT.values());
                list.sort(Comparator.comparing(d -> d.name));
                for (Design d : list) out.accept(MobEggItem.of(d));
            })
            .build());

    private ModContent() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
