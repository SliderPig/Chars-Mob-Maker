package com.example.charmobmaker.model;

// How designs name an item: minecraft:sugar for a real one, design:energy_drink for one made here.
public final class ItemRef {
    public static final String DESIGN = "design:";

    private ItemRef() {}

    public static boolean isDesign(String ref) {
        return ref != null && ref.startsWith(DESIGN);
    }

    public static String designId(String ref) {
        return ref.substring(DESIGN.length());
    }

    public static String ofDesign(String id) {
        return DESIGN + id;
    }

    public static boolean valid(String ref) {
        if (ref == null || ref.isEmpty()) return false;
        return isDesign(ref) ? Ids.valid(designId(ref)) : Ids.resource(ref);
    }
}
