package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Part;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Adding a part: a plain box, any block, any item, or a body part from any mob, with live pictures to choose from.
final class AddPartPopup implements EditorScreen.Popup {
    private enum Tab {BOX, BLOCK, MOB, ITEM}

    record Icon(String id, String name, String search, ItemStack stack) {}

    private final EditorScreen s;
    private final Part changing;  // when set, this picks a new mob or bone for a part that's already there
    private Tab tab = Tab.MOB;
    private String search = "";
    private String mob = "";
    private static List<Icon> blocks, items;

    AddPartPopup(EditorScreen s) {
        this.s = s;
        this.changing = null;
        this.tab = Tab.BOX;
    }

    AddPartPopup(EditorScreen s, Part changing) {
        this.s = s;
        this.changing = changing;
        this.tab = Tab.MOB;
        this.mob = changing.mob;
    }

    static List<Icon> blocks() {
        if (blocks == null) {
            List<Icon> out = new ArrayList<>();
            for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
                Block b = e.getValue();
                if (b.defaultBlockState().getRenderShape() == RenderShape.INVISIBLE) continue;
                ItemStack stack = new ItemStack(b);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;
                String name = b.getName().getString();
                out.add(new Icon(e.getKey().location().toString(), name, (name + " " + e.getKey().location()).toLowerCase(Locale.ROOT), stack));
            }
            out.sort((a, c) -> a.name().compareToIgnoreCase(c.name()));
            blocks = out;
        }
        return blocks;
    }

    static List<Icon> items() {
        if (items == null) {
            List<Icon> out = new ArrayList<>();
            for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
                ItemStack stack = new ItemStack(e.getValue());
                if (stack.isEmpty()) continue;
                String name = stack.getHoverName().getString();
                out.add(new Icon(e.getKey().location().toString(), name, (name + " " + e.getKey().location()).toLowerCase(Locale.ROOT), stack));
            }
            out.sort((a, c) -> a.name().compareToIgnoreCase(c.name()));
            items = out;
        }
        return items;
    }

    @Override
    public void draw(EditorScreen sc) {
        Ui ui = s.ui;
        float w = Math.min(ui.width - 60f, 470f), h = tab == Tab.BOX ? 150f : Math.min(ui.height - 60f, 320f);
        float x0 = (ui.width - w) / 2f, y0 = (ui.height - h) / 2f, x1 = x0 + w, y1 = y0 + h;
        boolean clickedOutside = ui.pressed && !(ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1);
        ui.draw.rect(0f, 0f, ui.width, ui.height, 0f, 0x60100815);
        ui.panel(x0, y0, x1, y1, 12f);
        ui.flush();
        ui.claim(0f, 0f, ui.width, ui.height);
        ui.text(changing == null ? "Add a part" : "Choose from another mob", x0 + 12f, y0 + 9f, Ui.TEXT);
        if (changing == null) {
            Part on = s.state.design.part(parentForNew());
            ui.textRight(ui.fit(on == null ? "Goes on the mob" : "Goes on " + on.name, 110f), x1 - 32f, y0 + 9f, Ui.TEXT_DIM);
        }
        if (ui.button("popup_close", x1 - 26f, y0 + 6f, 20f, 14f, "X")) {
            s.popup = null;
            return;
        }
        if (changing == null) {
            String[] names = {"Box", "Block", "Mob part", "Item"};
            Tab[] tabs = Tab.values();
            float tw = 52f, tx = x0 + 80f;
            for (int i = 0; i < 4; i++) {
                if (ui.chip("add_tab_" + tabs[i].name().toLowerCase(), tx + i * (tw + 2f), y0 + 6f, tw, 14f, names[i], tab == tabs[i])) {
                    tab = tabs[i];
                    search = "";
                }
            }
        }
        float top = y0 + 26f;
        if (tab != Tab.BOX) {
            search = ui.textBox("add_search", x0 + 10f, top, w - 20f, 14f, search, "Search...");
            top += 18f;
        }
        switch (tab) {
            case BOX -> boxTab(x0, top, x1, y1);
            case BLOCK -> grid(blocks(), x0, top, x1, y1, this::addBlock);
            case ITEM -> grid(items(), x0, top, x1, y1, this::addItem);
            case MOB -> mobTab(x0, top, x1, y1);
        }
        if (clickedOutside) s.popup = null;
        ui.pressed = false;
        ui.wheel = 0f;
    }

    private void boxTab(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        float cx = (x0 + x1) / 2f;
        ui.textCentre("A plain box you can size, colour, texture or paint.", cx, y0 + 16f, Ui.TEXT_DIM);
        ui.textCentre("It's what a cat made of pink wool is built from.", cx, y0 + 28f, Ui.TEXT_DIM);
        float b = 90f;
        if (ui.accent("add_box", cx - b / 2f, y0 + 46f, b, 18f, "Add a box")) {
            Part p = new Part();
            p.kind = Part.Kind.CUBE;
            p.name = "Box";
            p.color = 0xFFFF8FC7;
            place(p);
            s.popup = null;
        }
        float jb = 120f;
        if (ui.button("add_joint", cx - jb / 2f, y0 + 72f, jb, 16f, "Add an empty joint")) {
            Part p = new Part();
            p.kind = Part.Kind.CUBE;
            p.name = "Joint";
            p.size = new float[3];
            place(p);
            s.popup = null;
        }
        ui.textCentre("A joint draws nothing, it only holds other parts so they can turn together.", cx, y0 + 96f, Ui.TEXT_DIM);
    }

    private interface Pick {
        void pick(Icon icon);
    }

    private void grid(List<Icon> all, float x0, float y0, float x1, float y1, Pick onPick) {
        Ui ui = s.ui;
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Icon> list = new ArrayList<>();
        for (Icon i : all) if (q.isEmpty() || i.search().contains(q)) list.add(i);
        float pad = 10f, cell = 26f;
        float left = x0 + pad, right = x1 - pad - 4f;
        int across = Math.max(1, (int) ((right - left) / cell));
        int rows = (list.size() + across - 1) / across;
        float bottom = y1 - 8f;
        ui.well(x0 + 6f, y0, x1 - 6f, bottom, 7f);
        boolean keep = ui.blocked;
        float scroll = ui.scroll("add_grid", x0 + 6f, y0, x1 - 6f, bottom, rows * cell + 8f);
        ui.pushClip(x0 + 6f, y0 + 1f, x1 - 6f, bottom - 1f);
        int first = Math.max(0, (int) (scroll / cell)) * across;
        int last = Math.min(list.size(), first + across * ((int) ((bottom - y0) / cell) + 2));
        for (int n = first; n < last; n++) {
            Icon icon = list.get(n);
            float x = left + (n % across) * cell, y = y0 + 4f + (n / across) * cell - scroll;
            boolean hover = ui.over(x, y, x + cell - 2f, y + cell - 2f);
            if (hover) {
                ui.draw.rect(x, y, x + cell - 2f, y + cell - 2f, 6f, 0x40FF8FC7);
                ui.tooltip(icon.name());
            }
            ui.item(icon.stack(), x + 3f, y + 3f, 18f);
            if (ui.click("icon_" + icon.id(), x, y, x + cell - 2f, y + cell - 2f)) {
                onPick.pick(icon);
                s.popup = null;
            }
        }
        ui.popClip();
        ui.blocked = keep;
    }

    private void addBlock(Icon icon) {
        Part p = new Part();
        p.kind = Part.Kind.BLOCK;
        p.name = icon.name();
        p.block = icon.id();
        place(p);
    }

    private void addItem(Icon icon) {
        Part p = new Part();
        p.kind = Part.Kind.ITEM;
        p.name = icon.name();
        p.item = icon.id();
        place(p);
    }

    // New parts hang from the selected one, or go beside the part you only just added.
    private void place(Part p) {
        p.parent = parentForNew();
        p.id = "new";
        s.addParts(new ArrayList<>(List.of(p)));
    }

    private String parentForNew() {
        EditorState st = s.state;
        Part sel = st.selectedPart();
        if (sel == null) return "";
        return sel.id.equals(st.lastAdded) ? sel.parent : sel.id;
    }

    private void mobTab(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        float listW = 150f, bottom = y1 - 8f;
        ui.well(x0 + 6f, y0, x0 + 6f + listW, bottom, 7f);
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<MobLibrary.Entry> list = new ArrayList<>();
        for (MobLibrary.Entry e : MobLibrary.all()) {
            if (q.isEmpty() || e.name().getString().toLowerCase(Locale.ROOT).contains(q) || e.id().toString().contains(q)) list.add(e);
        }
        float rowH = 17f;
        float scroll = ui.scroll("add_mobs", x0 + 6f, y0, x0 + 6f + listW, bottom, list.size() * rowH + 4f);
        ui.pushClip(x0 + 6f, y0 + 1f, x0 + 6f + listW, bottom - 1f);
        int first = Math.max(0, (int) (scroll / rowH));
        int last = Math.min(list.size(), first + (int) ((bottom - y0) / rowH) + 2);
        for (int n = first; n < last; n++) {
            MobLibrary.Entry e = list.get(n);
            float y = y0 + 2f + n * rowH - scroll;
            String id = e.id().toString();
            boolean sel = id.equals(mob);
            if (sel) ui.draw.grad(x0 + 8f, y, x0 + 3f + listW, y + rowH - 1f, 5f, 0xFF6A4478, 0xFF593A69);
            else if (ui.over(x0 + 8f, y, x0 + 3f + listW, y + rowH - 1f)) ui.draw.rect(x0 + 8f, y, x0 + 3f + listW, y + rowH - 1f, 5f, 0x30FFFFFF);
            ui.item(e.egg(), x0 + 10f, y + 1f, 14f);
            ui.text(ui.fit(e.name().getString(), listW - 28f), x0 + 27f, y + 4f, sel ? 0xFFFFFFFF : Ui.TEXT);
            if (ui.click("mob_" + id, x0 + 8f, y, x0 + 3f + listW, y + rowH - 1f)) mob = id;
        }
        ui.popClip();
        bones(x0 + 12f + listW, y0, x1 - 6f, bottom);
    }

    private void bones(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ui.well(x0, y0, x1, y1, 7f);
        MobLibrary.Model m = mob.isEmpty() ? null : MobLibrary.get(mob);
        if (m == null) {
            ui.textCentre("Pick a mob to see its parts", (x0 + x1) / 2f, y0 + 24f, Ui.TEXT_DIM);
            return;
        }
        float tile = 56f, gap = 5f, label = 20f;
        int across = Math.max(1, (int) ((x1 - x0 - 10f) / (tile + gap)));
        int count = m.all.size() + 1;
        int rows = (count + across - 1) / across;
        float headH = changing == null ? 22f : 4f;
        float scroll = ui.scroll("add_bones", x0, y0, x1, y1, rows * (tile + gap + label) + headH + 6f);
        ui.pushClip(x0, y0 + 1f, x1, y1 - 1f);
        float tx = x0 + 6f, ty = y0 + 4f - scroll;
        if (changing == null) {
            if (ui.accent("add_all_bones", tx, ty, x1 - x0 - 16f, 15f, m.all.isEmpty() ? "No separate parts to read" : "Add all its parts, separately") && !m.all.isEmpty()) {
                List<Part> parts = MobLibrary.asParts(m);
                s.addParts(parts);
                s.popup = null;
                ui.popClip();
                return;
            }
            ty += headH;
        }
        for (int i = 0; i < count; i++) {
            float x = tx + (i % across) * (tile + gap), y = ty + (i / across) * (tile + gap + label);
            if (y + tile + label < y0 || y > y1) continue;
            boolean whole = i == 0;
            MobLibrary.Bone b = whole ? null : m.all.get(i - 1);
            boolean hover = ui.over(x, y, x + tile, y + tile + label);
            ui.draw.rect(x, y, x + tile, y + tile, 7f, hover ? 0xFF5A3E6C : 0xFF2C1D36);
            ui.draw.border(x, y, x + tile, y + tile, 7f, 0.5f, hover ? Ui.PINK : 0x20FFFFFF);
            if (whole) {
                if (m.dummy != null) Tiles.wholeMob(ui, x + tile / 2f, y + tile / 2f, tile, m);
            } else {
                Tiles.mesh(ui, x + tile / 2f, y + tile / 2f, tile, b.whole, m.texture);
            }
            String name = whole ? "Whole mob" : b.name;
            List<String> lines = ui.wrap(name, tile + 3f);
            for (int n = 0; n < Math.min(2, lines.size()); n++) {
                ui.textCentre(ui.fit(lines.get(n), tile + 3f), x + tile / 2f, y + tile + 1f + n * 9f, hover ? 0xFFFFFFFF : Ui.TEXT_DIM);
            }
            if (hover && !whole) ui.tooltip(b.path);
            if (ui.click("bone_" + (whole ? "whole" : b.path), x, y, x + tile, y + tile + label)) {
                pickBone(m, b);
                s.popup = null;
            }
        }
        ui.popClip();
    }

    private void pickBone(MobLibrary.Model m, MobLibrary.Bone b) {
        if (changing != null) {
            changing.mob = m.id.toString();
            changing.bone = b == null ? "" : b.path;
            changing.mobTexture = "";
            s.state.changed();
            return;
        }
        Part p = new Part();
        p.kind = Part.Kind.MOB;
        p.mob = m.id.toString();
        p.bone = b == null ? "" : b.path;
        p.name = (MobLibrary.pretty(m.id.getPath()) + (b == null ? "" : " " + b.name)).trim();
        if (p.name.length() > 32) p.name = p.name.substring(0, 32);
        place(p);
    }
}
