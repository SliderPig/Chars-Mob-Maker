package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Channel;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.ClipTools;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Ease;
import com.example.charmobmaker.model.Key;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.PartTree;
import com.example.charmobmaker.model.Track;

import java.util.ArrayList;
import java.util.List;

// The timeline along the bottom and the pose numbers on the right. Change anything on the mob while here and a key is made at the playhead.
final class AnimatePanel {
    private static final String[] MOB_CLIPS = {"idle", "walk", "attack", "hurt", "death"};
    private static final String[] BLOCK_CLIPS = {"idle", "working"};
    private static final String[] ITEM_CLIPS = {"idle"};
    private static final int[] CHANNEL_COLOURS = {Ui.PINK, Ui.BLUE, Ui.GREEN};
    private final EditorScreen s;
    private float y, left, width, contentH = 200f;
    private String renaming = "";

    AnimatePanel(EditorScreen s) {
        this.s = s;
    }

    private void section(String title) {
        Ui ui = s.ui;
        y += 2f;
        ui.text(title, left, y, Ui.PINK_SOFT);
        float tw = ui.width(title) + 5f;
        ui.draw.rect(left + tw, y + 3.5f, left + width, y + 4.25f, 0.4f, 0x28FFFFFF);
        y += 12f;
    }

    // ---------- right panel ----------

    void drawSide(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        EditorState st = s.state;
        ui.panel(x0, y0, x1, y1, 9f);
        float scroll = ui.scroll("anim_scroll", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentH);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
        float start = y;
        Clip c = st.clip();
        section("This clip");
        if (c == null) {
            ui.text("Not made yet", left, y, Ui.TEXT_DIM);
            y += 12f;
            if (ui.accent("clip_make", left, y, width, 14f, "Start this clip")) {
                st.clipOrMake();
                st.commit();
            }
            y += 18f;
        } else {
            clipSettings(c);
        }
        Part p = st.selectedPart();
        if (p == null) {
            section("Pose");
            ui.text("Pick a part to pose it", left, y, Ui.TEXT_DIM);
            y += 12f;
        } else {
            section("Pose of " + ui.fit(p.name, width - 50f));
            pose(p, Channel.ROT, "Turn", -180f, 180f, 1);
            pose(p, Channel.POS, "Move", -32f, 32f, 2);
            pose(p, Channel.SCALE, "Size", 0f, 3f, 3);
        }
        if (st.key != null && c != null) {
            section("Selected key");
            ui.text("At " + Ui.format(st.key.t, 2) + " s, goes on to the next key:", left, y, Ui.TEXT_DIM);
            y += 11f;
            Ease[] eases = Ease.values();
            float cw = width / 3f;
            for (int i = 0; i < eases.length; i++) {
                if (ui.chip("ease_" + eases[i].name().toLowerCase(), left + (i % 3) * cw, y + (i / 3) * 14f, cw - 2f, 12f, eases[i].label, st.key.ease == eases[i])) st.setEase(eases[i]);
            }
            y += 30f;
        }
        contentH = y - start + 8f;
        ui.popClip();
    }

