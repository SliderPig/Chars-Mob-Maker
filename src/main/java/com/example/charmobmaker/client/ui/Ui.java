package com.example.charmobmaker.client.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// The little widget kit behind the editor: every frame the screen simply asks for its buttons and sliders and gets the answers back.
// All in layout units that don't depend on the game's GUI scale, so the editor always has room, drawn soft and round.
public final class Ui {
    public static final int BG_TOP = 0xFF231528, BG_BOTTOM = 0xFF120A17;
    public static final int PANEL_TOP = 0xF2352240, PANEL_BOTTOM = 0xF2281A32;
    public static final int RAISED = 0xFF463056, RAISED_HOVER = 0xFF5A3E6C, WELL = 0xFF1C1124;
    public static final int PINK = 0xFFFF8FC7, PINK_SOFT = 0xFFFFB6DC, PINK_DEEP = 0xFFD6559C;
    public static final int TEXT = 0xFFF8EAF3, TEXT_DIM = 0xFFB9A0B6, TEXT_DARK = 0xFF2A1420;
    public static final int RED = 0xFFFF6B81, GREEN = 0xFF8CE07A, BLUE = 0xFF6FB5FF;

    public final Draw draw = new Draw();
    public GuiGraphics g;
    public Font font;
    public float scale = 2f;     // real pixels per layout unit
    public float width, height;  // the window in layout units
    public float mx, my;
    public float partial;
    public boolean pressed, released, down, rightPressed, doubleClick;
    public float wheel;
    public String active = "";   // the widget holding the mouse
    public String focus = "";    // the text box being typed in
    public boolean blocked;      // a popup is on top: nothing underneath reacts
    private float[] clip;
    private final Deque<float[]> clipStack = new ArrayDeque<>();
    private final List<float[]> panels = new ArrayList<>();
    private List<float[]> lastPanels = new ArrayList<>();
    private final Deque<Input> inputs = new ArrayDeque<>();
    private final Deque<Key> keys = new ArrayDeque<>();
    private final List<Runnable> afterShapes = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private boolean hadItems;
    private String tooltip;
    private long lastClick;
    private float lastClickX, lastClickY;
    // where each widget was last drawn, so the test walk-through can press them like a person
    public final Map<String, float[]> rects = new HashMap<>();
    public Float testX, testY;
    private String editing = "";
    private String buffer = "";
    private int caret;

    private record Input(int kind, int button) {}

    public record Key(int code, int mods, char ch) {}

    private record Text(String s, float x, float y, int colour) {}

    // ---------- the frame ----------

    public void begin(GuiGraphics g, float partial) {
        this.g = g;
        this.partial = partial;
        Minecraft mc = Minecraft.getInstance();
        font = mc.font;
        Window w = mc.getWindow();
        // whole steps only: the Minecraft font is sharp at a whole number of real pixels and nowhere else
        scale = w.getHeight() >= 1350 ? (float) Math.floor(w.getHeight() / 450.0) : w.getHeight() >= 640 ? 2f : 1f;
        width = w.getWidth() / scale;
        height = w.getHeight() / scale;
        mx = testX != null ? testX : (float) (mc.mouseHandler.xpos() / scale);
        my = testY != null ? testY : (float) (mc.mouseHandler.ypos() / scale);
        pressed = released = rightPressed = doubleClick = false;
        Input in = inputs.poll();
        if (in != null) {
            if (in.kind() == 0) {
                if (in.button() == 0) {
                    pressed = true;
                    down = true;
                    long now = System.currentTimeMillis();
                    doubleClick = now - lastClick < 300 && Math.abs(mx - lastClickX) < 3 && Math.abs(my - lastClickY) < 3;
                    lastClick = now;
                    lastClickX = mx;
                    lastClickY = my;
                } else {
                    rightPressed = true;
                }
            } else if (in.kind() == 1 && in.button() == 0) {
                released = true;
                down = false;
            }
        }
        if (pressed && !focus.isEmpty() && focusRect != null && (mx < focusRect[0] || my < focusRect[1] || mx >= focusRect[2] || my >= focusRect[3])) commitNow = true;
        double gui = w.getGuiScale();
        g.pose().pushPose();
        g.pose().scale((float) (scale / gui), (float) (scale / gui), 1f);
        draw.begin(g, 1f / scale);
        lastPanels = new ArrayList<>(panels);
        panels.clear();
        tooltip = null;
        clip = null;
        clipStack.clear();
        rects.clear();
        RenderSystem.disableCull();
    }

