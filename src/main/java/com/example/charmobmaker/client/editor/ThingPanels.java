package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.BlockInfo;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.ItemInfo;
import com.example.charmobmaker.model.ItemRef;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.DesignStacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

// The second tab for made blocks and items: how they act, in the same soft style as the mob stats.
final class ThingPanels {
    private final EditorScreen s;
    private float y, left, width, contentH = 200f;

    ThingPanels(EditorScreen s) {
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

    private float num(String id, String label, float v, float softMin, float softMax, float hardMin, float hardMax, int decimals) {
        float labelW = Math.min(52f, width * 0.36f);
        float n = s.ui.row(id, left, y, width, label, labelW, v, softMin, softMax, hardMin, hardMax, decimals);
        y += 14f;
        if (n != v) s.state.touch();
        return n;
    }

    private void begin(float x0, float y0, float x1, float y1, String scrollId) {
        Ui ui = s.ui;
        ui.panel(x0, y0, x1, y1, 9f);
        float scroll = ui.scroll(scrollId, x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentH);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
    }

    void drawBlock(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        BlockInfo b = s.state.design.block;
        begin(x0, y0, x1, y1, "block_scroll");
        float start = y;
        section("How solid");
        BlockInfo.Shape[] shapes = BlockInfo.Shape.values();
        int shape = ui.chips("shape", left, y, width, 13f, new String[] {shapes[0].label, shapes[1].label, shapes[2].label}, b.shape.ordinal());
        if (shape >= 0) {
            b.shape = shapes[shape];
            s.state.touch();
        }
        y += 15f;
        ui.text(switch (b.shape) {
            case PARTS -> "Its boxes and blocks are solid";
            case FULL -> "A whole solid block";
            case NONE -> "You can walk through it";
        }, left, y, Ui.TEXT_DIM);
        y += 12f;
        b.hardness = num("hardness", "Tough", b.hardness, 0f, 10f, 0f, 100f, 1);
        b.blast = num("blast", "Blast", b.blast, 0f, 30f, 0f, 1200f, 0);
        b.light = Math.round(num("light", "Light", b.light, 0f, 15f, 0f, 15f, 0));
        b.stack = Math.round(num("block_stack", "Stack", b.stack, 1f, 64f, 1f, 64f, 0));
        section("Sound");
        float cw = width / 4f;
        for (int i = 0; i < BlockInfo.SOUNDS.length; i++) {
            String name = BlockInfo.SOUNDS[i];
            String label = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            if (ui.chip("sound_" + name, left + (i % 4) * cw, y + (i / 4) * 14f, cw - 2f, 12f, label, b.sound.equals(name))) {
                b.sound = name;
                s.state.touch();
            }
        }
        y += 30f;
        section("Opens a screen");
        Design screen = b.screen.isEmpty() ? null : Designs.CLIENT.get(b.screen);
        String label = b.screen.isEmpty() ? "No screen, just a block" : screen == null ? "Missing: " + b.screen : screen.name;
        if (ui.button("block_screen", left, y, width, 14f, label)) {
            s.popup = PickerPopup.screens(s, id -> {
                b.screen = id;
                s.state.touch();
            });
        }
        y += 17f;
        for (String line : ui.wrap("Make a screen in Mobs > New Screen, save it, then pick it here. Right clicking the block opens it, and its steps run in the block.", width)) {
            ui.text(line, left, y, Ui.TEXT_DIM);
            y += 10f;
        }
        y += 4f;
        section("Good to know");
        boolean moves = com.example.charmobmaker.client.render.BlockBakes.animated(s.state.design);
        for (String line : ui.wrap(moves ? "It has a clip, so each one placed is drawn every frame. Lovely for a machine, but a whole house of them costs more." : "No clips: boxes and blocks go right into the chunk, so you can place thousands. Paintings, mob parts and items cost a little more.", width)) {
            ui.text(line, left, y, Ui.TEXT_DIM);
            y += 10f;
        }
        contentH = y - start + 8f;
        ui.popClip();
    }

    void drawItem(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ItemInfo i = s.state.design.item;
        begin(x0, y0, x1, y1, "item_scroll");
        float start = y;
        section("Using it");
        ItemInfo.Use[] uses = ItemInfo.Use.values();
        int use = ui.chips("use", left, y, width, 13f, new String[] {uses[0].label, uses[1].label, uses[2].label, uses[3].label}, i.use.ordinal());
        if (use >= 0) {
            i.use = uses[use];
            s.state.touch();
        }
        y += 16f;
        if (i.use == ItemInfo.Use.DRINK || i.use == ItemInfo.Use.EAT) i.useSeconds = num("use_time", "Takes", i.useSeconds, 0.2f, 5f, 0.1f, 30f, 1);
        i.cooldown = num("cooldown", "Wait", i.cooldown, 0f, 60f, 0f, 3600f, 0);
        boolean consume = ui.toggle("consume", left, y, width, "Gets used up", i.consume);
        y += 15f;
        if (consume != i.consume) {
            i.consume = consume;
            s.state.touch();
        }
        if (i.consume) {
            ui.text("Leaves", left, y + 2f, Ui.TEXT_DIM);
            ItemStack back = i.returns.isEmpty() ? ItemStack.EMPTY : DesignStacks.ofRef(i.returns, 1, Designs.CLIENT);
            float bx = left + 34f;
            if (ui.button("returns", bx, y, width - 34f - (back.isEmpty() ? 0f : 16f), 12f, back.isEmpty() ? "Nothing" : back.getHoverName().getString())) {
                s.popup = PickerPopup.itemRefs(s, id -> {
                    i.returns = id;
                    s.state.touch();
                });
            }
            if (!back.isEmpty() && ui.chip("returns_x", left + width - 14f, y, 14f, 12f, "x", false)) {
                i.returns = "";
                s.state.touch();
            }
            y += 15f;
        }
        section("What it does");
        effects(i);
        i.heal = num("heal", "Heal", i.heal, 0f, 20f, 0f, 200f, 1);
        i.food = Math.round(num("food", "Food", i.food, 0f, 20f, 0f, 40f, 0));
        if (i.food > 0) i.saturation = num("sat", "Filling", i.saturation, 0f, 1.2f, 0f, 2f, 2);
        i.xp = Math.round(num("xp", "XP", i.xp, 0f, 100f, 0f, Limits.MAX_XP, 0));
        i.launch = num("launch", "Bounce", i.launch, 0f, 2f, 0f, 4f, 2);
        boolean clear = ui.toggle("clear", left, y, width, "Clears other effects", i.clearEffects);
        y += 15f;
        if (clear != i.clearEffects) {
            i.clearEffects = clear;
            s.state.touch();
        }
        section("Sound and sparkle");
        ui.text("Sound", left, y + 2f, Ui.TEXT_DIM);
        if (ui.button("item_sound", left + 34f, y, width - 34f, 12f, i.sound.isEmpty() ? "None" : i.sound.replace("minecraft:", "").replace("entity.", ""))) {
            s.popup = PickerPopup.sounds(s, id -> {
                i.sound = id;
                s.state.touch();
            });
        }
        y += 14f;
        ui.text("Sparkle", left, y + 2f, Ui.TEXT_DIM);
        if (ui.button("item_particle", left + 40f, y, width - 40f, 12f, i.particle.isEmpty() ? "None" : i.particle.replace("minecraft:", "").replace('_', ' '))) {
            s.popup = PickerPopup.particles(s, id -> {
                i.particle = id;
                s.state.touch();
            });
        }
        y += 14f;
        if (!i.particle.isEmpty()) i.particleCount = Math.round(num("particles", "How many", i.particleCount, 0f, 64f, 0f, 64f, 0));
        section("Tooltip");
        for (int n = 0; n < i.lines.size(); n++) {
            String was = i.lines.get(n);
            String now = ui.textBox("line" + n, left, y, width - 16f, 12f, was, "A line of words");
            if (!now.equals(was)) {
                i.lines.set(n, DesignRules.cleanName(now, 60, ""));
                s.state.touch();
            }
            if (ui.chip("line_x" + n, left + width - 14f, y, 14f, 12f, "x", false)) {
                i.lines.remove(n);
                s.state.touch();
                break;
            }
            y += 14f;
        }
        if (i.lines.size() < Limits.MAX_LINES && ui.button("line_add", left, y, width, 12f, "+ Add a line")) {
            i.lines.add("Tastes pink");
            s.state.touch();
        }
        y += 16f;
        section("In the hand");
        i.handScale = num("hand", "Size", i.handScale, 0.25f, 2f, 0.1f, 4f, 2);
        i.handGrip = num("grip", "Grip", i.handGrip, -16f, 16f, -32f, 32f, 1);
        i.stack = Math.round(num("item_stack", "Stack", i.stack, 1f, 64f, 1f, 64f, 0));
        contentH = y - start + 8f;
        ui.popClip();
    }

    private void effects(ItemInfo i) {
        Ui ui = s.ui;
        for (int n = 0; n < i.effects.size(); n++) {
            ItemInfo.Effect e = i.effects.get(n);
            ResourceLocation rl = ResourceLocation.tryParse(e.id);
            var effect = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
            ui.text(ui.fit(effect == null ? e.id : effect.getDisplayName().getString(), width - 18f), left, y + 2f, Ui.TEXT);
            if (ui.chip("effect_x" + n, left + width - 14f, y, 14f, 12f, "x", false)) {
                i.effects.remove(n);
                s.state.touch();
                return;
            }
            y += 14f;
            ui.text("Level", left, y + 2f, Ui.TEXT_DIM);
            float lv = ui.number("effect_lv" + n, left + 30f, y, 24f, 12f, e.level, 0);
            if (Math.round(lv) != e.level) {
                e.level = Math.max(1, Math.min(Limits.MAX_EFFECT_LEVEL, Math.round(lv)));
                s.state.touch();
            }
            ui.text("Seconds", left + 60f, y + 2f, Ui.TEXT_DIM);
            float sec = ui.number("effect_s" + n, left + 104f, y, Math.max(24f, width - 104f), 12f, e.seconds, 1);
            if (sec != e.seconds) {
                e.seconds = Math.max(0.05f, Math.min(Limits.MAX_EFFECT_SECONDS, sec));
                s.state.touch();
            }
            y += 16f;
        }
        if (i.effects.size() < Limits.MAX_EFFECTS && ui.button("effect_add", left, y, width, 13f, "+ Add an effect")) {
            s.popup = PickerPopup.effects(s, id -> {
                ItemInfo.Effect e = new ItemInfo.Effect();
                e.id = id;
                i.effects.add(e);
                s.state.touch();
            });
        }
        y += 16f;
    }

    static String nameOfRef(String ref) {
        if (ItemRef.isDesign(ref)) {
            Design d = Designs.CLIENT.get(ItemRef.designId(ref));
            return d == null ? "Missing" : d.name;
        }
        ItemStack st = DesignStacks.ofRef(ref, 1, Designs.CLIENT);
        return st.isEmpty() ? ref : st.getHoverName().getString();
    }
}
