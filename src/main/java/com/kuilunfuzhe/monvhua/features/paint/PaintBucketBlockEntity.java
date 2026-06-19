package com.kuilunfuzhe.monvhua.features.paint;

import com.mojang.serialization.Codec;
import com.kuilunfuzhe.monvhua.item.config.PaintConfig;
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

public class PaintBucketBlockEntity extends BlockEntity {
    private int color = 0xFFFFFF;
    private boolean filled;
    private long brushLoadDay = Long.MIN_VALUE;
    private int brushLoadsToday;

    public PaintBucketBlockEntity(BlockPos pos, BlockState state) {
        super(PaintBucketBlockEntities.PAINT_BUCKET_BLOCK_ENTITY, pos, state);
    }

    public int getColor() {
        return color & 0xFFFFFF;
    }

    public boolean isFilled() {
        return filled;
    }

    public void fill(int color) {
        this.color = color & 0xFFFFFF;
        this.filled = true;
        sync();
    }

    public void empty() {
        if (!filled) {
            return;
        }
        this.filled = false;
        sync();
    }

    public void refillBrushLoads() {
        refreshBrushLoadDay();
        brushLoadsToday = 0;
        sync();
    }

    public boolean canLoadBrush() {
        if (!filled) {
            return false;
        }
        refreshBrushLoadDay();
        return brushLoadsToday < PaintConfig.getInstance().bucketBrushLoads;
    }

    public boolean takeBrushLoad() {
        if (!canLoadBrush()) {
            return false;
        }
        brushLoadsToday++;
        sync();
        return true;
    }

    public int remainingBrushLoadsToday() {
        refreshBrushLoadDay();
        return Math.max(0, PaintConfig.getInstance().bucketBrushLoads - brushLoadsToday);
    }

    private void refreshBrushLoadDay() {
        if (world == null) {
            return;
        }
        long day = world.getTimeOfDay() / 24000L;
        if (day != brushLoadDay) {
            brushLoadDay = day;
            brushLoadsToday = 0;
        }
    }

    private void sync() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        color = view.read("color", Codec.INT).orElse(0xFFFFFF) & 0xFFFFFF;
        filled = view.getBoolean("filled", false);
        brushLoadDay = view.read("brush_load_day", Codec.LONG).orElse(Long.MIN_VALUE);
        brushLoadsToday = view.read("brush_loads_today", Codec.INT).orElse(0);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put("color", Codec.INT, color & 0xFFFFFF);
        view.putBoolean("filled", filled);
        view.put("brush_load_day", Codec.LONG, brushLoadDay);
        view.put("brush_loads_today", Codec.INT, brushLoadsToday);
    }
}
