package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.client.ui.Ui;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

// How the mob acts: left panel its numbers, right panel its behaviour.
final class StatsPanel {
    private static final String[][] GROUPS = {{"player", "Players"}, {"villager", "Villagers"}, {"animal", "Animals"}, {"monster", "Monsters"}, {"golem", "Golems"}};
    private final EditorScreen s;
    private float y, left, width, contentLeft = 200f, contentRight = 200f;

    StatsPanel(EditorScreen s) {
        this.s = s;
    }

    private Stats st() {
        return s.state.design.stats;
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
        float labelW = Math.min(50f, width * 0.36f);
        float n = s.ui.row(id, left, y, width, label, labelW, v, softMin, softMax, hardMin, hardMax, decimals);
        y += 14f;
        if (n != v) s.state.touch();
        return n;
    }

    void drawLeft(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ui.panel(x0, y0, x1, y1, 9f);
        Stats t = st();
        float scroll = ui.scroll("stats_left", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentLeft);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
        float start = y;
        section("Body");
        t.health = num("health", "Health", t.health, 1f, 100f, 1f, Limits.MAX_HEALTH, 1);
        t.armor = num("armor", "Armour", t.armor, 0f, 20f, 0f, Limits.MAX_ARMOR, 1);
        t.knockbackResist = num("kbres", "Sturdy", t.knockbackResist, 0f, 1f, 0f, 1f, 2);
        t.xp = Math.round(num("xp", "XP", t.xp, 0f, 50f, 0f, Limits.MAX_XP, 0));
        section("Moving");
        t.speed = num("speed", "Speed", t.speed, 0f, 0.6f, 0f, Limits.MAX_SPEED, 3);
        boolean fly = ui.toggle("flying", left, y, width, "Flies", t.flying);
        y += 15f;
        boolean swims = ui.toggle("swims", left, y, width, "Bobs up in water", t.swims);
        y += 15f;
        boolean fire = ui.toggle("fire", left, y, width, "Fire can't hurt it", t.fireImmune);
        y += 15f;
        if (fly != t.flying || swims != t.swims || fire != t.fireImmune) {
            t.flying = fly;
            t.swims = swims;
            t.fireImmune = fire;
            s.state.touch();
        }
        section("Hitbox");
        t.width = num("hit_w", "Width", t.width, 0.2f, 4f, Limits.MIN_WIDTH, Limits.MAX_WIDTH, 2);
        t.height = num("hit_h", "Height", t.height, 0.2f, 5f, Limits.MIN_HEIGHT, Limits.MAX_HEIGHT, 2);
        section("Fighting");
        t.damage = num("damage", "Damage", t.damage, 0f, 40f, 0f, Limits.MAX_DAMAGE, 1);
        t.attackRate = num("rate", "Every", t.attackRate, 0.25f, 5f, Limits.MIN_RATE, Limits.MAX_RATE, 2);
        ui.text("seconds between attacks", left, y, Ui.TEXT_DIM);
        y += 11f;
        contentLeft = y - start + 8f;
        ui.popClip();
    }

    void drawRight(float x0, float y0, float x1, float y1) {
        Ui ui = s.ui;
        ui.panel(x0, y0, x1, y1, 9f);
        Stats t = st();
        float scroll = ui.scroll("stats_right", x0 + 2f, y0 + 2f, x1 - 1f, y1 - 2f, contentRight);
        ui.pushClip(x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f);
        left = x0 + 7f;
        width = x1 - x0 - 15f;
        y = y0 + 6f - scroll;
        float start = y;
        section("Mood");
        Stats.Mood[] moods = Stats.Mood.values();
        float cw = width / 3f;
        for (int i = 0; i < 3; i++) {
            if (ui.chip("mood_" + i, left + i * cw, y, cw - 2f, 13f, moods[i].label, t.mood == moods[i])) {
                t.mood = moods[i];
                s.state.touch();
            }
        }
        y += 15f;
        ui.text(t.mood == Stats.Mood.HOSTILE ? "Goes after its targets" : t.mood == Stats.Mood.NEUTRAL ? "Only fights back when hit" : "Runs away when hit", left, y, Ui.TEXT_DIM);
        y += 12f;
        if (t.mood == Stats.Mood.HOSTILE) targets(t);
        if (t.mood != Stats.Mood.PASSIVE) attacks(t);
        drops(t);
        sounds(t);
        spawning(t);
        eggColours(t);
        contentRight = y - start + 8f;
        ui.popClip();
    }

