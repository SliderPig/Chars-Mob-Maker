package com.example.charmobmaker.client.machine;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.client.ui.Draw;
import com.example.charmobmaker.client.ui.ScreenPainter;
import com.example.charmobmaker.machine.MachineMenu;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.ScreenInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// The real screen of a machine: a normal container screen, looking the way the design says.
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private final Draw draw = new Draw();

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        Design d = Designs.CLIENT.get(menu.screenId);
        imageWidth = d == null ? 176 : d.screen.width;
        imageHeight = d == null ? 166 : d.screen.height;
    }

    private Design design() {
        return Designs.CLIENT.get(menu.screenId);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        Design d = design();
        if (d == null) return;
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0f);
        RenderSystem.disableCull();
        float px = 1f / (float) Minecraft.getInstance().getWindow().getGuiScale();
        ScreenPainter.paint(g, draw, font, d, new ScreenPainter.Live(menu.progress(), menu.step(), menu.step() >= 0), mouseX - leftPos, mouseY - topPos, px);
        g.flush();
        RenderSystem.enableCull();
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        stepTooltip(g, mouseX, mouseY);
    }

    // Hover a step to see what it needs.
    private void stepTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Design d = design();
        if (d == null) return;
        float mx = mouseX - leftPos, my = mouseY - topPos;
        for (ScreenInfo.Widget w : d.screen.widgets) {
            int step = ScreenPainter.stepAt(d.screen, w, mx, my);
            if (step < 0) continue;
            ScreenInfo.Recipe r = d.screen.recipes.get(step);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(r.name));
            for (ScreenInfo.Ingredient in : r.inputs) lines.add(Component.literal(in.count + " x " + nameOf(in.item)));
            lines.add(Component.literal("makes " + r.outputCount + " x " + nameOf(r.output)));
            lines.add(Component.literal(r.seconds + " seconds"));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    private static String nameOf(String ref) {
        ItemStack s = DesignStacks.ofRef(ref, 1, Designs.CLIENT);
        return s.isEmpty() ? ref : s.getHoverName().getString();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        Design d = design();
        if (d != null && button == 0) {
            float mx = (float) x - leftPos, my = (float) y - topPos;
            for (ScreenInfo.Widget w : d.screen.widgets) {
                if (w.kind == ScreenInfo.Widget.Kind.BUTTON && mx >= w.x && my >= w.y && mx < w.x + w.w && my < w.y + w.h) {
                    int code = w.action == ScreenInfo.Widget.ACTION_START ? MachineMenu.BUTTON_START : w.action == ScreenInfo.Widget.ACTION_STOP ? MachineMenu.BUTTON_STOP : MachineMenu.BUTTON_STEP + w.action;
                    press(code);
                    return true;
                }
                int step = ScreenPainter.stepAt(d.screen, w, mx, my);
                if (step >= 0) {
                    press(MachineMenu.BUTTON_STEP + step);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private void press(int code) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, code);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, 0.4f));
    }

    // For the test walk-through.
    public void pressForTest(int code) {
        press(code);
    }
}
