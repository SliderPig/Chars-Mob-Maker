package com.example.charmobmaker.model;

import java.util.List;

// The sums behind a machine, with no game code: can this step run with what's in the slots, and what gets taken and where the result lands.
// A slot is just an item ref and a count here. Results can use what's in an output slot too, so one step's result can feed the next without moving anything by hand.
public final class MachineRules {
    public record Held(String ref, int count, int max) {
        public boolean empty() {
            return count <= 0 || ref == null || ref.isEmpty();
        }
    }

    // What running a step does to the slots.
    public record Plan(int[] take, int resultSlot) {}

    private MachineRules() {}

    // Null when the step can't run now.
    public static Plan plan(ScreenInfo.Recipe r, Held[] slots, boolean[] isOutput, int resultMax) {
        int[] take = new int[slots.length];
        for (ScreenInfo.Ingredient in : r.inputs) {
            int need = in.count;
            // ordinary slots first, output slots only for what's left
            for (int pass = 0; pass < 2 && need > 0; pass++) {
                for (int i = 0; i < slots.length && need > 0; i++) {
                    if (isOutput[i] != (pass == 1) || slots[i].empty() || !slots[i].ref().equals(in.item)) continue;
                    int have = slots[i].count() - take[i];
                    int use = Math.min(have, need);
                    take[i] += use;
                    need -= use;
                }
            }
            if (need > 0) return null;
        }
        int result = resultSlot(r, slots, isOutput, take, resultMax);
        if (result < 0) return null;
        return new Plan(take, result);
    }

    private static int resultSlot(ScreenInfo.Recipe r, Held[] slots, boolean[] isOutput, int[] take, int resultMax) {
        // first a slot already holding the same thing, then an empty one
        for (int i = 0; i < slots.length; i++) {
            if (!isOutput[i] || slots[i].empty() || !slots[i].ref().equals(r.output)) continue;
            int left = slots[i].count() - take[i];
            if (left > 0 && left + r.outputCount <= Math.min(resultMax, slots[i].max())) return i;
        }
        for (int i = 0; i < slots.length; i++) {
            if (isOutput[i] && (slots[i].empty() || slots[i].count() - take[i] <= 0) && r.outputCount <= resultMax) return i;
        }
        return -1;
    }

    // The first step that can run, or -1.
    public static int firstRunnable(List<ScreenInfo.Recipe> recipes, Held[] slots, boolean[] isOutput, java.util.function.ToIntFunction<String> maxOf) {
        for (int i = 0; i < recipes.size(); i++) {
            ScreenInfo.Recipe r = recipes.get(i);
            if (r.inputs.isEmpty()) continue;
            if (plan(r, slots, isOutput, maxOf.applyAsInt(r.output)) != null) return i;
        }
        return -1;
    }
}
