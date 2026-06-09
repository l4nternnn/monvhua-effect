package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.item.paint.PaintBucketItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaintBucketBlock extends BlockWithEntity {
    private static final double KICK_SPEED_SQUARED = 0.0121D;
    private static final double KICK_APPROACH_DOT = 0.01D;
    private static final double KICK_RADIUS_SQUARED = 0.68D * 0.68D;
    private static final VoxelShape SHAPE = Block.createCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 14.0D, 14.0D);
    private static final Map<UUID, Vec3d> LAST_PLAYER_POSITIONS = new ConcurrentHashMap<>();

    public PaintBucketBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(PaintBucketBlock::new);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient() && PaintBucketItem.isFilled(itemStack) && world.getBlockEntity(pos) instanceof PaintBucketBlockEntity bucket) {
            bucket.fill(PaintBucketItem.getColor(itemStack));
        }
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler) {
        if (!(world instanceof ServerWorld serverWorld) || !(entity instanceof PlayerEntity player)) {
            return;
        }
        if (!player.isSprinting() || !(world.getBlockEntity(pos) instanceof PaintBucketBlockEntity bucket) || !bucket.isFilled()) {
            return;
        }
        Vec3d velocity = player.getVelocity();
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeedSquared < KICK_SPEED_SQUARED) {
            return;
        }
        if (!isApproaching(player.getX() - velocity.x, player.getZ() - velocity.z, pos, velocity)) {
            return;
        }
        kick(serverWorld, pos, bucket, velocity);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PaintBucketBlockEntity(pos, state);
    }

    public static void tickKicks(ServerWorld world) {
        for (PlayerEntity player : world.getPlayers()) {
            if (!player.isSprinting()) {
                recordPosition(player);
                continue;
            }
            Vec3d motion = movementSinceLastTick(player);
            double horizontalSpeedSquared = motion.x * motion.x + motion.z * motion.z;
            if (horizontalSpeedSquared < KICK_SPEED_SQUARED) {
                continue;
            }
            double previousX = player.getX() - motion.x;
            double previousZ = player.getZ() - motion.z;
            BlockPos base = player.getBlockPos();
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos pos = base.add(x, y, z);
                        if (world.getBlockState(pos).getBlock() != com.kuilunfuzhe.monvhua.item.paint.PaintItems.PAINT_BUCKET_BLOCK) {
                            continue;
                        }
                        double dx = player.getX() - (pos.getX() + 0.5D);
                        double dz = player.getZ() - (pos.getZ() + 0.5D);
                        if (dx * dx + dz * dz > KICK_RADIUS_SQUARED) {
                            continue;
                        }
                        if (!isApproaching(previousX, previousZ, pos, motion)) {
                            continue;
                        }
                        if (world.getBlockEntity(pos) instanceof PaintBucketBlockEntity bucket && bucket.isFilled()) {
                            kick(world, pos, bucket, motion);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void recordPosition(PlayerEntity player) {
        LAST_PLAYER_POSITIONS.put(player.getUuid(), player.getPos());
    }

    private static Vec3d movementSinceLastTick(PlayerEntity player) {
        Vec3d current = player.getPos();
        Vec3d previous = LAST_PLAYER_POSITIONS.put(player.getUuid(), current);
        if (previous == null || previous.squaredDistanceTo(current) > 9.0D) {
            return Vec3d.ZERO;
        }
        return current.subtract(previous);
    }

    private static boolean isApproaching(double previousX, double previousZ, BlockPos pos, Vec3d motion) {
        double bucketX = pos.getX() + 0.5D;
        double bucketZ = pos.getZ() + 0.5D;
        double approach = (bucketX - previousX) * motion.x + (bucketZ - previousZ) * motion.z;
        return approach > KICK_APPROACH_DOT;
    }

    private static void kick(ServerWorld world, BlockPos pos, PaintBucketBlockEntity bucket, Vec3d velocity) {
        double length = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (length < 0.001D) {
            return;
        }
        double fX = velocity.x / length;
        double fZ = velocity.z / length;
        spill(world, pos, fX, fZ, 0xFF000000 | bucket.getColor());
        spawnSpillParticles(world, pos, fX, fZ, bucket.getColor());
        bucket.empty();
    }

    private static void spill(ServerWorld world, BlockPos bucketPos, double fX, double fZ, int color) {
        Random random = world.getRandom();
        BlockPos floorOrigin = bucketPos.down();
        double blockedDistance = firstBlockedDistance(world, floorOrigin, fX, fZ);
        double maxDistance = blockedDistance < 0.0D ? 4.0D : Math.max(0.0D, blockedDistance - 0.2D);

        paintContinuousFloorSpill(world, bucketPos, floorOrigin, fX, fZ, color, random.nextInt(), maxDistance);
        paintParabolicSplatter(world, bucketPos, floorOrigin, fX, fZ, color, random, maxDistance);
    }

    private static double firstBlockedDistance(ServerWorld world, BlockPos floorOrigin, double fX, double fZ) {
        for (double distance = 0.75D; distance <= 4.1D; distance += 0.25D) {
            BlockPos check = offset(floorOrigin, fX * distance, fZ * distance).up();
            if (!world.isAir(check) && world.getBlockState(check).getBlock() != com.kuilunfuzhe.monvhua.item.paint.PaintItems.PAINT_BUCKET_BLOCK) {
                return distance;
            }
        }
        return -1.0D;
    }

    private static void paintContinuousFloorSpill(ServerWorld world, BlockPos bucketPos, BlockPos floorOrigin,
                                                 double fX, double fZ, int color, int seed, double maxDistance) {
        double originX = bucketPos.getX() + 0.5D;
        double originZ = bucketPos.getZ() + 0.5D;
        double lX = -fZ;
        double lZ = fX;
        double length = maxDistance + 0.85D;
        int range = (int) Math.ceil(length + 3.0D);

        for (int dxBlock = -range; dxBlock <= range; dxBlock++) {
            for (int dzBlock = -range; dzBlock <= range; dzBlock++) {
                BlockPos floorPos = findOpenFloor(world, floorOrigin.add(dxBlock, 0, dzBlock), floorOrigin.getY() - 3);
                if (floorPos == null) {
                    continue;
                }
                PaintOverlayFeature.paintPixels(world, floorPos, Direction.UP, color, (x, y) -> {
                    double worldX = floorPos.getX() + (x + 0.5D) / PaintOverlayStore.SIZE;
                    double worldZ = floorPos.getZ() + (y + 0.5D) / PaintOverlayStore.SIZE;
                    double dx = worldX - originX;
                    double dz = worldZ - originZ;
                    double t = dx * fX + dz * fZ;
                    if (t < -0.45D || t > length) {
                        return false;
                    }
                    double side = dx * lX + dz * lZ;
                    double progress = Math.max(0.0D, Math.min(1.0D, t / Math.max(0.1D, length)));
                    double center = centerline(t, seed);
                    double width = 0.42D + 1.52D * Math.sin(Math.PI * progress);
                    double noise = edgeNoise(worldX, worldZ, seed) * 0.32D;
                    return Math.abs(side - center) <= width + noise;
                });
            }
        }
    }

    private static void paintParabolicSplatter(ServerWorld world, BlockPos bucketPos, BlockPos floorOrigin,
                                               double fX, double fZ, int color, Random random, double maxDistance) {
        double originX = bucketPos.getX() + 0.5D;
        double originY = bucketPos.getY();
        double originZ = bucketPos.getZ() + 0.5D;
        double lX = -fZ;
        double lZ = fX;
        int drops = 26 + random.nextInt(10);
        double reach = Math.max(0.75D, Math.min(4.0D, maxDistance + 0.25D));

        for (int i = 0; i < drops; i++) {
            double targetDistance = 0.55D + random.nextDouble() * reach;
            double spread = (random.nextDouble() - random.nextDouble()) * (0.18D + targetDistance * 0.26D);
            double previousX = originX;
            double previousY = originY + 0.35D;
            double previousZ = originZ;
            boolean impacted = false;

            for (double distance = 0.18D; distance <= targetDistance; distance += 0.14D) {
                double progress = distance / targetDistance;
                double worldX = originX + fX * distance + lX * spread * progress;
                double worldY = originY + parabolicHeight(distance) + (random.nextDouble() - 0.5D) * 0.12D;
                double worldZ = originZ + fZ * distance + lZ * spread * progress;
                BlockPos hitPos = BlockPos.ofFloored(worldX, worldY, worldZ);

                if (!hitPos.equals(bucketPos) && isSolidPaintTarget(world, hitPos)) {
                    Direction face = impactFace(previousX, previousY, previousZ, worldX, worldY, worldZ);
                    if (face != Direction.DOWN) {
                        paintImpactBlob(world, hitPos, face, worldX, worldY, worldZ, color, random, distance);
                    }
                    impacted = true;
                    break;
                }

                previousX = worldX;
                previousY = worldY;
                previousZ = worldZ;
            }

            if (!impacted) {
                paintFallingDrop(world, floorOrigin, originX, originY, originZ, fX, fZ, lX, lZ,
                        targetDistance, spread, color, random);
            }
        }
    }

    private static void paintFallingDrop(ServerWorld world, BlockPos floorOrigin,
                                         double originX, double originY, double originZ,
                                         double fX, double fZ, double lX, double lZ,
                                         double distance, double spread, int color, Random random) {
        double worldX = originX + fX * distance + lX * spread;
        double worldY = originY + parabolicHeight(distance);
        double worldZ = originZ + fZ * distance + lZ * spread;
        BlockPos column = BlockPos.ofFloored(worldX, floorOrigin.getY(), worldZ);
        BlockPos floorPos = findOpenFloor(world, column, floorOrigin.getY() - 3);
        if (floorPos == null || worldY > floorPos.getY() + 1.22D) {
            return;
        }
        paintImpactBlob(world, floorPos, Direction.UP, worldX, floorPos.getY() + 1.0D, worldZ, color, random, distance);
    }

    private static double parabolicHeight(double distance) {
        return 0.8D - 0.2D * (distance - 1.0D) * (distance - 1.0D);
    }

    @Nullable
    private static BlockPos findOpenFloor(ServerWorld world, BlockPos start, int minY) {
        for (BlockPos pos = start; pos.getY() >= minY; pos = pos.down()) {
            if (isSolidPaintTarget(world, pos) && isOpenAboveForFloorPaint(world, pos.up())) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isOpenAboveForFloorPaint(ServerWorld world, BlockPos pos) {
        return world.isAir(pos) || world.getBlockState(pos).getBlock() == com.kuilunfuzhe.monvhua.item.paint.PaintItems.PAINT_BUCKET_BLOCK;
    }

    private static boolean isSolidPaintTarget(ServerWorld world, BlockPos pos) {
        return !world.isAir(pos) && world.getBlockState(pos).getBlock() != com.kuilunfuzhe.monvhua.item.paint.PaintItems.PAINT_BUCKET_BLOCK;
    }

    private static BlockPos offset(BlockPos origin, double x, double z) {
        return BlockPos.ofFloored(origin.getX() + x, origin.getY(), origin.getZ() + z);
    }

    private static Direction wallFaceFor(double x, double z) {
        return Math.abs(x) > Math.abs(z)
                ? (x > 0.0D ? Direction.WEST : Direction.EAST)
                : (z > 0.0D ? Direction.NORTH : Direction.SOUTH);
    }

    private static Direction impactFace(double previousX, double previousY, double previousZ,
                                        double worldX, double worldY, double worldZ) {
        double dx = worldX - previousX;
        double dy = worldY - previousY;
        double dz = worldZ - previousZ;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy < 0.0D ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return dx > 0.0D ? Direction.WEST : Direction.EAST;
        }
        return dz > 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static void paintImpactBlob(ServerWorld world, BlockPos pos, Direction face,
                                        double worldX, double worldY, double worldZ,
                                        int color, Random random, double distance) {
        int[] center = facePixel(pos, face, worldX, worldY, worldZ);
        int radius = distance > 2.2D ? 1 + random.nextInt(2) : 2 + random.nextInt(2);
        int radiusY = face == Direction.UP ? radius : radius + random.nextInt(2);
        paintBlobIfPresent(world, pos, face, center[0], center[1], radius, radiusY, color, random);
    }

    private static int[] facePixel(BlockPos pos, Direction face, double worldX, double worldY, double worldZ) {
        double localX = worldX - pos.getX();
        double localY = worldY - pos.getY();
        double localZ = worldZ - pos.getZ();
        int x;
        int y;
        switch (face) {
            case UP -> {
                x = toPixel(localX);
                y = toPixel(localZ);
            }
            case NORTH -> {
                x = toPixel(1.0D - localX);
                y = toPixel(1.0D - localY);
            }
            case SOUTH -> {
                x = toPixel(localX);
                y = toPixel(1.0D - localY);
            }
            case WEST -> {
                x = toPixel(localZ);
                y = toPixel(1.0D - localY);
            }
            case EAST -> {
                x = toPixel(1.0D - localZ);
                y = toPixel(1.0D - localY);
            }
            case DOWN -> {
                x = toPixel(localX);
                y = toPixel(1.0D - localZ);
            }
            default -> {
                x = PaintOverlayStore.SIZE / 2;
                y = PaintOverlayStore.SIZE / 2;
            }
        }
        return new int[]{x, y};
    }

    private static int toPixel(double value) {
        int pixel = (int) Math.floor(value * PaintOverlayStore.SIZE);
        if (pixel < 0) {
            return 0;
        }
        if (pixel >= PaintOverlayStore.SIZE) {
            return PaintOverlayStore.SIZE - 1;
        }
        return pixel;
    }

    private static void paintWallBlob(ServerWorld world, BlockPos pos, Direction face, int color, Random random, boolean large) {
        if (world.isAir(pos)) {
            return;
        }
        int radius = large ? 4 + random.nextInt(3) : 3 + random.nextInt(2);
        paintBlobIfPresent(world, pos, face, randomCenter(random), 9 + random.nextInt(5),
                radius, radius + random.nextInt(2), color, random);
    }

    private static void paintBlobIfPresent(ServerWorld world, BlockPos pos, Direction face, int centerX, int centerY,
                                           int radiusX, int radiusY, int color, Random random) {
        if (world.isAir(pos)) {
            return;
        }
        PaintOverlayFeature.paintBlob(world, pos, face, centerX, centerY, radiusX, radiusY, color, random.nextInt());
    }

    private static int randomCenter(Random random) {
        return 5 + random.nextInt(7);
    }

    private static double centerline(double t, int seed) {
        double s = (seed & 1023) * 0.017D;
        return Math.sin(t * 1.35D + s) * 0.18D + Math.sin(t * 2.4D + s * 0.7D) * 0.08D;
    }

    private static double edgeNoise(double x, double z, int seed) {
        int xi = (int) Math.floor(x * 7.0D);
        int zi = (int) Math.floor(z * 7.0D);
        int hash = xi * 734287 + zi * 912271 + seed * 438289;
        return ((hash & 31) / 15.5D) - 1.0D;
    }

    private static void spawnSpillParticles(ServerWorld world, BlockPos pos, double fX, double fZ, int color) {
        DustParticleEffect particle = new DustParticleEffect(color & 0xFFFFFF, 1.2F);
        double x = pos.getX() + 0.5D + fX * 0.25D;
        double y = pos.getY() + 0.65D;
        double z = pos.getZ() + 0.5D + fZ * 0.25D;
        world.spawnParticles(
                particle,
                x, y, z,
                42,
                Math.abs(fZ) * 0.45D + 0.18D,
                0.18D,
                Math.abs(fX) * 0.45D + 0.18D,
                0.08D
        );
    }
}