    private void targets(Stats t) {
        Ui ui = s.ui;
        section("Goes after");
        float cw = width / 3f;
        int i = 0;
        for (String[] g : GROUPS) {
            boolean on = t.targets.contains(g[0]);
            if (ui.chip("target_" + g[0], left + (i % 3) * cw, y + (i / 3) * 14f, cw - 2f, 12f, g[1], on)) {
                if (on) t.targets.remove(g[0]);
                else if (t.targets.size() < Limits.MAX_TARGETS) t.targets.add(g[0]);
                s.state.touch();
            }
            i++;
        }
        y += 30f;
        // one kind of mob
        for (String id : t.targets.toArray(new String[0])) {
            if (isGroup(id)) continue;
            ui.text(ui.fit(id, width - 20f), left, y + 2f, Ui.TEXT);
            if (ui.chip("target_x_" + id, left + width - 14f, y, 14f, 12f, "x", false)) {
                t.targets.remove(id);
                s.state.touch();
            }
            y += 14f;
        }
        if (ui.button("target_add", left, y, width, 13f, "+ One kind of mob")) {
            s.popup = PickerPopup.mobs(s, id -> {
                if (!t.targets.contains(id) && t.targets.size() < Limits.MAX_TARGETS) t.targets.add(id);
                s.state.touch();
            });
        }
        y += 17f;
    }

    private static boolean isGroup(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return true;
        return false;
    }

    private void attacks(Stats t) {
        Ui ui = s.ui;
        section("Attack");
        Stats.Attack[] kinds = Stats.Attack.values();
        float cw = width / 4f;
        for (int i = 0; i < 4; i++) {
            if (ui.chip("attack_" + i, left + i * cw, y, cw - 2f, 13f, kinds[i].label, t.attack == kinds[i])) {
                t.attack = kinds[i];
                s.state.touch();
            }
        }
        y += 17f;
        section("Abilities");
        boolean leap = ui.toggle("leap", left, y, width, "Leaps at its target", t.leap);
        y += 15f;
        boolean tele = ui.toggle("teleport", left, y, width, "Teleports next to it", t.teleport);
        y += 15f;
        boolean summon = ui.toggle("summon", left, y, width, "Calls helpers", t.summon);
        y += 15f;
        if (leap != t.leap || tele != t.teleport || summon != t.summon) {
            t.leap = leap;
            t.teleport = tele;
            t.summon = summon;
            s.state.touch();
        }
        if (leap || tele || summon) {
            t.abilityCooldown = num("cooldown", "Every", t.abilityCooldown, 2f, 60f, Limits.MIN_COOLDOWN, Limits.MAX_COOLDOWN, 1);
            ui.text("seconds at most", left, y, Ui.TEXT_DIM);
            y += 11f;
        }
        if (summon) {
            if (ui.button("summon_pick", left, y, width, 13f, ui.fit("Helper: " + t.summonMob, width - 8f))) {
                s.popup = PickerPopup.mobs(s, id -> {
                    t.summonMob = id;
                    s.state.touch();
                });
            }
            y += 16f;
            t.summonCount = Math.round(num("summon_n", "How many", t.summonCount, 1f, Limits.MAX_SUMMON, 1f, Limits.MAX_SUMMON, 0));
        }
    }

    private void drops(Stats t) {
        Ui ui = s.ui;
        section("Drops");
        for (int i = 0; i < t.drops.size(); i++) {
            Stats.Drop d = t.drops.get(i);
            ResourceLocation rl = ResourceLocation.tryParse(d.item);
            var it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            ItemStack icon = it == null ? ItemStack.EMPTY : new ItemStack(it);
            ui.item(icon, left, y - 1f, 13f);
            ui.text(ui.fit(icon.isEmpty() ? d.item : icon.getHoverName().getString(), width - 34f), left + 16f, y + 2f, Ui.TEXT);
            if (ui.chip("drop_x" + i, left + width - 14f, y, 14f, 12f, "x", false)) {
                t.drops.remove(i);
                s.state.touch();
                return;
            }
            y += 14f;
            float third = (width - 4f) / 3f;
            ui.text("From", left, y + 2f, Ui.TEXT_DIM);
            d.min = Math.round(ui.number("drop_min" + i, left + 24f, y, third - 24f + 6f, 12f, d.min, 0));
            ui.text("to", left + third + 8f, y + 2f, Ui.TEXT_DIM);
            d.max = Math.round(ui.number("drop_max" + i, left + third + 20f, y, third - 20f, 12f, d.max, 0));
            float chance = ui.number("drop_ch" + i, left + width - third + 2f, y, third - 16f, 12f, d.chance * 100f, 0);
            ui.text("%", left + width - 11f, y + 2f, Ui.TEXT_DIM);
            float newChance = Math.max(0f, Math.min(100f, chance)) / 100f;
            if (Math.abs(newChance - d.chance) > 0.001f) {
                d.chance = newChance;
                s.state.touch();
            }
            d.min = Math.max(0, Math.min(64, d.min));
            d.max = Math.max(d.min, Math.min(64, d.max));
            y += 16f;
        }
        if (t.drops.size() < Limits.MAX_DROPS && ui.button("drop_add", left, y, width, 13f, "+ Add a drop")) {
            s.popup = PickerPopup.items(s, id -> {
                Stats.Drop d = new Stats.Drop();
                d.item = id;
                t.drops.add(d);
                s.state.touch();
            });
        }
        y += 17f;
    }

