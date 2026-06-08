package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    public static final Codec<Cell> CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(Cell::x),
            Codec.INT.fieldOf("y").forGetter(Cell::y),
            PIXELS_CODEC.fieldOf("pixels").forGetter(Cell::pixels)
    ).apply(instance, Cell::new));
    public static final Codec<PaperData> PAPER_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("id").forGetter(PaperData::id),
            Codec.INT.fieldOf("size").forGetter(PaperData::size),
            CELL_CODEC.listOf().fieldOf("cells").forGetter(PaperData::cells)
    ).apply(instance, PaperData::new));
    public static final Codec<PaintPaperStore> CODEC = PAPER_DATA_CODEC.listOf()
            .xmap(PaintPaperStore::new, PaintPaperStore::toPapers);
    public static final PersistentStateType<PaintPaperStore> TYPE = new PersistentStateType<>(
            "monvhua_paint_papers",
            PaintPaperStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, PaperData> papers = new HashMap<>();

    public PaintPaperStore() {
    }

    private PaintPaperStore(List<PaperData> entries) {
        for (PaperData entry : entries) {
            papers.put(entry.id(), entry.sanitized());
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

    private List<PaperData> toPapers() {
        return new ArrayList<>(papers.values());
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    public record Cell(int x, int y, int[] pixels) {
        public Cell {
            pixels = sanitizePixels(pixels);
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
}
