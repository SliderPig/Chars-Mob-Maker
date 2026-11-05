package com.example.charmobmaker.client;

import com.example.charmobmaker.CharMobMaker;
import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.editor.EditorScreen;
import com.example.charmobmaker.client.editor.EditorState;
import com.example.charmobmaker.client.render.RenderCache;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Ids;
import com.example.charmobmaker.model.Packer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

// What arrives from the server. Designs come in pieces, get checked here too, and appear in mobs straight away.
public final class ClientNet {
    private static final Map<String, ByteArrayOutputStream> INCOMING = new HashMap<>();

    private ClientNet() {}

    public static void piece(Net.DesignPiece m) {
        if (!Ids.valid(m.id())) return;
        ByteArrayOutputStream out = m.index() == 0 ? new ByteArrayOutputStream() : INCOMING.get(m.id());
        if (out == null) return;
        out.writeBytes(m.data());
        if (m.index() + 1 < m.total()) {
            INCOMING.put(m.id(), out);
            return;
        }
        INCOMING.remove(m.id());
        String json = Packer.unpack(out.toByteArray());
        if (json == null) {
            CharMobMaker.LOG.warn("[MobMaker] Design {} arrived damaged", m.id());
            return;
        }
        try {
            Design d = DesignJson.read(json);
            DesignRules.tidy(d);
            d.id = m.id();
            Designs.CLIENT.put(d.id, d);
            RenderCache.forget(d.id);
            refresh(d.id);
            rebuildTab();
        } catch (DesignJson.BadDesign e) {
            CharMobMaker.LOG.warn("[MobMaker] Design {} couldn't be read: {}", m.id(), e.getMessage());
        }
    }

    public static void gone(String id) {
        Designs.CLIENT.remove(id);
        RenderCache.forget(id);
        refresh(id);
        rebuildTab();
    }

    public static void reset() {
        Designs.CLIENT.clear();
        RenderCache.clear();
        INCOMING.clear();
    }

    public static void open(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!id.isEmpty()) {
            Design d = Designs.CLIENT.get(id);
            if (d != null) EditorState.get().load(d.copy());
        }
        mc.setScreen(new EditorScreen());
    }

    public static void saved(Net.SaveReply m) {
        EditorState.get().onServerReply(m.ok(), m.id(), m.message());
    }

    public static void play(Net.PlayClip m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(m.entity());
        if (e instanceof CustomMob mob) {
            if (m.clip().equals("stop")) mob.anim.stop();
            else mob.playClip(m.clip(), false);
        }
    }

    private static void refresh(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CustomMob mob && id.equals(mob.designId())) mob.applyDesign();
        }
    }

    // New eggs show up in the creative tab without having to rejoin.
    private static void rebuildTab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            CreativeModeTab tab = ModContent.TAB.get();
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(), mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get(), mc.level.registryAccess()));
        } catch (RuntimeException e) {
            CharMobMaker.LOG.debug("[MobMaker] Couldn't update the creative tab", e);
        }
    }
}
