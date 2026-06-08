package com.kuilunfuzhe.monvhua.features.paint;

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

public class PaintBucketBlockEntity extends BlockEntity {
    private int color = 0xFFFFFF;
    private boolean filled;

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
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put("color", Codec.INT, color & 0xFFFFFF);
        view.putBoolean("filled", filled);
    }
}
