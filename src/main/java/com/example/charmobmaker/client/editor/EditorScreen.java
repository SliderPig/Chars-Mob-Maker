package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.render.Compiled;
import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.PartTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

// The Mob Maker's main screen: the 3D view fills everything, with soft floating panels on top.
public final class EditorScreen extends Screen {
    private static final ResourceLocation CAT = new ResourceLocation("charmobmaker", "textures/gui/cat.png");

    public final Ui ui = new Ui();
    public final EditorState state = EditorState.get();
    final Viewport viewport = new Viewport();
    final PartsPanel parts = new PartsPanel(this);
    final BuildPanel build = new BuildPanel(this);
    final StatsPanel stats = new StatsPanel(this);
    final AnimatePanel animate = new AnimatePanel(this);
    public Popup popup;
    private boolean aimed;
    private long lastNanos = System.nanoTime();

    public interface Popup {
        void draw(EditorScreen s);
    }

    public EditorScreen() {
        super(Component.literal("Mob Maker"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        ui.begin(g, partial);
        Draw d = ui.draw;
        float w = ui.width, h = ui.height;
        d.grad(0f, 0f, w, h, 0f, Ui.BG_TOP, Ui.BG_BOTTOM);
        pool(d, w * 0.5f, h * 0.5f, w * 0.62f, h * 0.72f, 0x22FF8FC7);

        float leftW = Math.max(132f, Math.min(176f, w * 0.215f));
        float rightW = Math.max(150f, Math.min(196f, w * 0.245f));
        float top = 6f, barH = 22f;
        float bottom = state.tab == EditorState.Tab.ANIMATE ? h - Math.min(150f, h * 0.36f) : h - 6f;
        float freeCx = (leftW + 12f + w - rightW - 12f) / 2f;
        float freeCy = (top + barH + bottom) / 2f + 6f;
        tickPlayback();
        Compiled c = state.compiled();
        if (!aimed) {
            viewport.aimAt(c);
            aimed = true;
        }
        viewport.setup(ui, freeCx, freeCy, bottom - top - barH);
        pose(c);
        viewport.backdrop(ui);
        int glow = state.tab == EditorState.Tab.STATS ? -1 : c.rig.indexOf(state.selected);
        if (glow >= 0 && !c.hasMesh(glow)) glow = -1;
        viewport.render3D(ui, state, glow);
        if (state.showHitbox || state.tab == EditorState.Tab.STATS) viewport.hitbox(ui, state.design.stats, 0xB08CF0D8);
        viewport.selection(ui, state);
        ui.flush();

        ui.blocked = popup != null;
        topBar(leftW, rightW, top, barH);
        float panelTop = top + barH + 6f;
        switch (state.tab) {
            case BUILD -> {
                parts.draw(6f, panelTop, 6f + leftW, h - 6f);
                build.draw(w - rightW - 6f, panelTop, w - 6f, h - 6f);
                toolChips(freeCx, panelTop + 2f);
            }
            case STATS -> {
                stats.drawLeft(6f, panelTop, 6f + leftW, h - 6f);
                stats.drawRight(w - rightW - 6f, panelTop, w - 6f, h - 6f);
            }
            case ANIMATE -> {
                parts.draw(6f, panelTop, 6f + leftW, bottom - 5f);
                animate.drawSide(w - rightW - 6f, panelTop, w - 6f, bottom - 5f);
                animate.drawTimeline(6f, bottom, w - 6f, h - 6f);
                toolChips(freeCx, panelTop + 2f);
            }
        }
        viewChips(freeCx, bottom);
        ui.flush();
        ui.blocked = false;
        if (popup != null) {
            popup.draw(this);
            ui.flush();
        }
        viewport.input(ui, state);
        drawToast();
        ui.end();
        if (!ui.mouseIsDown() && !ui.typing()) state.commit();
    }

    // A soft round pool of light, for depth behind everything.
    private static void pool(Draw d, float cx, float cy, float rx, float ry, int colour) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n, b = Math.PI * 2.0 * (i + 1) / n;
            d.fan(cx, cy, cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry, cx + (float) Math.cos(b) * rx, cy + (float) Math.sin(b) * ry, colour, colour & 0xFFFFFF);
        }
    }

    // Works out the pose shown: base shape, an idle playing in Build if there is one, the clip at the playhead in Animate.
    private void pose(Compiled c) {
        c.out.clear();
        if (state.tab == EditorState.Tab.ANIMATE) {
            Clip clip = state.clip();
            var rc = clip == null ? null : c.rig.clip(clip.name);
            if (rc != null) rc.sample(state.time, c.out);
        }
        c.rig.pose(c.out, 0f, 0f, c.world);
    }

