package com.kuilunfuzhe.monvhua.renderer.body;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class SkinOuterLayerVoxelRenderer {
    private static final float PIXEL_DEPTH = 0.25F;
    private static final Map<Identifier, SkinPixels> PIXEL_CACHE = new HashMap<>();

    private SkinOuterLayerVoxelRenderer() {
    }

    public static void renderHeadHat(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        renderCuboid(matrices, vertices, texture, light, overlay, 32, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8);
    }

    public static void renderJacket(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        renderCuboid(matrices, vertices, texture, light, overlay, 16, 32, -4.0F, -12.0F, -2.0F, 8, 12, 4);
    }

    public static void renderRightSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        renderCuboid(matrices, vertices, texture, light, overlay, 40, 32, -4.0F, -12.0F, -4.0F, slim ? 3 : 4, 12, 4);
    }

    public static void renderLeftSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        renderCuboid(matrices, vertices, texture, light, overlay, 48, 48, -4.0F, -12.0F, -4.0F, slim ? 3 : 4, 12, 4);
    }

    public static void renderRightPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        renderCuboid(matrices, vertices, texture, light, overlay, 0, 48, -2.0F, -12.0F, -2.0F, 4, 12, 4);
    }

    public static void renderLeftPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        renderCuboid(matrices, vertices, texture, light, overlay, 0, 32, -4.0F, -12.0F, -4.0F, 4, 12, 4);
    }

    private static void renderCuboid(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay,
                                     int textureX, int textureY, float x, float y, float z, int width, int height, int depth) {
        SkinPixels pixels = getPixels(texture);
        if (pixels == null) {
            return;
        }

        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY,
                width, depth, new Point(x, y, z), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, 1.0F), new Point(0.0F, -1.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY,
                width, depth, new Point(x, y + height, z + depth), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX, textureY + depth,
                depth, height, new Point(x, y, z + depth), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(-1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY + depth,
                width, height, new Point(x, y, z), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY + depth,
                depth, height, new Point(x + width, y, z), new Point(0.0F, 0.0F, 1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width + depth, textureY + depth,
                width, height, new Point(x + width, y, z + depth), new Point(-1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, 1.0F));
    }

    private static void renderFace(MatrixStack matrices, VertexConsumer vertices, SkinPixels pixels, int light, int overlay,
                                   int textureX, int textureY, int width, int height, Point origin, Point uStep, Point vStep, Point normal) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int pixelX = textureX + col;
                int pixelY = textureY + row;
                if (!pixels.isOpaque(pixelX, pixelY)) {
                    continue;
                }

                Point base00 = origin.add(uStep.multiply(col)).add(vStep.multiply(row));
                Point base10 = base00.add(uStep);
                Point base11 = base10.add(vStep);
                Point base01 = base00.add(vStep);
                Point offset = normal.multiply(PIXEL_DEPTH);
                Point outer00 = base00.add(offset);
                Point outer10 = base10.add(offset);
                Point outer11 = base11.add(offset);
                Point outer01 = base01.add(offset);

                float u0 = pixelX / (float) pixels.width();
                float v0 = pixelY / (float) pixels.height();
                float u1 = (pixelX + 1) / (float) pixels.width();
                float v1 = (pixelY + 1) / (float) pixels.height();
                emitTexturedQuad(matrices, vertices, light, overlay, outer00, outer10, outer11, outer01, normal, u0, v0, u1, v1);

                float centerU = (pixelX + 0.5F) / pixels.width();
                float centerV = (pixelY + 0.5F) / pixels.height();
                if (!pixels.isOpaque(pixelX, pixelY - 1)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer00, outer10, base10, base00, vStep.negate(), centerU, centerV);
                }
                if (!pixels.isOpaque(pixelX + 1, pixelY)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer10, outer11, base11, base10, uStep, centerU, centerV);
                }
                if (!pixels.isOpaque(pixelX, pixelY + 1)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer11, outer01, base01, base11, vStep, centerU, centerV);
                }
                if (!pixels.isOpaque(pixelX - 1, pixelY)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer01, outer00, base00, base01, uStep.negate(), centerU, centerV);
                }
            }
        }
    }

    private static void emitTexturedQuad(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                         Point p0, Point p1, Point p2, Point p3, Point normal,
                                         float u0, float v0, float u1, float v1) {
        if (p1.subtract(p0).cross(p2.subtract(p0)).dot(normal) >= 0.0F) {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u0, v0);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u1, v0);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u1, v1);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u0, v1);
        } else {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u0, v0);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u0, v1);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u1, v1);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u1, v0);
        }
    }

    private static void emitSolidUvQuad(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                        Point p0, Point p1, Point p2, Point p3, Point normal, float u, float v) {
        if (p1.subtract(p0).cross(p2.subtract(p0)).dot(normal) >= 0.0F) {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u, v);
        } else {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u, v);
        }
    }

    private static void emitVertex(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, Point point, Point normal, float u, float v) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        vertices.vertex(positionMatrix, point.x() / 16.0F, point.y() / 16.0F, point.z() / 16.0F)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal.x(), normal.y(), normal.z());
    }

    private static SkinPixels getPixels(Identifier textureId) {
        SkinPixels cached = PIXEL_CACHE.get(textureId);
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
            // Try the resource manager below.
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

        SkinPixels pixels = new SkinPixels(width, height, argb);
        PIXEL_CACHE.put(textureId, pixels);
        return pixels;
    }

    private record SkinPixels(int width, int height, int[] argb) {
        boolean isOpaque(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return false;
            }
            return ((argb[y * width + x] >> 24) & 0xFF) >= 128;
        }
    }

    private record Point(float x, float y, float z) {
        Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point multiply(float scalar) {
            return new Point(x * scalar, y * scalar, z * scalar);
        }

        Point negate() {
            return new Point(-x, -y, -z);
        }

        Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }
}
