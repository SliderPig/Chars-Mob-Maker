package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.render.BlockMeshes;
import com.example.charmobmaker.client.render.MobLibrary;
import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Channel;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Paints;
import com.example.charmobmaker.model.Part;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

// The numbers of the selected part, on the right.
final class BuildPanel {
    static final int[] SWATCHES = {0xFFFF8FC7, 0xFFFFB6DC, 0xFFD6559C, 0xFF7A3B69, 0xFF2A1B33, 0xFF16121A, 0xFFFFFFFF, 0xFFB9B9B9,
            0xFF6B6B6B, 0xFFFF6B6B, 0xFFFFA94D, 0xFFFFE066, 0xFF8CE07A, 0xFF4DBFA8, 0xFF6FB5FF, 0xFFA58CFF};
    private static final String[] DISPLAYS = {"third_person_right_hand", "first_person_right_hand", "head", "ground", "fixed", "gui"};
    private static final String[] DISPLAY_NAMES = {"In hand", "Held up", "On head", "Dropped", "Flat", "Icon"};
    private final EditorScreen s;
    private float contentH = 200f;
    private float y;
    private float left, width;

    BuildPanel(EditorScreen s) {
        this.s = s;
    }

    void draw(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ui.panel(x0, y0, x1, y1, 9f);
        Part p = s.state.selectedPart();
        if (p == null) {
            hint(x0, y0, x1);
            return;
        }
        float scroll = ui.scroll("build_scroll", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentH);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
        float start = y;
        String name = ui.textBox("part_name", left, y, width, 14f, p.name, "Part name");
        if (!name.equals(p.name)) {
            p.name = DesignRules.cleanName(name, 32, "");
            s.state.touch();
        }
        y += 19f;
        switch (p.kind) {
            case CUBE -> cube(p);
            case BLOCK -> block(p);
            case MOB -> mob(p);
            case ITEM -> item(p);
        }
        transform(p);
        extras(p);
        contentH = y - start + 8f;
        ui.popClip();
    }

    private void hint(float x0, float y0, float x1) {
        Ui ui = s.ui;
        float cx = (x0 + x1) / 2f;
        List<String> lines = ui.wrap("Pick a part from the list or click it on the mob. Its numbers show up here.\n\nDrag in the middle to look around, wheel to zoom, right mouse drag to slide the view.", x1 - x0 - 20f);
        for (int i = 0; i < lines.size(); i++) ui.textCentre(lines.get(i), cx, y0 + 16f + i * 11f, Ui.TEXT_DIM);
    }

    void section(String title) {
        Ui ui = s.ui;
        y += 2f;
        ui.text(title, left, y, Ui.PINK_SOFT);
        float tw = ui.width(title) + 5f;
        ui.draw.rect(left + tw, y + 3.5f, left + width, y + 4.25f, 0.4f, 0x28FFFFFF);
        y += 12f;
    }

    private float[] triple(String id, String[] labels, float[] v, float softMin, float softMax, float hardMin, float hardMax, int decimals) {
        Ui ui = s.ui;
        float[] out = v.clone();
        for (int i = 0; i < 3; i++) {
            float n = ui.row(id + i, left, y, width, labels[i], 10f, v[i], softMin, softMax, hardMin, hardMax, decimals);
            if (n != v[i]) out[i] = n;
            y += 14f;
        }
        return out;
    }

    private static final String[] XYZ = {"X", "Y", "Z"};

    private void cube(Part p) {
        Ui ui = s.ui;
        section("Box size");
        float[] size = triple("size", XYZ, p.size, 0f, 48f, 0f, Limits.MAX_SIZE, 2);
        if (!same(size, p.size)) {
            p.size = size;
            s.state.changed();
        }
        section("Colour");
        int colour = colourEditor("col", p.color);
        if (colour != p.color) {
            p.color = colour;
            s.state.changed();
        }
        section("Texture");
        float bw = (width - 3f) / 2f;
        if (ui.button("tex_pick", left, y, bw, 14f, p.texture.isEmpty() ? "Block texture" : "Change")) {
            s.popup = PickerPopup.blockTextures(s, tex -> {
                p.texture = tex;
                s.state.changed();
            });
        }
        if (ui.button("tex_clear", left + bw + 3f, y, bw, 14f, "Plain", false, !p.texture.isEmpty())) {
            p.texture = "";
            s.state.changed();
        }
        y += 18f;
        if (!p.texture.isEmpty()) {
            ui.text(ui.fit(p.texture, width), left, y, Ui.TEXT_DIM);
            y += 12f;
        }
        section("Painted");
        if (ui.accent("paint_open", left, y, width, 14f, Paints.usesPaint(p) ? "Change the painting" : "Paint it yourself")) s.popup = new PaintPopup(s, p);
        y += 18f;
    }

