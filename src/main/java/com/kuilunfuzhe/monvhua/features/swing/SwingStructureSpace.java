package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A single immutable pose shared by rendering, ray queries and collision calculations. */
public record SwingStructureSpace(SwingStructure structure, Vec3d pivot, float angle, boolean zAxis) {
    public Vec3d toWorld(Vec3d point) { return SwingTransform.localToWorld(point, pivot, angle, zAxis); }
    public Vec3d toLocal(Vec3d point) { return SwingTransform.rotate(point.subtract(pivot), -angle, zAxis); }
    public Box bounds() { return transformBounds(structure.geometry().bounds); }

    public Box transformBounds(Box box) {
        Vec3d center = toWorld(box.getCenter());
        double c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
        double x = box.getLengthX() * .5, y = box.getLengthY() * .5, z = box.getLengthZ() * .5;
        double hx = zAxis ? c * x + s * y : x;
        double hy = zAxis ? s * x + c * y : c * y + s * z;
        double hz = zAxis ? z : s * y + c * z;
        return new Box(center.x - hx, center.y - hy, center.z - hz,
                center.x + hx, center.y + hy, center.z + hz);
    }

    public Optional<Hit> raycast(Vec3d start, Vec3d end) {
        // BlockView shapes use block corners; the swing pivot uses block centers.
        Vec3d localStart = toLocal(start).add(.5, .5, .5);
        Vec3d localEnd = toLocal(end).add(.5, .5, .5);
        Hit nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Part part : structure.geometry().parts) {
            var hit = part.outline.raycast(localStart, localEnd, part.pos);
            if (hit == null) continue;
            double distance = localStart.squaredDistanceTo(hit.getPos());
            if (distance >= best) continue;
            best = distance;
            Vec3d localPoint = hit.getPos().subtract(.5, .5, .5);
            nearest = new Hit(part.pos, hit.getSide(), localPoint, toWorld(localPoint), part.seat);
        }
        return Optional.ofNullable(nearest);
    }

    /** Bounded voxel approximation of rotated cuboids; never a union of the entire structure. */
    public List<Box> collisionBoxes(Box query) {
        ArrayList<Box> result = new ArrayList<>();
        for (Part part : structure.geometry().parts) {
            if (query != null && !transformBounds(part.bounds).intersects(query)) continue;
            List<Box> cells = Math.abs(angle) < 1.0e-7 ? part.collisions : (zAxis ? part.zCells : part.xCells);
            for (Box cell : cells) {
                Box world = transformBounds(cell);
                if (query == null || world.intersects(query)) result.add(world);
            }
        }
        return result;
    }

    public record Hit(BlockPos block, Direction localFace, Vec3d localPoint, Vec3d worldPoint, int seat) { }

    private record Part(BlockPos pos, VoxelShape outline, int seat, Box bounds,
                        List<Box> collisions, List<Box> xCells, List<Box> zCells) { }

    public static final class Geometry {
        private final List<Part> parts;
        private final Box bounds;
        Geometry(SwingStructure structure) {
            int bottom = structure.seatBlocks().stream().mapToInt(b -> b.localPos().getY()).min().orElse(Integer.MIN_VALUE);
            ArrayList<Part> built = new ArrayList<>();
            Box total = structure.localBounds();
            int seat = 0;
            for (SwingBlock block : structure.blocks()) {
                BlockPos pos = block.localPos();
                var outline = block.state().getOutlineShape(structure.blockWorld(), pos);
                List<Box> collisions = block.state().getCollisionShape(structure.blockWorld(), pos).getBoundingBoxes()
                        .stream().map(b -> b.offset(pos.getX() - .5, pos.getY() - .5, pos.getZ() - .5)).toList();
                int slot = SwingBlockRoles.seat(block.state()) && pos.getY() == bottom && !outline.isEmpty() ? seat++ : -1;
                Box bounds = new Box(pos).offset(-.5, -.5, -.5);
                for (Box box : collisions) bounds = bounds.union(box);
                if (!outline.isEmpty()) bounds = bounds.union(outline.getBoundingBox().offset(pos.getX() - .5, pos.getY() - .5, pos.getZ() - .5));
                total = total.union(bounds);
                double cellSize = slot >= 0 ? 1.0 / 16.0 : 1.0 / 8.0;
                built.add(new Part(pos, outline, slot, bounds, collisions,
                        subdivide(collisions, false, cellSize), subdivide(collisions, true, cellSize)));
            }
            parts = List.copyOf(built);
            bounds = total;
        }
    }

    private static List<Box> subdivide(List<Box> boxes, boolean zAxis, double size) {
        ArrayList<Box> cells = new ArrayList<>();
        for (Box box : boxes) {
            int nx = zAxis ? Math.max(1, (int) Math.ceil(box.getLengthX() / size)) : 1;
            int ny = Math.max(1, (int) Math.ceil(box.getLengthY() / size));
            int nz = zAxis ? 1 : Math.max(1, (int) Math.ceil(box.getLengthZ() / size));
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                cells.add(new Box(MathHelper.lerp(x / (double) nx, box.minX, box.maxX),
                        MathHelper.lerp(y / (double) ny, box.minY, box.maxY), MathHelper.lerp(z / (double) nz, box.minZ, box.maxZ),
                        MathHelper.lerp((x + 1.0) / nx, box.minX, box.maxX), MathHelper.lerp((y + 1.0) / ny, box.minY, box.maxY),
                        MathHelper.lerp((z + 1.0) / nz, box.minZ, box.maxZ)));
            }
        }
        return List.copyOf(cells);
    }
}
