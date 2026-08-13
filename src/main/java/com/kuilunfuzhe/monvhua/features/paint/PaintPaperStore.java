package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public class PaintPaperStore extends PersistentState {
    public static final int MAX_IMPORTED_IMAGE_PIXELS = 8_388_608;
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    public static final Codec<Cell> CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(Cell::x),
            Codec.INT.fieldOf("y").forGetter(Cell::y),
            Codec.INT.optionalFieldOf("grid_size", PaintOverlayStore.BASE_SIZE).forGetter(Cell::gridSize),
            PIXELS_CODEC.fieldOf("pixels").forGetter(Cell::pixels)
    ).apply(instance, Cell::new));
    public static final Codec<PaperData> PAPER_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("id").forGetter(PaperData::id),
            Codec.INT.fieldOf("size").forGetter(PaperData::size),
            CELL_CODEC.listOf().fieldOf("cells").forGetter(PaperData::cells)
    ).apply(instance, PaperData::new));
    public static final Codec<ImportedImage> IMPORTED_IMAGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("id").forGetter(ImportedImage::id),
            Codec.STRING.fieldOf("name").forGetter(ImportedImage::name),
            Codec.INT.fieldOf("width").forGetter(ImportedImage::width),
            Codec.INT.fieldOf("height").forGetter(ImportedImage::height),
            PIXELS_CODEC.fieldOf("pixels").forGetter(ImportedImage::pixels)
    ).apply(instance, ImportedImage::new));
    private static final Codec<StoreData> STORE_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PAPER_DATA_CODEC.listOf().optionalFieldOf("papers", List.of()).forGetter(StoreData::papers),
            IMPORTED_IMAGE_CODEC.listOf().optionalFieldOf("imported_images", List.of()).forGetter(StoreData::importedImages)
    ).apply(instance, StoreData::new));
    public static final Codec<PaintPaperStore> CODEC = Codec.either(PAPER_DATA_CODEC.listOf(), STORE_DATA_CODEC)
            .xmap(data -> data.map(PaintPaperStore::new,
                            stored -> new PaintPaperStore(stored.papers(), stored.importedImages())),
                    store -> Either.right(new StoreData(store.toPapers(), store.toImportedImages())));
    public static final PersistentStateType<PaintPaperStore> TYPE = new PersistentStateType<>(
            "monvhua_paint_papers",
            PaintPaperStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, PaperData> papers = new HashMap<>();
    private final Map<UUID, ImportedImage> importedImages = new HashMap<>();

    public PaintPaperStore() {
    }

    private PaintPaperStore(List<PaperData> entries) {
        for (PaperData entry : entries) {
            papers.put(entry.id(), entry.sanitized());
        }
    }

    private PaintPaperStore(List<PaperData> paperEntries, List<ImportedImage> imageEntries) {
        this(paperEntries);
        for (ImportedImage image : imageEntries) {
            if (image.isUsable()) {
                importedImages.put(image.id(), image);
            }
        }
    }

    public static PaintPaperStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public PaperData get(UUID id) {
        return papers.get(id);
    }

    public void put(PaperData data) {
        papers.put(data.id(), data.sanitized());
        markDirty();
    }

    public ImportedImage getImportedImage(UUID id) {
        return importedImages.get(id);
    }

    public void putImportedImage(ImportedImage image) {
        if (!image.isUsable()) {
            return;
        }
        importedImages.put(image.id(), image);
        markDirty();
    }

    private List<PaperData> toPapers() {
        return new ArrayList<>(papers.values());
    }

    private List<ImportedImage> toImportedImages() {
        return new ArrayList<>(importedImages.values());
    }

    private record StoreData(List<PaperData> papers, List<ImportedImage> importedImages) {
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        if (source == null) {
            return pixels;
        }
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static int[] expandPixels(int[] source, int sourceGridSize) {
        if (sourceGridSize == PaintOverlayStore.SIZE) {
            return sanitizePixels(source);
        }
        int[] expanded = new int[PaintOverlayStore.FACE_PIXELS];
        if (source == null || sourceGridSize <= 0) {
            return expanded;
        }
        for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
            int sourceY = Math.min(sourceGridSize - 1, y * sourceGridSize / PaintOverlayStore.SIZE);
            for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                int sourceX = Math.min(sourceGridSize - 1, x * sourceGridSize / PaintOverlayStore.SIZE);
                int sourceIndex = sourceY * sourceGridSize + sourceX;
                if (sourceIndex < source.length) {
                    expanded[y * PaintOverlayStore.SIZE + x] = source[sourceIndex];
                }
            }
        }
        return expanded;
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    public record Cell(int x, int y, int gridSize, int[] pixels) {
        public Cell {
            gridSize = gridSize > 0 ? gridSize : PaintOverlayStore.BASE_SIZE;
            pixels = expandPixels(pixels, gridSize);
            gridSize = PaintOverlayStore.SIZE;
        }

        public Cell(int x, int y, int[] pixels) {
            this(x, y, inferGridSize(pixels), pixels);
        }

        private static int inferGridSize(int[] pixels) {
            if (pixels == null || pixels.length == 0) {
                return PaintOverlayStore.SIZE;
            }
            int inferred = (int) Math.round(Math.sqrt(pixels.length));
            return inferred * inferred == pixels.length ? inferred : PaintOverlayStore.SIZE;
        }

        private boolean isVisible() {
            return hasPixels(pixels);
        }
    }

    public record PaperData(UUID id, int size, List<Cell> cells) {
        public PaperData {
            cells = List.copyOf(cells);
        }

        private PaperData sanitized() {
            List<Cell> visible = new ArrayList<>();
            for (Cell cell : cells) {
                if (cell.x() >= 0 && cell.x() < size && cell.y() >= 0 && cell.y() < size && cell.isVisible()) {
                    visible.add(cell);
                }
            }
            return new PaperData(id, size, visible);
        }
    }

    /** Original imported image data. One image pixel maps to one world microcell. */
    public record ImportedImage(UUID id, String name, int width, int height, int[] pixels) {
        public ImportedImage {
            name = name == null || name.isBlank() ? "image" : name;
            width = Math.max(0, width);
            height = Math.max(0, height);
            int expected = safePixelCount(width, height);
            pixels = pixels == null ? new int[0] : Arrays.copyOf(pixels, Math.min(pixels.length, expected));
        }

        public boolean isUsable() {
            return id != null && width > 0 && height > 0 && pixels.length == safePixelCount(width, height);
        }

        private static int safePixelCount(int width, int height) {
            long count = (long) width * height;
            return count > 0 && count <= MAX_IMPORTED_IMAGE_PIXELS ? (int) count : 0;
        }
    }
}
