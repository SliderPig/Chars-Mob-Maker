package com.example.charmobmaker.model;

// What one mob is playing right now. Lives on the mob on the PC side. Idle and walk mix by how fast it walks, and one-shots (attack, hurt...) fade in on top,
// moving only the parts they have keys for: legs keep walking while the arm swings.
public final class AnimState {
    public static final float FADE_IN = 0.08f, FADE_OUT = 0.16f;
    public static final float WALK_FULL_SPEED = 0.6f;

    private String shot = "";
    private boolean shotOnce;
    private float shotStart;

    // Plays a clip on top. once = a looping clip still only plays through one time.
    public void play(String clip, float now, boolean once) {
        shot = clip == null ? "" : clip;
        shotOnce = once;
        shotStart = now;
    }

    public void stop() {
        shot = "";
    }

    public String playing() {
        return shot;
    }

    // walkPos and walkSpeed come from the mob's walking animation. deathSeconds is below 0 while alive.
    public void pose(Rig rig, float now, float walkPos, float walkSpeed, float deathSeconds, PoseOut out) {
        PoseOut idle = rig.scratchA, walk = rig.scratchB, over = rig.scratchC;
        out.clear();
        Rig.RigClip idleClip = rig.clip("idle"), walkClip = rig.clip("walk");
        if (idleClip != null) {
            idleClip.sample(idleClip.clip.wrapTime(now * idleClip.clip.speed), idle);
            out.blend(idle, 1f, false);
        }
        if (walkClip != null) {
            float w = smooth(walkSpeed / 0.2f);
            if (w > 0f) {
                walkClip.sample(walkClip.clip.wrapTime(walkTime(walkPos) * walkClip.clip.speed), walk);
                out.blend(walk, w, true);
            }
        }
        if (deathSeconds >= 0f) {
            Rig.RigClip death = rig.clip("death");
            if (death != null) {
                death.sample(Math.min(death.clip.length, deathSeconds * death.clip.speed), over);
                out.blend(over, Math.min(1f, deathSeconds / FADE_IN), true);
            }
            return;
        }
        if (!shot.isEmpty()) {
            Rig.RigClip c = rig.clip(shot);
            if (c == null) {
                shot = "";
                return;
            }
            float age = now - shotStart;
            float time = age * c.clip.speed;
            float length = c.clip.length;
            boolean ends = shotOnce || c.clip.loop == Clip.Loop.ONCE;
            float weight = Math.min(1f, age / FADE_IN);
            if (ends && c.clip.loop != Clip.Loop.HOLD && time >= length) {
                float past = (time - length) / c.clip.speed;
                weight = Math.min(weight, 1f - past / FADE_OUT);
                if (weight <= 0f) {
                    shot = "";
                    return;
                }
                time = length;
            }
            float sampleAt = ends ? Math.min(time, length) : c.clip.wrapTime(time);
            c.sample(sampleAt, over);
            out.blend(over, weight, true);
        }
    }

    // At the normal pace (walk speed 0.6) a walk clip plays at the speed it was made; a faster mob plays it faster.
    public static float walkTime(float walkPos) {
        return walkPos / (WALK_FULL_SPEED * 20f);
    }

    private static float smooth(float a) {
        if (a <= 0f) return 0f;
        if (a >= 1f) return 1f;
        return a * a * (3f - 2f * a);
    }
}
