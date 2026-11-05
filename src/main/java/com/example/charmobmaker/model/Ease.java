package com.example.charmobmaker.model;

public enum Ease {
    LINEAR("Linear"), SMOOTH("Smooth"), IN("Slow start"), OUT("Slow stop"), STEP("Snap"), BOUNCE("Bounce");

    public final String label;

    Ease(String label) {
        this.label = label;
    }

    public static Ease of(String s) {
        for (Ease e : values()) if (e.name().equalsIgnoreCase(s)) return e;
        return SMOOTH;
    }

    // a goes 0 to 1, the answer is how far along the move is
    public float apply(float a) {
        if (a <= 0f) return 0f;
        if (a >= 1f) return 1f;
        switch (this) {
            case LINEAR:
                return a;
            case SMOOTH:
                return a * a * (3f - 2f * a);
            case IN:
                return a * a;
            case OUT:
                return 1f - (1f - a) * (1f - a);
            case STEP:
                return 0f;
            case BOUNCE:
            default:
                // gets there, then two small hops
                if (a < 0.5f) return 4f * a * a;
                if (a < 0.8f) {
                    float b = (a - 0.65f) / 0.15f;
                    return 1f - 0.12f * (1f - b * b);
                }
                float c = (a - 0.9f) / 0.1f;
                return 1f - 0.03f * (1f - c * c);
        }
    }
}
