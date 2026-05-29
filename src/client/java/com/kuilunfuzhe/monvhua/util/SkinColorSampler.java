package com.kuilunfuzhe.monvhua.util;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SkinColorSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("monvhua-skin-color");
    private static final Map<Identifier, OuterLayerColors> colorCache = new HashMap<>();
    private static final Identifier VANILLA_WHITE = Identifier.ofVanilla("textures/block/torso.png");

    public record OuterLayerColors(int hat, int jacket, int leftSleeve, int rightSleeve, int leftPants, int rightPants) {}

    @Nullable
    public static OuterLayerColors getOrSample(Identifier skinTexture) {
        if (skinTexture == null || skinTexture.equals(VANILLA_WHITE)) {
            return null;
        }
        OuterLayerColors cached = colorCache.get(skinTexture);
        if (cached != null) return cached;
        OuterLayerColors sampled = sampleColors(skinTexture);
        if (sampled != null) {
            colorCache.put(skinTexture, sampled);
        }
        return sampled;
    }

    public static void clearCache() {
        colorCache.clear();
    }

    @Nullable
    private static OuterLayerColors sampleColors(Identifier textureId) {
        NativeImage ownedImage = null;
        NativeImage image = null;

        try {
            AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            if (tex instanceof NativeImageBackedTexture nib) {
                image = nib.getImage();
            }
        } catch (Exception e) {
            // Not in TextureManager, will try resource system
        }

        if (image == null) {
            try {
                var resourceOpt = MinecraftClient.getInstance().getResourceManager().getResource(textureId);
                if (resourceOpt.isPresent()) {
                    ownedImage = NativeImage.read(resourceOpt.get().getInputStream());
                    image = ownedImage;
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to load skin texture for color sampling: {}", textureId);
                return null;
            }
        }

        if (image == null || image.getWidth() < 64 || image.getHeight() < 64) {
            if (ownedImage != null) ownedImage.close();
            return null;
        }

        OuterLayerColors colors = new OuterLayerColors(
            averageColorArgb(image, 32, 0, 64, 16),     // hat
            averageColorArgb(image, 16, 32, 40, 48),     // jacket
            averageColorArgb(image, 48, 48, 64, 64),     // left sleeve
            averageColorArgb(image, 40, 32, 56, 48),     // right sleeve
            averageColorArgb(image, 0, 48, 16, 64),      // left pants
            averageColorArgb(image, 0, 32, 16, 48)       // right pants
        );

        if (ownedImage != null) {
            ownedImage.close();
        }

        return colors;
    }

    private static int averageColorArgb(NativeImage image, int x1, int y1, int x2, int y2) {
        long r = 0, g = 0, b = 0;
        int count = 0;
        int w = image.getWidth();
        int h = image.getHeight();

        x1 = Math.max(0, Math.min(x1, w));
        y1 = Math.max(0, Math.min(y1, h));
        x2 = Math.max(0, Math.min(x2, w));
        y2 = Math.max(0, Math.min(y2, h));

        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int argb = image.getColorArgb(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 128) continue;
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            return 0xFF808080;
        }

        int ri = (int) (r / count);
        int gi = (int) (g / count);
        int bi = (int) (b / count);
        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
