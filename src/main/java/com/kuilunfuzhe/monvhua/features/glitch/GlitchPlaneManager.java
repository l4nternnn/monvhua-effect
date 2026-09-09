package com.kuilunfuzhe.monvhua.features.glitch;

import com.kuilunfuzhe.monvhua.network.glitch.GlitchPlanePackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class GlitchPlaneManager {
    public static final int MAX_SIDE = 64;
    public static final int MAX_PLANES_PER_DIMENSION = 64;
    private GlitchPlaneManager() {}

    public static Optional<GlitchPlane> create(ServerWorld world, String name, BlockPos first, BlockPos second) {
        if (world == null || name == null || !name.matches("[a-zA-Z0-9_-]{1,32}")) return Optional.empty();
        GlitchPlane.Axis axis = normalAxis(first, second);
        if (axis == null || GlitchPlaneStore.get(world).all().size() >= MAX_PLANES_PER_DIMENSION) return Optional.empty();
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        if (sideLength(axis, min, max, true) > MAX_SIDE || sideLength(axis, min, max, false) > MAX_SIDE) return Optional.empty();
        GlitchPlane plane = new GlitchPlane(null, name, axis, min, max, true, ThreadLocalRandom.current().nextLong(), .65F, 3);
        return GlitchPlaneStore.get(world).add(plane) ? Optional.of(plane) : Optional.empty();
    }

    /** Exactly one fixed coordinate and non-zero extents in both in-plane coordinates. */
    private static GlitchPlane.Axis normalAxis(BlockPos a, BlockPos b) {
        int equal = (a.getX() == b.getX() ? 1 : 0) + (a.getY() == b.getY() ? 1 : 0) + (a.getZ() == b.getZ() ? 1 : 0);
        if (equal != 1) return null;
        if (a.getX() == b.getX()) return GlitchPlane.Axis.X;
        if (a.getY() == b.getY()) return GlitchPlane.Axis.Y;
        return GlitchPlane.Axis.Z;
    }
    private static int sideLength(GlitchPlane.Axis axis, BlockPos min, BlockPos max, boolean first) {
        return switch (axis) {
            case X -> (first ? max.getY() - min.getY() + 1 : max.getZ() - min.getZ() + 1);
            case Y -> (first ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1);
            case Z -> (first ? max.getX() - min.getX() + 1 : max.getY() - min.getY() + 1);
        };
    }
    public static Optional<GlitchPlane> update(ServerWorld world, String name, java.util.function.UnaryOperator<GlitchPlane> change) {
        GlitchPlaneStore store = GlitchPlaneStore.get(world); Optional<GlitchPlane> old = store.find(name);
        if (old.isEmpty()) return Optional.empty(); GlitchPlane updated = change.apply(old.get());
        return store.replace(updated) ? Optional.of(updated) : Optional.empty();
    }
    public static Optional<GlitchPlane> remove(ServerWorld world, String name) { return GlitchPlaneStore.get(world).remove(name); }
    public static List<GlitchPlane> all(ServerWorld world) { return GlitchPlaneStore.get(world).all(); }
    public static void syncTo(ServerPlayerEntity player) { if (player != null && player.getWorld() instanceof ServerWorld world) ServerPlayNetworking.send(player, new GlitchPlanePackets.FullSyncS2C(all(world))); }
    public static void broadcast(ServerWorld world) { GlitchPlanePackets.FullSyncS2C packet = new GlitchPlanePackets.FullSyncS2C(all(world)); for (ServerPlayerEntity player : world.getPlayers()) ServerPlayNetworking.send(player, packet); }
}
