package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ModelPaintData {
    public static final String MODEL_PAINT_KEY = "model_paint_faces";
    public static final int SIZE = PaintOverlayStore.SIZE;
    public static final int FACE_PIXELS = PaintOverlayStore.FACE_PIXELS;

    private ModelPaintData() {
    }

    public static List<ModelFace> readFaces(NbtCompound root, boolean slim) {
        if (root == null) {
            return List.of();
        }
        NbtList list = root.getListOrEmpty(MODEL_PAINT_KEY);
        List<ModelFace> faces = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i).orElse(null);
            if (entry == null) {
                continue;
            }
            String surface = entry.getString("surface", "");
            Direction face = Direction.byIndex(entry.getInt("face", Direction.SOUTH.getIndex()));
            FaceSize size = size(surface, face, slim);
            int[] pixels = sanitizePixels(entry.getIntArray("pixels").orElse(new int[0]), size.width(), size.height());
            if (!surface.isEmpty() && hasPixels(pixels)) {
                faces.add(new ModelFace(surface, face, size.width(), size.height(), pixels));
            }
        }
        return faces;
    }

    public static boolean paint(NbtCompound root, String surface, Direction face, int x, int y, int radius, int color, boolean clearFace, boolean slim) {
        if (surface == null || surface.isEmpty()) {
            return false;
        }
        FaceSize size = size(surface, face, slim);
        NbtList list = root.getListOrEmpty(MODEL_PAINT_KEY);
        int index = findFace(list, surface, face);
        if (clearFace) {
            if (index < 0) {
                return false;
            }
            list.remove(index);
            writeList(root, list);
            return true;
        }

        int[] pixels = index >= 0
                ? sanitizePixels(list.getCompound(index).flatMap(entry -> entry.getIntArray("pixels")).orElse(new int[0]), size.width(), size.height())
                : new int[size.pixelCount()];
        int normalizedColor = color == 0 ? 0 : ((color >>> 24) == 0 ? color | 0xFF000000 : color);
        radius = MathHelper.clamp(radius, 1, PaintOverlayFeature.MAX_RADIUS);
        int radiusSquared = radius * radius;
        boolean changed = false;
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                int px = x + dx;
                int py = y + dy;
                if (px < 0 || px >= size.width() || py < 0 || py >= size.height()) {
                    continue;
                }
                int pixelIndex = py * size.width() + px;
                if (pixels[pixelIndex] != normalizedColor) {
                    pixels[pixelIndex] = normalizedColor;
                    changed = true;
                }
            }
        }
        if (!changed) {
            return false;
        }
        if (index >= 0) {
            list.set(index, createFace(surface, face, size, pixels));
        } else {
            list.add(createFace(surface, face, size, pixels));
        }
        writeList(root, list);
        return true;
    }

    private static int findFace(NbtList list, String surface, Direction face) {
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i).orElse(null);
            if (entry == null) {
                continue;
            }
            if (surface.equals(entry.getString("surface", "")) && entry.getInt("face", -1) == face.getIndex()) {
                return i;
            }
        }
        return -1;
    }

    private static NbtCompound createFace(String surface, Direction face, FaceSize size, int[] pixels) {
        NbtCompound entry = new NbtCompound();
        entry.putString("surface", surface);
        entry.putInt("face", face.getIndex());
        entry.putInt("width", size.width());
        entry.putInt("height", size.height());
        entry.putIntArray("pixels", sanitizePixels(pixels, size.width(), size.height()));
        return entry;
    }

    private static void writeList(NbtCompound root, NbtList list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            NbtCompound entry = list.getCompound(i).orElse(null);
            if (entry == null || !hasPixels(entry.getIntArray("pixels").orElse(new int[0]))) {
                list.remove(i);
            }
        }
        if (list.isEmpty()) {
            root.remove(MODEL_PAINT_KEY);
        } else {
            root.put(MODEL_PAINT_KEY, list);
        }
    }

    private static int[] sanitizePixels(int[] source, int width, int height) {
        int[] pixels = new int[Math.max(1, width * height)];
        if (source.length == pixels.length) {
            System.arraycopy(source, 0, pixels, 0, pixels.length);
            return pixels;
        }
        if (source.length == FACE_PIXELS && (width != SIZE || height != SIZE)) {
            for (int y = 0; y < height; y++) {
                int sourceY = MathHelper.clamp((int) (y * (double) SIZE / height), 0, SIZE - 1);
                for (int x = 0; x < width; x++) {
                    int sourceX = MathHelper.clamp((int) (x * (double) SIZE / width), 0, SIZE - 1);
                    pixels[y * width + x] = source[sourceY * SIZE + sourceX];
                }
            }
            return pixels;
        }
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static boolean hasPixels(int[] pixels) {
        return Arrays.stream(pixels).anyMatch(pixel -> pixel != 0);
    }

    public static FaceSize size(String surface, Direction face, boolean slim) {
        int width = switch (surface) {
            case "head" -> 8;
            case "body_upper", "body_lower" -> 8;
            case "left_arm_upper", "left_arm_lower", "right_arm_upper", "right_arm_lower" -> slim ? 3 : 4;
            case "left_leg_upper", "left_leg_lower", "right_leg_upper", "right_leg_lower" -> 4;
            default -> SIZE;
        };
        int height = switch (surface) {
            case "head" -> 8;
            case "body_upper", "body_lower",
                 "left_arm_upper", "left_arm_lower", "right_arm_upper", "right_arm_lower",
                 "left_leg_upper", "left_leg_lower", "right_leg_upper", "right_leg_lower" -> 6;
            default -> SIZE;
        };
        int depth = switch (surface) {
            case "head" -> 8;
            case "body_upper", "body_lower",
                 "left_arm_upper", "left_arm_lower", "right_arm_upper", "right_arm_lower",
                 "left_leg_upper", "left_leg_lower", "right_leg_upper", "right_leg_lower" -> 4;
            default -> SIZE;
        };
        return switch (face) {
            case UP, DOWN -> new FaceSize(width, depth);
            case EAST, WEST -> new FaceSize(depth, height);
            default -> new FaceSize(width, height);
        };
    }

    public record FaceSize(int width, int height) {
        public FaceSize {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }

        public int pixelCount() {
            return width * height;
        }
    }

    public record ModelFace(String surface, Direction face, int width, int height, int[] pixels) {
        public ModelFace {
            width = Math.max(1, width);
            height = Math.max(1, height);
            pixels = sanitizePixels(pixels, width, height);
        }
    }
}
