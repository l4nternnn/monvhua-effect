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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DrawingBoardBlockEntity extends BlockEntity {
    public static final int WIDTH = 100;
    public static final int HEIGHT = 150;
    public static final int PIXELS = WIDTH * HEIGHT;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final Codec<int[]> PIXELS_CODEC = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);

    private int[] pixels = filledCanvas();
    private int version;

    public DrawingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(DrawingBoardBlockEntities.DRAWING_BOARD_BLOCK_ENTITY, pos, state);
    }

    public int[] copyPixels() {
        return Arrays.copyOf(pixels, PIXELS);
    }

    public int getVersion() {
        return version;
    }

    public void setPixels(int[] source) {
        pixels = sanitize(source);
        version++;
        sync();
    }

    public boolean paint(int x, int y, int radius, int color) {
        return paintLimited(x, y, radius, color, Integer.MAX_VALUE) > 0;
    }

    public int paintLimited(int x, int y, int radius, int color, int budget) {
        if (budget <= 0) {
            return 0;
        }
        radius = Math.max(1, Math.min(12, radius));
        color = normalizeColor(color);
        int changedPixels = 0;
        int r2 = radius * radius;
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
                if (px < 0 || px >= WIDTH || py < 0 || py >= HEIGHT) {
                    continue;
                }
                int index = py * WIDTH + px;
                if (pixels[index] != color) {
                    pixels[index] = color;
                    changedPixels++;
                }
            }
        }
        if (changedPixels > 0) {
            version++;
            sync();
        }
        return changedPixels;
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
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        pixels = sanitize(view.read("pixels", PIXELS_CODEC).orElseGet(DrawingBoardBlockEntity::filledCanvas));
        version++;
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put("pixels", PIXELS_CODEC, pixels);
    }

    private static int[] sanitize(int[] source) {
        int[] result = filledCanvas();
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
        int[] result = new int[PIXELS];
        Arrays.fill(result, DEFAULT_COLOR);
        return result;
    }
}
