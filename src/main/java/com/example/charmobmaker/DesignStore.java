package com.example.charmobmaker;

import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Ids;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Packer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Keeps designs as JSON files inside the world: <world>/charmobmaker/designs/<id>.json
public final class DesignStore {
    public record Saved(boolean ok, String id, String message) {}

    private static MinecraftServer server;
    private static Path folder;
    private static final Map<String, byte[]> PACKED = new ConcurrentHashMap<>();

    private DesignStore() {}

    public static void start(MinecraftServer s) {
        server = s;
        Designs.SERVER.clear();
        PACKED.clear();
        folder = s.getWorldPath(LevelResource.ROOT).resolve("charmobmaker").resolve("designs");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            CharMobMaker.LOG.error("Can't make the designs folder {}", folder, e);
        }
        addStarters();
        loadAll();
        // mobs from chunks that loaded before this point
        for (ServerLevel level : s.getAllLevels()) {
            for (var e : level.getAllEntities()) if (e instanceof CustomMob m) m.applyDesign();
        }
        CharMobMaker.LOG.info("[MobMaker] {} mob designs ready", Designs.SERVER.size());
    }

    public static void stop() {
        server = null;
        folder = null;
        Designs.SERVER.clear();
        PACKED.clear();
    }

    // The ready-made mobs come along once per world. Delete one and it stays deleted.
    private static void addStarters() {
        Path marker = folder.resolve(".starters_added");
        if (Files.exists(marker)) return;
        var found = server.getResourceManager().listResources("charmobmaker_starters", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> e : found.entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
                Design d = DesignJson.read(b.toString());
                DesignRules.tidy(d);
                if (!Ids.valid(d.id)) d.id = Ids.slug(d.name);
                Path file = fileOf(d.id);
                if (file != null && !Files.exists(file)) write(file, DesignJson.write(d));
            } catch (IOException | DesignJson.BadDesign ex) {
                CharMobMaker.LOG.warn("[MobMaker] Starter {} didn't load: {}", e.getKey(), ex.getMessage());
            }
        }
        try {
            Files.writeString(marker, "These mobs came with the mod. Delete this file to get any deleted ones back.\n");
        } catch (IOException ex) {
            CharMobMaker.LOG.warn("[MobMaker] Can't write {}", marker);
        }
    }

    public static void loadAll() {
        Designs.SERVER.clear();
        PACKED.clear();
        if (folder == null || !Files.isDirectory(folder)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if (!Ids.valid(id)) {
                    CharMobMaker.LOG.warn("[MobMaker] Skipping {}: a design's file name can only use a-z 0-9 _", name);
                    continue;
                }
                if (Files.size(f) > Limits.MAX_JSON_BYTES) {
                    CharMobMaker.LOG.warn("[MobMaker] Skipping {}: too big", name);
                    continue;
                }
                try {
                    Design d = DesignJson.read(Files.readString(f, StandardCharsets.UTF_8));
                    DesignRules.tidy(d);
                    d.id = id;
                    keep(d);
                } catch (DesignJson.BadDesign | IOException ex) {
                    CharMobMaker.LOG.warn("[MobMaker] Skipping {}: {}", name, ex.getMessage());
                }
            }
        } catch (IOException e) {
            CharMobMaker.LOG.error("[MobMaker] Can't read the designs folder", e);
        }
    }

    private static void keep(Design d) {
        Designs.SERVER.put(d.id, d);
        PACKED.put(d.id, Packer.pack(DesignJson.write(d)));
    }

    // Never points anywhere but straight inside the folder.
    private static Path fileOf(String id) {
        if (folder == null || !Ids.valid(id)) return null;
        Path file = folder.resolve(id + ".json").normalize();
        return file.getParent().equals(folder.normalize()) ? file : null;
    }

    private static void write(Path file, String text) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // An upload from a player: never trusted. Tidied to the limits, the server picks the file name.
    public static Saved save(ServerPlayer who, String json) {
        if (server == null) return new Saved(false, "", "The server isn't ready.");
        if (!Access.canEdit(who)) return new Saved(false, "", Access.refusal());
        Design d;
        try {
            d = DesignJson.read(json);
        } catch (DesignJson.BadDesign e) {
            return new Saved(false, "", "That design couldn't be read: " + e.getMessage());
        }
        DesignRules.tidy(d);
        Design existing = Ids.valid(d.id) ? Designs.SERVER.get(d.id) : null;
        if (existing != null && !Access.canChange(who, existing)) return new Saved(false, d.id, "That mob belongs to someone else. Save a copy instead.");
        Saved saved = store(d, who.getStringUUID(), who.getGameProfile().getName());
        if (saved.ok()) CharMobMaker.LOG.info("[MobMaker] {} saved design {} ({} parts)", who.getGameProfile().getName(), saved.id(), d.parts.size());
        return saved;
    }

    // Tidies and keeps a design: a known id overwrites it, anything else gets a fresh id from its name.
    public static Saved store(Design d, String owner, String author) {
        if (server == null) return new Saved(false, "", "The server isn't ready.");
        DesignRules.tidy(d);
        String id = d.id;
        Design existing = Ids.valid(id) ? Designs.SERVER.get(id) : null;
        if (existing != null) {
            d.owner = existing.owner;
            d.author = existing.author;
        } else {
            if (Designs.SERVER.size() >= Config.MAX_DESIGNS.get()) return new Saved(false, "", "This world is full of mob designs (" + Config.MAX_DESIGNS.get() + "). Delete some first.");
            id = freeId(Ids.slug(d.name));
            d.owner = owner;
            d.author = DesignRules.cleanName(author, 32, "");
        }
        d.id = id;
        Path file = fileOf(id);
        if (file == null) return new Saved(false, "", "That name can't be used.");
        try {
            write(file, DesignJson.write(d));
        } catch (IOException e) {
            CharMobMaker.LOG.error("[MobMaker] Can't save {}", file, e);
            return new Saved(false, id, "Couldn't save on the server.");
        }
        keep(d);
        broadcast(id);
        refreshMobs(id);
        return new Saved(true, id, "Saved " + d.name);
    }

    private static String freeId(String base) {
        if (!Designs.SERVER.containsKey(base) && !Files.exists(fileOf(base))) return base;
        for (int i = 2; ; i++) {
            String s = base + "_" + i;
            if (!Designs.SERVER.containsKey(s) && !Files.exists(fileOf(s))) return s;
        }
    }

    public static boolean delete(String id) {
        Path file = fileOf(id);
        if (file == null || !Designs.SERVER.containsKey(id)) return false;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            CharMobMaker.LOG.error("[MobMaker] Can't delete {}", file, e);
            return false;
        }
        Designs.SERVER.remove(id);
        PACKED.remove(id);
        Net.sendGone(id);
        refreshMobs(id);
        return true;
    }

    public static void reload() {
        if (folder == null) return;
        loadAll();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendAll(p);
        for (ServerLevel level : server.getAllLevels()) {
            for (var e : level.getAllEntities()) if (e instanceof CustomMob m) m.applyDesign();
        }
    }

    public static void sendAll(ServerPlayer p) {
        Net.sendReset(p);
        for (Map.Entry<String, byte[]> e : PACKED.entrySet()) Net.sendDesign(p, e.getKey(), e.getValue());
    }

    private static void broadcast(String id) {
        byte[] data = PACKED.get(id);
        if (server == null || data == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) Net.sendDesign(p, id, data);
    }

    private static void refreshMobs(String id) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (var e : level.getAllEntities()) if (e instanceof CustomMob m && id.equals(m.designId())) m.applyDesign();
        }
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>(Designs.SERVER.keySet());
        out.sort(String::compareTo);
        return out;
    }
}
