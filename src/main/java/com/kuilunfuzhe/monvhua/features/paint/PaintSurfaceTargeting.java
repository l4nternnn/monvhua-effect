package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves every exposed block-face pixel touched by a paint tool's world-space volume. */
public final class PaintSurfaceTargeting {
    private static final double PIXEL_SIZE = 1.0D / PaintOverlayStore.SIZE;
    private static final double EPSILON = 1.0E-9D;

    private PaintSurfaceTargeting() {
    }

    public static List<SurfacePixel> collect(BlockView world, Vec3d center, int radiusPixels) {
        if (world == null || !isFinite(center)) {
            return List.of();
        }
        int radius = MathHelper.clamp(radiusPixels, PaintOverlayFeature.MIN_RADIUS,
                PaintOverlayFeature.MAX_MANUAL_RADIUS);
        double worldRadius = Math.max(0.5D, radius - 0.5D) * PIXEL_SIZE;
        double radiusSquared = worldRadius * worldRadius;
        int minX = MathHelper.floor(center.x - worldRadius);
        int minY = MathHelper.floor(center.y - worldRadius);
        int minZ = MathHelper.floor(center.z - worldRadius);
        int maxX = MathHelper.floor(center.x + worldRadius);
        int maxY = MathHelper.floor(center.y + worldRadius);
        int maxZ = MathHelper.floor(center.z + worldRadius);

        List<SurfacePixel> result = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockY = minY; blockY <= maxY; blockY++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    pos.set(blockX, blockY, blockZ);
                    if (!isPaintableBlock(world, pos)) {
                        continue;
                    }
                    BlockPos immutablePos = pos.toImmutable();
                    for (Direction face : Direction.values()) {
                        if (!isExposed(world, immutablePos, face)
                                || faceDistanceSquared(center, immutablePos, face) > radiusSquared + EPSILON) {
                            continue;
                        }
                        collectFacePixels(result, center, radiusSquared, immutablePos, face);
                    }
                }
            }
        }
        result.sort(Comparator.comparingDouble(SurfacePixel::distanceSquared));
        return result;
    }

    public static Set<PaintOverlayStore.FaceKey> collectFaces(BlockView world, Vec3d center, int radiusPixels) {
        Set<PaintOverlayStore.FaceKey> faces = new LinkedHashSet<>();
        if (world == null || !isFinite(center)) {
            return faces;
        }
        int radius = MathHelper.clamp(radiusPixels, PaintOverlayFeature.MIN_RADIUS,
                PaintOverlayFeature.MAX_MANUAL_RADIUS);
        double worldRadius = Math.max(0.5D, radius - 0.5D) * PIXEL_SIZE;
        double radiusSquared = worldRadius * worldRadius;
        int minX = MathHelper.floor(center.x - worldRadius);
        int minY = MathHelper.floor(center.y - worldRadius);
        int minZ = MathHelper.floor(center.z - worldRadius);
        int maxX = MathHelper.floor(center.x + worldRadius);
        int maxY = MathHelper.floor(center.y + worldRadius);
        int maxZ = MathHelper.floor(center.z + worldRadius);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockY = minY; blockY <= maxY; blockY++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    pos.set(blockX, blockY, blockZ);
                    if (!isPaintableBlock(world, pos)) {
                        continue;
                    }
                    BlockPos immutablePos = pos.toImmutable();
                    for (Direction face : Direction.values()) {
                        if (isExposed(world, immutablePos, face)
                                && faceDistanceSquared(center, immutablePos, face) <= radiusSquared + EPSILON) {
                            faces.add(new PaintOverlayStore.FaceKey(immutablePos, face));
                        }
                    }
                }
            }
        }
        return faces;
    }

    public static double worldRadius(int radiusPixels) {
        int radius = MathHelper.clamp(radiusPixels, PaintOverlayFeature.MIN_RADIUS,
                PaintOverlayFeature.MAX_MANUAL_RADIUS);
        return Math.max(0.5D, radius - 0.5D) * PIXEL_SIZE;
    }

    private static void collectFacePixels(List<SurfacePixel> result, Vec3d center, double radiusSquared,
                                          BlockPos pos, Direction face) {
        FaceLocal local = faceLocal(center, pos, face);
        double planeRadiusSquared = radiusSquared - local.normalDistance() * local.normalDistance();
        if (planeRadiusSquared < -EPSILON) {
            return;
        }
        double planeRadius = Math.sqrt(Math.max(0.0D, planeRadiusSquared)) + PIXEL_SIZE;
        int minX = pixelMin(local.u() - planeRadius);
        int maxX = pixelMax(local.u() + planeRadius);
        int minY = pixelMin(local.v() - planeRadius);
        int maxY = pixelMax(local.v() + planeRadius);
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(pos, face);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double distanceSquared = pixelDistanceSquared(center, pos, face, x, y);
                if (distanceSquared <= radiusSquared + EPSILON) {
                    result.add(new SurfacePixel(key, x, y, distanceSquared));
                }
            }
        }
    }

    private static int pixelMin(double localCoordinate) {
        return MathHelper.clamp(MathHelper.floor(localCoordinate / PIXEL_SIZE), 0, PaintOverlayStore.SIZE - 1);
    }

    private static int pixelMax(double localCoordinate) {
        return MathHelper.clamp(MathHelper.floor(localCoordinate / PIXEL_SIZE), 0, PaintOverlayStore.SIZE - 1);
    }

    private static FaceLocal faceLocal(Vec3d center, BlockPos pos, Direction face) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;
        return switch (face) {
            case UP -> new FaceLocal(center.x - minX, center.z - minZ, center.y - maxY);
            case DOWN -> new FaceLocal(center.x - minX, maxZ - center.z, center.y - minY);
            case NORTH -> new FaceLocal(maxX - center.x, maxY - center.y, center.z - minZ);
            case SOUTH -> new FaceLocal(center.x - minX, maxY - center.y, center.z - maxZ);
            case WEST -> new FaceLocal(center.z - minZ, maxY - center.y, center.x - minX);
            case EAST -> new FaceLocal(maxZ - center.z, maxY - center.y, center.x - maxX);
        };
    }

    private static boolean isPaintableBlock(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir()
                && state.getBlock() != ModBlocks.DRAWING_BOARD
                && state.getBlock() != PaintItems.PAINT_BUCKET_BLOCK
                && PaintOverlayFeature.canPlacePaint(world, pos);
    }

    private static boolean isExposed(BlockView world, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.offset(face);
        return !world.getBlockState(neighborPos).isFullCube(world, neighborPos);
    }

    private static double faceDistanceSquared(Vec3d center, BlockPos pos, Direction face) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;
        return switch (face) {
            case DOWN -> squaredDistance(center, minX, minY, minZ, maxX, minY, maxZ);
            case UP -> squaredDistance(center, minX, maxY, minZ, maxX, maxY, maxZ);
            case NORTH -> squaredDistance(center, minX, minY, minZ, maxX, maxY, minZ);
            case SOUTH -> squaredDistance(center, minX, minY, maxZ, maxX, maxY, maxZ);
            case WEST -> squaredDistance(center, minX, minY, minZ, minX, maxY, maxZ);
            case EAST -> squaredDistance(center, maxX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    private static double pixelDistanceSquared(Vec3d center, BlockPos pos, Direction face, int x, int y) {
        double u0 = x * PIXEL_SIZE;
        double u1 = u0 + PIXEL_SIZE;
        double v0 = y * PIXEL_SIZE;
        double v1 = v0 + PIXEL_SIZE;
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;
        return switch (face) {
            case UP -> squaredDistance(center, minX + u0, maxY, minZ + v0, minX + u1, maxY, minZ + v1);
            case DOWN -> squaredDistance(center, minX + u0, minY, maxZ - v1, minX + u1, minY, maxZ - v0);
            case NORTH -> squaredDistance(center, maxX - u1, maxY - v1, minZ, maxX - u0, maxY - v0, minZ);
            case SOUTH -> squaredDistance(center, minX + u0, maxY - v1, maxZ, minX + u1, maxY - v0, maxZ);
            case WEST -> squaredDistance(center, minX, maxY - v1, minZ + u0, minX, maxY - v0, minZ + u1);
            case EAST -> squaredDistance(center, maxX, maxY - v1, maxZ - u1, maxX, maxY - v0, maxZ - u0);
        };
    }

    private static double squaredDistance(Vec3d point, double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        double dx = point.x < minX ? minX - point.x : Math.max(point.x - maxX, 0.0D);
        double dy = point.y < minY ? minY - point.y : Math.max(point.y - maxY, 0.0D);
        double dz = point.z < minZ ? minZ - point.z : Math.max(point.z - maxZ, 0.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isFinite(Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private record FaceLocal(double u, double v, double normalDistance) {
    }

    public record SurfacePixel(PaintOverlayStore.FaceKey key, int x, int y, double distanceSquared) {
    }
}
