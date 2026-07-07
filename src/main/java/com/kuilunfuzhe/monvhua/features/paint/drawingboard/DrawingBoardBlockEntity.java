package com.kuilunfuzhe.monvhua.features.paint.drawingboard;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DrawingBoardBlockEntity extends BlockEntity {
    public static final int WIDTH = 100;
    public static final int HEIGHT = 150;
    public static final int PIXELS = WIDTH * HEIGHT;
    public static final int MIN_WIDTH = 16;
    public static final int MAX_WIDTH = 1024;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);

    private int canvasWidth = WIDTH;
    private int canvasHeight = HEIGHT;
    private int[] pixels = filledCanvas();
    private int version;

    public DrawingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(DrawingBoardBlockEntities.DRAWING_BOARD_BLOCK_ENTITY, pos, state);
    }

    public int[] copyPixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    public int getVersion() {
        return version;
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public void setPixels(int[] source) {
        pixels = sanitize(source, canvasWidth, canvasHeight);
        version++;
        sync();
    }

    public void setPixels(int width, int height, int[] source) {
        canvasWidth = sanitizeWidth(width);
        canvasHeight = sanitizeHeight(canvasWidth, height);
        pixels = sanitize(source, canvasWidth, canvasHeight);
        version++;
        sync();
    }

    public void resizeToWidth(int width) {
        int nextWidth = sanitizeWidth(width);
        int nextHeight = heightForWidth(nextWidth);
        if (nextWidth == canvasWidth && nextHeight == canvasHeight) {
            return;
        }
        int[] nextPixels = filledCanvas(nextWidth, nextHeight);
        int copyW = Math.min(canvasWidth, nextWidth);
        int copyH = Math.min(canvasHeight, nextHeight);
        for (int y = 0; y < copyH; y++) {
            System.arraycopy(pixels, y * canvasWidth, nextPixels, y * nextWidth, copyW);
        }
        canvasWidth = nextWidth;
        canvasHeight = nextHeight;
        pixels = nextPixels;
        version++;
        sync();
    }

    public boolean paint(int x, int y, int radius, int color) {
        return !paintPatch(x, y, radius, color, Integer.MAX_VALUE).isEmpty();
    }

    public int paintLimited(int x, int y, int radius, int color, int budget) {
        return paintPatch(x, y, radius, color, budget).changedCount();
    }

    public PixelPatch paintPatch(int x, int y, int radius, int color, int budget) {
        if (budget <= 0) {
            return PixelPatch.EMPTY;
        }
        radius = Math.max(1, Math.min(12, radius));
        color = normalizeColor(color);
        int changedPixels = 0;
        int r2 = radius * radius;
        int maxChanged = Math.min(Math.max(0, budget), Math.max(1, (radius * 2 - 1) * (radius * 2 - 1)));
        int[] indices = new int[maxChanged];
        int[] colors = new int[maxChanged];
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (changedPixels >= budget) {
                    break;
                }
                if (dx * dx + dy * dy > r2) {
                    continue;
                }
                int px = x + dx;
                int py = y + dy;
                if (px < 0 || px >= canvasWidth || py < 0 || py >= canvasHeight) {
                    continue;
                }
                int index = py * canvasWidth + px;
                if (pixels[index] != color) {
                    pixels[index] = color;
                    indices[changedPixels] = index;
                    colors[changedPixels] = color;
                    changedPixels++;
                }
            }
        }
        if (changedPixels > 0) {
            version++;
            sync();
        }
        return new PixelPatch(Arrays.copyOf(indices, changedPixels), Arrays.copyOf(colors, changedPixels));
    }

    public boolean applyPatch(int[] indices, int[] colors) {
        if (indices == null || colors == null) {
            return false;
        }
        boolean changed = false;
        int length = Math.min(indices.length, colors.length);
        for (int i = 0; i < length; i++) {
            int index = indices[i];
            if (index < 0 || index >= pixels.length) {
                continue;
            }
            int color = normalizeColor(colors[i]);
            if (pixels[index] != color) {
                pixels[index] = color;
                changed = true;
            }
        }
        if (changed) {
            version++;
            sync();
        }
        return changed;
    }

    public void clear() {
        Arrays.fill(pixels, DEFAULT_COLOR);
        version++;
        sync();
    }

    private void sync() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this, (blockEntity, registries) -> createClientSyncNbt());
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createClientSyncNbt();
    }

    private NbtCompound createClientSyncNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("canvas_width", canvasWidth);
        nbt.putInt("canvas_height", canvasHeight);
        nbt.putInt("version", version);
        return nbt;
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        int nextWidth = sanitizeWidth(view.getInt("canvas_width", WIDTH));
        int nextHeight = sanitizeHeight(nextWidth, view.getInt("canvas_height", heightForWidth(nextWidth)));
        int[] storedPixels = view.read("pixels", PIXELS_CODEC).orElse(null);
        if (storedPixels != null) {
            canvasWidth = nextWidth;
            canvasHeight = nextHeight;
            pixels = sanitize(storedPixels, canvasWidth, canvasHeight);
        } else if (nextWidth != canvasWidth || nextHeight != canvasHeight || pixels.length != nextWidth * nextHeight) {
            int[] nextPixels = filledCanvas(nextWidth, nextHeight);
            int copyW = Math.min(canvasWidth, nextWidth);
            int copyH = Math.min(canvasHeight, nextHeight);
            for (int y = 0; y < copyH; y++) {
                System.arraycopy(pixels, y * canvasWidth, nextPixels, y * nextWidth, copyW);
            }
            canvasWidth = nextWidth;
            canvasHeight = nextHeight;
            pixels = nextPixels;
        }
        version++;
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putInt("canvas_width", canvasWidth);
        view.putInt("canvas_height", canvasHeight);
        view.put("pixels", PIXELS_CODEC, pixels);
    }

    public static int sanitizeWidth(int width) {
        return MathHelper.clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    public static int heightForWidth(int width) {
        return Math.max(1, MathHelper.ceil(sanitizeWidth(width) * (HEIGHT / (float) WIDTH)));
    }

    private static int sanitizeHeight(int width, int height) {
        return height <= 0 ? heightForWidth(width) : MathHelper.clamp(height, 1, MAX_WIDTH * HEIGHT / WIDTH);
    }

    private static int[] sanitize(int[] source, int width, int height) {
        int[] result = filledCanvas(width, height);
        if (source == null) {
            return result;
        }
        for (int i = 0; i < Math.min(source.length, result.length); i++) {
            result[i] = normalizeColor(source[i]);
        }
        return result;
    }

    private static int normalizeColor(int color) {
        return (color >>> 24) == 0 ? DEFAULT_COLOR : color;
    }

    private static int[] filledCanvas() {
        return filledCanvas(WIDTH, HEIGHT);
    }

    private static int[] filledCanvas(int width, int height) {
        int[] result = new int[Math.max(1, width * height)];
        Arrays.fill(result, DEFAULT_COLOR);
        return result;
    }

    public record PixelPatch(int[] indices, int[] colors) {
        public static final PixelPatch EMPTY = new PixelPatch(new int[0], new int[0]);

        public PixelPatch {
            int length = Math.min(indices == null ? 0 : indices.length, colors == null ? 0 : colors.length);
            indices = indices == null || indices.length != length ? Arrays.copyOf(indices == null ? new int[0] : indices, length) : indices;
            colors = colors == null || colors.length != length ? Arrays.copyOf(colors == null ? new int[0] : colors, length) : colors;
        }

        public int changedCount() {
            return indices.length;
        }

        public boolean isEmpty() {
            return indices.length == 0;
        }
    }
}
