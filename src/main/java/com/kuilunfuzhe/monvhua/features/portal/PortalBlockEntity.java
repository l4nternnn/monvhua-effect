package com.kuilunfuzhe.monvhua.features.portal;

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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class PortalBlockEntity extends BlockEntity {
    private BlockPos targetPos;
    private Direction targetFacing = Direction.NORTH;
    private Vec3d targetCenter;
    private BlockPos origin;
    private int width = 1;
    private int height = 1;
    private String groupId = "";
    private long activeAfterTick;
    private boolean active;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BLOCK_ENTITY, pos, state);
        origin = pos.toImmutable();
    }

    public Direction getFacing() {
        return getCachedState().contains(PortalBlock.FACING)
                ? getCachedState().get(PortalBlock.FACING)
                : Direction.NORTH;
    }

    public boolean isController() {
        return getCachedState().contains(PortalBlock.CONTROLLER) && getCachedState().get(PortalBlock.CONTROLLER);
    }

    public BlockPos getOrigin() {
        return origin == null ? pos : origin;
    }

    public int getPortalWidth() {
        return Math.max(1, width);
    }

    public int getPortalHeight() {
        return Math.max(1, height);
    }

    public String getGroupId() {
        return groupId == null ? "" : groupId;
    }

    public boolean hasTarget() {
        return targetPos != null;
    }

    public boolean isActive() {
        return active && targetPos != null;
    }

    public long getActiveAfterTick() {
        return activeAfterTick;
    }

    @Nullable
    public PortalLinkData getLinkData() {
        return targetPos == null ? null : new PortalLinkData(
                targetPos,
                targetFacing,
                resolvedTargetCenter()
        );
    }

    public Vec3d getPortalCenter() {
        BlockPos base = getOrigin();
        Direction facing = getFacing();
        double y = base.getY() + getPortalHeight() / 2.0D;
        if (facing.getAxis() == Direction.Axis.X) {
            return new Vec3d(base.getX() + 0.5D, y, base.getZ() + getPortalWidth() / 2.0D);
        }
        return new Vec3d(base.getX() + getPortalWidth() / 2.0D, y, base.getZ() + 0.5D);
    }

    public void setStructure(BlockPos origin, int width, int height) {
        this.origin = origin.toImmutable();
        this.width = Math.max(1, Math.min(PortalManager.MAX_PORTAL_SIZE, width));
        this.height = Math.max(1, Math.min(PortalManager.MAX_PORTAL_SIZE, height));
        sync();
    }

    public void setGroupId(String groupId) {
        this.groupId = sanitizeGroup(groupId);
        sync();
    }

    public void setTarget(BlockPos pos, Direction facing, long activeAfterTick) {
        setTarget(pos, facing, Vec3d.ofCenter(pos), activeAfterTick);
    }

    public void setTarget(BlockPos pos, Direction facing, Vec3d center, long activeAfterTick) {
        targetPos = pos.toImmutable();
        targetFacing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
        targetCenter = center == null ? Vec3d.ofCenter(targetPos) : center;
        this.activeAfterTick = Math.max(0L, activeAfterTick);
        active = world == null || this.activeAfterTick <= world.getTime();
        sync();
    }

    public void clearTarget() {
        targetPos = null;
        targetFacing = Direction.NORTH;
        targetCenter = null;
        activeAfterTick = 0L;
        active = false;
        sync();
    }

    public void tickActivation(ServerWorld world) {
        if (targetPos != null && !active && world.getTime() >= activeAfterTick) {
            active = true;
            sync();
        }
    }

    public boolean containsSurface(BlockPos checkPos) {
        BlockPos base = getOrigin();
        int y = checkPos.getY() - base.getY();
        if (y < 0 || y >= getPortalHeight()) {
            return false;
        }
        Direction facing = getFacing();
        if (facing.getAxis() == Direction.Axis.X) {
            return checkPos.getX() == base.getX()
                    && checkPos.getZ() >= base.getZ()
                    && checkPos.getZ() < base.getZ() + getPortalWidth();
        }
        return checkPos.getZ() == base.getZ()
                && checkPos.getX() >= base.getX()
                && checkPos.getX() < base.getX() + getPortalWidth();
    }

    private static String sanitizeGroup(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
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
        targetPos = readPos(view, "target_pos");
        targetCenter = readVec3d(view, "target_center");
        origin = readPos(view, "origin_pos");
        if (origin == null) {
            origin = pos.toImmutable();
        }
        int index = view.read("target_facing", Codec.INT).orElse(Direction.NORTH.getIndex());
        targetFacing = Direction.byIndex(index);
        if (targetFacing.getAxis().isVertical()) {
            targetFacing = Direction.NORTH;
        }
        width = Math.max(1, Math.min(PortalManager.MAX_PORTAL_SIZE, view.read("width", Codec.INT).orElse(1)));
        height = Math.max(1, Math.min(PortalManager.MAX_PORTAL_SIZE, view.read("height", Codec.INT).orElse(1)));
        groupId = sanitizeGroup(view.read("group_id", Codec.STRING).orElse(""));
        activeAfterTick = Math.max(0L, view.read("active_after_tick", Codec.LONG).orElse(0L));
        active = view.read("active", Codec.BOOL).orElse(false);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        writePos(view, "target_pos", targetPos);
        writeVec3d(view, "target_center", targetPos == null ? null : resolvedTargetCenter());
        writePos(view, "origin_pos", getOrigin());
        view.put("target_facing", Codec.INT, targetFacing.getIndex());
        view.put("width", Codec.INT, getPortalWidth());
        view.put("height", Codec.INT, getPortalHeight());
        view.put("group_id", Codec.STRING, getGroupId());
        view.put("active_after_tick", Codec.LONG, activeAfterTick);
        view.put("active", Codec.BOOL, active);
    }

    @Nullable
    private static BlockPos readPos(ReadView view, String key) {
        int[] data = view.read(key, Codec.INT_STREAM.xmap(stream -> stream.toArray(), java.util.Arrays::stream)).orElse(null);
        return data != null && data.length == 3 ? new BlockPos(data[0], data[1], data[2]) : null;
    }

    private static void writePos(WriteView view, String key, @Nullable BlockPos value) {
        if (value != null) {
            view.put(key, Codec.INT_STREAM.xmap(stream -> stream.toArray(), java.util.Arrays::stream),
                    new int[]{value.getX(), value.getY(), value.getZ()});
        }
    }

    @Nullable
    private static Vec3d readVec3d(ReadView view, String key) {
        java.util.List<Double> data = view.read(key, Codec.DOUBLE.listOf()).orElse(null);
        return data != null && data.size() == 3 ? new Vec3d(data.get(0), data.get(1), data.get(2)) : null;
    }

    private static void writeVec3d(WriteView view, String key, @Nullable Vec3d value) {
        if (value != null) {
            view.put(key, Codec.DOUBLE.listOf(), java.util.List.of(value.x, value.y, value.z));
        }
    }

    private Vec3d resolvedTargetCenter() {
        Vec3d center = targetCenter;
        if (center == null && world != null && targetPos != null) {
            BlockEntity blockEntity = world.getBlockEntity(targetPos);
            if (blockEntity instanceof PortalBlockEntity portal && portal.isController()) {
                center = portal.getPortalCenter();
            }
        }
        return center == null && targetPos != null ? Vec3d.ofCenter(targetPos) : center;
    }
}
