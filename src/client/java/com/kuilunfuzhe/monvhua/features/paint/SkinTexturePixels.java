package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class SkinTexturePixels {
    private static final Map<Identifier, SkinTexturePixels> CACHE = new HashMap<>();

    private final int width;
    private final int height;
    private final int[] argb;

    private SkinTexturePixels(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public static SkinTexturePixels get(Identifier textureId) {
        SkinTexturePixels cached = CACHE.get(textureId);
        if (cached != null) {
            return cached;
        }

        NativeImage ownedImage = null;
        NativeImage image = null;
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                image = nativeTexture.getImage();
            }
        } catch (Exception ignored) {
        }

        if (image == null) {
            try {
                var resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);
                if (resource.isPresent()) {
                    ownedImage = NativeImage.read(resource.get().getInputStream());
                    image = ownedImage;
                }
            } catch (IOException ignored) {
                return null;
            }
        }

        if (image == null || image.getWidth() < 64 || image.getHeight() < 64) {
            if (ownedImage != null) {
                ownedImage.close();
            }
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = image.getColorArgb(x, y);
            }
        }

        if (ownedImage != null) {
            ownedImage.close();
        }

        SkinTexturePixels pixels = new SkinTexturePixels(width, height, argb);
        CACHE.put(textureId, pixels);
        return pixels;
    }

    public boolean isOpaque(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return ((argb[y * width + x] >>> 24) & 0xFF) >= 128;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int getArgb(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return argb[y * width + x];
    }
}
