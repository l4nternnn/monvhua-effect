package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public record GravityAreaSpec(Shape shape, Half half, int sizeX, int sizeY, int sizeZ) {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 96;
    public static final int MAX_RENDER_BLOCKS = 24000;

    public GravityAreaSpec {
        shape = shape == null ? Shape.SPHERE : shape;
        half = half == null ? Half.LOWER : half;
        sizeX = clampSize(sizeX);
        sizeY = clampSize(sizeY);
        sizeZ = clampSize(sizeZ);
        if (shape == Shape.CUBE) {
            int size = Math.max(sizeX, Math.max(sizeY, sizeZ));
            sizeX = size;
            sizeY = size;
            sizeZ = size;
        }
    }

    public static GravityAreaSpec legacy(int radius, int height) {
        return new GravityAreaSpec(Shape.SPHERE, Half.LOWER, radius, height, radius);
    }

    public int maxExtent() {
        return Math.max(sizeX, Math.max(sizeY, sizeZ));
    }

    public boolean contains(BlockPos center, Vec3d pos) {
        Vec3d origin = center.toCenterPos();
        double dx = pos.x - origin.x;
        double dy = pos.y - origin.y;
        double dz = pos.z - origin.z;
        if (!half.containsY(dy, sizeY)) {
            return false;
        }

        return switch (shape) {
            case SPHERE -> ellipsoid(dx, dy, dz);
            case BOX, CUBE -> Math.abs(dx) <= sizeX + 0.5D
                    && Math.abs(dz) <= sizeZ + 0.5D
                    && half.containsY(dy, sizeY + 0.5D);
        };
    }

    public boolean containsBlock(BlockPos center, BlockPos pos) {
        return contains(center, pos.toCenterPos());
    }

    public Bounds bounds(BlockPos center) {
        int minY = switch (half) {
            case FULL -> center.getY() - sizeY;
            case UPPER -> center.getY();
            case LOWER -> center.getY() - sizeY;
        };
        int maxY = switch (half) {
            case FULL -> center.getY() + sizeY;
            case UPPER -> center.getY() + sizeY;
            case LOWER -> center.getY();
        };
        return new Bounds(
                center.getX() - sizeX,
                minY,
                center.getZ() - sizeZ,
                center.getX() + sizeX,
                maxY,
                center.getZ() + sizeZ
        );
    }

    public List<BlockPos> coveredBlocks(BlockPos center) {
        Bounds bounds = bounds(center);
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (containsBlock(center, pos)) {
                        blocks.add(pos);
                        if (blocks.size() >= MAX_RENDER_BLOCKS) {
                            return blocks;
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private boolean ellipsoid(double dx, double dy, double dz) {
        double nx = dx / Math.max(1.0D, sizeX + 0.5D);
        double ny = dy / Math.max(1.0D, sizeY + 0.5D);
        double nz = dz / Math.max(1.0D, sizeZ + 0.5D);
        return nx * nx + ny * ny + nz * nz <= 1.0D;
    }

    private static int clampSize(int value) {
        return Math.clamp(value, MIN_SIZE, MAX_SIZE);
    }

    public enum Shape {
        SPHERE,
        BOX,
        CUBE;

        public static Shape byId(int id) {
            Shape[] values = values();
            return id >= 0 && id < values.length ? values[id] : SPHERE;
        }
    }

    public enum Half {
        FULL,
        UPPER,
        LOWER;

        public static Half byId(int id) {
            Half[] values = values();
            return id >= 0 && id < values.length ? values[id] : LOWER;
        }

        private boolean containsY(double dy, double sizeY) {
            return switch (this) {
                case FULL -> Math.abs(dy) <= sizeY;
                case UPPER -> dy >= 0.0D && dy <= sizeY;
                case LOWER -> dy <= 0.0D && dy >= -sizeY;
            };
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
