package com.example.charmobmaker.model;

import java.util.Locale;
import java.util.Set;

// Turns a mob's name into its file name. Only a to z, 0 to 9 and _ ever get through, so a design can never point outside its folder.
public final class Ids {
    private static final Set<String> WINDOWS_RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private Ids() {}

    public static String slug(String name) {
        StringBuilder b = new StringBuilder();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && b.length() < 40; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
            else if (b.length() > 0 && b.charAt(b.length() - 1) != '_') b.append('_');
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '_') b.setLength(b.length() - 1);
        String s = b.toString();
        if (s.isEmpty()) return "mob";
        return WINDOWS_RESERVED.contains(s) ? "mob_" + s : s;
    }

    public static boolean valid(String id) {
        if (id == null || id.isEmpty() || id.length() > 48 || WINDOWS_RESERVED.contains(id)) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) return false;
        }
        return true;
    }

    // minecraft:stone and friends
    public static boolean resource(String s) {
        if (s == null || s.isEmpty() || s.length() > 128) return false;
        int colon = s.indexOf(':');
        if (colon <= 0 || colon != s.lastIndexOf(':') || colon == s.length() - 1) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == ':' || i > colon && c == '/';
            if (!ok) return false;
        }
        return !s.contains("..");
    }

    // minecraft:oak_stairs[facing=east,half=top]
    public static boolean blockState(String s) {
        if (s == null || s.isEmpty() || s.length() > 192) return false;
        int open = s.indexOf('[');
        String block = open < 0 ? s : s.substring(0, open);
        if (!resource(block)) return false;
        if (open < 0) return true;
        if (!s.endsWith("]")) return false;
        for (int i = open + 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '=' || c == ',')) return false;
        }
        return true;
    }
}
