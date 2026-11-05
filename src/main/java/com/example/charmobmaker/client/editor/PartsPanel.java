package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.PartTree;

import java.util.List;

// The tree of parts on the left. Drag a row onto another to hang it from that part; drag it out to the empty space to free it.
final class PartsPanel {
    private final EditorScreen s;
    private String dragging = "";
    private float dragX, dragY;
    private boolean dragMoved;
    private String dropOn;
    private boolean dropToTop;

    PartsPanel(EditorScreen s) {
        this.s = s;
    }

    void draw(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        EditorState st = s.state;
        Draw d = ui.draw;
        ui.panel(x0, y0, x1, y1, 9f);
        float pad = 6f;
        ui.text("Parts", x0 + pad + 1f, y0 + 7f, Ui.TEXT);
        ui.textRight(st.design.parts.size() + " / " + Limits.MAX_PARTS, x1 - pad, y0 + 7f, Ui.TEXT_DIM);
        if (ui.accent("add_part", x0 + pad, y0 + 19f, x1 - x0 - pad * 2f, 15f, "+  Add a part")) s.popup = new AddPartPopup(s);

        float listTop = y0 + 39f, listBottom = y1 - 22f;
        ui.well(x0 + 4f, listTop, x1 - 4f, listBottom, 6f);
        List<PartTree.Row> rows = PartTree.rows(st.design, st.collapsed);
        float rowH = 14f;
        float scroll = ui.scroll("parts_scroll", x0 + 4f, listTop, x1 - 4f, listBottom, rows.size() * rowH + 4f);
        ui.pushClip(x0 + 4f, listTop + 1f, x1 - 4f, listBottom - 1f);
        dropOn = null;
        dropToTop = false;
        float rowLeft = x0 + 6f, rowRight = x1 - 8f;
        for (int i = 0; i < rows.size(); i++) {
            PartTree.Row r = rows.get(i);
            float y = listTop + 2f + i * rowH - scroll;
            if (y + rowH < listTop || y > listBottom) continue;
            row(r, rowLeft, y, rowRight, rowH);
        }
        if (rows.isEmpty()) {
            for (int i = 0; i < 2; i++) ui.textCentre(i == 0 ? "No parts yet." : "Press Add a part.", (x0 + x1) / 2f, listTop + 14f + i * 11f, Ui.TEXT_DIM);
        }
        ui.popClip();
        // letting go of a dragged row
        if (!dragging.isEmpty()) {
            if (Math.abs(ui.mx - dragX) + Math.abs(ui.my - dragY) > 4f) dragMoved = true;
            if (!dragMoved && !ui.mouseIsDown()) dragging = "";
            if (dragMoved) {
                if (dropOn == null && ui.mx >= x0 && ui.mx < x1 && ui.my >= listTop && ui.my < listBottom) dropToTop = true;
                Part moving = st.design.part(dragging);
                if (moving != null) {
                    float w = ui.width(moving.name) + 12f;
                    d.rect(ui.mx + 4f, ui.my - 7f, ui.mx + 4f + w, ui.my + 6f, 6f, 0xE0FF8FC7);
                    ui.text(moving.name, ui.mx + 10f, ui.my - 5f, Ui.TEXT_DARK);
                }
                if (!ui.mouseIsDown()) {
                    if (dropOn != null) {
                        if (PartTree.reparent(st.design, dragging, dropOn)) {
                            st.changed();
                        } else {
                            st.say("It can't hang from itself or its own children", true);
                        }
                    } else if (dropToTop) {
                        PartTree.reparent(st.design, dragging, "");
                        st.changed();
                    }
                    dragging = "";
                    dragMoved = false;
                }
            }
        }

        // the buttons under the list
        float by = y1 - 19f, bh = 14f;
        float bw = (x1 - x0 - pad * 2f - 4f) / 3f;
        boolean has = st.selectedPart() != null;
        if (ui.button("part_copy", x0 + pad, by, bw, bh, "Copy", false, has)) duplicate();
        if (ui.button("part_mirror", x0 + pad + bw + 2f, by, bw, bh, "Mirror", false, has)) mirror();
        if (ui.button("part_delete", x0 + pad + (bw + 2f) * 2f, by, bw, bh, "Delete", false, has)) delete();
        if (has && ui.over(x0 + pad + bw + 2f, by, x0 + pad + bw * 2f + 2f, by + bh)) ui.tooltip("A copy on the other side of the mob, left becomes right");
    }

