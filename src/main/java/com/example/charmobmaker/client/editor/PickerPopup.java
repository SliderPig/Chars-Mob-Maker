package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.render.BlockMeshes;
import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// A list with a search box, used for everything you pick by name: sounds, items, biomes, textures.
final class PickerPopup implements EditorScreen.Popup {
    private record Row(String id, String label, ItemStack icon) {}

    private final EditorScreen s;
    private final String title;
    private final List<Row> rows;
    private final Consumer<String> onPick;
    private final boolean sounds;
    private String search = "";

    private PickerPopup(EditorScreen s, String title, List<Row> rows, Consumer<String> onPick, boolean sounds) {
        this.s = s;
        this.title = title;
        this.rows = rows;
        this.onPick = onPick;
        this.sounds = sounds;
    }

    static PickerPopup blocks(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        for (var i : AddPartPopup.blocks()) rows.add(new Row(i.id(), i.name(), i.stack()));
        return new PickerPopup(s, "Choose a block", rows, pick, false);
    }

    static PickerPopup items(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        for (var i : AddPartPopup.items()) rows.add(new Row(i.id(), i.name(), i.stack()));
        return new PickerPopup(s, "Choose an item", rows, pick, false);
    }

    static PickerPopup blockTextures(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var i : AddPartPopup.blocks()) {
            ResourceLocation rl = ResourceLocation.tryParse(i.id());
            Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
            if (b == null) continue;
            String tex = BlockMeshes.textureOf(b.defaultBlockState());
            if (seen.add(tex)) rows.add(new Row(tex, i.name(), i.stack()));
        }
        return new PickerPopup(s, "Choose a block's texture", rows, pick, false);
    }

    static PickerPopup mobs(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        for (MobLibrary.Entry e : MobLibrary.all()) rows.add(new Row(e.id().toString(), e.name().getString(), e.egg()));
        return new PickerPopup(s, "Choose a mob", rows, pick, false);
    }

    // For helpers: made mobs as well as real ones.
    static PickerPopup helpers(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        List<com.example.charmobmaker.model.Design> made = new ArrayList<>(com.example.charmobmaker.Designs.CLIENT.values());
        made.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        for (var d : made) rows.add(new Row("charmobmaker:" + d.id, d.name + " (made here)", ItemStack.EMPTY));
        for (MobLibrary.Entry e : MobLibrary.all()) rows.add(new Row(e.id().toString(), e.name().getString(), e.egg()));
        return new PickerPopup(s, "Choose a helper", rows, pick, false);
    }

    static PickerPopup sounds(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.sounds.SoundEvent>, net.minecraft.sounds.SoundEvent> e : ForgeRegistries.SOUND_EVENTS.getEntries()) {
            String id = e.getKey().location().toString();
            rows.add(new Row(id, id.replace("minecraft:", ""), ItemStack.EMPTY));
        }
        rows.sort((a, b) -> a.label().compareTo(b.label()));
        return new PickerPopup(s, "Choose a sound (click the arrow to hear it)", rows, pick, true);
    }

    static PickerPopup biomes(EditorScreen s, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (ResourceLocation rl : level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) {
                rows.add(new Row(rl.toString(), rl.toString().replace("minecraft:", "").replace('_', ' '), ItemStack.EMPTY));
            }
        }
        rows.sort((a, b) -> a.label().compareTo(b.label()));
        return new PickerPopup(s, "Choose a biome", rows, pick, false);
    }

    static PickerPopup skins(EditorScreen s, ResourceLocation mobId, Consumer<String> pick) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("", "Its normal skin", ItemStack.EMPTY));
        var found = Minecraft.getInstance().getResourceManager().listResources("textures/entity/" + mobId.getPath(), rl -> rl.getPath().endsWith(".png"));
        for (ResourceLocation rl : found.keySet()) rows.add(new Row(rl.toString(), rl.getPath().replace("textures/entity/", ""), ItemStack.EMPTY));
        return new PickerPopup(s, "Choose a skin", rows, pick, false);
    }

    @Override
    public void draw(EditorScreen sc) {
        Ui ui = s.ui;
        float w = Math.min(ui.width - 60f, 300f), h = Math.min(ui.height - 40f, 320f);
        float x0 = (ui.width - w) / 2f, y0 = (ui.height - h) / 2f, x1 = x0 + w, y1 = y0 + h;
        boolean outside = ui.pressed && !(ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1);
        ui.draw.rect(0f, 0f, ui.width, ui.height, 0f, 0x60100815);
        ui.panel(x0, y0, x1, y1, 12f);
        ui.flush();
        ui.claim(0f, 0f, ui.width, ui.height);
        ui.text(ui.fit(title, w - 40f), x0 + 12f, y0 + 9f, Ui.TEXT);
        if (ui.button("popup_close", x1 - 26f, y0 + 6f, 20f, 14f, "X")) {
            s.popup = null;
            return;
        }
        search = ui.textBox("pick_search", x0 + 8f, y0 + 24f, w - 16f, 14f, search, "Search...");
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Row> list = new ArrayList<>();
        for (Row r : rows) if (q.isEmpty() || r.label().toLowerCase(Locale.ROOT).contains(q) || r.id().contains(q)) list.add(r);
        float top = y0 + 42f, bottom = y1 - 8f, rowH = 16f;
        ui.well(x0 + 6f, top, x1 - 6f, bottom, 7f);
        float scroll = ui.scroll("pick_list", x0 + 6f, top, x1 - 6f, bottom, list.size() * rowH + 4f);
        ui.pushClip(x0 + 6f, top + 1f, x1 - 6f, bottom - 1f);
        int first = Math.max(0, (int) (scroll / rowH));
        int last = Math.min(list.size(), first + (int) ((bottom - top) / rowH) + 2);
        for (int n = first; n < last; n++) {
            Row r = list.get(n);
            float y = top + 2f + n * rowH - scroll;
            boolean hover = ui.over(x0 + 8f, y, x1 - 10f, y + rowH - 1f);
            if (hover) ui.draw.rect(x0 + 8f, y, x1 - 10f, y + rowH - 1f, 5f, 0x40FF8FC7);
            float tx = x0 + 12f;
            if (!r.icon().isEmpty()) {
                ui.item(r.icon(), tx - 1f, y + 0.5f, 14f);
                tx += 16f;
            }
            ui.text(ui.fit(r.label(), x1 - tx - (sounds ? 34f : 16f)), tx, y + 3.5f, hover ? 0xFFFFFFFF : Ui.TEXT);
            if (sounds && ui.chip("hear_" + n, x1 - 28f, y + 1f, 16f, rowH - 3f, ">", false)) StatsPanel.playSound(r.id());
            if (ui.click("pick_" + r.id(), x0 + 8f, y, x1 - 10f, y + rowH - 1f)) {
                onPick.accept(r.id());
                s.popup = null;
            }
        }
        if (list.isEmpty()) ui.textCentre("Nothing by that name", (x0 + x1) / 2f, top + 12f, Ui.TEXT_DIM);
        ui.popClip();
        if (outside) s.popup = null;
        ui.pressed = false;
        ui.wheel = 0f;
    }
}
