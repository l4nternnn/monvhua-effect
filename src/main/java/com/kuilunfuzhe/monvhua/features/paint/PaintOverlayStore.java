package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class PaintOverlayStore extends PersistentState {
    public static final int SIZE = 16;
    public static final int FACE_PIXELS = SIZE * SIZE;
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    public static final Codec<StoredFace> STORED_FACE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(StoredFace::pos),
            Direction.CODEC.fieldOf("face").forGetter(StoredFace::face),
            PIXELS_CODEC.fieldOf("pixels").forGetter(StoredFace::pixels)
    ).apply(instance, StoredFace::new));
    public static final Codec<PaintOverlayStore> CODEC = STORED_FACE_CODEC.listOf()
            .xmap(PaintOverlayStore::new, PaintOverlayStore::toStoredFaces);
    public static final PersistentStateType<PaintOverlayStore> TYPE = new PersistentStateType<>(
            "monvhua_paint_overlays",
            PaintOverlayStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<FaceKey, int[]> faces = new HashMap<>();

    public PaintOverlayStore() {
    }

    private PaintOverlayStore(List<StoredFace> entries) {
        for (StoredFace entry : entries) {
            int[] pixels = sanitizePixels(entry.pixels());
            if (hasPixels(pixels)) {
                faces.put(new FaceKey(entry.pos(), entry.face()), pixels);
            }
        }
    }

    public static PaintOverlayStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public int[] getPixels(BlockPos pos, Direction face) {
        int[] pixels = faces.get(new FaceKey(pos, face));
        return pixels == null ? new int[FACE_PIXELS] : Arrays.copyOf(pixels, FACE_PIXELS);
    }

    public boolean setPixels(BlockPos pos, Direction face, int[] source) {
        int[] pixels = sanitizePixels(source);
        FaceKey key = new FaceKey(pos.toImmutable(), face);
        if (!hasPixels(pixels)) {
            return clearFace(pos, face);
        }
        int[] existing = faces.get(key);
        if (existing != null && Arrays.equals(existing, pixels)) {
            return false;
        }
        faces.put(key, pixels);
        markDirty();
        return true;
    }

    public boolean setPixel(BlockPos pos, Direction face, int x, int y, int color) {
        if (!isInside(x, y)) {
            return false;
        }
        FaceKey key = new FaceKey(pos.toImmutable(), face);
        int[] pixels = faces.computeIfAbsent(key, ignored -> new int[FACE_PIXELS]);
        int index = y * SIZE + x;
        if (pixels[index] == color) {
            return false;
        }
        pixels[index] = color;
        markDirty();
        return true;
    }

    public boolean clearFace(BlockPos pos, Direction face) {
        boolean changed = faces.remove(new FaceKey(pos, face)) != null;
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public List<StoredFace> toStoredFaces() {
        List<StoredFace> entries = new ArrayList<>();
        for (Map.Entry<FaceKey, int[]> entry : faces.entrySet()) {
            if (hasPixels(entry.getValue())) {
                entries.add(new StoredFace(entry.getKey().pos(), entry.getKey().face(), Arrays.copyOf(entry.getValue(), FACE_PIXELS)));
            }
        }
        return entries;
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static boolean isInside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    public record FaceKey(BlockPos pos, Direction face) {
        public FaceKey {
            pos = pos.toImmutable();
        }
    }

    public record StoredFace(BlockPos pos, Direction face, int[] pixels) {
        public StoredFace {
            pos = pos.toImmutable();
            pixels = sanitizePixels(pixels);
        }
    }
}