    public void end() {
        if (!down) active = "";
        if (!focus.isEmpty() && !rects.containsKey(focus)) {
            focus = "";
            commitNow = false;
        }
        drawTooltip();
        flush();
        g.pose().popPose();
        RenderSystem.enableCull();
        wheel = 0f;
    }

    // Draws what has been asked for so far: shapes first, then icons, then text on top.
    public void flush() {
        RenderSystem.disableCull();
        g.flush();
        if (!afterShapes.isEmpty()) {
            List<Runnable> list = new ArrayList<>(afterShapes);
            afterShapes.clear();
            for (Runnable r : list) r.run();
            g.flush();
        }
        if (hadItems) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableCull();
            hadItems = false;
        }
        if (!texts.isEmpty()) {
            List<Text> list = new ArrayList<>(texts);
            texts.clear();
            g.drawManaged(() -> {
                for (Text t : list) {
                    g.pose().pushPose();
                    // on a real pixel, or the letters come out uneven
                    g.pose().translate(Math.round(t.x() * scale) / scale, Math.round(t.y() * scale) / scale, 0f);
                    g.drawString(font, t.s(), 0, 0, t.colour(), false);
                    g.pose().popPose();
                }
            });
            g.flush();
        }
    }

    public void pushClip(float x0, float y0, float x1, float y1) {
        flush();
        float[] c = clip == null ? new float[] {x0, y0, x1, y1} : new float[] {Math.max(x0, clip[0]), Math.max(y0, clip[1]), Math.min(x1, clip[2]), Math.min(y1, clip[3])};
        clipStack.push(clip == null ? new float[0] : clip);
        clip = c;
        applyClip();
    }

    public void popClip() {
        flush();
        float[] prev = clipStack.pop();
        clip = prev.length == 0 ? null : prev;
        applyClip();
    }

    private void applyClip() {
        if (clip == null) {
            RenderSystem.disableScissor();
            return;
        }
        int wh = Minecraft.getInstance().getWindow().getHeight();
        int x = Math.round(clip[0] * scale), y = Math.round(clip[1] * scale), x1 = Math.round(clip[2] * scale), y1 = Math.round(clip[3] * scale);
        RenderSystem.enableScissor(x, wh - y1, Math.max(0, x1 - x), Math.max(0, y1 - y));
    }

    // Marks an area as belonging to a panel, so clicks there don't reach the 3D view.
    public void claim(float x0, float y0, float x1, float y1) {
        panels.add(new float[] {x0, y0, x1, y1});
    }

    public boolean overPanels() {
        for (float[] p : lastPanels) if (mx >= p[0] && my >= p[1] && mx < p[2] && my < p[3]) return true;
        for (float[] p : panels) if (mx >= p[0] && my >= p[1] && mx < p[2] && my < p[3]) return true;
        return false;
    }

    // ---------- input from the screen ----------

    public void mousePressed(int button) {
        inputs.add(new Input(0, button));
    }

    public void mouseReleased(int button) {
        inputs.add(new Input(1, button));
    }

    public void mouseWheel(double delta) {
        wheel += (float) delta;
    }

    public boolean mouseIsDown() {
        return down;
    }

    public boolean typing() {
        return !focus.isEmpty();
    }

    // true when a text box used it
    public boolean key(int code, int mods) {
        if (focus.isEmpty()) return false;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (code) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                focus = "";
                editing = "";
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitNow = true;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    buffer = buffer.substring(0, caret - 1) + buffer.substring(caret);
                    caret--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < buffer.length()) buffer = buffer.substring(0, caret) + buffer.substring(caret + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                caret = Math.max(0, caret - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                caret = Math.min(buffer.length(), caret + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caret = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                caret = buffer.length();
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    selectAll = true;
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(buffer);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String paste = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " ");
                    if (selectAll) {
                        buffer = "";
                        caret = 0;
                        selectAll = false;
                    }
                    buffer = buffer.substring(0, caret) + paste + buffer.substring(caret);
                    caret += paste.length();
                    return true;
                }
            }
            default -> {
            }
        }
        return true;
    }

    private boolean commitNow, selectAll;
    private float[] focusRect;

    public boolean typed(char c) {
        if (focus.isEmpty() || c < 32 || c == 127 || c == '§') return !focus.isEmpty();
        if (selectAll) {
            buffer = "";
            caret = 0;
            selectAll = false;
        }
        buffer = buffer.substring(0, caret) + c + buffer.substring(caret);
        caret++;
        return true;
    }

    // ---------- plain drawing ----------

    public void text(String s, float x, float y, int colour) {
        texts.add(new Text(s, x, y, colour));
    }

    public void textCentre(String s, float cx, float y, int colour) {
        text(s, cx - width(s) / 2f, y, colour);
    }

    public void textRight(String s, float x1, float y, int colour) {
        text(s, x1 - width(s), y, colour);
    }

    public float width(String s) {
        return font.width(s);
    }

    // Shortens with dots to fit.
    public String fit(String s, float w) {
        if (width(s) <= w) return s;
        String dots = "..";
        float limit = w - width(dots);
        int n = s.length();
        while (n > 0 && width(s.substring(0, n)) > limit) n--;
        return s.substring(0, n) + dots;
    }

    public void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) return;
        hadItems = true;
        afterShapes.add(() -> {
            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            g.pose().scale(size / 16f, size / 16f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        });
    }

    // Something custom to draw after the shapes of this layer (little 3D previews).
    public void later(Runnable r) {
        hadItems = true;
        afterShapes.add(r);
    }

    public void tooltip(String s) {
        tooltip = s;
    }

    private void drawTooltip() {
        if (tooltip == null || tooltip.isEmpty()) return;
        flush();
        List<String> lines = wrap(tooltip, 200f);
        float w = 0f;
        for (String l : lines) w = Math.max(w, width(l));
        float h = lines.size() * 10f + 7f;
        float x = Math.min(mx + 8f, width - w - 12f), y = my + 10f;
        if (y + h > height - 2f) y = my - h - 4f;
        draw.halo(x, y, x + w + 10f, y + h, 5f, 0x60000000, 6f);
        draw.rect(x, y, x + w + 10f, y + h, 5f, 0xFA140C1A);
        draw.border(x, y, x + w + 10f, y + h, 5f, 0.5f, 0x60FF8FC7);
        for (int i = 0; i < lines.size(); i++) text(lines.get(i), x + 5f, y + 4f + i * 10f, TEXT);
    }

    public List<String> wrap(String s, float w) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (width(next) > w && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    public void panel(float x0, float y0, float x1, float y1, float r) {
        draw.halo(x0 + 1f, y0 + 3f, x1 - 1f, y1 + 3f, r, 0x70000000, 10f);
        draw.grad(x0, y0, x1, y1, r, PANEL_TOP, PANEL_BOTTOM);
        draw.border(x0, y0, x1, y1, r, 0.5f, 0x38FFFFFF);
        claim(x0, y0, x1, y1);
    }

    public void well(float x0, float y0, float x1, float y1, float r) {
        draw.rect(x0, y0, x1, y1, r, 0xB0170E1E);
        draw.border(x0, y0, x1, y1, r, 0.5f, 0x20FFFFFF);
    }

    // ---------- asking if the mouse is here ----------

    public boolean over(float x0, float y0, float x1, float y1) {
        if (blocked) return false;
        if (!active.isEmpty() && down) return false;
        if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[2] || my >= clip[3])) return false;
        return mx >= x0 && my >= y0 && mx < x1 && my < y1;
    }

    // Takes this frame's click if it landed here.
    public boolean click(String id, float x0, float y0, float x1, float y1) {
        rects.put(id, new float[] {x0, y0, x1, y1});
        if (pressed && over(x0, y0, x1, y1)) {
            pressed = false;
            active = id;
            return true;
        }
        return false;
    }

    public void sound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.18f));
    }

    // ---------- widgets ----------

    public boolean button(String id, float x, float y, float w, float h, String label) {
        return button(id, x, y, w, h, label, false, true);
    }

    public boolean accent(String id, float x, float y, float w, float h, String label) {
        return button(id, x, y, w, h, label, true, true);
    }

    public boolean button(String id, float x, float y, float w, float h, String label, boolean pink, boolean enabled) {
        boolean clicked = enabled && click(id, x, y, x + w, y + h);
        rects.put(id, new float[] {x, y, x + w, y + h});
        boolean hover = enabled && over(x, y, x + w, y + h);
        boolean held = active.equals(id) && down;
        float r = h / 2f;
        int top, bottom;
        if (!enabled) {
            top = bottom = 0x70463056;
        } else if (pink) {
            top = hover || held ? 0xFFFFC2E2 : PINK_SOFT;
            bottom = hover || held ? 0xFFFF9ED0 : PINK;
        } else {
            top = hover ? 0xFF6A4A7E : held ? 0xFF3D2A4C : 0xFF533A64;
            bottom = hover ? 0xFF573C69 : held ? 0xFF47305A : 0xFF412C52;
        }
        if (enabled && !held) draw.halo(x, y + 1f, x + w, y + h + 1f, r, 0x50000000, 3f);
        draw.grad(x, y + (held ? 0.5f : 0f), x + w, y + h + (held ? 0.5f : 0f), r, top, bottom);
        if (hover && pink) draw.halo(x, y, x + w, y + h, r, 0x50FF8FC7, 5f);
        int colour = !enabled ? 0x80B9A0B6 : pink ? TEXT_DARK : TEXT;
        textCentre(fit(label, w - 4f), x + w / 2f, y + (h - 8f) / 2f + (held ? 0.5f : 0f), colour);
        if (clicked) sound();
        return clicked;
    }

    public boolean chip(String id, float x, float y, float w, float h, String label, boolean on) {
        boolean clicked = click(id, x, y, x + w, y + h);
        boolean hover = over(x, y, x + w, y + h);
        float r = h / 2f;
        if (on) {
            draw.halo(x, y, x + w, y + h, r, 0x38FF8FC7, 4f);
            draw.grad(x, y, x + w, y + h, r, PINK_SOFT, PINK);
        } else {
            draw.rect(x, y, x + w, y + h, r, hover ? 0xFF4F3660 : 0xC0261830);
            draw.border(x, y, x + w, y + h, r, 0.5f, hover ? 0x60FF8FC7 : 0x28FFFFFF);
        }
        textCentre(fit(label, w - 4f), x + w / 2f, y + (h - 8f) / 2f, on ? TEXT_DARK : hover ? TEXT : TEXT_DIM);
        if (clicked) sound();
        return clicked;
    }

    // A row of chips sharing a width, each as wide as its words need. Answers which one was pressed, or -1.
    public int chips(String id, float x, float y, float w, float h, String[] labels, int on) {
        float total = 0f;
        for (String l : labels) total += width(l) + 2f;
        float gap = 2f;
        float extra = Math.max(0f, (w - total - gap * (labels.length - 1)) / labels.length);
        float cx = x;
        int pressed = -1;
        for (int i = 0; i < labels.length; i++) {
            float cw = width(labels[i]) + 2f + extra;
            if (chip(id + "_" + i, cx, y, cw, h, labels[i], i == on)) pressed = i;
            cx += cw + gap;
        }
        return pressed;
    }

    // The pill switch.
    public boolean toggle(String id, float x, float y, float w, String label, boolean on) {
        float h = 13f;
        float sw = 20f, sh = 10f;
        float sx = x + w - sw, sy = y + (h - sh) / 2f;
        boolean clicked = click(id, x, y, x + w, y + h);
        boolean hover = over(x, y, x + w, y + h);
        text(fit(label, w - sw - 4f), x, y + 2.5f, hover ? TEXT : TEXT_DIM);
        draw.rect(sx, sy, sx + sw, sy + sh, sh / 2f, on ? PINK : 0xFF1C1124);
        draw.border(sx, sy, sx + sw, sy + sh, sh / 2f, 0.5f, 0x30FFFFFF);
        float knob = sh / 2f - 1f;
        draw.circle(on ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, knob, on ? 0xFFFFFFFF : 0xFFB9A0B6);
        if (clicked) sound();
        return clicked ? !on : on;
    }

    // A smooth slider. Answers the new value.
    public float slider(String id, float x, float y, float w, float h, float value, float min, float max) {
        rects.put(id, new float[] {x, y, x + w, y + h});
        if (pressed && over(x - 2f, y, x + w + 2f, y + h)) {
            pressed = false;
            active = id;
        }
        boolean held = active.equals(id) && down;
        float t = max > min ? (Math.max(min, Math.min(max, value)) - min) / (max - min) : 0f;
        float mid = y + h / 2f;
        float thick = 3f;
        draw.rect(x, mid - thick / 2f, x + w, mid + thick / 2f, thick / 2f, 0xFF1C1124);
        draw.rect(x, mid - thick / 2f, x + w * t, mid + thick / 2f, thick / 2f, PINK_DEEP);
        boolean hover = held || over(x - 2f, y, x + w + 2f, y + h);
        float knob = hover ? 4f : 3.2f;
        if (hover) draw.halo(x + w * t - knob, mid - knob, x + w * t + knob, mid + knob, knob, 0x60FF8FC7, 4f);
        draw.circle(x + w * t, mid, knob, hover ? 0xFFFFFFFF : PINK_SOFT);
        if (held) return min + Math.max(0f, Math.min(1f, (mx - x) / Math.max(1f, w))) * (max - min);
        return value;
    }

    // Starts editing, shared by the text and number boxes.
    private void startEditing(String id, String value) {
        focus = id;
        editing = id;
        buffer = value;
        caret = value.length();
        selectAll = true;
        commitNow = false;
    }

    // A text box. Answers the current text; changes as you type.
    public String textBox(String id, float x, float y, float w, float h, String value, String hint) {
        boolean focused = focus.equals(id);
        rects.put(id, new float[] {x, y, x + w, y + h});
        boolean hover = over(x, y, x + w, y + h);
        if (pressed && hover) {
            if (!focused) startEditing(id, value);
            pressed = false;
            focused = true;
        }
        String shown = focused ? buffer : value;
        if (focused) focusRect = rects.get(id);
        if (focused && commitNow) {
            commitNow = false;
            focus = "";
            focused = false;
        }
        float r = Math.min(6f, h / 2f);
        draw.rect(x, y, x + w, y + h, r, 0xFF1C1124);
        draw.border(x, y, x + w, y + h, r, 0.75f, focused ? PINK : hover ? 0x70FF8FC7 : 0x28FFFFFF);
        float pad = 5f;
        float room = w - pad * 2f;
        if (shown.isEmpty() && !focused) {
            text(fit(hint, room), x + pad, y + (h - 8f) / 2f, 0xFF7E6A80);
        } else {
            drawEdited(shown, x + pad, y + (h - 8f) / 2f, room, focused);
        }
        return shown;
    }

    private void drawEdited(String shown, float tx, float ty, float room, boolean focused) {
        if (!focused) {
            text(fit(shown, room), tx, ty, TEXT);
            return;
        }
        // keep the caret in view by showing the end of long text
        int from = 0;
        while (from < caret && width(shown.substring(from, caret)) > room - 2f) from++;
        int to = shown.length();
        while (to > caret && width(shown.substring(from, to)) > room) to--;
        String part = shown.substring(from, to);
        if (selectAll && !shown.isEmpty()) {
            float sel = Math.min(width(part), room);
            draw.rect(tx - 1f, ty - 1f, tx + sel + 1f, ty + 9f, 2f, 0x90FF8FC7);
        }
        text(part, tx, ty, TEXT);
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cx = tx + width(shown.substring(from, Math.max(from, caret)));
            draw.rect(cx, ty - 1f, cx + 0.75f, ty + 9f, 0f, PINK_SOFT);
        }
    }

    // A number box: click and type, Enter or clicking away takes it. Answers the value.
    public float number(String id, float x, float y, float w, float h, float value, int decimals) {
        boolean focused = focus.equals(id);
        rects.put(id, new float[] {x, y, x + w, y + h});
        boolean hover = over(x, y, x + w, y + h);
        float out = value;
        if (pressed && hover) {
            if (!focused) {
                startEditing(id, format(value, decimals));
                focused = true;
            }
            pressed = false;
        }
        if (focused) focusRect = rects.get(id);
        if (focused && commitNow) {
            commitNow = false;
            try {
                float parsed = Float.parseFloat(buffer.trim());
                if (Float.isFinite(parsed)) out = parsed;
            } catch (NumberFormatException ignored) {
                // keep the old value
            }
            focus = "";
            focused = false;
        }
        float r = Math.min(4f, h / 2f);
        draw.rect(x, y, x + w, y + h, r, focused ? 0xFF2A1830 : 0xB0170E1E);
        draw.border(x, y, x + w, y + h, r, 0.5f, focused ? PINK : hover ? 0x70FF8FC7 : 0x20FFFFFF);
        String shown = focused ? buffer : format(out, decimals);
        if (focused) drawEdited(shown, x + 3f, y + (h - 8f) / 2f, w - 6f, true);
        else textRight(fit(shown, w - 5f), x + w - 3f, y + (h - 8f) / 2f, hover ? TEXT : 0xFFE2CFE0);
        return out;
    }

    public static String format(float v, int decimals) {
        if (decimals <= 0) return String.valueOf(Math.round(v));
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s.equals("-0") ? "0" : s;
    }

    // A label, then a slider, then a number box, all in one row. Answers the new value.
    public float row(String id, float x, float y, float w, String label, float labelW, float value, float softMin, float softMax, float hardMin, float hardMax, int decimals) {
        float h = 12f;
        text(fit(label, labelW - 2f), x, y + 2f, TEXT_DIM);
        float boxW = Math.min(36f, w * 0.26f);
        float sliderX = x + labelW, sliderW = w - labelW - boxW - 5f;
        float out = value;
        if (sliderW > 8f) {
            float s = slider(id + "#s", sliderX, y, sliderW, h, value, Math.min(softMin, value), Math.max(softMax, value));
            if (s != value) out = rounded(s, decimals);
        }
        float n = number(id + "#n", x + w - boxW, y, boxW, h, out, decimals);
        if (n != out) out = n;
        return Math.max(hardMin, Math.min(hardMax, out));
    }

    private static float rounded(float v, int decimals) {
        float p = (float) Math.pow(10.0, decimals);
        return Math.round(v * p) / p;
    }

    // A scrolling area: answers how far down it is scrolled.
    private final Map<String, Float> scrolls = new HashMap<>();

    public float scroll(String id, float x0, float y0, float x1, float y1, float contentH) {
        float room = y1 - y0;
        float max = Math.max(0f, contentH - room);
        float s = Math.max(0f, Math.min(max, scrolls.getOrDefault(id, 0f)));
        boolean inside = !blocked && mx >= x0 && my >= y0 && mx < x1 && my < y1;
        if (inside && wheel != 0f) {
            s = Math.max(0f, Math.min(max, s - wheel * 16f));
            wheel = 0f;
        }
        scrolls.put(id, s);
        if (max > 0f) {
            float bx = x1 - 3f;
            float len = Math.max(12f, room * room / contentH);
            float at = y0 + (room - len) * (s / max);
            String bar = id + "#bar";
            if (pressed && inside && mx >= bx - 3f) {
                pressed = false;
                active = bar;
            }
            if (active.equals(bar) && down) {
                s = Math.max(0f, Math.min(max, (my - y0 - len / 2f) / Math.max(1f, room - len) * max));
                scrolls.put(id, s);
                at = y0 + (room - len) * (s / max);
            }
            draw.rect(bx, at, bx + 2f, at + len, 1f, active.equals(bar) ? PINK : 0x50FFFFFF);
        }
        return s;
    }

    public void scrollTo(String id, float value) {
        scrolls.put(id, value);
    }
}