    private void clipSettings(Clip c) {
        Ui ui = s.ui;
        EditorState st = s.state;
        boolean builtIn = isBuiltIn(c.name);
        if (!builtIn) {
            String name = ui.textBox("clip_name", left, y, width, 13f, renaming.isEmpty() ? c.name : renaming, "Clip name");
            if (!name.equals(renaming.isEmpty() ? c.name : renaming)) renaming = name;
            if (!ui.focus.equals("clip_name") && !renaming.isEmpty()) {
                String clean = DesignRules.cleanClipName(renaming);
                if (!clean.equals(c.name) && st.design.clip(clean) == null && !isBuiltIn(clean)) {
                    c.name = clean;
                    st.clipName = clean;
                    st.changed();
                }
                renaming = "";
            }
            y += 16f;
        } else {
            ui.text(describe(c.name), left, y, Ui.TEXT_DIM);
            y += 11f;
        }
        float len = ui.row("clip_len", left, y, width, "Long", 34f, c.length, 0.1f, 5f, Limits.MIN_CLIP, Limits.MAX_CLIP, 2);
        if (len != c.length) {
            ClipTools.setLength(c, len);
            st.time = Math.min(st.time, c.length);
            st.changed();
        }
        y += 14f;
        float speed = ui.row("clip_speed", left, y, width, "Speed", 34f, c.speed, 0.1f, 3f, 0.05f, 10f, 2);
        if (speed != c.speed) {
            c.speed = speed;
            st.touch();
        }
        y += 15f;
        Clip.Loop[] loops = Clip.Loop.values();
        float cw = width / 3f;
        for (int i = 0; i < 3; i++) {
            if (ui.chip("loop_" + i, left + i * cw, y, cw - 2f, 12f, loops[i] == Clip.Loop.LOOP ? "Loops" : loops[i] == Clip.Loop.ONCE ? "Once" : "Holds", c.loop == loops[i])) {
                c.loop = loops[i];
                st.changed();
            }
        }
        y += 16f;
        if (c.name.equals("walk") && ui.button("quick_walk", left, y, width, 13f, "Make a walk for me")) {
            quick(ClipTools.makeWalk(st.design));
        }
        if (c.name.equals("idle") && ui.button("quick_idle", left, y, width, 13f, "Make an idle for me")) {
            quick(ClipTools.makeIdle(st.design));
        }
        if (c.name.equals("walk") || c.name.equals("idle")) y += 16f;
        if (!isBuiltIn(c.name) && ui.button("clip_delete", left, y, width, 13f, "Delete this clip")) {
            st.commit();
            st.design.clips.remove(c);
            st.clipName = "idle";
            st.key = null;
            st.keyTrack = null;
            st.changed();
            st.commit();
        }
        if (!isBuiltIn(c.name)) y += 16f;
        if (isBuiltIn(c.name) && !c.tracks.isEmpty() && ui.button("clip_clear", left, y, width, 13f, "Clear all its keys")) {
            st.commit();
            c.tracks.clear();
            st.key = null;
            st.keyTrack = null;
            st.changed();
            st.commit();
        }
        if (isBuiltIn(c.name) && !c.tracks.isEmpty()) y += 16f;
    }

    private void quick(Clip made) {
        EditorState st = s.state;
        st.commit();
        Clip old = st.design.clip(made.name);
        if (old != null) st.design.clips.remove(old);
        st.design.clips.add(made);
        st.key = null;
        st.keyTrack = null;
        st.changed();
        st.commit();
        if (made.tracks.isEmpty()) st.say("No part names to go on: call parts things like Left leg or Tail", true);
    }

    private String[] builtIns() {
        return switch (s.state.design.type) {
            case BLOCK -> BLOCK_CLIPS;
            case ITEM -> ITEM_CLIPS;
            default -> MOB_CLIPS;
        };
    }

    private boolean isBuiltIn(String n) {
        for (String b : builtIns()) if (b.equals(n)) return true;
        return false;
    }

    private String describe(String clip) {
        if (clip.equals("idle") && s.state.design.type != com.example.charmobmaker.model.Design.Type.MOB) return "Plays all the time";
        return switch (clip) {
            case "working" -> "Plays while the machine runs";
            case "idle" -> "Plays standing around";
            case "walk" -> "Plays while it moves";
            case "attack" -> "Plays when it hits";
            case "hurt" -> "Plays when it gets hit";
            case "death" -> "Plays as it dies, 5 s at most";
            default -> "";
        };
    }

