package com.example.charmobmaker;

import com.example.charmobmaker.model.Design;
import net.minecraft.server.level.ServerPlayer;

// Who may use the editor: anyone in their own singleplayer world, ops on a server, everyone if the config says so.
public final class Access {
    private Access() {}

    public static boolean canEdit(ServerPlayer p) {
        if (p.getServer() == null) return false;
        return isBoss(p) || Config.ALLOW_EVERYONE.get();
    }

    private static boolean isBoss(ServerPlayer p) {
        return p.getServer().isSingleplayerOwner(p.getGameProfile()) || p.hasPermissions(2);
    }

    // Changing or deleting one that's already there: ops can, others only their own.
    public static boolean canChange(ServerPlayer p, Design existing) {
        if (!canEdit(p)) return false;
        if (isBoss(p) || existing == null) return true;
        return p.getStringUUID().equalsIgnoreCase(existing.owner);
    }

    public static String refusal() {
        return "Only ops can use the Mob Maker on this server.";
    }
}
