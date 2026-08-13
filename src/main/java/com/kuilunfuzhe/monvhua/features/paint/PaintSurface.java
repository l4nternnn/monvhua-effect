package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Shared coordinate system for a paintable block face. */
public final class PaintSurface {
    private PaintSurface() {
    }

    public static BlockPos blockAt(BlockPos origin, Direction face, int blockU, int blockV) {
        BlockPos right = rightOffset(face);
        BlockPos down = downOffset(face);
        return origin.add(
                right.getX() * blockU + down.getX() * blockV,
                right.getY() * blockU + down.getY() * blockV,
                right.getZ() * blockU + down.getZ() * blockV);
    }

    public static Vec3d point(BlockPos pos, Direction face, int microU, int microV, double microStep, double offset) {
        double u = microU * microStep;
        double v = microV * microStep;
        return switch (face) {
            case UP -> new Vec3d(pos.getX() + u, pos.getY() + 1.0D + offset, pos.getZ() + v);
            case DOWN -> new Vec3d(pos.getX() + u, pos.getY() - offset, pos.getZ() + 1.0D - v);
            case NORTH -> new Vec3d(pos.getX() + 1.0D - u, pos.getY() + 1.0D - v, pos.getZ() - offset);
            case SOUTH -> new Vec3d(pos.getX() + u, pos.getY() + 1.0D - v, pos.getZ() + 1.0D + offset);
            case WEST -> new Vec3d(pos.getX() - offset, pos.getY() + 1.0D - v, pos.getZ() + u);
            case EAST -> new Vec3d(pos.getX() + 1.0D + offset, pos.getY() + 1.0D - v, pos.getZ() + 1.0D - u);
        };
    }

    public static Vec3d normal(Direction face) {
        return new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
    }

    public static int blockU(BlockPos origin, BlockPos pos, Direction face) {
        BlockPos right = rightOffset(face);
        return (pos.getX() - origin.getX()) * right.getX()
                + (pos.getY() - origin.getY()) * right.getY()
                + (pos.getZ() - origin.getZ()) * right.getZ();
    }

    public static int blockV(BlockPos origin, BlockPos pos, Direction face) {
        BlockPos down = downOffset(face);
        return (pos.getX() - origin.getX()) * down.getX()
                + (pos.getY() - origin.getY()) * down.getY()
                + (pos.getZ() - origin.getZ()) * down.getZ();
    }

    private static BlockPos rightOffset(Direction face) {
        return switch (face) {
            case NORTH -> new BlockPos(-1, 0, 0);
            case EAST -> new BlockPos(0, 0, -1);
            case WEST -> new BlockPos(0, 0, 1);
            default -> new BlockPos(1, 0, 0);
        };
    }

    private static BlockPos downOffset(Direction face) {
        return switch (face) {
            case UP -> new BlockPos(0, 0, 1);
            case DOWN -> new BlockPos(0, 0, -1);
            default -> new BlockPos(0, -1, 0);
        };
    }
}
