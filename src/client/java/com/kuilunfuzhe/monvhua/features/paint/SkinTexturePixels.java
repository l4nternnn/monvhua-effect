package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SkinTexturePixels {
    private static final List<String> PAINT_SURFACES = List.of(
            "head",
            "body_upper", "body_lower",
            "left_arm_upper", "left_arm_lower", "right_arm_upper", "right_arm_lower",
            "left_leg_upper", "left_leg_lower", "right_leg_upper", "right_leg_lower"
    );
    private static final Map<Identifier, SkinTexturePixels> CACHE = new HashMap<>();
    private static final Map<PaintedSkinKey, PaintedSkinTexture> PAINTED_CACHE = new HashMap<>();

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

    public static PaintedSkinTexture getWithModelPaint(Identifier textureId, NbtCompound customData, boolean slim) {
        SkinTexturePixels base = get(textureId);
        if (base == null || customData == null || !customData.contains(ModelPaintData.MODEL_PAINT_KEY)) {
            return new PaintedSkinTexture(textureId, base);
        }

        List<ModelPaintData.ModelFace> faces = ModelPaintData.readFaces(customData, slim);
        if (faces.isEmpty()) {
            return new PaintedSkinTexture(textureId, base);
        }

        int paintHash = paintHash(faces);
        PaintedSkinKey key = new PaintedSkinKey(textureId, slim, paintHash);
        PaintedSkinTexture cached = PAINTED_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        int[] paintedArgb = Arrays.copyOf(base.argb, base.argb.length);
        if (!applyModelPaint(paintedArgb, base.width, base.height, faces, slim)) {
            return new PaintedSkinTexture(textureId, base);
        }

        NativeImage image = new NativeImage(base.width, base.height, false);
        for (int y = 0; y < base.height; y++) {
            for (int x = 0; x < base.width; x++) {
                image.setColorArgb(x, y, paintedArgb[y * base.width + x]);
            }
        }

        Identifier paintedTextureId = Identifier.of("monvhua", "dynamic/painted_skin/"
                + Integer.toUnsignedString(Objects.hash(textureId.toString(), slim, paintHash), 16));
        NativeImageBackedTexture paintedTexture = new NativeImageBackedTexture(
                () -> "monvhua painted true skeletal skin", image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(paintedTextureId, paintedTexture);

        PaintedSkinTexture result = new PaintedSkinTexture(paintedTextureId,
                new SkinTexturePixels(base.width, base.height, paintedArgb));
        PAINTED_CACHE.put(key, result);
        return result;
    }

    public static PaintSurfacePixel paintSurfacePixel(float u, float v, boolean outer, boolean slim) {
        if (u < -0.0001F || v < -0.0001F || u > 1.0001F || v > 1.0001F) {
            return null;
        }
        int textureX = clamp((int) Math.floor(u * 64.0F), 0, 63);
        int textureY = clamp((int) Math.floor(v * 64.0F), 0, 63);
        for (String surface : PAINT_SURFACES) {
            SurfaceTexture surfaceTexture = surfaceTexture(surface, outer, slim);
            if (surfaceTexture == null) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                TextureFace face = surfaceTexture.face(direction);
                if (face == null || !contains(face, textureX, textureY)) {
                    continue;
                }
                String surfaceName = outer ? surface + "_outer_" + direction.asString() : surface;
                ModelPaintData.FaceSize size = ModelPaintData.size(surfaceName, direction, slim);
                int x = clamp((int) ((textureX - face.x()) * size.width() / (float) face.width()), 0, size.width() - 1);
                int y = clamp((int) ((textureY - face.y()) * size.height() / (float) face.height()), 0, size.height() - 1);
                return new PaintSurfacePixel(surfaceName, direction, x, y);
            }
        }
        return null;
    }

    private static int paintHash(List<ModelPaintData.ModelFace> faces) {
        int hash = 1;
        for (ModelPaintData.ModelFace face : faces) {
            hash = 31 * hash + face.surface().hashCode();
            hash = 31 * hash + face.face().getIndex();
            hash = 31 * hash + face.width();
            hash = 31 * hash + face.height();
            hash = 31 * hash + Arrays.hashCode(face.pixels());
        }
        return hash;
    }

    private static boolean applyModelPaint(int[] targetArgb, int imageWidth, int imageHeight,
                                           List<ModelPaintData.ModelFace> faces, boolean slim) {
        boolean changed = false;
        for (ModelPaintData.ModelFace face : faces) {
            TextureFace textureFace = textureFaceFor(face.surface(), face.face(), slim);
            if (textureFace == null) {
                continue;
            }
            int paintWidth = Math.max(1, face.width());
            int paintHeight = Math.max(1, face.height());
            for (int y = 0; y < paintHeight; y++) {
                for (int x = 0; x < paintWidth; x++) {
                    int color = face.pixels()[y * paintWidth + x];
                    if (((color >>> 24) & 0xFF) == 0) {
                        continue;
                    }
                    if (paintTexturePixel(targetArgb, imageWidth, imageHeight, textureFace, x, y,
                            paintWidth, paintHeight, color)) {
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean paintTexturePixel(int[] targetArgb, int imageWidth, int imageHeight, TextureFace face,
                                             int x, int y, int paintWidth, int paintHeight, int color) {
        float u0 = (face.x() + x * face.width() / (float) paintWidth) / 64.0F;
        float u1 = (face.x() + (x + 1) * face.width() / (float) paintWidth) / 64.0F;
        float v0 = (face.y() + y * face.height() / (float) paintHeight) / 64.0F;
        float v1 = (face.y() + (y + 1) * face.height() / (float) paintHeight) / 64.0F;
        int px0 = clamp((int) Math.floor(u0 * imageWidth), 0, imageWidth - 1);
        int px1 = clamp((int) Math.ceil(u1 * imageWidth) - 1, 0, imageWidth - 1);
        int py0 = clamp((int) Math.floor(v0 * imageHeight), 0, imageHeight - 1);
        int py1 = clamp((int) Math.ceil(v1 * imageHeight) - 1, 0, imageHeight - 1);
        boolean changed = false;
        for (int py = py0; py <= py1; py++) {
            for (int px = px0; px <= px1; px++) {
                int index = py * imageWidth + px;
                int blended = blendOver(targetArgb[index], color);
                if (targetArgb[index] != blended) {
                    targetArgb[index] = blended;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static int blendOver(int base, int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha >= 250) {
            return color | 0xFF000000;
        }
        int inverse = 255 - alpha;
        int red = (((color >>> 16) & 0xFF) * alpha + ((base >>> 16) & 0xFF) * inverse) / 255;
        int green = (((color >>> 8) & 0xFF) * alpha + ((base >>> 8) & 0xFF) * inverse) / 255;
        int blue = ((color & 0xFF) * alpha + (base & 0xFF) * inverse) / 255;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static TextureFace textureFaceFor(String surface, Direction face, boolean slim) {
        ParsedSurface parsedSurface = ParsedSurface.parse(surface);
        if (parsedSurface == null) {
            return null;
        }
        SurfaceTexture surfaceTexture = surfaceTexture(parsedSurface.baseSurface(), parsedSurface.outer(), slim);
        if (surfaceTexture == null) {
            return null;
        }
        Direction textureDirection = parsedSurface.sourceFace() != null ? parsedSurface.sourceFace() : face;
        return surfaceTexture.face(textureDirection);
    }

    private static SurfaceTexture surfaceTexture(String surface, boolean outer, boolean slim) {
        int armWidth = slim ? 3 : 4;
        if (outer) {
            return switch (surface) {
                case "head" -> new SurfaceTexture(32, 0, 0, 8, 8, 8, true, true);
                case "body_upper" -> new SurfaceTexture(16, 32, 0, 8, 6, 4, true, false);
                case "body_lower" -> new SurfaceTexture(16, 32, 6, 8, 6, 4, false, true);
                case "left_arm_upper" -> new SurfaceTexture(48, 48, 0, armWidth, 6, 4, true, false);
                case "left_arm_lower" -> new SurfaceTexture(48, 48, 6, armWidth, 6, 4, false, true);
                case "right_arm_upper" -> new SurfaceTexture(40, 32, 0, armWidth, 6, 4, true, false);
                case "right_arm_lower" -> new SurfaceTexture(40, 32, 6, armWidth, 6, 4, false, true);
                case "left_leg_upper" -> new SurfaceTexture(0, 48, 0, 4, 6, 4, true, false);
                case "left_leg_lower" -> new SurfaceTexture(0, 48, 6, 4, 6, 4, false, true);
                case "right_leg_upper" -> new SurfaceTexture(0, 32, 0, 4, 6, 4, true, false);
                case "right_leg_lower" -> new SurfaceTexture(0, 32, 6, 4, 6, 4, false, true);
                default -> null;
            };
        }
        return switch (surface) {
            case "head" -> new SurfaceTexture(0, 0, 0, 8, 8, 8, true, true);
            case "body_upper" -> new SurfaceTexture(16, 16, 0, 8, 6, 4, true, false);
            case "body_lower" -> new SurfaceTexture(16, 16, 6, 8, 6, 4, false, true);
            case "left_arm_upper" -> new SurfaceTexture(32, 48, 0, armWidth, 6, 4, true, false);
            case "left_arm_lower" -> new SurfaceTexture(32, 48, 6, armWidth, 6, 4, false, true);
            case "right_arm_upper" -> new SurfaceTexture(40, 16, 0, armWidth, 6, 4, true, false);
            case "right_arm_lower" -> new SurfaceTexture(40, 16, 6, armWidth, 6, 4, false, true);
            case "left_leg_upper" -> new SurfaceTexture(16, 48, 0, 4, 6, 4, true, false);
            case "left_leg_lower" -> new SurfaceTexture(16, 48, 6, 4, 6, 4, false, true);
            case "right_leg_upper" -> new SurfaceTexture(0, 16, 0, 4, 6, 4, true, false);
            case "right_leg_lower" -> new SurfaceTexture(0, 16, 6, 4, 6, 4, false, true);
            default -> null;
        };
    }

    private static Direction parseDirection(String name) {
        return switch (name) {
            case "down" -> Direction.DOWN;
            case "up" -> Direction.UP;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean contains(TextureFace face, int x, int y) {
        return x >= face.x() && x < face.x() + face.width()
                && y >= face.y() && y < face.y() + face.height();
    }

    /**
     * Composite a single model paint face onto a target ARGB pixel array.
     * The paint data has dimensions paintWidth x paintHeight.
     * Pixels with zero alpha are skipped. Uses blend-over compositing.
     */
    public static boolean compositePaintFace(int[] targetArgb, int imageWidth, int imageHeight,
                                              String surface, Direction face, boolean slim,
                                              int paintWidth, int paintHeight, int[] paintPixels) {
        TextureFace texFace = textureFaceFor(surface, face, slim);
        if (texFace == null) {
            return false;
        }
        boolean changed = false;
        for (int y = 0; y < paintHeight; y++) {
            for (int x = 0; x < paintWidth; x++) {
                int color = paintPixels[y * paintWidth + x];
                if (((color >>> 24) & 0xFF) == 0) {
                    continue;
                }
                if (paintTexturePixel(targetArgb, imageWidth, imageHeight, texFace, x, y, paintWidth, paintHeight, color)) {
                    changed = true;
                }
            }
        }
        return changed;
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

    public record PaintedSkinTexture(Identifier textureId, SkinTexturePixels pixels) {
    }

    public record PaintSurfacePixel(String surface, Direction face, int x, int y) {
    }

    private record PaintedSkinKey(Identifier textureId, boolean slim, int paintHash) {
    }

    private record ParsedSurface(String baseSurface, boolean outer, Direction sourceFace) {
        private static ParsedSurface parse(String surface) {
            if (surface == null || surface.isBlank()) {
                return null;
            }
            int outerFaceIndex = surface.indexOf("_outer_");
            if (outerFaceIndex >= 0) {
                Direction sourceFace = parseDirection(surface.substring(outerFaceIndex + "_outer_".length()));
                return new ParsedSurface(surface.substring(0, outerFaceIndex), true, sourceFace);
            }
            if (surface.endsWith("_outer")) {
                return new ParsedSurface(surface.substring(0, surface.length() - "_outer".length()), true, null);
            }
            return new ParsedSurface(surface, false, null);
        }
    }

    private record SurfaceTexture(int textureX, int textureY, int textureYSegment,
                                  int width, int height, int depth,
                                  boolean renderStartCap, boolean renderEndCap) {
        private TextureFace face(Direction face) {
            int sideTextureY = textureY + depth + textureYSegment;
            return switch (face) {
                case UP -> renderStartCap ? new TextureFace(textureX + depth, textureY, width, depth) : null;
                case DOWN -> renderEndCap ? new TextureFace(textureX + depth + width, textureY, width, depth) : null;
                case WEST -> new TextureFace(textureX, sideTextureY, depth, height);
                case NORTH -> new TextureFace(textureX + depth, sideTextureY, width, height);
                case EAST -> new TextureFace(textureX + depth + width, sideTextureY, depth, height);
                case SOUTH -> new TextureFace(textureX + depth + width + depth, sideTextureY, width, height);
            };
        }
    }

    private record TextureFace(int x, int y, int width, int height) {
    }
}
