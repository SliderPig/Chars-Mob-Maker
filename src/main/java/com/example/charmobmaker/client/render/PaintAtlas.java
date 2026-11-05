package com.example.charmobmaker.client.render;

import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Paints;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicInteger;

// A design's painted cells, on one small texture of its own. 4 x 4 cells of 16 x 16.
public final class PaintAtlas {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static final int ACROSS = 4, SIZE = ACROSS * Paints.CELL;
    private final DynamicTexture texture;
    private final ResourceLocation location;
    private final int cells;

    PaintAtlas(Design d) {
        cells = Math.min(d.paints.size(), ACROSS * ACROSS);
        texture = new DynamicTexture(SIZE, SIZE, false);
        location = new ResourceLocation("charmobmaker", "paint/" + NEXT.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(location, texture);
        update(d);
    }

    public void update(Design d) {
        NativeImage img = texture.getPixels();
        if (img == null) return;
        for (int c = 0; c < cells && c < d.paints.size(); c++) {
            int[] cell = d.paints.get(c);
            int ox = (c % ACROSS) * Paints.CELL, oy = (c / ACROSS) * Paints.CELL;
            for (int y = 0; y < Paints.CELL; y++) {
                for (int x = 0; x < Paints.CELL; x++) {
                    int argb = cell[y * Paints.CELL + x];
                    // the image wants ABGR
                    img.setPixelRGBA(ox + x, oy + y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
                }
            }
        }
        texture.upload();
    }

    int cells() {
        return cells;
    }

    float u(int cell) {
        return (cell % ACROSS) / (float) ACROSS;
    }

    float v(int cell) {
        return (cell / ACROSS) / (float) ACROSS;
    }

    float cellSize() {
        return 1f / ACROSS;
    }

    ResourceLocation location() {
        return location;
    }

    void release() {
        Minecraft.getInstance().getTextureManager().release(location);
    }
}
