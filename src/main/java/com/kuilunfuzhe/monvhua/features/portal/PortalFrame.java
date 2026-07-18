package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record PortalFrame(Vec3d center,
                          Vec3d widthAxis,
                          Vec3d heightAxis,
                          Vec3d normal,
                          Vec3d contentNormal,
                          int width,
                          int height) {
    public PortalFrame {
        width = Math.max(1, width);
        height = Math.max(1, height);
        widthAxis = widthAxis.normalize();
        heightAxis = heightAxis.normalize();
        normal = widthAxis.crossProduct(heightAxis).normalize();
        contentNormal = normal.multiply(-1.0D);
    }

    public static PortalFrame of(BlockPos origin, Direction facing, int width, int height) {
        Direction direction = facing == null ? Direction.NORTH : facing;
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        Vec3d center = Vec3d.ofCenter(origin)
                .add(gridWidthAxis(direction).multiply((safeWidth - 1) * 0.5D))
                .add(gridHeightAxis(direction).multiply((safeHeight - 1) * 0.5D));
        return centered(center, direction, safeWidth, safeHeight);
    }

    public static PortalFrame local(Direction facing, int width, int height) {
        return of(BlockPos.ORIGIN, facing, width, height);
    }

    public static PortalFrame centered(Vec3d center, Direction facing, int width, int height) {
        Direction direction = facing == null ? Direction.NORTH : facing;
        return new PortalFrame(
                center,
                orientedWidthAxis(direction),
                orientedHeightAxis(direction),
                normal(direction),
                normal(direction).multiply(-1.0D),
                width,
                height
        );
    }

    public boolean containsBlock(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Vec3d local = Vec3d.ofCenter(pos).subtract(center);
        return Math.abs(local.dotProduct(normal)) <= 0.501D
                && containsProjection(Vec3d.ofCenter(pos), 0.0D);
    }

    public boolean containsProjection(Vec3d pos, double margin) {
        Vec3d local = pos.subtract(center);
        return Math.abs(local.dotProduct(widthAxis)) <= width * 0.5D + margin
                && Math.abs(local.dotProduct(heightAxis)) <= height * 0.5D + margin;
    }

    public static Vec3d normal(Direction direction) {
        Direction facing = direction == null ? Direction.NORTH : direction;
        return new Vec3d(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());
    }

    public static Vec3d gridWidthAxis(Direction facing) {
        Direction direction = facing == null ? Direction.NORTH : facing;
        return direction.getAxis() == Direction.Axis.X
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(1.0D, 0.0D, 0.0D);
    }

    public static Vec3d gridHeightAxis(Direction facing) {
        Direction direction = facing == null ? Direction.NORTH : facing;
        return direction.getAxis() == Direction.Axis.Y
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(0.0D, 1.0D, 0.0D);
    }

    private static Vec3d orientedWidthAxis(Direction facing) {
        return switch (facing) {
            case NORTH -> new Vec3d(-1.0D, 0.0D, 0.0D);
            case SOUTH -> new Vec3d(1.0D, 0.0D, 0.0D);
            case WEST -> new Vec3d(0.0D, 0.0D, 1.0D);
            case EAST -> new Vec3d(0.0D, 0.0D, -1.0D);
            case UP -> new Vec3d(-1.0D, 0.0D, 0.0D);
            case DOWN -> new Vec3d(1.0D, 0.0D, 0.0D);
        };
    }

    private static Vec3d orientedHeightAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(0.0D, 1.0D, 0.0D);
    }
}