    private void tickPlayback() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (!state.playing || state.tab != EditorState.Tab.ANIMATE) return;
        Clip clip = state.clip();
        if (clip == null) {
            state.playing = false;
            return;
        }
        float next = state.time + dt * clip.speed;
        if (clip.loop == Clip.Loop.LOOP) state.time = clip.wrapTime(next);
        else if (next >= clip.length) {
            state.time = clip.length;
            state.playing = false;
        } else state.time = next;
    }

    private void topBar(float leftW, float rightW, float y, float h) {
        float w = ui.width;
        Draw d = ui.draw;
        // name pill with the black cat
        float x0 = 6f, x1 = 6f + leftW;
        ui.panel(x0, y, x1, y + h, h / 2f);
        float cat = h - 4f;
        ui.later(() -> {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            ui.g.pose().pushPose();
            ui.g.pose().translate(x0 + 5f, y + 2f, 0f);
            ui.g.pose().scale(cat / 16f, cat / 16f, 1f);
            ui.g.blit(CAT, 0, 0, 0f, 0f, 16, 16, 16, 16);
            ui.g.pose().popPose();
        });
        String name = ui.textBox("name", x0 + cat + 8f, y + 3f, leftW - cat - 13f - (state.unsaved() ? 6f : 0f), h - 6f, state.design.name, "Name your mob");
        if (!name.equals(state.design.name)) {
            state.design.name = DesignRules.cleanName(name, com.example.charmobmaker.model.Limits.MAX_NAME, "");
            state.touch();
        }
        if (state.unsaved()) d.circle(x1 - 7f, y + h / 2f, 2f, Ui.PINK);

        // Build / Stats / Animate
        float tabW = 56f;
        float tabsX = (leftW + 12f + w - rightW - 12f) / 2f - tabW * 1.5f - 4f;
        ui.panel(tabsX, y, tabsX + tabW * 3f + 8f, y + h, h / 2f);
        String[] names = {"Build", "Stats", "Animate"};
        EditorState.Tab[] tabs = EditorState.Tab.values();
        for (int i = 0; i < 3; i++) {
            if (ui.chip("tab_" + names[i].toLowerCase(), tabsX + 4f + i * tabW, y + 3f, tabW, h - 6f, names[i], state.tab == tabs[i])) {
                state.commit();
                state.tab = tabs[i];
                state.playing = false;
            }
        }

        // the right hand buttons
        float gap = 3f, bh = h - 6f, pad = 4f;
        float x = w - 6f;
        // narrow windows squeeze these rather than running into the tabs
        float room = x - (tabsX + tabW * 3f + 8f) - 4f;
        float bw = Math.max(26f, Math.min(38f, (room - 22f - pad * 2f - gap * 6f) / 6f));
        float width = 6f * (bw + gap) + 22f + pad * 2f;
        ui.panel(x - width, y, x, y + h, h / 2f);
        float bx = x - width + pad;
        if (ui.button("undo", bx, y + 3f, bw, bh, "Undo", false, state.history.canUndo())) state.undo();
        bx += bw + gap;
        if (ui.button("redo", bx, y + 3f, bw, bh, "Redo", false, state.history.canRedo())) state.redo();
        bx += bw + gap;
        if (ui.button("load", bx, y + 3f, bw, bh, "Mobs")) popup = new LoadPopup(this);
        bx += bw + gap;
        if (ui.accent("save", bx, y + 3f, bw, bh, "Save")) state.save();
        bx += bw + gap;
        boolean known = !state.design.id.isEmpty() && Designs.CLIENT.containsKey(state.design.id);
        if (ui.button("spawn", bx, y + 3f, bw, bh, "Spawn", false, known)) Net.action(Net.Act.SPAWN, state.design.id, 1);
        if (ui.over(bx, y + 3f, bx + bw, y + 3f + bh)) ui.tooltip("Puts one in the world next to you");
        else if (!known && ui.mx >= bx && ui.mx < bx + bw && ui.my >= y && ui.my < y + h && !ui.blocked) ui.tooltip("Save the mob first");
        bx += bw + gap;
        if (ui.button("egg", bx, y + 3f, bw, bh, "Egg", false, known)) Net.action(Net.Act.EGG, state.design.id, 1);
        if (ui.over(bx, y + 3f, bx + bw, y + 3f + bh)) ui.tooltip("Gives you a spawn egg of this mob");
        bx += bw + gap;
        if (ui.button("close", bx, y + 3f, 22f, bh, "X")) onClose();
    }

    // Move, Turn, Size, floating above the mob.
    private void toolChips(float cx, float y) {
        if (state.selectedPart() == null) return;
        float cw = 40f, ch = 14f;
        float x = cx - cw * 1.5f - 3f;
        ui.panel(x, y + 2f, x + cw * 3f + 6f, y + 2f + ch + 6f, (ch + 6f) / 2f);
        String[] names = {"Move", "Turn", "Size"};
        EditorState.Tool[] tools = EditorState.Tool.values();
        for (int i = 0; i < 3; i++) {
            if (ui.chip("tool_" + names[i].toLowerCase(), x + 3f + i * cw, y + 5f, cw, ch, names[i], state.tool == tools[i])) state.tool = tools[i];
        }
    }

    private void viewChips(float cx, float bottom) {
        float cw = 40f, ch = 13f, n = 5f;
        float x = cx - cw * n / 2f - 3f, y = bottom - ch - 12f;
        ui.panel(x, y - 3f, x + cw * n + 6f, y + ch + 3f, (ch + 6f) / 2f);
        if (ui.chip("view_front", x + 3f, y, cw, ch, "Front", false)) viewport.view(0f, 8f);
        if (ui.chip("view_side", x + 3f + cw, y, cw, ch, "Side", false)) viewport.view(-90f, 8f);
        if (ui.chip("view_top", x + 3f + cw * 2f, y, cw, ch, "Top", false)) viewport.view(0f, 80f);
        if (ui.chip("view_fit", x + 3f + cw * 3f, y, cw, ch, "Fit", false)) viewport.aimAt(state.compiled());
        if (ui.chip("view_box", x + 3f + cw * 4f, y, cw, ch, "Hitbox", state.showHitbox)) state.showHitbox = !state.showHitbox;
    }

    private void drawToast() {
        long now = System.currentTimeMillis();
        if (state.toast.isEmpty() || now > state.toastUntil) return;
        float left = (state.toastUntil - now) / 400f;
        float a = Math.min(1f, left);
        float tw = ui.width(state.toast) + 22f;
        float x = (ui.width - tw) / 2f, y = 34f;
        ui.flush();
        int colour = state.toastBad ? 0xFFFF6B81 : Ui.PINK;
        ui.draw.halo(x, y, x + tw, y + 16f, 8f, Draw.alpha(colour, 0.25f * a), 8f);
        ui.draw.rect(x, y, x + tw, y + 16f, 8f, Draw.alpha(0xFF241630, a));
        ui.draw.border(x, y, x + tw, y + 16f, 8f, 0.75f, Draw.alpha(colour, a));
        ui.text(state.toast, x + 11f, y + 4f, Draw.alpha(0xFFFFFFFF, Math.max(0.1f, a)));
    }

    // ---------- things other panels ask for ----------

    public void selectPart(String id) {
        state.selected = id;
    }

    void addParts(java.util.List<Part> list) {
        state.commit();
        java.util.Map<String, String> ids = new java.util.HashMap<>();
        Part first = null;
        for (Part p : list) {
            String parent = ids.getOrDefault(p.parent, p.parent);
            String tmp = p.id;
            Part added = PartTree.add(state.design, p, parent);
            if (added == null) {
                state.say("A mob can have " + com.example.charmobmaker.model.Limits.MAX_PARTS + " parts at most.", true);
                break;
            }
            ids.put(tmp, added.id);
            if (first == null) first = added;
        }
        if (first != null) {
            state.selected = first.id;
            state.lastAdded = first.id;
        }
        state.changed();
        state.commit();
        viewport.backOffFor(state.compiled());
    }

    // ---------- mouse and keys ----------

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        ui.mousePressed(button);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        ui.mouseReleased(button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        ui.mouseWheel(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ui.typing()) return ui.key(key, mods);
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (popup != null) {
                popup = null;
                return true;
            }
            onClose();
            return true;
        }
        if (popup != null) return true;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) state.redo();
            else state.undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            state.redo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            state.save();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_D) {
            parts.duplicate();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (state.tab == EditorState.Tab.ANIMATE && state.key != null) animate.deleteKey();
            else parts.delete();
            return true;
        }
        if (state.tab == EditorState.Tab.ANIMATE) {
            if (key == GLFW.GLFW_KEY_SPACE) {
                state.playing = !state.playing;
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_C) {
                animate.copy();
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_V) {
                animate.paste();
                return true;
            }
            if (key == GLFW.GLFW_KEY_K) {
                animate.addKey();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_1) state.tool = EditorState.Tool.MOVE;
        if (key == GLFW.GLFW_KEY_2) state.tool = EditorState.Tool.TURN;
        if (key == GLFW.GLFW_KEY_3) state.tool = EditorState.Tool.SIZE;
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return ui.typed(c);
    }

    @Override
    public void onClose() {
        state.commit();
        super.onClose();
    }

    @Override
    public void removed() {
        state.commit();
    }

    public void copyToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(DesignJson.write(state.design));
        state.say("The whole mob is copied, as text you can share", false);
    }

    // Where the selected part shows on screen, for the test walk-through.
    public float[] screenOfSelected() {
        return viewport.screenOf(state, state.selected);
    }
}
