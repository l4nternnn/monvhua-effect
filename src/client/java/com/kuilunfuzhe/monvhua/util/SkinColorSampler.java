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

/**
 * 皮肤纹理像素采样工具。
 * 从玩家皮肤的64x64纹理中采样外层各区域的平均颜色，用于染色/渲染效果。
 * 采样结果缓存于colorCache中，避免重复读取纹理。
 */
public class SkinColorSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("monvhua-skin-color");
    /** 采样结果缓存：纹理ID → 外层各区域平均颜色 */
    private static final Map<Identifier, OuterLayerColors> colorCache = new HashMap<>();
    /** 原版白色占位纹理，对此纹理跳过采样 */
    private static final Identifier VANILLA_WHITE = Identifier.ofVanilla("textures/block/torso.png");

    /**
     * 皮肤外层各区域平均颜色记录。
     * @param hat 帽子区域平均色（ARGB）
     * @param jacket 夹克/身体外层区域平均色
     * @param leftSleeve 左袖区域平均色
     * @param rightSleeve 右袖区域平均色
     * @param leftPants 左裤腿区域平均色
     * @param rightPants 右裤腿区域平均色
     */
    public record OuterLayerColors(int hat, int jacket, int leftSleeve, int rightSleeve, int leftPants, int rightPants) {}

    /**
     * 获取或采样指定皮肤纹理的外层颜色。
     * 先查缓存；未命中则读取纹理采样，结果写入缓存并返回。
     * @return 各区域平均颜色，若纹理无效或为占位纹理则返回null
     */
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

    /** 清空采样缓存（皮肤重载时调用） */
    public static void clearCache() {
        colorCache.clear();
    }

    /**
     * 从纹理中采样6个外层区域的平均颜色。
     * 纹理布局基于Minecraft标准64x64皮肤格式（半臂模型旧版区域）：
     *   帽子 (hat)         : (32,0)  到 (64,16)
     *   身体外层 (jacket) : (16,32) 到 (40,48)
     *   左袖 (leftSleeve) : (48,48) 到 (64,64)
     *   右袖 (rightSleeve): (40,32) 到 (56,48)
     *   左裤 (leftPants)  : (0,48)  到 (16,64)
     *   右裤 (rightPants) : (0,32)  到 (16,48)
     */
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

    /**
     * 计算指定矩形区域内的平均颜色（仅统计不透明像素，alpha >= 128）。
     * @return ARGB格式的平均色，若无有效像素则返回中性灰色(0xFF808080)
     */
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
            return 0xFF808080; // 无有效像素时返回中性灰色
        }

        int ri = (int) (r / count);
        int gi = (int) (g / count);
        int bi = (int) (b / count);
        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
