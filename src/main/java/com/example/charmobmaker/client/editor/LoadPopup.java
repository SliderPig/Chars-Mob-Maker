package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.render.Compiled;
import com.example.charmobmaker.client.render.RenderCache;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// All the mobs: the ones saved in this world with little live pictures, the files on this PC, and starting a new one.
final class LoadPopup implements EditorScreen.Popup {
    private final EditorScreen s;
    private String armed = "";
    private List<Path> files;

    LoadPopup(EditorScreen s) {
        this.s = s;
    }

    @Override
    public void draw(EditorScreen sc) {
        Ui ui = s.ui;
        EditorState st = s.state;
        float w = Math.min(ui.width - 50f, 480f), h = Math.min(ui.height - 40f, 330f);
        float x0 = (ui.width - w) / 2f, y0 = (ui.height - h) / 2f, x1 = x0 + w, y1 = y0 + h;
        boolean outside = ui.pressed && !(ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1);
        ui.draw.rect(0f, 0f, ui.width, ui.height, 0f, 0x60100815);
        ui.panel(x0, y0, x1, y1, 12f);
        ui.flush();
        ui.claim(0f, 0f, ui.width, ui.height);
        ui.text("Mobs", x0 + 12f, y0 + 9f, Ui.TEXT);
        if (ui.button("popup_close", x1 - 26f, y0 + 6f, 20f, 14f, "X")) {
            s.popup = null;
            return;
        }

        // starting something and sharing
        float bx = x0 + 44f, bh = 14f, by = y0 + 6f;
        float bw = textButton(bx, "New");
        if (ui.accent("new_mob", bx, by, bw, bh, "New")) {
            st.commit();
            st.load(new Design());
            s.viewport.aimAt(st.compiled());
            s.popup = null;
            return;
        }
        bx += bw + 2f;
        bw = textButton(bx, "Save a copy");
        if (ui.button("copy_mob", bx, by, bw, bh, "Save a copy")) {
            Design copy = st.design.copy();
            copy.id = "";
            copy.owner = "";
            copy.name = DesignRules.cleanName(copy.name + " copy", 32, "Copy");
            st.load(copy);
            s.popup = null;
            return;
        }
        bx += bw + 2f;
        bw = textButton(bx, "To a file");
        if (ui.button("export_mob", bx, by, bw, bh, "To a file")) {
            try {
                Path p = DesignFiles.export(st.design);
                st.say("Saved as " + p.getFileName(), false);
                files = null;
            } catch (IOException e) {
                st.say("Couldn't write the file", true);
            }
        }
        bx += bw + 2f;
        bw = textButton(bx, "Copy text");
        if (ui.button("copy_text", bx, by, bw, bh, "Copy text")) s.copyToClipboard();
        if (ui.over(bx, by, bx + bw, by + bh)) ui.tooltip("Copies the mob as text, to send to someone");
        bx += bw + 2f;
        bw = textButton(bx, "Paste text");
        if (ui.button("paste_text", bx, by, bw, bh, "Paste text")) {
            try {
                Design d = DesignJson.read(Minecraft.getInstance().keyboardHandler.getClipboard());
                DesignRules.tidy(d);
                d.id = "";
                d.owner = "";
                st.load(d);
                s.viewport.aimAt(st.compiled());
                s.popup = null;
                st.say("Pasted " + d.name + ". Save it to keep it.", false);
                return;
            } catch (DesignJson.BadDesign e) {
                st.say("That text isn't a mob", true);
            }
        }

        float colW = (w - 22f) * 0.62f;
        float top = y0 + 26f, bottom = y1 - 8f;
        worldList(x0 + 8f, top, x0 + 8f + colW, bottom);
        fileList(x0 + 14f + colW, top, x1 - 8f, bottom);
        if (outside) s.popup = null;
        ui.pressed = false;
        ui.wheel = 0f;
    }

    private float textButton(float x, String label) {
        return s.ui.width(label) + 12f;
    }

