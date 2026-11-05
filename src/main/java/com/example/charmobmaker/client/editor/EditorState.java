package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.Net;
import com.example.charmobmaker.client.render.Compiled;
import com.example.charmobmaker.model.Channel;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.ClipTools;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Ease;
import com.example.charmobmaker.model.History;
import com.example.charmobmaker.model.Key;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Part;
import com.example.charmobmaker.model.Rig;
import com.example.charmobmaker.model.Stats;
import com.example.charmobmaker.model.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Set;

// Everything about the mob being edited. Stays around when the screen closes, so nothing is lost if you shut it or something knocks you out of it.
public final class EditorState {
    public enum Tab {BUILD, STATS, ANIMATE}

    public enum Tool {MOVE, TURN, SIZE}

    private static final EditorState INSTANCE = new EditorState();

    public Design design = new Design();
    public Compiled compiled;
    public final History history;
    public String selected = "";
    public final Set<String> collapsed = new HashSet<>();
    public Tab tab = Tab.BUILD;
    public Tool tool = Tool.MOVE;
    public String clipName = "idle";
    public float time;
    public boolean playing;
    public Track keyTrack;
    public Key key;
    public ClipTools.Board board;
    public boolean showHitbox;
    private boolean dirty, recompile;
    private String savedText;
    public String toast = "";
    public boolean toastBad;
    public long toastUntil;
    public boolean waitingForSave;

    private EditorState() {
        history = new History(DesignJson.write(design));
        savedText = history.current();
    }

    public static EditorState get() {
        return INSTANCE;
    }

    public void load(Design d) {
        design = d;
        selected = "";
        keyTrack = null;
        key = null;
        time = 0f;
        playing = false;
        collapsed.clear();
        clipName = d.clips.isEmpty() ? "idle" : d.clips.get(0).name;
        history.reset(DesignJson.write(d));
        savedText = d.id.isEmpty() ? "" : history.current();
        dirty = false;
        recompile = true;
    }

    public boolean unsaved() {
        return !history.current().equals(savedText);
    }

    // A number on a part moved: the mesh stays the same, just where it sits.
    public void touch() {
        dirty = true;
    }

    // Something that needs the mesh made again.
    public void changed() {
        dirty = true;
        recompile = true;
    }

    public void rebuildNow() {
        recompile = true;
    }

    public Compiled compiled() {
        if (compiled == null || recompile || compiled.design != design) {
            if (compiled != null) compiled.release();
            compiled = new Compiled(design);
            recompile = false;
        }
        return compiled;
    }

    // Called once a change is finished (mouse let go, typing done): one undo step.
    public void commit() {
        if (!dirty) return;
        dirty = false;
        history.push(DesignJson.write(design));
    }

    public void undo() {
        commit();
        apply(history.undo());
    }

    public void redo() {
        commit();
        apply(history.redo());
    }

    private void apply(String text) {
        if (text == null) return;
        try {
            design = DesignJson.read(text);
            DesignRules.tidy(design);
            keyTrack = null;
            key = null;
            if (design.part(selected) == null) selected = "";
            recompile = true;
        } catch (DesignJson.BadDesign ignored) {
            // can't happen with our own text
        }
    }

    public void say(String text, boolean bad) {
        toast = text;
        toastBad = bad;
        toastUntil = System.currentTimeMillis() + 3200L;
    }

    public void save() {
        commit();
        DesignRules.tidy(design);
        String json = DesignJson.write(design);
        if (json.length() > Limits.MAX_JSON_BYTES) {
            say("This mob has too much in it to save.", true);
            return;
        }
        waitingForSave = true;
        Net.uploadToServer(json);
    }

    public void onServerReply(boolean ok, boolean wasSave, String id, String message) {
        say(message, !ok);
        if (!wasSave) return;
        waitingForSave = false;
        if (!ok) return;
        if (!id.isEmpty()) design.id = id;
        history.reset(DesignJson.write(design));
        savedText = history.current();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CAT_AMBIENT, 1.2f, 0.35f));
    }

    // ---------- which part and clip ----------

    public Part selectedPart() {
        return design.part(selected);
    }

    public Clip clip() {
        return design.clip(clipName);
    }

    public Clip clipOrMake() {
        Clip c = design.clip(clipName);
        if (c == null) {
            if (design.clips.size() >= Limits.MAX_CLIPS) return null;
            c = new Clip(clipName, defaultLength(clipName), defaultLoop(clipName));
            design.clips.add(c);
            changed();
        }
        return c;
    }

    public static float defaultLength(String clip) {
        return switch (clip) {
            case "walk" -> 0.8f;
            case "idle" -> 3f;
            case "attack" -> 0.5f;
            case "hurt" -> 0.4f;
            case "death" -> 1.5f;
            default -> 1f;
        };
    }

    public static Clip.Loop defaultLoop(String clip) {
        return switch (clip) {
            case "walk", "idle" -> Clip.Loop.LOOP;
            case "death" -> Clip.Loop.HOLD;
            default -> Clip.Loop.ONCE;
        };
    }

    // ---------- numbers shown on the right and moved by the arrows: the part's own in Build, this moment's keys in Animate ----------

    public boolean animating() {
        return tab == Tab.ANIMATE;
    }

    public float[] values(Part p, Channel ch) {
        if (!animating()) {
            return switch (ch) {
                case ROT -> p.rot.clone();
                case POS -> p.pos.clone();
                case SCALE -> p.scale.clone();
            };
        }
        float[] out = new float[3];
        Clip c = clip();
        Track t = c == null ? null : c.track(p.id, ch, false);
        if (t == null) {
            out[0] = out[1] = out[2] = ch.rest();
        } else {
            t.sample(time, c.length, c.loop == Clip.Loop.LOOP, out, 0);
        }
        return out;
    }

    public void setValues(Part p, Channel ch, float[] v) {
        if (!animating()) {
            float[] target = switch (ch) {
                case ROT -> p.rot;
                case POS -> p.pos;
                case SCALE -> p.scale;
            };
            System.arraycopy(v, 0, target, 0, 3);
            touch();
            return;
        }
        Clip c = clipOrMake();
        if (c == null) return;
        Key k = ClipTools.setKey(c, p.id, ch, ClipTools.snap(time), v[0], v[1], v[2], null);
        if (k == null) return;
        keyTrack = c.track(p.id, ch, false);
        key = k;
        changed();
    }

    public boolean hasKeyNow(Part p, Channel ch) {
        Clip c = clip();
        Track t = c == null ? null : c.track(p.id, ch, false);
        return t != null && t.at(ClipTools.snap(time)) != null;
    }

    public void selectKey(Track t, Key k) {
        keyTrack = t;
        key = k;
    }

    public void setEase(Ease e) {
        if (key != null) {
            key.ease = e;
            changed();
        }
    }

    public Stats stats() {
        return design.stats;
    }

    public Rig rig() {
        return compiled().rig;
    }
}
