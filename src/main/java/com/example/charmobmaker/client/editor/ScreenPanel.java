package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.ScreenPainter;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.ScreenInfo;
import com.example.charmobmaker.model.ScreenInfo.Widget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// Making a screen: drag things around on it, what you see is what the real game shows. The second tab is the steps its machine can do.
public final class ScreenPanel {
    private static final int NONE = -1, INVENTORY = -2;
    private final EditorScreen s;
    private int selected = NONE;
    private boolean moving, resizing;
    private float grabX, grabY;
    private int startX, startY, startW, startH;
    private float y, left, width, contentH = 200f, recipesH = 200f;
    private float zoom = 2f, originX, originY;

    ScreenPanel(EditorScreen s) {
        this.s = s;
    }

    // Where a spot on the screen being made shows in the editor.
    public float[] canvasSpot(int x, int y) {
        return new float[] {originX + x * zoom, originY + y * zoom};
    }

    private ScreenInfo screen() {
        return s.state.design.screen;
    }

    private Widget widget() {
        List<Widget> list = screen().widgets;
        return selected >= 0 && selected < list.size() ? list.get(selected) : null;
    }

    private void section(String title) {
        Ui ui = s.ui;
        y += 2f;
        ui.text(title, left, y, Ui.PINK_SOFT);
        float tw = ui.width(title) + 5f;
        ui.draw.rect(left + tw, y + 3.5f, left + width, y + 4.25f, 0.4f, 0x28FFFFFF);
        y += 12f;
    }

    // ---------- the Layout tab ----------

    void drawLayout(float leftX0, float top, float leftX1, float bottom, float rightX0, float rightX1) {
        canvas(leftX1 + 6f, top, rightX0 - 6f, bottom);
        list(leftX0, top, leftX1, bottom);
        props(rightX0, top, rightX1, bottom);
    }