    private void row(PartTree.Row r, float x0, float y, float x1, float h) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        Part p = r.part();
        boolean selected = p.id.equals(st.selected);
        float indent = 9f * Math.min(r.depth(), 8);
        boolean hoverRow = ui.mx >= x0 && ui.mx < x1 && ui.my >= y && ui.my < y + h && !ui.blocked;
        if (!dragging.isEmpty() && dragMoved && hoverRow && !dragging.equals(p.id)) {
            dropOn = p.id;
            boolean ok = PartTree.canReparent(st.design, dragging, p.id);
            d.border(x0, y, x1, y + h - 1f, 5f, 1f, ok ? Ui.PINK : Ui.RED);
        }
        if (selected) {
            d.grad(x0, y, x1, y + h - 1f, 5f, 0xFF6A4478, 0xFF593A69);
            d.rect(x0 + 1f, y + 3f, x0 + 2.5f, y + h - 4f, 0.75f, Ui.PINK);
        } else if (ui.over(x0, y, x1, y + h - 1f)) {
            d.rect(x0, y, x1, y + h - 1f, 5f, 0x30FFFFFF);
        }
        float cx = x0 + 4f + indent;
        // the fold arrow
        if (r.hasChildren()) {
            boolean closed = st.collapsed.contains(p.id);
            if (closed) d.triangle(cx, y + 3.5f, cx, y + 9.5f, cx + 4f, y + 6.5f, Ui.TEXT_DIM);
            else d.triangle(cx - 0.5f, y + 4.5f, cx + 5.5f, y + 4.5f, cx + 2.5f, y + 8.5f, Ui.TEXT_DIM);
            if (ui.click("fold_" + p.id, cx - 2f, y, cx + 7f, y + h)) {
                if (closed) st.collapsed.remove(p.id);
                else st.collapsed.add(p.id);
                ui.active = "fold";
            }
        }
        cx += 8f;
        // a little dot says what kind of part it is
        int colour = switch (p.kind) {
            case CUBE -> (p.color & 0xFFFFFF) | 0xFF000000;
            case BLOCK -> 0xFF9AD1A0;
            case MOB -> 0xFFFFC56D;
            case ITEM -> 0xFF8FC7FF;
        };
        d.circle(cx + 2f, y + 6.5f, 2.5f, colour);
        d.ring(cx + 2f, y + 6.5f, 2.5f, 0.5f, 0x50FFFFFF);
        cx += 7f;
        ui.text(ui.fit(p.name, x1 - cx - 13f), cx, y + 2.5f, p.hidden ? 0xFF7E6A80 : selected ? 0xFFFFFFFF : Ui.TEXT);
        // the eye: hides it while you work
        float ex = x1 - 9f;
        boolean overEye = ui.over(ex - 1f, y, x1, y + h);
        if (overEye || p.hidden) {
            d.circle(ex + 3f, y + 6.5f, 2.5f, p.hidden ? 0x60FFFFFF : 0xFFFFFFFF);
            if (!p.hidden) d.circle(ex + 3f, y + 6.5f, 1f, 0xFF241630);
        }
        if (ui.click("eye_" + p.id, ex - 1f, y, x1, y + h)) {
            p.hidden = !p.hidden;
            st.changed();
            ui.active = "eye";
            return;
        }
        if (ui.click("row_" + p.id, x0, y, x1, y + h - 1f)) {
            st.selected = p.id;
            st.key = null;
            st.keyTrack = null;
            dragging = p.id;
            dragX = ui.mx;
            dragY = ui.my;
            dragMoved = false;
        }
    }

    void duplicate() {
        EditorState st = s.state;
        if (st.selectedPart() == null) return;
        st.commit();
        Part c = PartTree.duplicate(st.design, st.selected);
        if (c == null) {
            st.say("A mob can have " + Limits.MAX_PARTS + " parts at most.", true);
            return;
        }
        c.name = com.example.charmobmaker.model.DesignRules.cleanName(c.name + " copy", 32, c.name);
        st.selected = c.id;
        st.changed();
        st.commit();
    }

    void mirror() {
        EditorState st = s.state;
        if (st.selectedPart() == null) return;
        st.commit();
        Part c = PartTree.mirror(st.design, st.selected);
        if (c == null) {
            st.say("A mob can have " + Limits.MAX_PARTS + " parts at most.", true);
            return;
        }
        st.selected = c.id;
        st.changed();
        st.commit();
    }

    void delete() {
        EditorState st = s.state;
        if (st.selectedPart() == null) return;
        st.commit();
        PartTree.remove(st.design, st.selected);
        st.selected = "";
        st.key = null;
        st.keyTrack = null;
        st.changed();
        st.commit();
    }
}
