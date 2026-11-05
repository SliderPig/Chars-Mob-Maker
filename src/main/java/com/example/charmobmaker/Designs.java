package com.example.charmobmaker;

import com.example.charmobmaker.model.Design;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The designs known on each side. In singleplayer both sides live in one program, so they're kept apart here.
public final class Designs {
    public static final Map<String, Design> SERVER = new ConcurrentHashMap<>();
    public static final Map<String, Design> CLIENT = new ConcurrentHashMap<>();

    private Designs() {}

    public static Design of(Level level, String id) {
        if (id == null || id.isEmpty()) return null;
        return (level.isClientSide ? CLIENT : SERVER).get(id);
    }
}