    private void block(Part p) {
        Ui ui = s.ui;
        section("Block");
        BlockState state = BlockMeshes.parse(p.block);
        ItemStack icon = state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        ui.item(icon, left, y, 14f);
        ui.text(ui.fit(state == null ? "Unknown: " + p.block : state.getBlock().getName().getString(), width - 66f), left + 17f, y + 3f, Ui.TEXT);
        if (ui.button("block_change", left + width - 46f, y, 46f, 14f, "Change")) {
            s.popup = PickerPopup.blocks(s, id -> {
                p.block = id;
                s.state.changed();
            });
        }
        y += 18f;
        if (state != null) {
            for (Property<?> prop : state.getProperties()) {
                ui.text(ui.fit(MobLibrary.pretty(prop.getName()), width * 0.5f), left, y + 2f, Ui.TEXT_DIM);
                if (ui.chip("prop_" + prop.getName(), left + width * 0.52f, y, width * 0.48f, 12f, valueName(state, prop), false)) {
                    p.block = cycled(state, prop);
                    s.state.changed();
                }
                y += 14f;
            }
        }
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    private static <T extends Comparable<T>> String cycled(BlockState state, Property<T> prop) {
        return stateText(state.cycle(prop));
    }

    static String stateText(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        StringBuilder b = new StringBuilder(String.valueOf(id));
        if (!state.getProperties().isEmpty()) {
            b.append('[');
            boolean first = true;
            for (Property<?> prop : state.getProperties()) {
                if (!first) b.append(',');
                first = false;
                b.append(prop.getName()).append('=').append(valueName(state, prop));
            }
            b.append(']');
        }
        return b.toString();
    }

    private void mob(Part p) {
        Ui ui = s.ui;
        section("From a mob");
        MobLibrary.Model m = MobLibrary.get(p.mob);
        ui.text(ui.fit(m == null ? "Unknown: " + p.mob : MobLibrary.pretty(m.id.getPath()), width - 50f), left, y + 3f, Ui.TEXT);
        if (ui.button("mob_change", left + width - 46f, y, 46f, 14f, "Change")) s.popup = new AddPartPopup(s, p);
        y += 18f;
        if (m != null) {
            MobLibrary.Bone b = m.byPath.get(p.bone);
            ui.text(p.bone.isEmpty() ? "The whole mob" : b == null ? "Missing: " + p.bone : b.name, left, y, Ui.TEXT_DIM);
            y += 12f;
            if (b != null && !b.kids.isEmpty()) {
                boolean solo = ui.toggle("mob_solo", left, y, width, "Just this bone", p.solo);
                if (solo != p.solo) {
                    p.solo = solo;
                    s.state.changed();
                }
                y += 15f;
            }
            if (!p.bone.isEmpty() && ui.button("skin_pick", left, y, width, 14f, p.mobTexture.isEmpty() ? "Use another skin" : "Skin: " + shortTex(p.mobTexture))) {
                s.popup = PickerPopup.skins(s, m.id, tex -> {
                    p.mobTexture = tex;
                    s.state.changed();
                });
            }
            if (!p.bone.isEmpty()) y += 18f;
        }
    }

    private static String shortTex(String t) {
        int cut = t.lastIndexOf('/');
        String n = cut < 0 ? t : t.substring(cut + 1);
        return n.endsWith(".png") ? n.substring(0, n.length() - 4) : n;
    }

    private void item(Part p) {
        Ui ui = s.ui;
        section("Item");
        ResourceLocation rl = ResourceLocation.tryParse(p.item);
        var it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        ItemStack icon = it == null ? ItemStack.EMPTY : new ItemStack(it);
        ui.item(icon, left, y, 14f);
        ui.text(ui.fit(icon.isEmpty() ? "Unknown" : icon.getHoverName().getString(), width - 66f), left + 17f, y + 3f, Ui.TEXT);
        if (ui.button("item_change", left + width - 46f, y, 46f, 14f, "Change")) {
            s.popup = PickerPopup.items(s, id -> {
                p.item = id;
                s.state.changed();
            });
        }
        y += 18f;
        float cw = width / 3f;
        for (int i = 0; i < DISPLAYS.length; i++) {
            float cx = left + (i % 3) * cw, cy = y + (i / 3) * 14f;
            if (ui.chip("display_" + i, cx, cy, cw - 2f, 12f, DISPLAY_NAMES[i], p.display.equals(DISPLAYS[i]))) {
                p.display = DISPLAYS[i];
                s.state.changed();
            }
        }
        y += 30f;
        if (ui.button("item_hold", left, y, width, 13f, "Set it up like held in a hand")) {
            // what the game itself does when a mob holds something in a raised arm
            boolean right = !p.display.contains("left");
            p.pos = new float[] {right ? 1f : -1f, -10f, 2f};
            p.rot = new float[] {-90f, 180f, 0f};
            s.state.changed();
        }
        if (ui.over(left, y, left + width, y + 13f)) ui.tooltip("For an item hanging from an arm");
        y += 17f;
    }

    private void transform(Part p) {
        EditorState st = s.state;
        section("Where it sits");
        float[] pos = st.values(p, Channel.POS);
        float[] v = triple("pos", XYZ, pos, -48f, 48f, -Limits.MAX_OFFSET, Limits.MAX_OFFSET, 2);
        if (!same(v, pos)) st.setValues(p, Channel.POS, v);
        section("Turn");
        float[] rot = st.values(p, Channel.ROT);
        v = triple("rot", XYZ, rot, -180f, 180f, -3600f, 3600f, 1);
        if (!same(v, rot)) st.setValues(p, Channel.ROT, v);
        section("Size");
        float[] scale = st.values(p, Channel.SCALE);
        v = triple("scale", XYZ, scale, 0f, 4f, -Limits.MAX_SCALE, Limits.MAX_SCALE, 3);
        if (!same(v, scale)) st.setValues(p, Channel.SCALE, v);
        section("Pivot (where it turns from)");
        float[] pivot = triple("pivot", XYZ, p.pivot, -24f, 24f, -Limits.MAX_OFFSET, Limits.MAX_OFFSET, 2);
        if (!same(pivot, p.pivot)) {
            p.pivot = pivot;
            st.touch();
        }
    }

    private void extras(Part p) {
        Ui ui = s.ui;
        section("Extras");
        boolean head = ui.toggle("part_head", left, y, width, "Looks at things", p.head);
        y += 15f;
        boolean glow = ui.toggle("part_glow", left, y, width, "Bright in the dark", p.glow);
        y += 15f;
        if (head != p.head || glow != p.glow) {
            p.head = head;
            p.glow = glow;
            s.state.changed();
        }
    }

    private static boolean same(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    // Colour dots, then red green blue sliders.
    int colourEditor(String id, int colour) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        int out = colour;
        float dot = 5f, step = width / 8f;
        for (int i = 0; i < SWATCHES.length; i++) {
            float cx = left + step * (i % 8) + step / 2f, cy = y + 6f + (i / 8) * 13f;
            boolean on = (colour | 0xFF000000) == SWATCHES[i];
            if (on) d.ring(cx, cy, dot + 1.5f, 0.75f, Ui.PINK_SOFT);
            d.circle(cx, cy, dot, SWATCHES[i]);
            d.ring(cx, cy, dot, 0.5f, 0x40FFFFFF);
            if (ui.click(id + "_sw" + i, cx - dot - 1f, cy - dot - 1f, cx + dot + 1f, cy + dot + 1f)) out = (colour & 0xFF000000) | (SWATCHES[i] & 0xFFFFFF);
        }
        y += 28f;
        int r = (out >> 16) & 255, g = (out >> 8) & 255, b = out & 255;
        r = Math.round(ui.row(id + "_r", left, y, width, "R", 10f, r, 0f, 255f, 0f, 255f, 0));
        y += 14f;
        g = Math.round(ui.row(id + "_g", left, y, width, "G", 10f, g, 0f, 255f, 0f, 255f, 0));
        y += 14f;
        b = Math.round(ui.row(id + "_b", left, y, width, "B", 10f, b, 0f, 255f, 0f, 255f, 0));
        y += 14f;
        int a = Math.round(ui.row(id + "_a", left, y, width, "A", 10f, (out >>> 24), 0f, 255f, 0f, 255f, 0));
        y += 16f;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
