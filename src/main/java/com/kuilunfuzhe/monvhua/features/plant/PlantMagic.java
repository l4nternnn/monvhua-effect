package com.kuilunfuzhe.monvhua.features.plant;

import com.kuilunfuzhe.monvhua.config.GlobalConfigManager;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.features.gravity.GravityBlockEntity;
import com.kuilunfuzhe.monvhua.item.config.PlantMagicConfig;
import com.kuilunfuzhe.monvhua.item.plant.PlantMagicItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlantMagic {
    private static final int RADIUS = 4;
    private static final int TICK_INTERVAL = 5;
    private static final int RANDOM_TICK_ATTEMPTS = 14;
    private static final int LEAF_PULL_RADIUS = 10;
    private static final int LEAF_TARGET_RADIUS = 4;
    private static final int LEAF_HOLD_TICKS = 200;
    private static final int LEAF_STUCK_MOVE_ATTEMPTS = 4;
    private static final int LEAF_MAX_LIFETIME_TICKS = 20 * 60;
    private static final int MAX_MOVING_LEAVES = 256;
    private static final List<LeafJourney> ACTIVE_LEAF_JOURNEYS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, LeafPullCleanup> ACTIVE_LEAF_PULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LEAF_PULL_READY_TICKS = new ConcurrentHashMap<>();
    private static GlobalConfigManager configManager;
    private static final Block[] FLOWERS = {
            Blocks.DANDELION,
            Blocks.POPPY,
            Blocks.AZURE_BLUET,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER
    };

    private PlantMagic() {
    }

    public static void initialize(GlobalConfigManager manager) {
        configManager = manager;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickLeafJourneys();
            if (server.getTicks() % TICK_INTERVAL != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (isHoldingPlantMagic(player) && player.getWorld() instanceof ServerWorld world) {
                    accelerateNearbyPlants(world, player.getBlockPos());
                }
            }
        });
    }

    public static boolean startLeafPull(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        int stage = getPlayerStage(player);
        PlantMagicConfig config = PlantMagicConfig.getInstance();
        long now = player.getServer() != null ? player.getServer().getTicks() : player.getWorld().getTime();
        long readyTick = LEAF_PULL_READY_TICKS.getOrDefault(playerId, 0L);
        if (now < readyTick) {
            long remainingSeconds = Math.max(1L, (readyTick - now + 19L) / 20L);
            player.sendMessage(Text.literal("\u00a7c[Plant] Cooldown: " + remainingSeconds + "s"), true);
            return false;
        }
        if (hasActiveLeafPull(playerId)) {
            player.sendMessage(Text.literal("\u00a7a[Plant] Leaves are already moving"), true);
            return false;
        }
        LeafPullCleanup staleCleanup = ACTIVE_LEAF_PULLS.remove(playerId);
        if (staleCleanup != null) {
            staleCleanup.clearRemainingTargets();
        }

        ServerWorld world = player.getWorld();
        List<LeafSource> sources = collectLeafSources(world, player.getBlockPos());
        if (sources.isEmpty()) {
            player.sendMessage(Text.literal("\u00a7c[Plant] No nearby leaves"), true);
            return false;
        }

        double coverageDot = config.getShellCoverageDot(stage);
        List<BlockPos> targets = buildLeafTargets(world, player, coverageDot);
        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("\u00a7c[Plant] No open space ahead"), true);
            return false;
        }

        double moveSpeed = config.getLeafMoveSpeed(stage);
        int count = Math.min(MAX_MOVING_LEAVES, Math.min(sources.size(), targets.size()));
        Set<BlockPos> usedTargets = new HashSet<>();
        Set<BlockPos> movedSources = new HashSet<>();
        for (int i = 0; i < count; i++) {
            movedSources.add(sources.get(i).pos());
        }
        Map<BlockPos, BlockState> protectedLeaves = protectStationaryLeaves(world, sources, movedSources);
        for (int i = 0; i < count; i++) {
            LeafSource source = sources.get(i);
            BlockPos target = targets.get(i);
            usedTargets.add(target);
            world.setBlockState(source.pos(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            ACTIVE_LEAF_JOURNEYS.add(new LeafJourney(playerId, world, source.pos(), source.state(), target, moveSpeed));
        }
        ACTIVE_LEAF_PULLS.put(playerId, new LeafPullCleanup(world, usedTargets, protectedLeaves));
        int cooldownTicks = config.getCooldownTicks(stage);
        if (cooldownTicks > 0) {
            LEAF_PULL_READY_TICKS.put(playerId, now + cooldownTicks);
        } else {
            LEAF_PULL_READY_TICKS.remove(playerId);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 1.0F, 0.65F);
        player.sendMessage(Text.literal("\u00a7a[Plant] Moving " + count + " leaf blocks"), true);
        return true;
    }

    private static Map<BlockPos, BlockState> protectStationaryLeaves(ServerWorld world, List<LeafSource> sources, Set<BlockPos> movedSources) {
        Map<BlockPos, BlockState> protectedLeaves = new HashMap<>();
        for (LeafSource source : sources) {
            if (movedSources.contains(source.pos())) {
                continue;
            }
            BlockState currentState = world.getBlockState(source.pos());
            if (!currentState.isIn(BlockTags.LEAVES)) {
                continue;
            }
            BlockState protectedState = asNonDecayingLeaf(currentState);
            if (!protectedState.equals(currentState)) {
                protectedLeaves.put(source.pos(), currentState);
                world.setBlockState(source.pos(), protectedState, Block.NOTIFY_ALL);
            }
        }
        return protectedLeaves;
    }

    private static BlockState asNonDecayingLeaf(BlockState state) {
        return state.contains(Properties.PERSISTENT) ? state.with(Properties.PERSISTENT, true) : state;
    }

    public static int getCooldownTicks(ServerPlayerEntity player) {
        return PlantMagicConfig.getInstance().getCooldownTicks(getPlayerStage(player));
    }

    private static int getPlayerStage(ServerPlayerEntity player) {
        return configManager != null ? Evil_Eyes.getPlayerStage(player, configManager) : 1;
    }

    private static boolean isHoldingPlantMagic(PlayerEntity player) {
        return isPlantMagicStack(player.getMainHandStack()) || isPlantMagicStack(player.getOffHandStack());
    }

    private static boolean isPlantMagicStack(ItemStack stack) {
        return stack.getItem() == PlantMagicItems.PLANT_WAND;
    }

    private static boolean hasActiveLeafPull(UUID owner) {
        for (LeafJourney journey : ACTIVE_LEAF_JOURNEYS) {
            if (journey.owner().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    private static void tickLeafJourneys() {
        for (LeafJourney journey : ACTIVE_LEAF_JOURNEYS) {
            if (journey.tick()) {
                ACTIVE_LEAF_JOURNEYS.remove(journey);
            }
        }
        cleanupFinishedLeafPulls();
    }

    private static void cleanupFinishedLeafPulls() {
        Set<UUID> activeOwners = new HashSet<>();
        for (LeafJourney journey : ACTIVE_LEAF_JOURNEYS) {
            activeOwners.add(journey.owner());
        }

        for (Map.Entry<UUID, LeafPullCleanup> entry : ACTIVE_LEAF_PULLS.entrySet()) {
            if (!activeOwners.contains(entry.getKey())) {
                entry.getValue().clearRemainingTargets();
                ACTIVE_LEAF_PULLS.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static List<LeafSource> collectLeafSources(ServerWorld world, BlockPos center) {
        List<LeafSource> sources = new ArrayList<>();
        int radiusSq = LEAF_PULL_RADIUS * LEAF_PULL_RADIUS;
        for (int x = -LEAF_PULL_RADIUS; x <= LEAF_PULL_RADIUS; x++) {
            for (int y = -LEAF_PULL_RADIUS; y <= LEAF_PULL_RADIUS; y++) {
                for (int z = -LEAF_PULL_RADIUS; z <= LEAF_PULL_RADIUS; z++) {
                    if (x * x + y * y + z * z > radiusSq) {
                        continue;
                    }
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isIn(BlockTags.LEAVES)) {
                        sources.add(new LeafSource(pos.toImmutable(), state));
                    }
                }
            }
        }
        sources.sort(Comparator.comparingDouble(source -> source.pos().getSquaredDistance(center)));
        return sources;
    }

    private static List<BlockPos> buildLeafTargets(ServerWorld world, ServerPlayerEntity player, double coverageDot) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        BlockPos center = BlockPos.ofFloored(eye);
        Set<BlockPos> unique = new HashSet<>();
        List<BlockPos> targets = new ArrayList<>();
        int radius = LEAF_TARGET_RADIUS;
        double radiusSq = radius * radius;
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int y = -radius - 1; y <= radius + 1; y++) {
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!blockIntersectsSphereSurface(pos, eye, radiusSq)) {
                        continue;
                    }
                    Vec3d toPos = pos.toCenterPos().subtract(eye);
                    if (toPos.lengthSquared() <= 0.0001D) {
                        continue;
                    }
                    Vec3d normal = toPos.normalize();
                    if (normal.dotProduct(look) < coverageDot || !world.getBlockState(pos).isReplaceable()) {
                        continue;
                    }
                    if (unique.add(pos.toImmutable())) {
                        targets.add(pos.toImmutable());
                    }
                }
            }
        }
        targets.sort(Comparator
                .comparingDouble((BlockPos pos) -> -pos.toCenterPos().subtract(eye).normalize().dotProduct(look))
                .thenComparingDouble(pos -> Math.abs(pos.toCenterPos().squaredDistanceTo(eye) - radius * radius)));
        return targets;
    }

    private static boolean blockIntersectsSphereSurface(BlockPos pos, Vec3d center, double radiusSq) {
        double minSq = squaredDistanceToBox(center.x, pos.getX(), pos.getX() + 1.0D)
                + squaredDistanceToBox(center.y, pos.getY(), pos.getY() + 1.0D)
                + squaredDistanceToBox(center.z, pos.getZ(), pos.getZ() + 1.0D);
        if (minSq > radiusSq) {
            return false;
        }

        double maxSq = maxSquaredDistanceToBoxAxis(center.x, pos.getX(), pos.getX() + 1.0D)
                + maxSquaredDistanceToBoxAxis(center.y, pos.getY(), pos.getY() + 1.0D)
                + maxSquaredDistanceToBoxAxis(center.z, pos.getZ(), pos.getZ() + 1.0D);
        return maxSq >= radiusSq;
    }

    private static double squaredDistanceToBox(double value, double min, double max) {
        if (value < min) {
            double distance = min - value;
            return distance * distance;
        }
        if (value > max) {
            double distance = value - max;
            return distance * distance;
        }
        return 0.0D;
    }

    private static double maxSquaredDistanceToBoxAxis(double value, double min, double max) {
        double toMin = value - min;
        double toMax = value - max;
        return Math.max(toMin * toMin, toMax * toMax);
    }

    private static void accelerateNearbyPlants(ServerWorld world, BlockPos center) {
        Random random = world.getRandom();
        for (int i = 0; i < RANDOM_TICK_ATTEMPTS; i++) {
            BlockPos pos = center.add(
                    random.nextBetween(-RADIUS, RADIUS),
                    random.nextBetween(-RADIUS, RADIUS),
                    random.nextBetween(-RADIUS, RADIUS)
            );
            BlockState state = world.getBlockState(pos);
            if (isCropLike(state)) {
                state.randomTick(world, pos, random);
                if (random.nextInt(3) == 0) {
                    state.randomTick(world, pos, random);
                }
            } else if (state.isOf(Blocks.GRASS_BLOCK)) {
                state.randomTick(world, pos, random);
                maybeGrowGrassOrFlower(world, pos.up(), random);
            }
        }
    }

    private static boolean isCropLike(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.isOf(Blocks.NETHER_WART) || state.isOf(Blocks.COCOA);
    }

    private static void maybeGrowGrassOrFlower(ServerWorld world, BlockPos pos, Random random) {
        if (random.nextInt(5) != 0 || !world.getBlockState(pos).isReplaceable()) {
            return;
        }

        BlockState state = random.nextInt(4) == 0
                ? FLOWERS[random.nextInt(FLOWERS.length)].getDefaultState()
                : Blocks.SHORT_GRASS.getDefaultState();
        if (state.canPlaceAt(world, pos)) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
    }

    private record LeafSource(BlockPos pos, BlockState state) {
    }

    private record LeafPullCleanup(ServerWorld world, Set<BlockPos> targets, Map<BlockPos, BlockState> protectedLeaves) {
        private void clearRemainingTargets() {
            for (BlockPos target : targets) {
                if (world.getBlockState(target).isIn(BlockTags.LEAVES)) {
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            for (Map.Entry<BlockPos, BlockState> entry : protectedLeaves.entrySet()) {
                BlockState currentState = world.getBlockState(entry.getKey());
                if (currentState.isIn(BlockTags.LEAVES)) {
                    world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static final class LeafJourney {
        private final UUID owner;
        private final ServerWorld world;
        private final BlockState state;
        private final BlockState movingState;
        private final BlockPos origin;
        private final BlockPos target;
        private final double moveSpeed;
        private final int maxLifetimeTicks;
        private int holdTicks;
        private int ageTicks;
        private GravityBlockEntity animationEntity;
        private Phase phase = Phase.INBOUND;

        private LeafJourney(UUID owner, ServerWorld world, BlockPos origin, BlockState state, BlockPos target, double moveSpeed) {
            this.owner = owner;
            this.world = world;
            this.state = state;
            this.movingState = asNonDecayingLeaf(state);
            this.origin = origin.toImmutable();
            this.target = target.toImmutable();
            this.moveSpeed = Math.max(0.05D, moveSpeed);
            this.maxLifetimeTicks = Math.max(
                    LEAF_MAX_LIFETIME_TICKS,
                    travelTicks(this.origin, this.target) * 2 + LEAF_HOLD_TICKS + 100
            );
            startTravel(this.origin, this.target);
        }

        private UUID owner() {
            return owner;
        }

        private boolean tick() {
            ageTicks++;
            if (ageTicks > maxLifetimeTicks) {
                return abortAndRestore();
            }

            if (phase == Phase.INBOUND) {
                if (isTravelComplete()) {
                    discardAnimation();
                    if (!placeTarget()) {
                        return abortAndRestore();
                    }
                    phase = Phase.HOLD;
                    holdTicks = 0;
                }
                return false;
            }

            if (phase == Phase.HOLD) {
                holdTicks++;
                if (holdTicks >= LEAF_HOLD_TICKS) {
                    clearTarget();
                    phase = Phase.OUTBOUND;
                    startTravel(target, origin);
                }
                return false;
            }

            if (isTravelComplete()) {
                discardAnimation();
                restoreOrigin();
                return true;
            }
            return false;
        }

        private boolean abortAndRestore() {
            discardAnimation();
            clearTarget();
            restoreOrigin();
            return true;
        }

        private boolean placeTarget() {
            BlockState currentState = world.getBlockState(target);
            if (!currentState.isReplaceable() && !currentState.isIn(BlockTags.LEAVES)) {
                return false;
            }
            world.setBlockState(target, movingState, Block.NOTIFY_ALL);
            return true;
        }

        private void clearTarget() {
            if (!target.equals(origin) && world.getBlockState(target).isIn(BlockTags.LEAVES)) {
                world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        private void restoreOrigin() {
            BlockState originState = world.getBlockState(origin);
            if (originState.isReplaceable() || originState.isIn(BlockTags.LEAVES)) {
                world.setBlockState(origin, state, Block.NOTIFY_ALL);
            }
        }

        private void startTravel(BlockPos from, BlockPos to) {
            discardAnimation();
            Vec3d start = renderPosition(from);
            Vec3d end = renderPosition(to);
            int ticks = travelTicks(from, to);
            GravityBlockEntity entity = new GravityBlockEntity(world, start.x, start.y, start.z, movingState, Vec3d.ZERO, 0.0D);
            entity.setOwnerUuid(owner);
            entity.setGravityY(0.0D);
            entity.setNoGravity(true);
            entity.setTemporary(ticks + 40);
            entity.setPlaceOrDropOnSettle(false);
            entity.setLinearExtractionTarget(end, ticks);
            entity.setSlowFreeSpin(
                    signedAngularVelocity(world, 0.14D, 0.42D),
                    signedAngularVelocity(world, 0.16D, 0.48D),
                    signedAngularVelocity(world, 0.12D, 0.36D)
            );
            if (world.spawnEntity(entity)) {
                animationEntity = entity;
            }
        }

        private boolean isTravelComplete() {
            return animationEntity == null || !animationEntity.isAlive() || !animationEntity.isExtracting();
        }

        private void discardAnimation() {
            if (animationEntity != null && animationEntity.isAlive()) {
                animationEntity.discard();
            }
            animationEntity = null;
        }

        private int travelTicks(BlockPos from, BlockPos to) {
            double distance = renderPosition(from).distanceTo(renderPosition(to));
            return Math.max(1, (int) Math.ceil(distance / moveSpeed * 20.0D));
        }

        private static Vec3d renderPosition(BlockPos pos) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        }

        private static float signedAngularVelocity(ServerWorld world, double min, double max) {
            Random random = world.getRandom();
            double speed = min + random.nextDouble() * Math.max(0.0D, max - min);
            return (float) (random.nextBoolean() ? speed : -speed);
        }
    }

    private enum Phase {
        INBOUND,
        HOLD,
        OUTBOUND
    }
}
