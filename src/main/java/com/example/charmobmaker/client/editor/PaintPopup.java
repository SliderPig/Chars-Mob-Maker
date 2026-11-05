package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Paints;
import com.example.charmobmaker.model.Part;

// Painting a box by hand: 16 x 16 cells, one for the whole box or a different one per side. Sits at the side so you can see the mob while you paint.
final class PaintPopup implements EditorScreen.Popup {
    private static final String[] TOOLS = {"Pen", "Fill", "Eraser"};
    private final EditorScreen s;
    private final Part part;
    private int tool;
    private int colour = 0xFFFF8FC7;
    private int face = -1; // -1 = every side
    private int cell;
    private boolean painting;

    PaintPopup(EditorScreen s, Part part) {
        this.s = s;
        this.part = part;
        if (part.facePaint != null) {
            for (int i = 0; i < Part.FACES && face < 0; i++) {
                if (part.facePaint[i] >= 0) {
                    face = i;
                    cell = part.facePaint[i];
                }
            }
        }
        if (face < 0) cell = Math.max(0, part.paint);
    }

    @Override
    public void draw(EditorScreen sc) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        if (st.design.part(part.id) != part) {
            s.popup = null;
            return;
        }
        float w = 268f, h = Math.min(ui.height - 40f, 376f);
        float x0 = 10f, y0 = (ui.height - h) / 2f, x1 = x0 + w, y1 = y0 + h;
        boolean outside = ui.pressed && !(ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1);
        ui.panel(x0, y0, x1, y1, 12f);
        ui.flush();
        ui.claim(x0, y0, x1, y1);
        ui.text("Painting " + ui.fit(part.name, 120f), x0 + 12f, y0 + 9f, Ui.TEXT);
        if (ui.button("popup_close", x1 - 26f, y0 + 6f, 20f, 14f, "X")) {
            s.popup = null;
            st.changed();
            return;
        }
        if (st.design.paints.isEmpty()) {
            st.design.paints.add(new int[256]);
            st.changed();
        }
        cell = Math.max(0, Math.min(st.design.paints.size() - 1, cell));

        // which side of the box
        float y = y0 + 25f;
        float cw = (w - 16f) / 7f;
        if (ui.chip("face_all", x0 + 8f, y, cw - 1f, 13f, "All", face < 0)) face = -1;
        for (int i = 0; i < Part.FACES; i++) {
            if (ui.chip("face_" + i, x0 + 8f + (i + 1) * cw, y, cw - 1f, 13f, Part.FACE_NAMES[i], face == i)) {
                face = i;
                int c = Paints.cellFor(part, i);
                if (c >= 0) cell = c;
            }
        }
        y += 18f;

        // the cells
        ui.text("Cells", x0 + 10f, y + 3f, Ui.TEXT_DIM);
        float cellX = x0 + 36f, tiny = 15f;
        for (int i = 0; i < st.design.paints.size(); i++) {
            float cx = cellX + (i % 8) * (tiny + 3f), cy = y + (i / 8) * (tiny + 3f);
            boolean on = i == cell;
            preview(st.design.paints.get(i), cx, cy, tiny, on);
            if (ui.click("cell_" + i, cx, cy, cx + tiny, cy + tiny)) cell = i;
        }
        int nextIndex = st.design.paints.size();
        if (nextIndex < Limits.MAX_PAINTS) {
            float cx = cellX + (nextIndex % 8) * (tiny + 3f), cy = y + (nextIndex / 8) * (tiny + 3f);
            if (ui.chip("cell_add", cx, cy, tiny, tiny, "+", false)) {
                st.design.paints.add(new int[256]);
                cell = nextIndex;
                st.changed();
            }
        }
        y += (Math.min(Limits.MAX_PAINTS, nextIndex + 1) + 7) / 8 * (tiny + 3f) + 2f;