    private void worldList(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        EditorState st = s.state;
        ui.text("In this world", x0 + 3f, y0 + 1f, Ui.PINK_SOFT);
        float top = y0 + 12f;
        ui.well(x0, top, x1, y1, 7f);
        List<Design> list = new ArrayList<>(Designs.CLIENT.values());
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        float rowH = 34f;
        float scroll = ui.scroll("load_world", x0, top, x1, y1, list.size() * rowH + 4f);
        ui.pushClip(x0, top + 1f, x1, y1 - 1f);
        for (int i = 0; i < list.size(); i++) {
            Design d = list.get(i);
            float y = top + 2f + i * rowH - scroll;
            if (y + rowH < top || y > y1) continue;
            boolean current = d.id.equals(st.design.id);
            boolean hover = ui.over(x0 + 2f, y, x1 - 4f, y + rowH - 2f);
            if (current) ui.draw.grad(x0 + 2f, y, x1 - 4f, y + rowH - 2f, 6f, 0xFF6A4478, 0xFF593A69);
            else if (hover) ui.draw.rect(x0 + 2f, y, x1 - 4f, y + rowH - 2f, 6f, 0x30FFFFFF);
            Compiled c = RenderCache.of(d.id);
            Tiles.design(ui, x0 + 19f, y + rowH / 2f - 1f, 30f, c);
            ui.text(ui.fit(d.name, x1 - x0 - 90f), x0 + 38f, y + 6f, Ui.TEXT);
            ui.text(d.parts.size() + " parts, " + d.clips.size() + " clips", x0 + 38f, y + 17f, Ui.TEXT_DIM);
            boolean sure = armed.equals(d.id);
            if (ui.chip("del_" + d.id, x1 - 46f, y + 9f, 40f, 13f, sure ? "Sure?" : "Delete", sure)) {
                if (sure) {
                    Net.action(Net.Act.DELETE, d.id, 0);
                    armed = "";
                } else {
                    armed = d.id;
                }
            }
            if (ui.click("load_" + d.id, x0 + 2f, y, x1 - 4f, y + rowH - 2f)) {
                st.commit();
                st.load(d.copy());
                s.viewport.aimAt(st.compiled());
                s.popup = null;
            }
        }
        if (list.isEmpty()) ui.textCentre("Nothing saved yet", (x0 + x1) / 2f, top + 16f, Ui.TEXT_DIM);
        ui.popClip();
    }

    private void fileList(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        EditorState st = s.state;
        ui.text("Files on this PC", x0 + 3f, y0 + 1f, Ui.PINK_SOFT);
        if (files == null) files = DesignFiles.list();
        float top = y0 + 12f, bottom = y1 - 18f;
        ui.well(x0, top, x1, bottom, 7f);
        float rowH = 15f;
        float scroll = ui.scroll("load_files", x0, top, x1, bottom, files.size() * rowH + 4f);
        ui.pushClip(x0, top + 1f, x1, bottom - 1f);
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            float y = top + 2f + i * rowH - scroll;
            if (y + rowH < top || y > bottom) continue;
            String name = f.getFileName().toString();
            if (ui.over(x0 + 2f, y, x1 - 4f, y + rowH - 1f)) ui.draw.rect(x0 + 2f, y, x1 - 4f, y + rowH - 1f, 5f, 0x30FFFFFF);
            ui.text(ui.fit(name, x1 - x0 - 12f), x0 + 6f, y + 3f, Ui.TEXT);
            if (ui.click("file_" + name, x0 + 2f, y, x1 - 4f, y + rowH - 1f)) {
                try {
                    Design d = DesignFiles.read(f);
                    st.commit();
                    st.load(d);
                    s.viewport.aimAt(st.compiled());
                    s.popup = null;
                    st.say("Opened " + d.name + ". Save it to keep it in this world.", false);
                } catch (IOException | DesignJson.BadDesign e) {
                    st.say("Couldn't read " + name + ": " + e.getMessage(), true);
                }
            }
        }
        if (files.isEmpty()) {
            for (int i = 0; i < 3; i++) ui.textCentre(new String[] {"Empty for now.", "Mob files and", ".bbmodel go here"}[i], (x0 + x1) / 2f, top + 14f + i * 11f, Ui.TEXT_DIM);
        }
        ui.popClip();
        if (ui.button("open_folder", x0, y1 - 14f, x1 - x0, 14f, "Open the folder")) DesignFiles.openFolder();
    }
}
