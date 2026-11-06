package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.paint.PaintSurface;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

public final class BloodSurfaceProjection {
    private BloodSurfaceProjection() {}
    public static boolean supported(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getFluidState(pos).isEmpty()
                && Block.isShapeFullCube(world.getBlockState(pos).getCollisionShape(world, pos));
    }
    public static BlockHitResult cast(ServerWorld world, Vec3d from, Vec3d to, Entity entity) {
        // Never let forensic raycasts load another chunk.
        for (int i = 0; i <= 16; i++) if (!world.isChunkLoaded(BlockPos.ofFloored(from.lerp(to, i / 16.0)))) return null;
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, entity));
        return hit.getType() == HitResult.Type.BLOCK && supported(world, hit.getBlockPos()) ? hit : null;
    }
    public static BloodPatch impact(BlockHitResult hit, float radius, long seed) {
        BlockPos pos = hit.getBlockPos(); Direction face = hit.getSide();
        Vec3d origin = PaintSurface.point(pos, face, 0, 0, 1, 0), offset = hit.getPos().subtract(origin);
        float u = (float) offset.dotProduct(PaintSurface.point(pos, face, 1, 0, 1, 0).subtract(origin));
        float v = (float) offset.dotProduct(PaintSurface.point(pos, face, 0, 1, 1, 0).subtract(origin));
        return new BloodPatch(UUID.randomUUID().toString(), pos, face, u, v, radius, 0, seed, 1);
    }
    /** At most nine surface checks. Flood-fill prevents a disk crossing a gap. */
    public static Set<BlockPos> poolSupports(ServerWorld world, BlockPos center) {
        Set<BlockPos> allowed = new HashSet<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos p = center.add(x, 0, z);
            if (supported(world, p) && world.isChunkLoaded(p.up()) && world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()
                    && world.getFluidState(p.up()).isEmpty()) allowed.add(p);
        }
        Set<BlockPos> result = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (allowed.contains(center)) { result.add(center); queue.add(center); }
        while (!queue.isEmpty()) {
            BlockPos p = queue.removeFirst();
            // Process all four neighbors of this cell before taking the next cell.
            for (Direction next : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = p.offset(next);
                if (allowed.contains(neighbor) && result.add(neighbor)) queue.add(neighbor);
            }
        }
        return result;
    }
}
