package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.UUID;

final class DissolveTextureController {
    private final UUID targetUuid;
    private Identifier baseTextureId;
    private Identifier skinTextureId;
    private Identifier edgeTextureId;
    private NativeImageBackedTexture skinTexture;
    private NativeImageBackedTexture edgeTexture;
    private int imageWidth;
    private int imageHeight;
    private int[] basePixels = new int[0];
    private int lastUploadedElapsed = Integer.MIN_VALUE;

    DissolveTextureController(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    boolean ensureBase(Identifier textureId) {
        if (textureId == null || textureId.equals(skinTextureId) || textureId.equals(edgeTextureId)) {
            return skinTexture != null && edgeTexture != null;
        }
        if (textureId.equals(baseTextureId) && skinTexture != null && edgeTexture != null) {
            return true;
        }
        NativeImage baseImage = loadTexture(textureId);
        if (baseImage == null) {
            return false;
        }
        destroy();
        baseTextureId = textureId;
        imageWidth = baseImage.getWidth();
        imageHeight = baseImage.getHeight();
        basePixels = new int[imageWidth * imageHeight];
        NativeImage skinImage = new NativeImage(imageWidth, imageHeight, false);
        NativeImage edgeImage = new NativeImage(imageWidth, imageHeight, false);
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int argb = baseImage.getColorArgb(x, y);
                basePixels[y * imageWidth + x] = argb;
                skinImage.setColorArgb(x, y, argb);
                edgeImage.setColorArgb(x, y, 0);
            }
        }
        baseImage.close();
        String hash = Integer.toUnsignedString(Objects.hash(targetUuid.toString()), 16);
        skinTextureId = Identifier.of("monvhua", "dynamic/dissolve/skin/" + hash);
        edgeTextureId = Identifier.of("monvhua", "dynamic/dissolve/edge/" + hash);
        skinTexture = new NativeImageBackedTexture(() -> "monvhua dissolve skin " + targetUuid, skinImage);
        edgeTexture = new NativeImageBackedTexture(() -> "monvhua dissolve edge " + targetUuid, edgeImage);
        MinecraftClient client = MinecraftClient.getInstance();
        client.getTextureManager().registerTexture(skinTextureId, skinTexture);
        client.getTextureManager().registerTexture(edgeTextureId, edgeTexture);
        lastUploadedElapsed = Integer.MIN_VALUE;
        return true;
    }

    void update(int elapsedTicks, DissolveProfile profile, long seed) {
        if (skinTexture == null || edgeTexture == null || basePixels.length == 0) {
            return;
        }
        int interval = Math.max(1, profile.textureUploadIntervalTicks());
        if (lastUploadedElapsed != Integer.MIN_VALUE
                && elapsedTicks < profile.durationTicks()
                && Math.abs(elapsedTicks - lastUploadedElapsed) < interval) {
            return;
        }
        NativeImage skinImage = skinTexture.getImage();
        NativeImage edgeImage = edgeTexture.getImage();
        if (skinImage == null || edgeImage == null) {
            return;
        }
        int edgeAlpha = Math.max(0, Math.min(255, Math.round(profile.edgeAlpha() * 255.0F)));
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int index = y * imageWidth + x;
                int base = basePixels[index];
                int baseAlpha = (base >>> 24) & 0xFF;
                if (baseAlpha == 0) {
                    skinImage.setColorArgb(x, y, 0);
                    edgeImage.setColorArgb(x, y, 0);
                    continue;
                }
                DissolveMaskGenerator.Mask mask = DissolveMaskGenerator.mask(
                        x,
                        y,
                        imageWidth,
                        imageHeight,
                        elapsedTicks,
                        seed,
                        profile
                );
                skinImage.setColorArgb(x, y, mask.transparent() ? 0 : base);
                edgeImage.setColorArgb(x, y, mask.edge() ? ((edgeAlpha << 24) | 0x00FFFFFF) : 0);
            }
        }
        skinTexture.upload();
        edgeTexture.upload();
        lastUploadedElapsed = elapsedTicks;
    }

    Identifier skinTextureId() {
        return skinTextureId;
    }

    Identifier edgeTextureId() {
        return edgeTextureId;
    }

    void destroy() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (skinTextureId != null) {
            client.getTextureManager().destroyTexture(skinTextureId);
        }
        if (edgeTextureId != null) {
            client.getTextureManager().destroyTexture(edgeTextureId);
        }
        skinTextureId = null;
        edgeTextureId = null;
        skinTexture = null;
        edgeTexture = null;
        baseTextureId = null;
        imageWidth = 0;
        imageHeight = 0;
        basePixels = new int[0];
        lastUploadedElapsed = Integer.MIN_VALUE;
    }

    private static NativeImage loadTexture(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            AbstractTexture texture = client.getTextureManager().getTexture(textureId);
            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                NativeImage image = nativeTexture.getImage();
                if (image != null) {
                    NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
                    copy.copyFrom(image);
                    return copy;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            var resource = client.getResourceManager().getResource(textureId);
            if (resource.isPresent()) {
                return NativeImage.read(resource.get().getInputStream());
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
