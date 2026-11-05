package com.example.charmobmaker.client.ui;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.ScreenInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.List;

// Draws a made screen, the same way in the real game and in the editor. Soft and round like the rest of the mod.
// The pose must have the screen's top left at 0, 0 and one screen unit per unit.
public final class ScreenPainter {
    public record Live(float progress, int step, boolean running) {}

    public static final Live PREVIEW = new Live(0.6f, 0, true);

    private ScreenPainter() {}

    public static void paint(GuiGraphics g, Draw d, Font font, Design design, Live live, float mx, float my, float onePixel) {
        ScreenInfo s = design.screen;
        d.begin(g, onePixel);
        d.halo(1f, 3f, s.width - 1f, s.height + 3f, 9f, 0x90000000, 12f);
        d.grad(0f, 0f, s.width, s.height, 9f, s.colourTop, s.colourBottom);
        d.border(0f, 0f, s.width, s.height, 9f, 0.5f, 0x40FFFFFF);
        for (ScreenInfo.Widget w : s.widgets) shape(d, font, s, w, live, mx, my);
        inventory(d, s);
        g.flush();
        boolean items = false;
        for (ScreenInfo.Widget w : s.widgets) {
            if (w.kind != ScreenInfo.Widget.Kind.IMAGE) continue;
            if (w.paint >= 0 && w.paint < design.paints.size()) {
                int[] cell = design.paints.get(w.paint);
                float sx = w.w / 16f, sy = w.h / 16f;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int c = cell[y * 16 + x];
                        if ((c >>> 24) > 0) d.rect(w.x + x * sx, w.y + y * sy, w.x + (x + 1) * sx, w.y + (y + 1) * sy, 0f, c);
                    }
                }
                g.flush();
            } else if (!w.item.isEmpty()) {
                ItemStack stack = DesignStacks.ofRef(w.item, 1, Designs.CLIENT);
                if (!stack.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(w.x, w.y, 0f);
                    g.pose().scale(w.w / 16f, w.h / 16f, 1f);
                    g.renderItem(stack, 0, 0);
                    g.pose().popPose();
                    items = true;
                }
            }
        }
        if (items) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.drawManaged(() -> {
            for (ScreenInfo.Widget w : s.widgets) text(g, font, s, w, live, mx, my);
        });
    }

    private static boolean over(ScreenInfo.Widget w, float mx, float my) {
        return mx >= w.x && my >= w.y && mx < w.x + w.w && my < w.y + w.h;
    }

    private static void shape(Draw d, Font font, ScreenInfo s, ScreenInfo.Widget w, Live live, float mx, float my) {
        switch (w.kind) {
            case PANEL -> {
                float r = Math.min(6f, Math.min(w.w, w.h) / 2f);
                d.rect(w.x, w.y, w.x + w.w, w.y + w.h, r, w.colour);
                d.border(w.x, w.y, w.x + w.w, w.y + w.h, r, 0.5f, 0x28FFFFFF);
            }
            case SLOT -> well(d, w.x, w.y, w.output);
            case BUTTON -> {
                float r = w.h / 2f;
                boolean hover = over(w, mx, my);
                int top = lift(w.colour, hover ? 42 : 22), bottom = lift(w.colour, hover ? 8 : -14);
                d.halo(w.x, w.y + 1f, w.x + w.w, w.y + w.h + 1f, r, 0x50000000, 3f);
                d.grad(w.x, w.y, w.x + w.w, w.y + w.h, r, top, bottom);
                if (hover) d.halo(w.x, w.y, w.x + w.w, w.y + w.h, r, Draw.alpha(w.colour, 0.3f), 4f);
            }
            case PROGRESS -> {
                if (w.bar) bar(d, w, live);
                else arrow(d, w, live);
            }
            case STEPS -> steps(d, s, w, live, mx, my);
            default -> {
            }
        }
    }

    public static void well(Draw d, float x, float y, boolean output) {
        d.rect(x, y, x + 18f, y + 18f, 4f, 0xFF1C1124);
        d.rect(x + 0.5f, y + 0.5f, x + 17.5f, y + 2f, 1.5f, 0x40000000);
        d.border(x, y, x + 18f, y + 18f, 4f, 0.5f, output ? 0xC0FF8FC7 : 0x30FFFFFF);
    }

    private static void inventory(Draw d, ScreenInfo s) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) well(d, s.invX + col * 18, s.invY + row * 18, false);
        }
        for (int col = 0; col < 9; col++) well(d, s.invX + col * 18, s.invY + 58, false);
    }

    private static void bar(Draw d, ScreenInfo.Widget w, Live live) {
        float r = Math.min(w.w, w.h) / 2f;
        d.rect(w.x, w.y, w.x + w.w, w.y + w.h, r, 0xFF1C1124);
        float fill = w.w * Math.max(0f, Math.min(1f, live.progress()));
        if (fill > 0.5f) d.grad(w.x, w.y, w.x + Math.max(fill, r * 2f), w.y + w.h, r, lift(w.colour, 25), w.colour);
        d.border(w.x, w.y, w.x + w.w, w.y + w.h, r, 0.5f, 0x28FFFFFF);
    }

    // A rounded arrow pointing right, filling up from the left.
    private static void arrow(Draw d, ScreenInfo.Widget w, Live live) {
        float cy = w.y + w.h / 2f;
        float head = Math.min(w.h, w.w * 0.5f);
        float shaftEnd = w.x + w.w - head * 0.7f;
        float thick = Math.max(2f, w.h * 0.28f);
        int dark = 0xFF1C1124;
        d.rect(w.x, cy - thick / 2f, shaftEnd, cy + thick / 2f, thick / 2f, dark);
        d.triangle(shaftEnd - 1f, w.y + 1f, shaftEnd - 1f, w.y + w.h - 1f, w.x + w.w, cy, dark);
        float t = Math.max(0f, Math.min(1f, live.progress()));
        float reach = w.x + w.w * t;
        if (t <= 0f) return;
        d.rect(w.x, cy - thick / 2f, Math.min(reach, shaftEnd), cy + thick / 2f, thick / 2f, w.colour);
        if (reach > shaftEnd - 1f) {
            float along = (reach - (shaftEnd - 1f)) / (w.x + w.w - (shaftEnd - 1f));
            float half = (w.h / 2f - 1f) * (1f - along);
            float x1 = reach;
            d.triangle(shaftEnd - 1f, w.y + 1f, shaftEnd - 1f, w.y + w.h - 1f, x1, cy + half, w.colour);
            d.triangle(shaftEnd - 1f, w.y + 1f, x1, cy - half, x1, cy + half, w.colour);
        }
    }

    private static void steps(Draw d, ScreenInfo s, ScreenInfo.Widget w, Live live, float mx, float my) {
        int n = Math.max(1, s.recipes.size());
        float gap = 2f, cw = (w.w - gap * (n - 1)) / n, r = Math.min(w.h / 2f, 6f);
        for (int i = 0; i < n; i++) {
            float x = w.x + i * (cw + gap);
            boolean current = live.step() == i;
            boolean hover = mx >= x && my >= w.y && mx < x + cw && my < w.y + w.h;
            d.rect(x, w.y, x + cw, w.y + w.h, r, hover ? 0xFF4F3660 : 0xC0261830);
            if (current) {
                float fill = cw * Math.max(0.1f, live.progress());
                d.grad(x, w.y, x + Math.max(fill, r * 2f), w.y + w.h, r, lift(w.colour, 25), w.colour);
            }
            d.border(x, w.y, x + cw, w.y + w.h, r, 0.5f, current ? w.colour : hover ? 0x70FF8FC7 : 0x28FFFFFF);
        }
    }

    private static void text(GuiGraphics g, Font font, ScreenInfo s, ScreenInfo.Widget w, Live live, float mx, float my) {
        switch (w.kind) {
            case TEXT -> {
                String t = fill(w.text, s, live);
                int x = w.centred ? w.x + (w.w - font.width(t)) / 2 : w.x;
                g.drawString(font, t, x, w.y + Math.max(0, (w.h - 8) / 2), opaque(w.colour), false);
            }
            case BUTTON -> {
                String t = fit(font, w.text, w.w - 4);
                g.drawString(font, t, w.x + (w.w - font.width(t)) / 2, w.y + (w.h - 8) / 2, luminance(w.colour) > 140 ? 0xFF2A1420 : 0xFFF8EAF3, false);
            }
            case STEPS -> {
                int n = s.recipes.size();
                float gap = 2f, cw = (w.w - gap * (Math.max(1, n) - 1)) / Math.max(1, n);
                for (int i = 0; i < n; i++) {
                    float x = w.x + i * (cw + gap);
                    String t = fit(font, (i + 1) + " " + s.recipes.get(i).name, (int) cw - 3);
                    boolean current = live.step() == i;
                    g.drawString(font, t, Math.round(x + (cw - font.width(t)) / 2f), w.y + (w.h - 8) / 2, current ? 0xFF2A1420 : 0xFFE2CFE0, false);
                }
            }
            default -> {
            }
        }
    }

    private static String fill(String text, ScreenInfo s, Live live) {
        if (text.indexOf('{') < 0) return text;
        String step = live.step() >= 0 && live.step() < s.recipes.size() ? s.recipes.get(live.step()).name : "Waiting";
        return text.replace("{step}", step).replace("{percent}", Math.round(live.progress() * 100f) + "%");
    }

    private static String fit(Font font, String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width(".."))) + "..";
    }

    private static int opaque(int c) {
        return c | 0xFF000000;
    }

    private static int lift(int c, int by) {
        int r = Math.max(0, Math.min(255, ((c >> 16) & 255) + by)), g = Math.max(0, Math.min(255, ((c >> 8) & 255) + by)), b = Math.max(0, Math.min(255, (c & 255) + by));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int luminance(int c) {
        return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
    }

    // Which step of a Steps widget this spot is on, or -1.
    public static int stepAt(ScreenInfo s, ScreenInfo.Widget w, float mx, float my) {
        if (w.kind != ScreenInfo.Widget.Kind.STEPS || !over(w, mx, my) || s.recipes.isEmpty()) return -1;
        float cell = w.w / (float) s.recipes.size();
        return Math.min(s.recipes.size() - 1, (int) ((mx - w.x) / cell));
    }

    public static List<ScreenInfo.Widget> widgets(ScreenInfo s) {
        return s.widgets;
    }
}