        // the canvas
        float room = Math.min(w - 24f, y1 - y - 104f);
        float size = (float) Math.floor(room / 16f) * 16f;
        float px = x0 + (w - size) / 2f, py = y;
        float step = size / 16f;
        d.halo(px, py, px + size, py + size, 3f, 0x60000000, 6f);
        int[] pixels = st.design.paints.get(cell);
        for (int iy = 0; iy < 16; iy++) {
            for (int ix = 0; ix < 16; ix++) {
                float a = px + ix * step, b = py + iy * step;
                int c = pixels[iy * 16 + ix];
                // a soft checker shows see-through pixels
                d.rect(a, b, a + step, b + step, 0f, ((ix + iy) & 1) == 0 ? 0xFF3A2D42 : 0xFF2E2336);
                if ((c >>> 24) > 0) d.rect(a, b, a + step, b + step, 0f, c);
            }
        }
        boolean overCanvas = !ui.blocked && ui.mx >= px && ui.mx < px + size && ui.my >= py && ui.my < py + size;
        ui.rects.put("canvas", new float[] {px, py, px + size, py + size});
        if (ui.pressed && overCanvas) {
            ui.pressed = false;
            ui.active = "canvas";
            painting = true;
            int ix = (int) ((ui.mx - px) / step), iy = (int) ((ui.my - py) / step);
            if (tool == 1) {
                Paints.fillFrom(pixels, ix, iy, colour);
                applyToPart();
            }
        }
        if (painting && ui.mouseIsDown() && overCanvas && tool != 1) {
            int ix = (int) ((ui.mx - px) / step), iy = (int) ((ui.my - py) / step);
            if (ix >= 0 && iy >= 0 && ix < 16 && iy < 16) {
                int newColour = tool == 2 ? 0 : colour;
                if (pixels[iy * 16 + ix] != newColour) {
                    pixels[iy * 16 + ix] = newColour;
                    applyToPart();
                }
            }
        }
        if (!ui.mouseIsDown()) painting = false;
        if (overCanvas) {
            int ix = (int) ((ui.mx - px) / step), iy = (int) ((ui.my - py) / step);
            d.border(px + ix * step, py + iy * step, px + (ix + 1) * step, py + (iy + 1) * step, 1f, 0.75f, 0xFFFFFFFF);
        }
        d.border(px, py, px + size, py + size, 2f, 0.5f, 0x40FFFFFF);
        y = py + size + 5f;

        // tools and colours
        float tw = 44f;
        for (int i = 0; i < TOOLS.length; i++) {
            if (ui.chip("tool_paint_" + i, x0 + 10f + i * (tw + 2f), y, tw, 13f, TOOLS[i], tool == i)) tool = i;
        }
        if (ui.button("use_cell", x1 - 92f, y, 82f, 13f, face < 0 ? "Use on all sides" : "Use on this side")) applyToPart();
        y += 18f;
        float dot = 5f, stepD = (w - 24f) / 8f;
        for (int i = 0; i < BuildPanel.SWATCHES.length; i++) {
            float cx = x0 + 12f + stepD * (i % 8) + stepD / 2f, cy = y + 6f + (i / 8) * 12.5f;
            if ((colour | 0xFF000000) == BuildPanel.SWATCHES[i]) d.ring(cx, cy, dot + 1.25f, 0.75f, Ui.PINK_SOFT);
            d.circle(cx, cy, dot, BuildPanel.SWATCHES[i]);
            if (ui.click("paint_sw" + i, cx - dot, cy - dot, cx + dot, cy + dot)) {
                colour = BuildPanel.SWATCHES[i];
                if (tool == 2) tool = 0;
            }
        }
        y += 27f;
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        float third = (w - 24f) / 3f;
        r = Math.round(ui.row("paint_r", x0 + 12f, y, third - 3f, "R", 8f, r, 0f, 255f, 0f, 255f, 0));
        g = Math.round(ui.row("paint_g", x0 + 12f + third, y, third - 3f, "G", 8f, g, 0f, 255f, 0f, 255f, 0));
        b = Math.round(ui.row("paint_b", x0 + 12f + third * 2f, y, third - 3f, "B", 8f, b, 0f, 255f, 0f, 255f, 0));
        colour = 0xFF000000 | (r << 16) | (g << 8) | b;
        if (!outside) ui.pressed = false;
        if (!outside) ui.wheel = 0f;
    }

    // Tells the part to show the cell being painted.
    private void applyToPart() {
        EditorState st = s.state;
        if (face < 0) {
            part.paint = cell;
            if (part.facePaint != null) java.util.Arrays.fill(part.facePaint, -1);
        } else {
            if (part.facePaint == null) {
                part.facePaint = new int[Part.FACES];
                java.util.Arrays.fill(part.facePaint, -1);
            }
            part.facePaint[face] = cell;
        }
        st.changed();
    }

    private void preview(int[] pixels, float x, float y, float size, boolean on) {
        Ui ui = s.ui;
        float step = size / 16f;
        ui.draw.rect(x, y, x + size, y + size, 2f, 0xFF2E2336);
        for (int iy = 0; iy < 16; iy += 1) {
            for (int ix = 0; ix < 16; ix += 1) {
                int c = pixels[iy * 16 + ix];
                if ((c >>> 24) > 0) ui.draw.rect(x + ix * step, y + iy * step, x + (ix + 1) * step, y + (iy + 1) * step, 0f, c);
            }
        }
        ui.draw.border(x, y, x + size, y + size, 2f, on ? 1f : 0.5f, on ? Ui.PINK : 0x30FFFFFF);
    }
}
