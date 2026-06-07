package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityMagic {
    public static final double DEFAULT_GRAVITY = 0.04D;
    public static final double MIN_GRAVITY = 0.0D;
    public static final double MAX_GRAVITY = 0.30D;

    private static final int AREA_RADIUS = 10;
    private static final int ISLAND_VERTICAL_RADIUS = 8;
    private static final double UP_SPEED = 0.72D;
    private static final double RIGHT_SPEED = 0.82D;
    private static final int AREA_DURATION_TICKS = 320;
    private static final double INVERTED_WALK_ACCELERATION = 0.10D;
    private static final double INVERTED_SPRINT_ACCELERATION = 0.13D;
    private static final double INVERTED_SNEAK_ACCELERATION = 0.03D;
    private static final double INVERTED_AIR_ACCELERATION = 0.02D;
    private static final double INVERTED_GRAVITY = 0.08D;
    private static final double INVERTED_Y_DRAG = 0.98D;
    private static final double INVERTED_AIR_XZ_DRAG = 0.91D;
    private static final double INVERTED_GROUND_XZ_DRAG = 0.546D;
    private static final double INVERTED_MAX_FALL_UP_SPEED = 3.92D;
    private static final double INVERTED_JUMP_SPEED = -0.42D;
    private static final double INVERTED_CEILING_EPSILON = 0.035D;
    private static final double INVERTED_CEILING_PROBE = 0.09D;
    private static final double INVERTED_ATTACH_SNAP = 0.18D;
    private static final int INVERTED_JUMP_DETACH_TICKS = 8;
    private static final Map<UUID, LaunchMode> PLAYER_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PLAYER_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> ENTITY_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> SERVER_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> CLIENT_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final List<AreaGravityField> AREA_FIELDS = new CopyOnWriteArrayList<>();

    public enum LaunchMode {
        UP,
        RIGHT
    }

    private GravityMagic() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.AdjustGravityC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                double gravity = adjustTargetGravity(player, packet.entityId(), packet.gravity());
                player.sendMessage(Text.literal("\u00a7b[Gravity] g=" + format(gravity)), true);
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                tickAreaGravity(world);
                tickInvertedServerPlayerStates(world);
                applyEntityGravity(world);
            }
        });
    }

    public static LaunchMode toggleMode(ServerPlayerEntity player) {
        LaunchMode next = getMode(player) == LaunchMode.UP ? LaunchMode.RIGHT : LaunchMode.UP;
        PLAYER_MODES.put(player.getUuid(), next);
        player.sendMessage(Text.literal("\u00a7b[Gravity] Mode: " + displayName(next)), true);
        return next;
    }

    public static LaunchMode getMode(ServerPlayerEntity player) {
        return PLAYER_MODES.getOrDefault(player.getUuid(), LaunchMode.UP);
    }

    public static double getSelectedGravity(ServerPlayerEntity player) {
        return PLAYER_GRAVITY.getOrDefault(player.getUuid(), DEFAULT_GRAVITY);
    }

    public static double setSelectedGravity(ServerPlayerEntity player, double gravity) {
        double clamped = clampGravity(gravity);
        PLAYER_GRAVITY.put(player.getUuid(), clamped);
        return clamped;
    }

    public static double adjustTargetGravity(ServerPlayerEntity player, int entityId, double gravity) {
        double clamped = clampGravity(gravity);
        if (entityId >= 0) {
            Entity entity = player.getWorld().getEntityById(entityId);
            if (entity instanceof GravityBlockEntity gravityBlock) {
                gravityBlock.setGravityAmount(clamped);
            } else if (entity instanceof ServerPlayerEntity) {
                setSelectedGravity(player, clamped);
            } else if (entity != null) {
                ENTITY_GRAVITY.put(entity.getUuid(), clamped);
                entity.setNoGravity(true);
            } else {
                setSelectedGravity(player, clamped);
            }
        } else {
            setSelectedGravity(player, clamped);
        }
        return clamped;
    }

    public static int launch(ServerWorld world, ServerPlayerEntity player, BlockPos origin, boolean group) {
        if (group) {
            return activateAreaGravity(world, player, origin);
        }

        LaunchMode mode = getMode(player);
        Vec3d velocity = velocityFor(player, mode);
        double gravity = getSelectedGravity(player);
        int launched = 0;

        if (launchOne(world, origin, velocity, -gravity, 0.0D)) {
            launched = 1;
        }

        if (launched > 0) {
            player.sendMessage(Text.literal("\u00a7b[Gravity] Launched " + launched + " block(s) "
                    + displayName(mode) + " g=" + format(gravity)), true);
        } else {
            player.sendMessage(Text.literal("\u00a7c[Gravity] No movable blocks"), true);
        }
        return launched;
    }

    public static int activateAreaGravity(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        double gravity = getSelectedGravity(player);
        AreaGravityField field = new AreaGravityField(world.getRegistryKey(), origin.toImmutable(), AREA_RADIUS, AREA_DURATION_TICKS, gravity, false);
        AREA_FIELDS.add(field);
        broadcastAreaGravity(world, field);
        player.sendMessage(Text.literal("\u00a7b[Gravity] Inverted walk area r=" + AREA_RADIUS
                + " ticks=" + AREA_DURATION_TICKS), true);
        return 1;
    }

    private static boolean launchOne(ServerWorld world, BlockPos pos, Vec3d velocity, double gravityY, double riseLimit) {
        BlockState state = world.getBlockState(pos);
        if (!canMove(world, pos, state)) {
            return false;
        }

        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        GravityBlockEntity entity = new GravityBlockEntity(
                world,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                state,
                velocity,
                Math.abs(gravityY)
        );
        entity.setGravityY(gravityY);
        if (riseLimit > 0.0D) {
            entity.setRiseLimit(riseLimit);
        }
        world.spawnEntity(entity);
        return true;
    }

    private static boolean canMove(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || isGravityImmune(state)) {
            return false;
        }
        if (state.getHardness(world, pos) < 0) {
            return false;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity == null;
    }

    private static boolean isGravityImmune(BlockState state) {
        return state.isOf(Blocks.BEDROCK)
                || state.isOf(Blocks.OBSIDIAN)
                || state.isOf(Blocks.REINFORCED_DEEPSLATE)
                || state.isOf(Blocks.BARRIER);
    }

    private static Vec3d velocityFor(ServerPlayerEntity player, LaunchMode mode) {
        if (mode == LaunchMode.UP) {
            return new Vec3d(0.0D, UP_SPEED, 0.0D);
        }

        double yawRad = Math.toRadians(player.getYaw() + 90.0F);
        return new Vec3d(Math.cos(yawRad) * RIGHT_SPEED, 0.08D, Math.sin(yawRad) * RIGHT_SPEED);
    }

    private static void applyEntityGravity(ServerWorld world) {
        ENTITY_GRAVITY.entrySet().removeIf(entry -> {
            Entity entity = world.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                return true;
            }
            if (entity instanceof GravityBlockEntity) {
                return false;
            }
            entity.setNoGravity(true);
            entity.setVelocity(entity.getVelocity().add(0.0D, -entry.getValue(), 0.0D));
            return false;
        });
    }

    private static void tickAreaGravity(ServerWorld world) {
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced || !field.world.equals(world.getRegistryKey())) {
                continue;
            }
            field.ticks--;
            if (field.ticks <= 0) {
                AREA_FIELDS.remove(field);
            }
        }
    }

    private static void tickInvertedServerPlayerStates(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isInInvertedArea(player)) {
                SERVER_INVERTED_PLAYER_STATES.computeIfAbsent(player.getUuid(), uuid -> new InvertedPlayerState());
                player.setNoGravity(true);
                player.setSneaking(false);
                if (player.isInPose(EntityPose.CROUCHING)) {
                    player.setPose(EntityPose.STANDING);
                }
                player.fallDistance = 0.0D;
            } else if (SERVER_INVERTED_PLAYER_STATES.remove(player.getUuid()) != null) {
                player.setNoGravity(false);
            }
        }
    }

    public static boolean cancelServerInvertedTravel(Entity entity) {
        if (!isInInvertedArea(entity)) {
            return false;
        }
        entity.setNoGravity(true);
        entity.fallDistance = 0.0D;
        return true;
    }

    public static boolean tickInvertedPlayer(Entity entity, PlayerInput input) {
        Map<UUID, InvertedPlayerState> stateMap = invertedPlayerStates(entity);
        if (entity == null || input == null || getInvertedAreaGravity(entity) <= 0.0D) {
            InvertedPlayerState removed = entity == null ? null : stateMap.remove(entity.getUuid());
            if (removed != null && entity != null) {
                entity.setNoGravity(false);
            }
            return false;
        }

        InvertedPlayerState state = stateMap.computeIfAbsent(entity.getUuid(), uuid -> new InvertedPlayerState());
        entity.setNoGravity(true);
        entity.setSneaking(false);
        if (entity.isInPose(EntityPose.CROUCHING)) {
            entity.setPose(EntityPose.STANDING);
        }
        entity.fallDistance = 0.0D;

        if (state.detachTicks > 0) {
            state.detachTicks--;
        }

        Vec3d velocity = entity.getVelocity();
        CeilingSupport support = state.detachTicks <= 0 && velocity.y >= -0.02D ? findCeilingSupport(entity) : null;

        if (support != null) {
            double targetY = support.bottomY - standingHeight(entity) - INVERTED_CEILING_EPSILON;
            double delta = targetY - entity.getY();
            if (Math.abs(delta) > INVERTED_ATTACH_SNAP && state.attached) {
                entity.setPosition(entity.getX(), targetY, entity.getZ());
                delta = 0.0D;
            }

            if (input.jump()) {
                state.detachTicks = INVERTED_JUMP_DETACH_TICKS;
                state.attached = false;
                Vec3d acceleration = inputAcceleration(entity, input, INVERTED_AIR_ACCELERATION);
                Vec3d moveVelocity = new Vec3d(velocity.x + acceleration.x, INVERTED_JUMP_SPEED, velocity.z + acceleration.z);
                entity.move(MovementType.SELF, moveVelocity);
                velocity = new Vec3d(moveVelocity.x * INVERTED_AIR_XZ_DRAG, moveVelocity.y * INVERTED_Y_DRAG, moveVelocity.z * INVERTED_AIR_XZ_DRAG);
                entity.setOnGround(false);
            } else {
                state.attached = true;
                Vec3d acceleration = inputAcceleration(entity, input, groundAcceleration(input));
                Vec3d moveVelocity = new Vec3d(
                        velocity.x + acceleration.x,
                        Math.clamp(delta * 0.45D, -0.08D, 0.08D),
                        velocity.z + acceleration.z
                );
                entity.move(MovementType.SELF, moveVelocity);
                velocity = new Vec3d(moveVelocity.x * INVERTED_GROUND_XZ_DRAG, 0.0D, moveVelocity.z * INVERTED_GROUND_XZ_DRAG);
                entity.setOnGround(true);
            }
        } else {
            state.attached = false;
            Vec3d acceleration = inputAcceleration(entity, input, INVERTED_AIR_ACCELERATION);
            Vec3d moveVelocity = new Vec3d(
                    velocity.x + acceleration.x,
                    Math.min(velocity.y + INVERTED_GRAVITY, INVERTED_MAX_FALL_UP_SPEED),
                    velocity.z + acceleration.z
            );
            entity.move(MovementType.SELF, moveVelocity);
            velocity = new Vec3d(moveVelocity.x * INVERTED_AIR_XZ_DRAG, moveVelocity.y * INVERTED_Y_DRAG, moveVelocity.z * INVERTED_AIR_XZ_DRAG);
            entity.setOnGround(false);
        }

        entity.setVelocity(velocity);
        return true;
    }

    private static double groundAcceleration(PlayerInput input) {
        if (input.sneak()) {
            return INVERTED_SNEAK_ACCELERATION;
        }
        if (input.sprint()) {
            return INVERTED_SPRINT_ACCELERATION;
        }
        return INVERTED_WALK_ACCELERATION;
    }

    private static Vec3d inputAcceleration(Entity entity, PlayerInput input, double acceleration) {
        double forwardAmount = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        double sideAmount = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        if (forwardAmount == 0.0D && sideAmount == 0.0D) {
            return Vec3d.ZERO;
        }

        double yaw = Math.toRadians(entity.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3d movement = forward.multiply(forwardAmount).add(right.multiply(sideAmount));
        if (movement.lengthSquared() > 1.0D) {
            movement = movement.normalize();
        }
        return movement.multiply(acceleration);
    }

    private static CeilingSupport findCeilingSupport(Entity entity) {
        Box box = entity.getBoundingBox();
        int blockY = (int) Math.floor(box.maxY + INVERTED_CEILING_PROBE);
        double[][] samples = new double[][] {
                {entity.getX(), entity.getZ()},
                {box.minX + 0.05D, box.minZ + 0.05D},
                {box.minX + 0.05D, box.maxZ - 0.05D},
                {box.maxX - 0.05D, box.minZ + 0.05D},
                {box.maxX - 0.05D, box.maxZ - 0.05D}
        };

        for (double[] sample : samples) {
            BlockPos pos = BlockPos.ofFloored(sample[0], blockY, sample[1]);
            BlockState state = entity.getWorld().getBlockState(pos);
            if (!state.isAir() && state.isSideSolidFullSquare(entity.getWorld(), pos, Direction.DOWN)) {
                return new CeilingSupport(pos.getY());
            }
        }
        return null;
    }

    private static double standingHeight(Entity entity) {
        return entity.getDimensions(EntityPose.STANDING).height();
    }

    public static boolean isInInvertedArea(Entity entity) {
        return getInvertedAreaGravity(entity) > 0.0D;
    }

    public static boolean isInInvertedArea(RegistryKey<World> world, Vec3d pos) {
        return getInvertedAreaGravity(world, pos) > 0.0D;
    }

    public static boolean isInvertedWalking(Entity entity) {
        InvertedPlayerState state = entity == null ? null : invertedPlayerStates(entity).get(entity.getUuid());
        return state != null && state.attached;
    }

    private static Map<UUID, InvertedPlayerState> invertedPlayerStates(Entity entity) {
        return entity != null && entity.getWorld().isClient() ? CLIENT_INVERTED_PLAYER_STATES : SERVER_INVERTED_PLAYER_STATES;
    }

    private static void broadcastAreaGravity(ServerWorld world, AreaGravityField field) {
        GravityPackets.AreaGravityS2C packet = new GravityPackets.AreaGravityS2C(field.center, field.radius, field.ticks, field.gravity);
        double trackingDistanceSq = (field.radius + 96.0D) * (field.radius + 96.0D);
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.squaredDistanceTo(Vec3d.ofCenter(field.center)) <= trackingDistanceSq) {
                ServerPlayNetworking.send(target, packet);
            }
        }
    }

    public static boolean shouldSuppressVanillaGravity(Entity entity) {
        return ENTITY_GRAVITY.containsKey(entity.getUuid()) || isInInvertedArea(entity);
    }

    public static void addClientSyncedAreaGravity(RegistryKey<World> world, BlockPos center, int radius, int ticks, double gravity) {
        AREA_FIELDS.add(new AreaGravityField(world, center.toImmutable(), radius, ticks, gravity, true));
    }

    public static void tickClientSyncedAreaGravity() {
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced) {
                continue;
            }
            field.ticks--;
            if (field.ticks <= 0) {
                AREA_FIELDS.remove(field);
            }
        }
    }

    public static double getInvertedAreaGravity(Entity entity) {
        if (entity == null || entity.getWorld() == null) {
            return 0.0D;
        }

        RegistryKey<World> worldKey = entity.getWorld().getRegistryKey();
        return getInvertedAreaGravity(worldKey, entity.getPos());
    }

    public static double getInvertedAreaGravity(RegistryKey<World> worldKey, Vec3d pos) {
        double gravity = 0.0D;
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.world.equals(worldKey) && field.contains(pos)) {
                gravity = Math.max(gravity, field.gravity);
            }
        }
        return gravity;
    }

    public static double clampGravity(double gravity) {
        return Math.clamp(gravity, MIN_GRAVITY, MAX_GRAVITY);
    }

    public static String format(double gravity) {
        return String.format("%.3f", gravity);
    }

    private static String displayName(LaunchMode mode) {
        return mode == LaunchMode.UP ? "UP" : "RIGHT";
    }

    private static int invertIslandArea(ServerWorld world, BlockPos origin) {
        int changed = 0;
        int minY = origin.getY() - ISLAND_VERTICAL_RADIUS;
        int maxY = origin.getY() + ISLAND_VERTICAL_RADIUS;
        int flags = Block.NOTIFY_ALL | Block.FORCE_STATE;

        for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
            for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++) {
                if (dx * dx + dz * dz > AREA_RADIUS * AREA_RADIUS) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    int mirrorY = minY + maxY - y;
                    if (y > mirrorY) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    BlockPos mirrorPos = new BlockPos(origin.getX() + dx, mirrorY, origin.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    BlockState mirrorState = world.getBlockState(mirrorPos);
                    if (!canMirror(world, pos, state) || !canMirror(world, mirrorPos, mirrorState)) {
                        continue;
                    }

                    BlockState newState = flipVertical(mirrorState);
                    BlockState newMirrorState = flipVertical(state);
                    if (state == newState && mirrorState == newMirrorState) {
                        continue;
                    }

                    world.setBlockState(pos, newState, flags);
                    if (!pos.equals(mirrorPos)) {
                        world.setBlockState(mirrorPos, newMirrorState, flags);
                    }
                    changed += pos.equals(mirrorPos) ? 1 : 2;
                }
            }
        }
        return changed;
    }

    private static boolean canMirror(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (isGravityImmune(state) || !state.getFluidState().isEmpty() || state.getHardness(world, pos) < 0) {
            return false;
        }
        return world.getBlockEntity(pos) == null;
    }

    private static BlockState flipVertical(BlockState state) {
        if (state.isAir()) {
            return state;
        }
        if (state.contains(Properties.FACING)) {
            Direction facing = state.get(Properties.FACING);
            if (facing.getAxis() == Direction.Axis.Y) {
                state = state.with(Properties.FACING, facing.getOpposite());
            }
        }
        if (state.contains(Properties.VERTICAL_DIRECTION)) {
            state = state.with(Properties.VERTICAL_DIRECTION, state.get(Properties.VERTICAL_DIRECTION).getOpposite());
        }
        if (state.contains(Properties.BLOCK_HALF)) {
            state = state.with(Properties.BLOCK_HALF, state.get(Properties.BLOCK_HALF) == BlockHalf.TOP ? BlockHalf.BOTTOM : BlockHalf.TOP);
        }
        if (state.contains(Properties.SLAB_TYPE)) {
            SlabType slab = state.get(Properties.SLAB_TYPE);
            if (slab == SlabType.TOP) {
                state = state.with(Properties.SLAB_TYPE, SlabType.BOTTOM);
            } else if (slab == SlabType.BOTTOM) {
                state = state.with(Properties.SLAB_TYPE, SlabType.TOP);
            }
        }
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            state = state.with(Properties.DOUBLE_BLOCK_HALF,
                    state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        }
        if (state.contains(Properties.UP) && state.contains(Properties.DOWN)) {
            boolean up = state.get(Properties.UP);
            boolean down = state.get(Properties.DOWN);
            state = state.with(Properties.UP, down).with(Properties.DOWN, up);
        }
        return state;
    }

    private static final class AreaGravityField {
        private final RegistryKey<World> world;
        private final BlockPos center;
        private final int radius;
        private final double radiusSq;
        private final double gravity;
        private final boolean clientSynced;
        private int ticks;

        private AreaGravityField(RegistryKey<World> world, BlockPos center, int radius, int ticks, double gravity, boolean clientSynced) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.radiusSq = radius * radius;
            this.ticks = ticks;
            this.gravity = gravity;
            this.clientSynced = clientSynced;
        }

        private boolean contains(Vec3d pos) {
            return Vec3d.ofCenter(this.center).squaredDistanceTo(pos) <= this.radiusSq;
        }
    }

    private static final class InvertedPlayerState {
        private boolean attached;
        private int detachTicks;
    }

    private record CeilingSupport(double bottomY) {
    }

}
