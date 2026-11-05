package com.example.charmobmaker.client;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.editor.EditorState;
import com.example.charmobmaker.client.render.BlockMeshes;
import com.example.charmobmaker.client.render.CustomMobRenderer;
import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.render.RenderCache;
import com.example.charmobmaker.item.MobEggItem;
import com.example.charmobmaker.model.Design;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private static final KeyMapping OPEN = new KeyMapping("key.charmobmaker.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, "key.categories.charmobmaker");

    private ClientSetup() {}

    public static void init(IEventBus bus) {
        bus.addListener(ClientSetup::renderers);
        bus.addListener(ClientSetup::keys);
        bus.addListener(ClientSetup::colours);
        bus.addListener(ClientSetup::reload);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::loggedOut);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModContent.CUSTOM.get(), CustomMobRenderer::new);
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        e.register(OPEN);
    }

    private static void colours(RegisterColorHandlersEvent.Item e) {
        e.register((stack, layer) -> {
            Design d = Designs.CLIENT.get(MobEggItem.idOf(stack));
            if (d == null) return layer == 0 ? 0xFFFF8FC7 : 0xFF2A1B33;
            return layer == 0 ? d.stats.eggBase : d.stats.eggSpots;
        }, ModContent.EGG.get());
    }

    private static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) manager -> {
            RenderCache.clear();
            BlockMeshes.clear();
            MobLibrary.clear();
            EditorState.get().rebuildNow();
        });
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            while (OPEN.consumeClick()) Net.action(Net.Act.OPEN, "", 0);
        }
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientNet.reset();
        MobLibrary.worldChanged();
        EditorState.get().rebuildNow();
    }
}