    private void sounds(Stats t) {
        section("Sounds");
        t.ambientSound = soundRow("snd_ambient", "Idle", t.ambientSound);
        t.hurtSound = soundRow("snd_hurt", "Hurt", t.hurtSound);
        t.deathSound = soundRow("snd_death", "Death", t.deathSound);
        t.attackSound = soundRow("snd_attack", "Attack", t.attackSound);
        t.stepSound = soundRow("snd_step", "Steps", t.stepSound);
        y += 3f;
    }

    private String soundRow(String id, String label, String current) {
        Ui ui = s.ui;
        ui.text(label, left, y + 2f, Ui.TEXT_DIM);
        float bx = left + 34f, bw = width - 34f - (current.isEmpty() ? 0f : 30f);
        String pretty = current.isEmpty() ? "None" : current.replace("minecraft:", "");
        final String[] out = {current};
        if (ui.button(id, bx, y, bw, 12f, ui.fit(pretty, bw - 8f))) {
            s.popup = PickerPopup.sounds(s, chosen -> pendingSound(id, chosen));
        }
        String pending = takePending(id);
        if (pending != null) {
            out[0] = pending;
            s.state.touch();
        }
        if (!current.isEmpty()) {
            if (ui.chip(id + "_play", left + width - 28f, y, 14f, 12f, ">", false)) playSound(current);
            if (ui.chip(id + "_x", left + width - 14f, y, 14f, 12f, "x", false)) {
                out[0] = "";
                s.state.touch();
            }
        }
        y += 14f;
        return out[0];
    }

    private String pendingId, pendingValue;

    private void pendingSound(String id, String value) {
        pendingId = id;
        pendingValue = value;
    }

    private String takePending(String id) {
        if (id.equals(pendingId)) {
            pendingId = null;
            return pendingValue;
        }
        return null;
    }

    static void playSound(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.SOUND_EVENTS.containsKey(rl)) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ForgeRegistries.SOUND_EVENTS.getValue(rl), 1f, 0.7f));
    }

    private void spawning(Stats t) {
        Ui ui = s.ui;
        section("Spawning on its own");
        boolean on = ui.toggle("spawn_on", left, y, width, "Spawns by itself", t.spawnNaturally);
        y += 15f;
        if (on != t.spawnNaturally) {
            t.spawnNaturally = on;
            s.state.touch();
        }
        if (!on) return;
        t.spawnRate = Math.round(num("spawn_rate", "How often", t.spawnRate, 1f, 100f, 1f, 100f, 0));
        t.spawnGroup = Math.round(num("spawn_group", "Group", t.spawnGroup, 1f, 8f, 1f, 8f, 0));
        t.spawnMaxNearby = Math.round(num("spawn_max", "At most", t.spawnMaxNearby, 1f, 32f, 1f, 32f, 0));
        boolean night = ui.toggle("spawn_night", left, y, width, "Only in the dark", t.spawnNightOnly);
        y += 15f;
        if (night != t.spawnNightOnly) {
            t.spawnNightOnly = night;
            s.state.touch();
        }
        for (String b : t.spawnBiomes.toArray(new String[0])) {
            ui.text(ui.fit(b.replace("minecraft:", ""), width - 20f), left, y + 2f, Ui.TEXT);
            if (ui.chip("biome_x_" + b, left + width - 14f, y, 14f, 12f, "x", false)) {
                t.spawnBiomes.remove(b);
                s.state.touch();
            }
            y += 14f;
        }
        if (t.spawnBiomes.size() < Limits.MAX_BIOMES && ui.button("biome_add", left, y, width, 13f, t.spawnBiomes.isEmpty() ? "Anywhere. + Pick a biome" : "+ Add a biome")) {
            s.popup = PickerPopup.biomes(s, id -> {
                if (!t.spawnBiomes.contains(id)) t.spawnBiomes.add(id);
                s.state.touch();
            });
        }
        y += 17f;
    }

    private void eggColours(Stats t) {
        Ui ui = s.ui;
        section("Egg colours");
        float dot = 5f;
        ui.text("Base", left, y + 3f, Ui.TEXT_DIM);
        ui.text("Spots", left, y + 16f, Ui.TEXT_DIM);
        float step = (width - 30f) / 8f;
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 8; i++) {
                int c = BuildPanel.SWATCHES[(i * 2 + (row == 0 ? 0 : 1)) % BuildPanel.SWATCHES.length];
                float cx = left + 30f + step * i + step / 2f, cy = y + 6f + row * 13f;
                int current = row == 0 ? t.eggBase : t.eggSpots;
                if (current == c) ui.draw.ring(cx, cy, dot + 1.25f, 0.75f, Ui.PINK_SOFT);
                ui.draw.circle(cx, cy, dot, c);
                if (ui.click("egg_" + row + "_" + i, cx - dot, cy - dot, cx + dot, cy + dot)) {
                    if (row == 0) t.eggBase = c;
                    else t.eggSpots = c;
                    s.state.touch();
                }
            }
        }
        y += 28f;
    }
}
