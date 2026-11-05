package com.example.charmobmaker.client.editor;

import com.example.charmobmaker.model.BbModel;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Ids;
import com.example.charmobmaker.model.Limits;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Mob files on this PC, for sharing: <game folder>/charmobmaker/designs. Also takes Blockbench .bbmodel files dropped in there.
public final class DesignFiles {
    private DesignFiles() {}

    public static Path folder() throws IOException {
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("charmobmaker").resolve("designs");
        Files.createDirectories(p);
        return p;
    }

    public static Path export(Design d) throws IOException {
        Path file = folder().resolve(Ids.slug(d.name) + ".json");
        Design copy = d.copy();
        copy.id = "";
        copy.owner = "";
        Files.writeString(file, DesignJson.write(copy), StandardCharsets.UTF_8);
        return file;
    }

    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder())) {
            for (Path f : files) {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(".json") || n.endsWith(".bbmodel")) out.add(f);
            }
        } catch (IOException e) {
            return out;
        }
        out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return out;
    }

    // A file read this way starts as a new mob, and has to pass the same checks as any other.
    public static Design read(Path file) throws IOException, DesignJson.BadDesign {
        if (Files.size(file) > Limits.MAX_JSON_BYTES * 4L) throw new DesignJson.BadDesign("too big");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        Design d;
        if (name.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
            d = BbModel.read(text, name.substring(0, name.lastIndexOf('.')));
        } else {
            d = DesignJson.read(text);
        }
        DesignRules.tidy(d);
        d.id = "";
        d.owner = "";
        return d;
    }

    public static void openFolder() {
        try {
            Util.getPlatform().openFile(folder().toFile());
        } catch (IOException ignored) {
            // nothing to open
        }
    }
}
