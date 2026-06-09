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

    public static List<ModelFace> readFaces(NbtCompound root) {
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
            int[] pixels = sanitizePixels(entry.getIntArray("pixels").orElse(new int[0]));
            if (!surface.isEmpty() && hasPixels(pixels)) {
                faces.add(new ModelFace(surface, face, pixels));
            }
        }
        return faces;
    }

    public static boolean paint(NbtCompound root, String surface, Direction face, int x, int y, int radius, int color, boolean clearFace) {
        if (surface == null || surface.isEmpty()) {
            return false;
        }
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
                ? sanitizePixels(list.getCompound(index).flatMap(entry -> entry.getIntArray("pixels")).orElse(new int[0]))
                : new int[FACE_PIXELS];
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
                if (px < 0 || px >= SIZE || py < 0 || py >= SIZE) {
                    continue;
                }
                int pixelIndex = py * SIZE + px;
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
            list.set(index, createFace(surface, face, pixels));
        } else {
            list.add(createFace(surface, face, pixels));
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

    private static NbtCompound createFace(String surface, Direction face, int[] pixels) {
        NbtCompound entry = new NbtCompound();
        entry.putString("surface", surface);
        entry.putInt("face", face.getIndex());
        entry.putIntArray("pixels", sanitizePixels(pixels));
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

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static boolean hasPixels(int[] pixels) {
        return Arrays.stream(pixels).anyMatch(pixel -> pixel != 0);
    }

    public record ModelFace(String surface, Direction face, int[] pixels) {
        public ModelFace {
            pixels = sanitizePixels(pixels);
        }
    }
}
