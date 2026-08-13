package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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
    /** Original paint pixels are subdivided into this many independently paintable cells per axis. */
    public static final int BASE_SIZE = 16;
    public static final int SUBDIVISION = 4;
    public static final int SIZE = BASE_SIZE * SUBDIVISION;
    public static final int FACE_PIXELS = SIZE * SIZE;
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    public static final Codec<StoredFace> STORED_FACE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(StoredFace::pos),
            Direction.CODEC.fieldOf("face").forGetter(StoredFace::face),
            Codec.INT.optionalFieldOf("grid_size", BASE_SIZE).forGetter(StoredFace::gridSize),
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
    private final Map<ChunkPos, List<FaceKey>> facesByChunk = new HashMap<>();

    public PaintOverlayStore() {
    }

    private PaintOverlayStore(List<StoredFace> entries) {
        for (StoredFace entry : entries) {
            int[] pixels = expandPixels(entry.pixels(), entry.gridSize());
            if (hasPixels(pixels)) {
                FaceKey key = new FaceKey(entry.pos(), entry.face());
                faces.put(key, pixels);
                index(key);
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
        int[] pixels = normalizeInputPixels(source);
        FaceKey key = new FaceKey(pos.toImmutable(), face);
        if (!hasPixels(pixels)) {
            return clearFace(pos, face);
        }
        int[] existing = faces.get(key);
        if (existing != null && Arrays.equals(existing, pixels)) {
            return false;
        }
        faces.put(key, pixels);
        index(key);
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
        if (color == 0 && !hasPixels(pixels)) {
            faces.remove(key);
            unindex(key);
            markDirty();
            return true;
        }
        index(key);
        markDirty();
        return true;
    }

    public boolean clearFace(BlockPos pos, Direction face) {
        FaceKey key = new FaceKey(pos, face);
        boolean changed = faces.remove(key) != null;
        if (changed) {
            unindex(key);
            markDirty();
        }
        return changed;
    }

    public List<StoredFace> toStoredFaces() {
        List<StoredFace> entries = new ArrayList<>();
        for (Map.Entry<FaceKey, int[]> entry : faces.entrySet()) {
            if (hasPixels(entry.getValue())) {
                entries.add(new StoredFace(entry.getKey().pos(), entry.getKey().face(), SIZE,
                        Arrays.copyOf(entry.getValue(), FACE_PIXELS)));
            }
        }
        return entries;
    }

    public List<StoredFace> getStoredFacesNear(ChunkPos center, int chunkRadius) {
        List<StoredFace> entries = new ArrayList<>();
        for (int chunkX = center.x - chunkRadius; chunkX <= center.x + chunkRadius; chunkX++) {
            for (int chunkZ = center.z - chunkRadius; chunkZ <= center.z + chunkRadius; chunkZ++) {
                List<FaceKey> keys = facesByChunk.get(new ChunkPos(chunkX, chunkZ));
                if (keys == null) {
                    continue;
                }
                for (FaceKey key : keys) {
                    int[] pixels = faces.get(key);
                    if (pixels != null && hasPixels(pixels)) {
                        entries.add(new StoredFace(key.pos(), key.face(), SIZE,
                                Arrays.copyOf(pixels, FACE_PIXELS)));
                    }
                }
            }
        }
        return entries;
    }

    private void index(FaceKey key) {
        List<FaceKey> keys = facesByChunk.computeIfAbsent(new ChunkPos(key.pos()), ignored -> new ArrayList<>());
        if (!keys.contains(key)) {
            keys.add(key);
        }
    }

    private void unindex(FaceKey key) {
        ChunkPos chunkPos = new ChunkPos(key.pos());
        List<FaceKey> keys = facesByChunk.get(chunkPos);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            facesByChunk.remove(chunkPos);
        }
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[FACE_PIXELS];
        if (source == null) {
            return pixels;
        }
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static int[] normalizeInputPixels(int[] source) {
        if (source == null || source.length == 0) {
            return new int[FACE_PIXELS];
        }
        int sourceGridSize = (int) Math.round(Math.sqrt(source.length));
        if (sourceGridSize * sourceGridSize == source.length && sourceGridSize != SIZE) {
            return expandPixels(source, sourceGridSize);
        }
        return sanitizePixels(source);
    }

    private static int[] expandPixels(int[] source, int sourceGridSize) {
        if (sourceGridSize == SIZE) {
            return sanitizePixels(source);
        }
        if (sourceGridSize <= 0 || source == null || source.length == 0) {
            return new int[FACE_PIXELS];
        }
        int[] expanded = new int[FACE_PIXELS];
        for (int y = 0; y < SIZE; y++) {
            int sourceY = Math.min(sourceGridSize - 1, y * sourceGridSize / SIZE);
            for (int x = 0; x < SIZE; x++) {
                int sourceX = Math.min(sourceGridSize - 1, x * sourceGridSize / SIZE);
                int sourceIndex = sourceY * sourceGridSize + sourceX;
                if (sourceIndex < source.length) {
                    expanded[y * SIZE + x] = source[sourceIndex];
                }
            }
        }
        return expanded;
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

    public record StoredFace(BlockPos pos, Direction face, int gridSize, int[] pixels) {
        public StoredFace {
            pos = pos.toImmutable();
            gridSize = gridSize > 0 ? gridSize : inferGridSize(pixels);
            pixels = pixels == null ? new int[0] : Arrays.copyOf(pixels, pixels.length);
        }

        private static int inferGridSize(int[] pixels) {
            if (pixels == null || pixels.length == 0) {
                return SIZE;
            }
            int inferred = (int) Math.round(Math.sqrt(pixels.length));
            return inferred * inferred == pixels.length ? inferred : SIZE;
        }
    }
}