    private void pose(Part p, Channel ch, String label, float softMin, float softMax, int decimals) {
        Ui ui = s.ui;
        EditorState st = s.state;
        boolean keyed = st.hasKeyNow(p, ch);
        int colour = CHANNEL_COLOURS[ch.ordinal()];
        ui.text(label, left, y, ui.mouseIsDown() ? Ui.TEXT_DIM : Ui.TEXT);
        // the dot: filled when there's a key right here
        float dx = left + width - 4f;
        ui.draw.ring(dx, y + 3.5f, 3f, 0.75f, colour);
        if (keyed) ui.draw.circle(dx, y + 3.5f, 2.2f, colour);
        if (ui.click("key_" + ch.name().toLowerCase(), dx - 5f, y - 1f, dx + 5f, y + 8f)) {
            if (keyed) removeKeyAt(p, ch);
            else st.setValues(p, ch, st.values(p, ch));
            st.commit();
        }
        y += 10f;
        float[] v = st.values(p, ch);
        float[] out = v.clone();
        String[] names = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            float n = ui.row("pose_" + ch.name() + i, left, y, width, names[i], 10f, v[i], Math.min(softMin, v[i]), Math.max(softMax, v[i]), -3600f, 3600f, decimals);
            if (n != v[i]) out[i] = n;
            y += 14f;
        }
        if (out[0] != v[0] || out[1] != v[1] || out[2] != v[2]) {
            st.playing = false;
            st.setValues(p, ch, out);
        }
        y += 2f;
    }

    private void removeKeyAt(Part p, Channel ch) {
        EditorState st = s.state;
        Clip c = st.clip();
        Track t = c == null ? null : c.track(p.id, ch, false);
        Key k = t == null ? null : t.at(ClipTools.snap(st.time));
        if (k == null) return;
        ClipTools.removeKey(c, t, k);
        if (st.key == k) {
            st.key = null;
            st.keyTrack = null;
        }
        st.changed();
    }

    // ---------- the timeline ----------

    void drawTimeline(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        ui.panel(x0, y0, x1, y1, 9f);
        clipChips(x0 + 6f, y0 + 5f, x1 - 186f);
        transport(x1 - 182f, y0 + 4f, x1 - 6f);
        Clip c = st.clip();
        float labelW = Math.min(110f, (x1 - x0) * 0.17f);
        float laneX0 = x0 + 6f + labelW, laneX1 = x1 - 10f;
        float rulerY = y0 + 24f, rulerH = 11f;
        float listTop = rulerY + rulerH + 1f, listBottom = y1 - 5f;
        ui.well(x0 + 5f, rulerY, x1 - 5f, y1 - 4f, 6f);
        float length = c == null ? EditorState.defaultLength(st.clipName) : c.length;
        ruler(laneX0, laneX1, rulerY, rulerH, length);
        List<RowInfo> rows = rows(c);
        float scroll = ui.scroll("time_scroll", x0 + 5f, listTop, x1 - 5f, listBottom, totalHeight(rows));
        ui.pushClip(x0 + 5f, listTop, x1 - 5f, listBottom);
        float ry = listTop - scroll;
        for (RowInfo r : rows) {
            float h = r.height();
            if (ry + h >= listTop && ry <= listBottom) drawRow(r, c, x0 + 7f, laneX0, laneX1, ry, h, length);
            ry += h;
        }
        ui.popClip();
        if (rows.isEmpty()) ui.textCentre("Pick a part on the left, then press Key to pose it at this moment", (laneX0 + laneX1) / 2f, listTop + 16f, Ui.TEXT_DIM);
        // the playhead over everything
        float px = laneX0 + (laneX1 - laneX0) * (length > 0f ? st.time / length : 0f);
        d.rect(px - 0.5f, rulerY + 2f, px + 0.5f, y1 - 5f, 0.5f, Ui.PINK);
        d.circle(px, rulerY + 5f, 3.5f, Ui.PINK);
        d.circle(px, rulerY + 5f, 1.5f, 0xFFFFFFFF);
    }

    private record RowInfo(Part part, boolean open, int depth) {
        float height() {
            return open ? 40f : 13f;
        }
    }

    private float totalHeight(List<RowInfo> rows) {
        float t = 3f;
        for (RowInfo r : rows) t += r.height();
        return t;
    }

    // Parts with keys, plus the one you've got selected, in tree order.
    private List<RowInfo> rows(Clip c) {
        EditorState st = s.state;
        List<RowInfo> out = new ArrayList<>();
        for (PartTree.Row r : PartTree.rows(st.design, null)) {
            Part p = r.part();
            boolean has = false;
            if (c != null) for (Track t : c.tracks) if (t.part.equals(p.id)) has = true;
            boolean sel = p.id.equals(st.selected);
            if (has || sel) out.add(new RowInfo(p, sel, r.depth()));
        }
        return out;
    }

    private void drawRow(RowInfo r, Clip c, float labelX, float x0, float x1, float y, float h, float length) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        Part p = r.part();
        boolean selected = p.id.equals(st.selected);
        if (selected) d.rect(labelX - 1f, y + 0.5f, x1 + 3f, y + h - 0.5f, 5f, 0x2CFF8FC7);
        else if (ui.over(labelX - 1f, y, x1 + 3f, y + h)) d.rect(labelX - 1f, y + 0.5f, x1 + 3f, y + h - 0.5f, 5f, 0x18FFFFFF);
        float indent = 5f * Math.min(4, r.depth());
        ui.text(ui.fit(p.name, x0 - labelX - 8f - indent), labelX + indent + 2f, y + 2.5f, selected ? 0xFFFFFFFF : Ui.TEXT_DIM);
        if (ui.click("trow_" + p.id, labelX - 1f, y, x0 - 2f, y + h)) {
            st.selected = p.id;
            st.key = null;
            st.keyTrack = null;
        }
        if (r.open()) {
            Channel[] chs = Channel.values();
            for (int i = 0; i < 3; i++) {
                float ly = y + 13f + i * 9f;
                ui.textRight(chs[i].label, x0 - 6f, ly, chLabelColour(i));
                lane(p, c, chs[i], x0, x1, ly + 3.5f, length);
            }
        } else if (c != null) {
            // everything on one line for a part you're not working on
            for (Track t : c.tracks) {
                if (!t.part.equals(p.id)) continue;
                for (Key k : t.keys) {
                    float kx = x0 + (x1 - x0) * k.t / length;
                    d.circle(kx, y + 6.5f, 2f, Draw.alpha(CHANNEL_COLOURS[t.channel.ordinal()], 0.7f));
                }
            }
            if (ui.click("tline_" + p.id, x0, y, x1 + 3f, y + h)) {
                st.selected = p.id;
                st.key = null;
                st.keyTrack = null;
                ui.active = "row";
            }
        }
    }

    private static int chLabelColour(int i) {
        return Draw.alpha(CHANNEL_COLOURS[i], 0.8f);
    }

    private void lane(Part p, Clip c, Channel ch, float x0, float x1, float y, float length) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        d.rect(x0, y - 0.25f, x1, y + 0.25f, 0.25f, 0x18FFFFFF);
        Track t = c == null ? null : c.track(p.id, ch, false);
        String laneId = "lane_" + ch.name().toLowerCase();
        boolean clickedKey = false;
        if (t != null) {
            for (Key k : new ArrayList<>(t.keys)) {
                float kx = x0 + (x1 - x0) * k.t / length;
                boolean sel = st.key == k;
                boolean hover = ui.over(kx - 3.5f, y - 4f, kx + 3.5f, y + 4f);
                int colour = CHANNEL_COLOURS[ch.ordinal()];
                if (sel) d.ring(kx, y, 4.5f, 0.75f, 0xFFFFFFFF);
                d.circle(kx, y, hover ? 3.4f : 2.8f, hover ? 0xFFFFFFFF : colour);
                String kid = "k_" + ch.name() + "_" + Math.round(k.t * 1000f);
                if (ui.click(kid, kx - 3.5f, y - 4f, kx + 3.5f, y + 4f)) {
                    st.selectKey(t, k);
                    st.time = k.t;
                    st.playing = false;
                    clickedKey = true;
                    draggingKey = k;
                    draggingTrack = t;
                }
                if (ui.rightPressed && hover) {
                    ClipTools.removeKey(c, t, k);
                    if (st.key == k) {
                        st.key = null;
                        st.keyTrack = null;
                    }
                    st.changed();
                    ui.rightPressed = false;
                    return;
                }
            }
        }
        // dragging a key along
        if (draggingKey != null && draggingTrack == t && ui.mouseIsDown() && !clickedKey) {
            float time = ClipTools.snap(Math.max(0f, Math.min(length, (ui.mx - x0) / (x1 - x0) * length)));
            if (Math.abs(time - draggingKey.t) > 0.001f) {
                ClipTools.moveKey(c, t, draggingKey, time);
                st.time = time;
                st.changed();
            }
        }
        if (draggingKey != null && !ui.mouseIsDown()) {
            draggingKey = null;
            draggingTrack = null;
        }
        // clicking the empty line moves the playhead; twice makes a key
        if (ui.click(laneId, x0, y - 4.5f, x1 + 3f, y + 4.5f)) {
            boolean twice = ui.doubleClick;
            st.time = ClipTools.snap(Math.max(0f, Math.min(length, (ui.mx - x0) / (x1 - x0) * length)));
            st.playing = false;
            if (twice) {
                st.setValues(p, ch, st.values(p, ch));
                st.commit();
            }
        }
    }

    private Key draggingKey;
    private Track draggingTrack;

    private void ruler(float x0, float x1, float y, float h, float length) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        float seconds = length;
        float major = seconds > 20f ? 5f : seconds > 8f ? 2f : seconds > 3f ? 1f : 0.5f;
        float minor = major / 5f;
        int count = (int) (seconds / minor + 0.001f);
        for (int i = 0; i <= count; i++) {
            float t = i * minor;
            float tx = x0 + (x1 - x0) * t / seconds;
            boolean big = i % 5 == 0;
            d.rect(tx - 0.25f, y + (big ? 6f : 8.5f), tx + 0.25f, y + h, 0f, big ? 0x70FFFFFF : 0x30FFFFFF);
            if (big && tx + 12f < x1) ui.text(Ui.format(t, 1), tx + 2f, y + 1.5f, 0xFF8F7C93);
        }
        ui.rects.put("ruler", new float[] {x0, y, x1, y + h});
        if (ui.pressed && ui.over(x0 - 3f, y, x1 + 3f, y + h)) {
            ui.pressed = false;
            ui.active = "scrub";
            st.playing = false;
        }
        if (ui.active.equals("scrub") && ui.mouseIsDown()) st.time = ClipTools.snap(Math.max(0f, Math.min(length, (ui.mx - x0) / (x1 - x0) * length)));
    }

    private void clipChips(float x, float y, float limit) {
        Ui ui = s.ui;
        EditorState st = s.state;
        List<String> names = new ArrayList<>(List.of(builtIns()));
        for (Clip c : st.design.clips) if (!names.contains(c.name)) names.add(c.name);
        float cx = x;
        for (String n : names) {
            float w = Math.max(30f, ui.width(n) + 12f);
            if (cx + w > limit - 20f) break;
            boolean exists = st.design.clip(n) != null;
            if (ui.chip("clip_" + n, cx, y, w, 14f, n, st.clipName.equals(n))) {
                st.commit();
                st.clipName = n;
                st.time = 0f;
                st.playing = false;
                st.key = null;
                st.keyTrack = null;
            }
            if (exists && !st.clipName.equals(n)) ui.draw.circle(cx + w - 5f, y + 3.5f, 1.3f, Ui.PINK);
            cx += w + 2f;
        }
        if (ui.chip("clip_add", cx, y, 16f, 14f, "+", false)) {
            st.commit();
            String name = "clip_1";
            for (int i = 1; st.design.clip(name) != null; i++) name = "clip_" + i;
            if (st.design.clips.size() < Limits.MAX_CLIPS) {
                st.design.clips.add(new Clip(name, 1f, Clip.Loop.ONCE));
                st.clipName = name;
                st.time = 0f;
                st.changed();
                st.commit();
            }
        }
    }

    private void transport(float x, float y, float x1) {
        Ui ui = s.ui;
        Draw d = ui.draw;
        EditorState st = s.state;
        float bw = 18f, bh = 16f;
        // to the start
        if (ui.button("t_start", x, y, bw, bh, "")) {
            st.time = 0f;
            st.playing = false;
        }
        d.rect(x + 6f, y + 5f, x + 7f, y + 11f, 0.5f, Ui.TEXT);
        d.triangle(x + 13f, y + 5f, x + 13f, y + 11f, x + 8f, y + 8f, Ui.TEXT);
        // play or pause
        float px = x + bw + 2f;
        if (ui.accent("t_play", px, y, bw + 4f, bh, "")) {
            if (st.clip() != null) {
                if (!st.playing && st.clip().loop != Clip.Loop.LOOP && st.time >= st.clip().length) st.time = 0f;
                st.playing = !st.playing;
            }
        }
        float mid = px + (bw + 4f) / 2f;
        if (st.playing) {
            d.rect(mid - 3f, y + 4.5f, mid - 1f, y + 11.5f, 0.5f, Ui.TEXT_DARK);
            d.rect(mid + 1f, y + 4.5f, mid + 3f, y + 11.5f, 0.5f, Ui.TEXT_DARK);
        } else {
            d.triangle(mid - 3f, y + 4f, mid - 3f, y + 12f, mid + 4f, y + 8f, Ui.TEXT_DARK);
        }
        float bx = px + bw + 6f;
        boolean hasPart = st.selectedPart() != null;
        if (ui.button("t_key", bx, y, 26f, bh, "Key", false, hasPart)) addKey();
        if (ui.over(bx, y, bx + 26f, y + bh)) ui.tooltip("Sets keys for this part at the playhead (K)");
        bx += 28f;
        if (ui.button("t_copy", bx, y, 32f, bh, "Copy", false, hasPart && st.clip() != null)) copy();
        bx += 34f;
        if (ui.button("t_paste", bx, y, 36f, bh, "Paste", false, hasPart && st.board != null)) paste();
        bx += 38f;
        Part other = hasPart ? ClipTools.opposite(st.design, st.selectedPart()) : null;
        if (ui.button("t_mirror", bx, y, 28f, bh, "Flip", false, other != null && st.clip() != null)) mirrorOnto(other);
        if (ui.over(bx, y, bx + 28f, y + bh)) ui.tooltip("Copies this part's keys to the part on the other side, half a cycle later. That's how you get walking legs.");
    }

    void addKey() {
        EditorState st = s.state;
        Part p = st.selectedPart();
        if (p == null) return;
        st.playing = false;
        Clip c = st.clipOrMake();
        if (c == null) return;
        for (Channel ch : Channel.values()) {
            if (ch == Channel.SCALE && c.track(p.id, Channel.SCALE, false) == null) continue;
            if (ch == Channel.POS && c.track(p.id, Channel.POS, false) == null) continue;
            st.setValues(p, ch, st.values(p, ch));
        }
        st.commit();
    }

    void deleteKey() {
        EditorState st = s.state;
        Clip c = st.clip();
        if (c == null || st.key == null || st.keyTrack == null) return;
        st.commit();
        ClipTools.removeKey(c, st.keyTrack, st.key);
        st.key = null;
        st.keyTrack = null;
        st.changed();
        st.commit();
    }

    void copy() {
        EditorState st = s.state;
        Part p = st.selectedPart();
        Clip c = st.clip();
        if (p == null || c == null) return;
        st.board = ClipTools.copyPart(c, p.id, 0f);
        st.say("Copied the keys of " + p.name, false);
    }

    void paste() {
        EditorState st = s.state;
        Part p = st.selectedPart();
        if (p == null || st.board == null) return;
        Clip c = st.clipOrMake();
        if (c == null) return;
        st.commit();
        ClipTools.paste(c, p.id, st.board, 0f);
        st.changed();
        st.commit();
    }

    private void mirrorOnto(Part other) {
        EditorState st = s.state;
        Clip c = st.clip();
        Part p = st.selectedPart();
        if (c == null || p == null) return;
        st.commit();
        ClipTools.mirrorOnto(c, p.id, other.id, c.loop == Clip.Loop.LOOP ? c.length / 2f : 0f);
        st.changed();
        st.commit();
        st.say("Copied to " + other.name, false);
    }
}