    private void canvas(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ScreenInfo sc = screen();
        float roomW = x1 - x0 - 12f, roomH = y1 - y0 - 12f;
        zoom = Math.max(1f, Math.min(3f, (float) Math.floor(Math.min(roomW / sc.width, roomH / sc.height))));
        originX = Math.round((x0 + x1 - sc.width * zoom) / 2f);
        originY = Math.round((y0 + y1 - sc.height * zoom) / 2f);
        ui.flush();
        float gmx = (ui.mx - originX) / zoom, gmy = (ui.my - originY) / zoom;
        PoseStack pose = ui.g.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0f);
        pose.scale(zoom, zoom, 1f);
        ScreenPainter.paint(ui.g, ui.draw, ui.font, s.state.design, ScreenPainter.PREVIEW, ui.overPanels() ? -999f : gmx, ui.overPanels() ? -999f : gmy, 1f / (ui.scale * zoom));
        ui.g.flush();
        pose.popPose();
        ui.draw.begin(ui.g, 1f / ui.scale);
        ui.flush();
        // the marks over the selected thing
        Draw d = ui.draw;
        if (selected == INVENTORY) {
            float ax = originX + sc.invX * zoom, ay = originY + sc.invY * zoom;
            d.border(ax - 1f, ay - 1f, ax + 162f * zoom + 1f, ay + 76f * zoom + 1f, 3f, 1f, Ui.PINK);
        }
        Widget w = widget();
        if (w != null) {
            float ax = originX + w.x * zoom, ay = originY + w.y * zoom, bx = ax + w.w * zoom, by = ay + w.h * zoom;
            d.border(ax - 1f, ay - 1f, bx + 1f, by + 1f, 3f, 1f, Ui.PINK);
            if (w.kind != Widget.Kind.SLOT) {
                d.circle(bx, by, 3.5f, 0xFFFFFFFF);
                d.ring(bx, by, 3.5f, 0.75f, Ui.PINK);
            }
        }
        input(x0, y0, x1, y1);
    }

    private void input(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ScreenInfo sc = screen();
        float gx = (ui.mx - originX) / zoom, gy = (ui.my - originY) / zoom;
        if (ui.pressed && !ui.blocked && ui.active.isEmpty() && ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1 && !ui.overPanels()) {
            ui.pressed = false;
            ui.active = "canvas";
            moving = false;
            resizing = false;
            Widget w = widget();
            if (w != null && w.kind != Widget.Kind.SLOT && Math.abs(ui.mx - (originX + (w.x + w.w) * zoom)) < 5f && Math.abs(ui.my - (originY + (w.y + w.h) * zoom)) < 5f) {
                resizing = true;
            } else {
                selected = pick(gx, gy);
                moving = selected != NONE;
            }
            grabX = ui.mx;
            grabY = ui.my;
            Widget now = widget();
            if (selected == INVENTORY) {
                startX = sc.invX;
                startY = sc.invY;
            } else if (now != null) {
                startX = now.x;
                startY = now.y;
                startW = now.w;
                startH = now.h;
            }
        }
        if (ui.active.equals("canvas") && ui.mouseIsDown()) {
            int dx = Math.round((ui.mx - grabX) / zoom), dy = Math.round((ui.my - grabY) / zoom);
            Widget w = widget();
            if (resizing && w != null) {
                int nw = Math.max(4, Math.min(sc.width - w.x, startW + dx)), nh = Math.max(4, Math.min(sc.height - w.y, startH + dy));
                if (nw != w.w || nh != w.h) {
                    w.w = nw;
                    w.h = nh;
                    s.state.touch();
                }
            } else if (moving && selected == INVENTORY) {
                int nx = Math.max(0, Math.min(sc.width - 162, startX + dx)), ny = Math.max(0, Math.min(sc.height - 76, startY + dy));
                if (nx != sc.invX || ny != sc.invY) {
                    sc.invX = nx;
                    sc.invY = ny;
                    s.state.touch();
                }
            } else if (moving && w != null) {
                int nx = Math.max(0, Math.min(sc.width - w.w, startX + dx)), ny = Math.max(0, Math.min(sc.height - w.h, startY + dy));
                if (nx != w.x || ny != w.y) {
                    w.x = nx;
                    w.y = ny;
                    s.state.touch();
                }
            }
        }
    }

    private int pick(float gx, float gy) {
        ScreenInfo sc = screen();
        for (int i = sc.widgets.size() - 1; i >= 0; i--) {
            Widget w = sc.widgets.get(i);
            if (gx >= w.x && gy >= w.y && gx < w.x + w.w && gy < w.y + w.h) return i;
        }
        if (gx >= sc.invX && gy >= sc.invY && gx < sc.invX + 162 && gy < sc.invY + 76) return INVENTORY;
        return NONE;
    }

    private void list(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ScreenInfo sc = screen();
        ui.panel(x0, y0, x1, y1, 9f);
        float pad = 6f;
        ui.text("On the screen", x0 + pad + 1f, y0 + 7f, Ui.TEXT);
        ui.textRight(sc.widgets.size() + " / " + Limits.MAX_WIDGETS, x1 - pad, y0 + 7f, Ui.TEXT_DIM);
        Widget.Kind[] kinds = Widget.Kind.values();
        float cw = (x1 - x0 - pad * 2f) / 2f;
        float ay = y0 + 19f;
        for (int i = 0; i < kinds.length; i++) {
            float cx = x0 + pad + (i % 2) * cw, cy = ay + (i / 2) * 15f;
            if (ui.button("add_w_" + kinds[i].name().toLowerCase(), cx, cy, cw - 2f, 13f, "+ " + kinds[i].label, false, sc.widgets.size() < Limits.MAX_WIDGETS)) add(kinds[i]);
        }
        float listTop = ay + 15f * ((kinds.length + 1) / 2) + 2f, listBottom = y1 - 22f;
        ui.well(x0 + 4f, listTop, x1 - 4f, listBottom, 6f);
        float rowH = 14f;
        float scroll = ui.scroll("widget_scroll", x0 + 4f, listTop, x1 - 4f, listBottom, (sc.widgets.size() + 1) * rowH + 4f);
        ui.pushClip(x0 + 4f, listTop + 1f, x1 - 4f, listBottom - 1f);
        float ry = listTop + 2f - scroll;
        // the player's inventory always exists
        row("w_inv", x0 + 6f, ry, x1 - 8f, rowH, "Player's bag", selected == INVENTORY, INVENTORY);
        ry += rowH;
        for (int i = 0; i < sc.widgets.size(); i++) {
            Widget w = sc.widgets.get(i);
            String label = w.kind.label + (w.text.isEmpty() ? "" : ": " + w.text) + (w.kind == Widget.Kind.SLOT && w.output ? ": output" : "");
            row("w_" + i, x0 + 6f, ry, x1 - 8f, rowH, label, selected == i, i);
            ry += rowH;
        }
        ui.popClip();
        float by = y1 - 19f, bh = 14f, bw = (x1 - x0 - pad * 2f - 6f) / 4f;
        boolean has = widget() != null;
        if (ui.button("w_up", x0 + pad, by, bw, bh, "Up", false, has && selected < sc.widgets.size() - 1)) move(1);
        if (ui.button("w_down", x0 + pad + bw + 2f, by, bw, bh, "Down", false, has && selected > 0)) move(-1);
        if (ui.button("w_copy", x0 + pad + (bw + 2f) * 2f, by, bw, bh, "Copy", false, has && sc.widgets.size() < Limits.MAX_WIDGETS)) copy();
        if (ui.button("w_delete", x0 + pad + (bw + 2f) * 3f, by, bw, bh, "Del", false, has)) delete();
    }

    private void row(String id, float x0, float ry, float x1, float h, String label, boolean on, int index) {
        Ui ui = s.ui;
        if (on) ui.draw.grad(x0, ry, x1, ry + h - 1f, 5f, 0xFF6A4478, 0xFF593A69);
        else if (ui.over(x0, ry, x1, ry + h - 1f)) ui.draw.rect(x0, ry, x1, ry + h - 1f, 5f, 0x30FFFFFF);
        ui.text(ui.fit(label, x1 - x0 - 8f), x0 + 5f, ry + 2.5f, on ? 0xFFFFFFFF : Ui.TEXT);
        if (ui.click(id, x0, ry, x1, ry + h - 1f)) selected = index;
    }

    private void add(Widget.Kind kind) {
        ScreenInfo sc = screen();
        Widget w = new Widget();
        w.kind = kind;
        w.x = Math.max(0, sc.width / 2 - 20);
        w.y = 24;
        switch (kind) {
            case PANEL -> {
                w.w = 80;
                w.h = 40;
                w.colour = 0xB0170E1E;
            }
            case TEXT -> {
                w.w = 80;
                w.h = 10;
                w.text = "Some words";
                w.colour = 0xFFF8EAF3;
            }
            case SLOT -> {
                w.w = 18;
                w.h = 18;
            }
            case BUTTON -> {
                w.w = 50;
                w.h = 16;
                w.text = "Start";
            }
            case PROGRESS -> {
                w.w = 30;
                w.h = 14;
            }
            case IMAGE -> {
                w.w = 16;
                w.h = 16;
                w.item = "minecraft:sugar";
            }
            case STEPS -> {
                w.x = 8;
                w.w = Math.max(40, sc.width - 16);
                w.h = 14;
            }
        }
        w.x = Math.min(w.x, sc.width - w.w);
        sc.widgets.add(w);
        selected = sc.widgets.size() - 1;
        s.state.changed();
        s.state.commit();
    }

    private void move(int by) {
        List<Widget> list = screen().widgets;
        int to = selected + by;
        if (selected < 0 || to < 0 || to >= list.size()) return;
        Widget w = list.remove(selected);
        list.add(to, w);
        selected = to;
        s.state.touch();
    }

    private void copy() {
        Widget w = widget();
        if (w == null) return;
        Widget c = w.copy();
        c.x = Math.min(screen().width - c.w, c.x + 4);
        c.y = Math.min(screen().height - c.h, c.y + 4);
        screen().widgets.add(c);
        selected = screen().widgets.size() - 1;
        s.state.touch();
    }

    void delete() {
        if (widget() == null) return;
        screen().widgets.remove(selected);
        selected = NONE;
        s.state.touch();
    }

    private int intRow(String id, String label, int v, int softMax, int hardMin, int hardMax) {
        float n = s.ui.row(id, left, y, width, label, 34f, v, Math.min(hardMin, v), Math.max(softMax, v), hardMin, hardMax, 0);
        y += 14f;
        int out = Math.round(n);
        if (out != v) s.state.touch();
        return out;
    }

    private void props(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ui.panel(x0, y0, x1, y1, 9f);
        float scroll = ui.scroll("screen_props", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentH);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
        float start = y;
        Widget w = widget();
        ScreenInfo sc = screen();
        if (selected == INVENTORY) {
            section("Player's bag");
            sc.invX = intRow("inv_x", "X", sc.invX, Math.max(0, sc.width - 162), 0, Math.max(0, sc.width - 162));
            sc.invY = intRow("inv_y", "Y", sc.invY, Math.max(0, sc.height - 76), 0, Math.max(0, sc.height - 76));
            for (String line : ui.wrap("The player's own slots are always on the screen, you can only move them.", width)) {
                ui.text(line, left, y, Ui.TEXT_DIM);
                y += 10f;
            }
        } else if (w == null) {
            screenProps(sc);
        } else {
            widgetProps(sc, w);
        }
        contentH = y - start + 8f;
        ui.popClip();
    }

    private void screenProps(ScreenInfo sc) {
        Ui ui = s.ui;
        section("The whole screen");
        int nw = intRow("scr_w", "Wide", sc.width, 320, Limits.MIN_SCREEN_W, Limits.MAX_SCREEN_W);
        int nh = intRow("scr_h", "High", sc.height, 240, Limits.MIN_SCREEN_H, Limits.MAX_SCREEN_H);
        if (nw != sc.width || nh != sc.height) {
            sc.width = nw;
            sc.height = nh;
            // nothing gets left outside
            sc.invX = Math.max(0, Math.min(sc.width - 162, sc.invX));
            sc.invY = Math.max(0, Math.min(sc.height - 76, sc.invY));
            for (Widget w : sc.widgets) {
                w.w = Math.min(w.w, sc.width);
                w.h = Math.min(w.h, sc.height);
                w.x = Math.max(0, Math.min(sc.width - w.w, w.x));
                w.y = Math.max(0, Math.min(sc.height - w.h, w.y));
            }
        }
        section("Colour at the top");
        int top = s.build.colourAt("scr_top", left, y, width, sc.colourTop);
        y = s.build.nextY();
        if (top != sc.colourTop) {
            sc.colourTop = top;
            s.state.touch();
        }
        section("Colour at the bottom");
        int bottom = s.build.colourAt("scr_bottom", left, y, width, sc.colourBottom);
        y = s.build.nextY();
        if (bottom != sc.colourBottom) {
            sc.colourBottom = bottom;
            s.state.touch();
        }
        section("How to use this");
        for (String line : ui.wrap("Click something on the screen to change it, drag it to move it, drag its white dot to size it. Save, then give a block this screen on the block's own second tab.", width)) {
            ui.text(line, left, y, Ui.TEXT_DIM);
            y += 10f;
        }
    }

    private void widgetProps(ScreenInfo sc, Widget w) {
        Ui ui = s.ui;
        section(w.kind.label);
        if (w.kind == Widget.Kind.TEXT || w.kind == Widget.Kind.BUTTON) {
            String t = ui.textBox("w_text", left, y, width, 14f, w.text, "Words");
            if (!t.equals(w.text)) {
                w.text = DesignRules.cleanName(t, 80, "");
                s.state.touch();
            }
            y += 17f;
            if (w.kind == Widget.Kind.TEXT) {
                for (String line : ui.wrap("{step} shows the step running and {percent} how far along it is.", width)) {
                    ui.text(line, left, y, Ui.TEXT_DIM);
                    y += 10f;
                }
                y += 2f;
            }
        }
        w.x = intRow("w_x", "X", w.x, Math.max(1, sc.width - w.w), 0, Math.max(0, sc.width - w.w));
        w.y = intRow("w_y", "Y", w.y, Math.max(1, sc.height - w.h), 0, Math.max(0, sc.height - w.h));
        if (w.kind != Widget.Kind.SLOT) {
            w.w = intRow("w_w", "Wide", w.w, sc.width, 1, sc.width - w.x);
            w.h = intRow("w_h", "High", w.h, Math.min(60, sc.height), 1, sc.height - w.y);
        }
        switch (w.kind) {
            case SLOT -> {
                boolean out = ui.toggle("w_output", left, y, width, "Output (only the machine fills it)", w.output);
                y += 15f;
                if (out != w.output) {
                    w.output = out;
                    s.state.touch();
                }
                int number = sc.slots().indexOf(w) + 1;
                ui.text("This is slot " + number, left, y, Ui.TEXT_DIM);
                y += 12f;
            }
            case TEXT -> {
                boolean c = ui.toggle("w_centred", left, y, width, "Centred", w.centred);
                y += 15f;
                if (c != w.centred) {
                    w.centred = c;
                    s.state.touch();
                }
            }
            case PROGRESS -> {
                int style = ui.chips("w_style", left, y, width, 13f, new String[] {"Arrow", "Bar"}, w.bar ? 1 : 0);
                y += 16f;
                if (style >= 0 && (style == 1) != w.bar) {
                    w.bar = style == 1;
                    s.state.touch();
                }
            }
            case BUTTON -> buttonAction(sc, w);
            case IMAGE -> imageProps(w);
            default -> {
            }
        }
        if (w.kind != Widget.Kind.SLOT && w.kind != Widget.Kind.IMAGE) {
            section("Colour");
            int c = s.build.colourAt("w_col", left, y, width, w.colour);
            y = s.build.nextY();
            if (c != w.colour) {
                w.colour = c;
                s.state.touch();
            }
        }
    }

    private void buttonAction(ScreenInfo sc, Widget w) {
        Ui ui = s.ui;
        section("When pressed");
        if (ui.chip("act_start", left, y, width / 2f - 1f, 12f, "Start", w.action == Widget.ACTION_START)) {
            w.action = Widget.ACTION_START;
            s.state.touch();
        }
        if (ui.chip("act_stop", left + width / 2f + 1f, y, width / 2f - 1f, 12f, "Stop", w.action == Widget.ACTION_STOP)) {
            w.action = Widget.ACTION_STOP;
            s.state.touch();
        }
        y += 14f;
        for (int i = 0; i < sc.recipes.size(); i++) {
            if (ui.chip("act_" + i, left, y, width, 12f, "Run: " + sc.recipes.get(i).name, w.action == i)) {
                w.action = i;
                s.state.touch();
            }
            y += 14f;
        }
    }

    private void imageProps(Widget w) {
        Ui ui = s.ui;
        section("Picture");
        float half = width / 2f - 1f;
        if (ui.button("w_item", left, y, half, 13f, "An item")) {
            s.popup = PickerPopup.itemRefs(s, ref -> {
                w.item = ref;
                w.paint = -1;
                s.state.touch();
            });
        }
        if (ui.accent("w_paint", left + half + 2f, y, half, 13f, "Paint it")) s.popup = new PaintPopup(s, w);
        y += 16f;
        if (!w.item.isEmpty() && w.paint < 0) {
            ui.text(ui.fit(ThingPanels.nameOfRef(w.item), width), left, y, Ui.TEXT_DIM);
            y += 11f;
        }
    }

    // ---------- the Recipes tab ----------

    void drawRecipes(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ScreenInfo sc = screen();
        float w = Math.min(x1 - x0, 620f);
        x0 = (x0 + x1 - w) / 2f;
        x1 = x0 + w;
        ui.panel(x0, y0, x1, y1, 10f);
        float scroll = ui.scroll("recipes", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, recipesH);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 10f;
        width = x1 - x0 - 22f;
        y = y0 + 8f - scroll;
        float start = y;
        ui.text("What this machine makes", left, y, Ui.PINK_SOFT);
        y += 12f;
        for (String line : ui.wrap("Each step takes what's in the slots, waits, and puts its result in an output slot. A result sitting in an output slot can go straight into the next step, so a three step drink needs no moving things by hand.", width)) {
            ui.text(line, left, y, Ui.TEXT_DIM);
            y += 10f;
        }
        y += 3f;
        boolean auto = ui.toggle("auto", left, y, Math.min(width, 190f), "Starts by itself", sc.auto);
        if (auto != sc.auto) {
            sc.auto = auto;
            s.state.touch();
        }
        y += 16f;
        warnings(sc);
        for (int i = 0; i < sc.recipes.size(); i++) {
            if (card(sc, i)) break;
        }
        if (sc.recipes.size() < Limits.MAX_RECIPES && ui.accent("recipe_add", left, y, 120f, 15f, "+ Add a step")) {
            ScreenInfo.Recipe r = new ScreenInfo.Recipe();
            r.name = "Step " + (sc.recipes.size() + 1);
            sc.recipes.add(r);
            s.state.touch();
        }
        y += 22f;
        recipesH = y - start + 8f;
        ui.popClip();
    }

    private void warnings(ScreenInfo sc) {
        Ui ui = s.ui;
        boolean output = false, button = false;
        for (Widget w : sc.widgets) {
            if (w.kind == Widget.Kind.SLOT && w.output) output = true;
            if (w.kind == Widget.Kind.BUTTON || w.kind == Widget.Kind.STEPS) button = true;
        }
        String warn = !output ? "This screen has no output slot yet, so steps have nowhere to put things." : !button && !sc.auto ? "Add a button or a Steps strip on the Layout tab, or turn on Starts by itself." : "";
        if (!warn.isEmpty()) {
            ui.text(ui.fit(warn, width), left, y, Ui.RED);
            y += 13f;
        }
    }

    // True when the list changed under it.
    private boolean card(ScreenInfo sc, int index) {
        Ui ui = s.ui;
        ScreenInfo.Recipe r = sc.recipes.get(index);
        float h = 46f;
        ui.draw.rect(left, y, left + width, y + h, 7f, 0xB0170E1E);
        ui.draw.border(left, y, left + width, y + h, 7f, 0.5f, 0x28FFFFFF);
        float tx = left + 6f, ty = y + 5f;
        ui.draw.circle(tx + 6f, ty + 6.5f, 6f, Ui.PINK);
        ui.textCentre(String.valueOf(index + 1), tx + 6f, ty + 2.5f, Ui.TEXT_DARK);
        String name = ui.textBox("r_name" + index, tx + 16f, ty, 110f, 13f, r.name, "Step name");
        if (!name.equals(r.name)) {
            r.name = DesignRules.cleanName(name, 24, "");
            s.state.touch();
        }
        ui.text("Takes", tx + 132f, ty + 2.5f, Ui.TEXT_DIM);
        float sec = ui.number("r_sec" + index, tx + 164f, ty, 34f, 13f, r.seconds, 2);
        if (sec != r.seconds) {
            r.seconds = Math.max(0.05f, Math.min(Limits.MAX_RECIPE_SECONDS, sec));
            s.state.touch();
        }
        ui.text("s", tx + 201f, ty + 2.5f, Ui.TEXT_DIM);
        float bx = left + width - 58f;
        if (ui.chip("r_up" + index, bx, ty, 18f, 13f, "^", false) && index > 0) {
            sc.recipes.remove(index);
            sc.recipes.add(index - 1, r);
            s.state.touch();
            return true;
        }
        if (ui.chip("r_down" + index, bx + 19f, ty, 18f, 13f, "v", false) && index < sc.recipes.size() - 1) {
            sc.recipes.remove(index);
            sc.recipes.add(index + 1, r);
            s.state.touch();
            return true;
        }
        if (ui.chip("r_x" + index, bx + 38f, ty, 18f, 13f, "x", false)) {
            sc.recipes.remove(index);
            s.state.touch();
            return true;
        }
        // what goes in, an arrow, what comes out
        float iy = y + 22f, ix = left + 6f;
        for (int n = 0; n < r.inputs.size(); n++) {
            ScreenInfo.Ingredient in = r.inputs.get(n);
            ix = itemCell("r" + index + "_in" + n, ix, iy, in.item, in.count, c -> in.count = c, ref -> in.item = ref, () -> r.inputs.remove(in));
            if (ix < 0f) return true;
        }
        if (r.inputs.size() < Limits.MAX_INPUTS) {
            if (ui.chip("r" + index + "_add", ix, iy + 2f, 16f, 14f, "+", false)) {
                s.popup = PickerPopup.itemRefs(s, ref -> {
                    r.inputs.add(new ScreenInfo.Ingredient(ref, 1));
                    s.state.touch();
                });
            }
            ix += 20f;
        }
        Draw d = ui.draw;
        float ax = ix + 2f, ay = iy + 9f;
        d.rect(ax, ay - 1f, ax + 10f, ay + 1f, 1f, Ui.PINK);
        d.triangle(ax + 9f, ay - 4f, ax + 9f, ay + 4f, ax + 15f, ay, Ui.PINK);
        ix = ax + 20f;
        itemCell("r" + index + "_out", ix, iy, r.output, r.outputCount, c -> r.outputCount = c, ref -> r.output = ref, null);
        y += h + 5f;
        return false;
    }

    private interface IntSet {
        void set(int v);
    }

    private interface RefSet {
        void set(String ref);
    }

    // An item with its count. Answers where the next one goes, or -1 when it removed itself.
    private float itemCell(String id, float x, float cy, String ref, int count, IntSet setCount, RefSet setRef, Runnable remove) {
        Ui ui = s.ui;
        float w = remove == null ? 44f : 58f;
        ui.draw.rect(x, cy, x + w, cy + 18f, 5f, 0xFF2C1D36);
        ItemStack stack = DesignStacks.ofRef(ref, 1, Designs.CLIENT);
        if (!stack.isEmpty()) ui.item(stack, x + 2f, cy + 2f, 14f);
        else ui.text("?", x + 6f, cy + 5f, Ui.RED);
        boolean over = ui.over(x, cy, x + 18f, cy + 18f);
        if (over) ui.tooltip(ThingPanels.nameOfRef(ref) + "\nClick to change");
        if (ui.click(id + "_pick", x, cy, x + 18f, cy + 18f)) {
            s.popup = PickerPopup.itemRefs(s, r -> {
                setRef.set(r);
                s.state.touch();
            });
        }
        float n = ui.number(id + "_n", x + 18f, cy + 3f, 22f, 12f, count, 0);
        int cn = Math.max(1, Math.min(64, Math.round(n)));
        if (cn != count) {
            setCount.set(cn);
            s.state.touch();
        }
        if (remove != null && ui.chip(id + "_x", x + 42f, cy + 3f, 13f, 12f, "x", false)) {
            remove.run();
            s.state.touch();
            return -1f;
        }
        return x + w + 3f;
    }
}
