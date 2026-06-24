package com.kuilunfuzhe.monvhua.features.gravity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.item.config.GravityConfig;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityMagic {
    public static final double DEFAULT_GRAVITY = 0.04D;
    public static final double MIN_GRAVITY = 0.0D;
    public static final double MAX_GRAVITY = 0.30D;
    public static final int INFINITE_AREA_TICKS = -1;

    public static final int DEFAULT_AREA_HEIGHT = 4;

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
    private static final double INVERTED_STEP_HEIGHT = 0.6D;
    private static final int INVERTED_JUMP_DETACH_TICKS = 8;
    private static final double LIGHTEN_ENTITY_REACH = 32.0D;
    private static final double NORMAL_WORLD_GRAVITY = 0.08D;
    private static final double DIRECTED_PLAYER_GROUND_ACCELERATION = 0.10D;
    private static final double DIRECTED_PLAYER_AIR_ACCELERATION = 0.026D;
    private static final double DIRECTED_PLAYER_VERTICAL_DRAG = 0.98D;
    private static final double DIRECTED_PLAYER_AIR_XZ_DRAG = 0.94D;
    private static final double DIRECTED_PLAYER_BASE_JUMP_SPEED = 0.42D;
    private static final double DIRECTED_PLAYER_JUMP_FORCE_SCALE = 4.5D;
    private static final double DIRECTED_PLAYER_MAX_UP_SPEED = 2.10D;
    private static final double DIRECTED_PLAYER_MAX_DOWN_SPEED = -3.20D;
    private static final double DIRECTED_PLAYER_MAX_HORIZONTAL_SPEED = 1.45D;
    private static final double DIRECTED_GRASS_STOP_DISTANCE = 10.0D;
    private static final double DIRECTED_STONE_STOP_DISTANCE = 6.0D;
    private static final double DIRECTED_DEFAULT_STOP_DISTANCE = 8.0D;
    private static final Map<UUID, LaunchMode> PLAYER_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PLAYER_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, DirectedEntityGravity> ENTITY_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> SERVER_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> CLIENT_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final List<AreaGravityField> AREA_FIELDS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, java.util.ArrayDeque<AreaEdit>> DEBUG_UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.ArrayDeque<AreaEdit>> DEBUG_REDO = new ConcurrentHashMap<>();
    private static boolean serverAreasLoaded = false;

    public enum LaunchMode {
        UP,
        RIGHT
    }

    public record AreaGravityView(UUID id, BlockPos center, GravityAreaSpec spec, int ticks) {
    }

    private record AreaSnapshot(UUID id, RegistryKey<World> world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity) {
    }

    private record AreaEdit(List<AreaSnapshot> added, List<AreaSnapshot> removed) {
    }

    private record DirectedForce(Vec3d direction, double acceleration, int ticks) {
        private DirectedForce {
            direction = normalizedDirection(direction);
            acceleration = clampGravity(acceleration);
            ticks = Math.max(0, ticks);
        }

        private DirectedForce ticked() {
            return new DirectedForce(direction, acceleration, ticks - 1);
        }

        private Vec3d vector() {
            return direction.multiply(acceleration);
        }
    }

    private record DirectedEntityGravity(RegistryKey<World> world, List<DirectedForce> forces, boolean previousNoGravity) {
        private DirectedEntityGravity {
            forces = forces == null ? List.of() : List.copyOf(forces);
        }

        private DirectedEntityGravity withAdded(DirectedForce force) {
            List<DirectedForce> nextForces = new ArrayList<>(forces.size() + 1);
            for (DirectedForce existing : forces) {
                if (existing.ticks() > 0) {
                    nextForces.add(existing);
                }
            }
            if (force.ticks() > 0) {
                nextForces.add(force);
            }
            return new DirectedEntityGravity(world, nextForces, previousNoGravity);
        }

        private DirectedEntityGravity ticked() {
            List<DirectedForce> nextForces = new ArrayList<>(forces.size());
            for (DirectedForce force : forces) {
                DirectedForce ticked = force.ticked();
                if (ticked.ticks() > 0) {
                    nextForces.add(ticked);
                }
            }
            return new DirectedEntityGravity(world, nextForces, previousNoGravity);
        }

        private boolean expired() {
            return forces.isEmpty();
        }

        private Vec3d directedForce() {
            Vec3d total = Vec3d.ZERO;
            for (DirectedForce force : forces) {
                total = total.add(force.vector());
            }
            return total;
        }
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
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.DebugAreaActionC2S.ID, (packet, context) -> {
            context.server().execute(() -> handleDebugAreaAction(context.player(), packet));
        });
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.RequestConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> syncConfigTo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.UpdateConfigC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (!player.hasPermissionLevel(2) && !player.isCreative()) {
                    player.sendMessage(Text.literal("\u00a7cNo permission to update gravity config"), true);
                    return;
                }
                GravityConfig config = GravityConfig.fromJson(packet.json());
                GravityConfig.setInstance(config);
                for (ServerPlayerEntity target : context.server().getPlayerManager().getPlayerList()) {
                    syncConfigTo(target);
                }
                player.sendMessage(Text.literal("\u00a7aGravity config updated"), true);
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ensureServerAreasLoaded(server);
            for (ServerWorld world : server.getWorlds()) {
                tickAreaGravity(world);
                tickInvertedServerPlayerStates(world);
                applyEntityGravity(world);
            }
        });

        UseBlockCallback.EVENT.register(GravityMagic::useInvertedBlockSurface);
        UseEntityCallback.EVENT.register(GravityMagic::useGravityWandOnEntity);
    }

    public static void syncConfigTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new GravityPackets.ConfigS2C(GravityConfig.getInstance().toJson()));
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
            } else {
                setSelectedGravity(player, clamped);
            }
        } else {
            setSelectedGravity(player, clamped);
        }
        return clamped;
    }

    public static boolean lightenLookedAtEntity(ServerPlayerEntity player) {
        if (player.isSneaking()) {
            return lightenEntity(player, player, player.getRotationVec(1.0F));
        }
        Entity target = findLookedAtEntity(player, LIGHTEN_ENTITY_REACH);
        return target != null && lightenEntity(player, target);
    }

    public static boolean lightenEntity(ServerPlayerEntity player, Entity entity) {
        return lightenEntity(player, entity, player.getRotationVec(1.0F));
    }

    public static boolean lightenEntity(ServerPlayerEntity player, Entity entity, Vec3d direction) {
        if (player == null || entity == null || !entity.isAlive()) {
            return false;
        }

        double gravity = getSelectedGravity(player);
        Vec3d normalizedDirection = normalizedDirection(direction);
        Vec3d netForce = previewComposedForce(entity, normalizedDirection, gravity);
        int ticks = GravityConfig.getInstance().getForceDurationTicks();
        setDirectedEntityGravity(entity, normalizedDirection, gravity, ticks);
        player.sendMessage(Text.literal("\u00a7b[Gravity] Force -> " + entity.getName().getString()
                + " F=" + format(gravity)
                + " net=" + format(netForce.length())
                + " ticks=" + ticks), true);
        return true;
    }

    public static Vec3d appliedForce(Vec3d direction, double acceleration) {
        return normalizedDirection(direction).multiply(clampGravity(acceleration));
    }

    public static Vec3d previewComposedForce(Entity target, Vec3d direction, double acceleration) {
        if (target == null) {
            return appliedForce(direction, acceleration);
        }
        DirectedEntityGravity directed = ENTITY_GRAVITY.get(target.getUuid());
        if (directed != null && !directed.world().equals(target.getWorld().getRegistryKey())) {
            directed = null;
        }
        Vec3d existing = directed == null ? Vec3d.ZERO : directed.directedForce();
        boolean previousNoGravity = directed == null ? target.hasNoGravity() : directed.previousNoGravity();
        return existing.add(appliedForce(direction, acceleration)).add(worldGravityForce(target, previousNoGravity));
    }

    public static double previewComposedForceMagnitude(Entity target, Vec3d direction, double acceleration) {
        return previewComposedForce(target, direction, acceleration).length();
    }

    private static void setDirectedEntityGravity(Entity entity, Vec3d direction, double acceleration, int ticks) {
        DirectedForce force = new DirectedForce(direction, acceleration, ticks);
        DirectedEntityGravity previous = ENTITY_GRAVITY.get(entity.getUuid());
        boolean previousNoGravity = previous == null ? entity.hasNoGravity() : previous.previousNoGravity();
        DirectedEntityGravity next = previous != null && previous.world().equals(entity.getWorld().getRegistryKey())
                ? previous.withAdded(force)
                : new DirectedEntityGravity(entity.getWorld().getRegistryKey(), List.of(force), previousNoGravity);
        ENTITY_GRAVITY.put(entity.getUuid(), next);
        entity.setNoGravity(true);
        if (entity instanceof GravityBlockEntity gravityBlock) {
            gravityBlock.setGravityY(0.0D);
        }
        syncDirectedEntityGravity(entity, force.direction(), force.acceleration(), force.ticks());
    }

    public static void setClientDirectedEntityGravity(Entity entity, Vec3d direction, double acceleration, int ticks) {
        if (entity == null || entity.getWorld() == null) {
            return;
        }
        if (ticks <= 0) {
            DirectedEntityGravity removed = ENTITY_GRAVITY.remove(entity.getUuid());
            if (removed != null) {
                finishDirectedEntityGravity(entity, removed);
            }
            return;
        }
        DirectedEntityGravity previous = ENTITY_GRAVITY.get(entity.getUuid());
        boolean previousNoGravity = previous == null ? entity.hasNoGravity() : previous.previousNoGravity();
        DirectedForce force = new DirectedForce(direction, acceleration, ticks);
        DirectedEntityGravity next = previous != null && previous.world().equals(entity.getWorld().getRegistryKey())
                ? previous.withAdded(force)
                : new DirectedEntityGravity(entity.getWorld().getRegistryKey(), List.of(force), previousNoGravity);
        ENTITY_GRAVITY.put(entity.getUuid(), next);
        entity.setNoGravity(true);
    }

    private static void syncDirectedEntityGravity(Entity entity, Vec3d direction, double acceleration, int ticks) {
        if (entity instanceof ServerPlayerEntity player) {
            ServerPlayNetworking.send(player, new GravityPackets.EntityGravityS2C(
                    entity.getId(), ticks, acceleration, direction.x, direction.y, direction.z));
        }
    }

    private static void syncClearDirectedEntityGravity(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            ServerPlayNetworking.send(player, new GravityPackets.EntityGravityS2C(
                    entity.getId(), 0, 0.0D, 0.0D, 0.0D, 0.0D));
        }
    }

    private static Vec3d normalizedDirection(Vec3d direction) {
        return direction == null || direction.lengthSquared() < 1.0E-6D
                ? new Vec3d(0.0D, -1.0D, 0.0D)
                : direction.normalize();
    }

    private static Vec3d composedForce(Entity entity, DirectedEntityGravity directed) {
        if (directed == null) {
            return Vec3d.ZERO;
        }
        return directed.directedForce().add(worldGravityForce(entity, directed.previousNoGravity()));
    }

    private static Vec3d worldGravityForce(Entity entity, boolean previousNoGravity) {
        if (entity == null || previousNoGravity) {
            return Vec3d.ZERO;
        }
        if (!shouldIgnoreInvertedPull(entity)) {
            double inverted = getInvertedAreaGravity(entity);
            if (inverted > 0.0D) {
                return new Vec3d(0.0D, inverted, 0.0D);
            }
        }
        return new Vec3d(0.0D, -NORMAL_WORLD_GRAVITY, 0.0D);
    }

    private static Vec3d clampHorizontal(Vec3d velocity, double maxHorizontal) {
        double horizontalSq = velocity.x * velocity.x + velocity.z * velocity.z;
        double maxSq = maxHorizontal * maxHorizontal;
        if (horizontalSq <= maxSq || horizontalSq < 1.0E-8D) {
            return velocity;
        }
        double scale = maxHorizontal / Math.sqrt(horizontalSq);
        return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    private static Vec3d applyGroundFriction(Entity entity, Vec3d velocity) {
        if (entity == null || !entity.isOnGround()) {
            return velocity;
        }
        double horizontalSq = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSq < 1.0E-8D) {
            return new Vec3d(0.0D, velocity.y, 0.0D);
        }

        double horizontal = Math.sqrt(horizontalSq);
        double stopDistance = groundStopDistance(entity);
        double nextHorizontal = horizontal * Math.max(0.0D, 1.0D - horizontal / stopDistance);
        double scale = nextHorizontal / horizontal;
        return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    private static double groundStopDistance(Entity entity) {
        BlockPos pos = BlockPos.ofFloored(entity.getX(), entity.getBoundingBox().minY - 0.05D, entity.getZ());
        BlockState state = entity.getWorld().getBlockState(pos);
        if (state.isAir()) {
            state = entity.getWorld().getBlockState(pos.down());
        }
        if (isGrassFrictionBlock(state)) {
            return DIRECTED_GRASS_STOP_DISTANCE;
        }
        if (isStoneFrictionBlock(state)) {
            return DIRECTED_STONE_STOP_DISTANCE;
        }
        return DIRECTED_DEFAULT_STOP_DISTANCE;
    }

    private static boolean isGrassFrictionBlock(BlockState state) {
        return state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.PODZOL)
                || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.MOSS_BLOCK);
    }

    private static boolean isStoneFrictionBlock(BlockState state) {
        return state.isOf(Blocks.STONE)
                || state.isOf(Blocks.SMOOTH_STONE)
                || state.isOf(Blocks.COBBLESTONE)
                || state.isOf(Blocks.MOSSY_COBBLESTONE)
                || state.isOf(Blocks.STONE_BRICKS)
                || state.isOf(Blocks.CRACKED_STONE_BRICKS)
                || state.isOf(Blocks.MOSSY_STONE_BRICKS)
                || state.isOf(Blocks.CHISELED_STONE_BRICKS)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.POLISHED_GRANITE)
                || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.POLISHED_DIORITE)
                || state.isOf(Blocks.ANDESITE)
                || state.isOf(Blocks.POLISHED_ANDESITE)
                || state.isOf(Blocks.DEEPSLATE)
                || state.isOf(Blocks.COBBLED_DEEPSLATE)
                || state.isOf(Blocks.POLISHED_DEEPSLATE)
                || state.isOf(Blocks.DEEPSLATE_BRICKS)
                || state.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || state.isOf(Blocks.DEEPSLATE_TILES)
                || state.isOf(Blocks.CRACKED_DEEPSLATE_TILES)
                || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.POLISHED_TUFF)
                || state.isOf(Blocks.TUFF_BRICKS)
                || state.isOf(Blocks.CALCITE)
                || state.isOf(Blocks.GRAVEL);
    }

    private static void finishDirectedEntityGravity(Entity entity, DirectedEntityGravity directed) {
        entity.setNoGravity(directed.previousNoGravity());
        entity.fallDistance = 0.0F;
        syncClearDirectedEntityGravity(entity);
    }

    private static ActionResult useGravityWandOnEntity(PlayerEntity player, World world, Hand hand, Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
        if (player.getStackInHand(hand).getItem() != GravityItems.GRAVITY_WAND) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer
                && lightenEntity(serverPlayer, serverPlayer.isSneaking() ? serverPlayer : entity, serverPlayer.getRotationVec(1.0F))) {
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.FAIL;
    }

    private static Entity findLookedAtEntity(ServerPlayerEntity player, double reach) {
        ServerWorld world = player.getWorld();
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0F).multiply(reach));
        double maxDistanceSq = reach * reach;

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistanceSq = start.squaredDistanceTo(blockHit.getPos());
        }

        Vec3d ray = end.subtract(start);
        Box searchBox = player.getBoundingBox().stretch(ray).expand(1.0D);
        Entity nearest = null;
        double nearestDistanceSq = maxDistanceSq;
        for (Entity entity : world.getOtherEntities(player, searchBox, entity -> entity.isAlive() && entity.canHit())) {
            Box box = entity.getBoundingBox().expand(0.35D);
            java.util.Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distanceSq = start.squaredDistanceTo(hit.get());
            if (distanceSq < nearestDistanceSq) {
                nearest = entity;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
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
        addAreaGravity(world, origin, AREA_RADIUS, DEFAULT_AREA_HEIGHT, AREA_DURATION_TICKS, gravity);
        player.sendMessage(Text.literal("\u00a7b[Gravity] Inverted walk area r=" + AREA_RADIUS
                + " h=" + DEFAULT_AREA_HEIGHT + " ticks=" + AREA_DURATION_TICKS), true);
        return 1;
    }

    public static int addAreaGravity(ServerWorld world, BlockPos center, int radius, int ticks, double gravity) {
        return addAreaGravity(world, center, radius, DEFAULT_AREA_HEIGHT, ticks, gravity);
    }

    public static int addAreaGravity(ServerWorld world, BlockPos center, int radius, int height, int ticks, double gravity) {
        return addAreaGravity(world, center, GravityAreaSpec.legacy(radius, height), ticks, gravity);
    }

    public static int addAreaGravity(ServerWorld world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity) {
        ensureServerAreasLoaded(world.getServer());
        AreaGravityField field = new AreaGravityField(UUID.randomUUID(), world.getRegistryKey(), center.toImmutable(), spec, ticks, gravity, false);
        AREA_FIELDS.add(field);
        broadcastAreaGravity(world, field);
        saveServerAreas(world.getServer());
        return 1;
    }

    private static void handleDebugAreaAction(ServerPlayerEntity player, GravityPackets.DebugAreaActionC2S packet) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world) || !canUseDebugStick(player)) {
            return;
        }

        GravityAreaSpec spec = new GravityAreaSpec(
                GravityAreaSpec.Shape.byId(packet.shape()),
                GravityAreaSpec.Half.byId(packet.half()),
                packet.sizeX(),
                packet.sizeY(),
                packet.sizeZ()
        );
        int ticks = packet.ticks() == 0 ? INFINITE_AREA_TICKS : packet.ticks();
        double gravity = clampGravity(packet.gravity());

        switch (packet.action()) {
            case 0 -> placeDebugArea(player, world, packet.center(), spec, ticks, gravity);
            case 1 -> moveNearestDebugArea(player, world, packet.center());
            case 2 -> deleteDebugAreas(player, world, packet.center(), spec);
            case 3 -> undoDebugAreaEdit(player, world);
            case 4 -> redoDebugAreaEdit(player, world);
            default -> {
            }
        }
    }

    private static boolean canUseDebugStick(ServerPlayerEntity player) {
        return player.isCreative() || player.hasPermissionLevel(2);
    }

    private static void placeDebugArea(ServerPlayerEntity player, ServerWorld world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity) {
        ensureServerAreasLoaded(world.getServer());
        AreaGravityField field = new AreaGravityField(UUID.randomUUID(), world.getRegistryKey(), center.toImmutable(), spec, ticks, gravity, false);
        AREA_FIELDS.add(field);
        broadcastAreaGravity(world, field);
        saveServerAreas(world.getServer());
        pushDebugEdit(player, new AreaEdit(List.of(snapshot(field)), List.of()));
        player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Placed " + describeSpec(spec) + " at " + center.toShortString()), true);
    }

    private static void moveNearestDebugArea(ServerPlayerEntity player, ServerWorld world, BlockPos center) {
        ensureServerAreasLoaded(world.getServer());
        AreaGravityField field = nearestArea(world, player.getPos());
        if (field == null) {
            player.sendMessage(Text.literal("\u00a7c[Gravity Debug] No area to move"), true);
            return;
        }

        AreaSnapshot removed = snapshot(field);
        AREA_FIELDS.remove(field);
        broadcastClearAreaGravity(world, field, false);
        AreaGravityField moved = new AreaGravityField(UUID.randomUUID(), field.world, center.toImmutable(), field.spec, field.ticks, field.gravity, false);
        AREA_FIELDS.add(moved);
        broadcastAreaGravity(world, moved);
        saveServerAreas(world.getServer());
        pushDebugEdit(player, new AreaEdit(List.of(snapshot(moved)), List.of(removed)));
        player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Moved nearest area"), true);
    }

    private static void deleteDebugAreas(ServerPlayerEntity player, ServerWorld world, BlockPos center, GravityAreaSpec spec) {
        ensureServerAreasLoaded(world.getServer());
        List<AreaSnapshot> removed = new java.util.ArrayList<>();
        GravityAreaSpec.Bounds selection = spec.bounds(center);
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced || !field.world.equals(world.getRegistryKey())) {
                continue;
            }
            GravityAreaSpec.Bounds bounds = field.spec.bounds(field.center);
            if (intersects(selection, bounds)) {
                removed.add(snapshot(field));
                AREA_FIELDS.remove(field);
                broadcastClearAreaGravity(world, field, false);
            }
        }

        if (removed.isEmpty()) {
            player.sendMessage(Text.literal("\u00a7c[Gravity Debug] No area in selection"), true);
            return;
        }

        saveServerAreas(world.getServer());
        pushDebugEdit(player, new AreaEdit(List.of(), removed));
        player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Deleted " + removed.size() + " area(s)"), true);
    }

    private static void undoDebugAreaEdit(ServerPlayerEntity player, ServerWorld world) {
        AreaEdit edit = popEdit(DEBUG_UNDO, player.getUuid());
        if (edit == null) {
            player.sendMessage(Text.literal("\u00a7c[Gravity Debug] Nothing to undo"), true);
            return;
        }
        applyInverseEdit(world, edit);
        pushEdit(DEBUG_REDO, player.getUuid(), edit);
        player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Undo"), true);
    }

    private static void redoDebugAreaEdit(ServerPlayerEntity player, ServerWorld world) {
        AreaEdit edit = popEdit(DEBUG_REDO, player.getUuid());
        if (edit == null) {
            player.sendMessage(Text.literal("\u00a7c[Gravity Debug] Nothing to redo"), true);
            return;
        }
        applyEdit(world, edit);
        pushEdit(DEBUG_UNDO, player.getUuid(), edit);
        player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Redo"), true);
    }

    private static void pushDebugEdit(ServerPlayerEntity player, AreaEdit edit) {
        pushEdit(DEBUG_UNDO, player.getUuid(), edit);
        DEBUG_REDO.computeIfAbsent(player.getUuid(), ignored -> new java.util.ArrayDeque<>()).clear();
    }

    private static void pushEdit(Map<UUID, java.util.ArrayDeque<AreaEdit>> edits, UUID playerId, AreaEdit edit) {
        java.util.ArrayDeque<AreaEdit> stack = edits.computeIfAbsent(playerId, ignored -> new java.util.ArrayDeque<>());
        stack.push(edit);
        while (stack.size() > 64) {
            stack.removeLast();
        }
    }

    private static AreaEdit popEdit(Map<UUID, java.util.ArrayDeque<AreaEdit>> edits, UUID playerId) {
        java.util.ArrayDeque<AreaEdit> stack = edits.get(playerId);
        return stack == null || stack.isEmpty() ? null : stack.pop();
    }

    private static void applyEdit(ServerWorld world, AreaEdit edit) {
        for (AreaSnapshot snapshot : edit.removed()) {
            removeSnapshot(world, snapshot);
        }
        for (AreaSnapshot snapshot : edit.added()) {
            restoreSnapshot(world, snapshot);
        }
        saveServerAreas(world.getServer());
    }

    private static void applyInverseEdit(ServerWorld world, AreaEdit edit) {
        for (AreaSnapshot snapshot : edit.added()) {
            removeSnapshot(world, snapshot);
        }
        for (AreaSnapshot snapshot : edit.removed()) {
            restoreSnapshot(world, snapshot);
        }
        saveServerAreas(world.getServer());
    }

    private static void removeSnapshot(ServerWorld world, AreaSnapshot snapshot) {
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced && field.id.equals(snapshot.id())) {
                AREA_FIELDS.remove(field);
                if (field.world.equals(world.getRegistryKey())) {
                    broadcastClearAreaGravity(world, field, false);
                }
                return;
            }
        }
    }

    private static void restoreSnapshot(ServerWorld world, AreaSnapshot snapshot) {
        AreaGravityField field = new AreaGravityField(snapshot.id(), snapshot.world(), snapshot.center(), snapshot.spec(), snapshot.ticks(), snapshot.gravity(), false);
        AREA_FIELDS.add(field);
        if (field.world.equals(world.getRegistryKey())) {
            broadcastAreaGravity(world, field);
        }
    }

    private static AreaGravityField nearestArea(ServerWorld world, Vec3d pos) {
        AreaGravityField nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced || !field.world.equals(world.getRegistryKey())) {
                continue;
            }
            double distanceSq = field.center.toCenterPos().squaredDistanceTo(pos);
            if (distanceSq < nearestDistanceSq) {
                nearest = field;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private static AreaSnapshot snapshot(AreaGravityField field) {
        return new AreaSnapshot(field.id, field.world, field.center, field.spec, field.ticks, field.gravity);
    }

    private static boolean intersects(GravityAreaSpec.Bounds a, GravityAreaSpec.Bounds b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
                && a.minY() <= b.maxY() && a.maxY() >= b.minY()
                && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    public static int clearNearestAreaGravity(ServerWorld world, Vec3d pos) {
        AreaGravityField nearest = nearestArea(world, pos);
        if (nearest == null) {
            return 0;
        }

        AREA_FIELDS.remove(nearest);
        broadcastClearAreaGravity(world, nearest, false);
        resetInvertedPlayersOutsideAreas(world);
        saveServerAreas(world.getServer());
        return 1;
    }

    public static int clearAllAreaGravity(ServerWorld world) {
        int removed = 0;
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced && field.world.equals(world.getRegistryKey()) && AREA_FIELDS.remove(field)) {
                removed++;
            }
        }
        if (removed > 0) {
            broadcastClearAllAreaGravity(world);
            resetInvertedPlayersOutsideAreas(world);
            saveServerAreas(world.getServer());
        }
        return removed;
    }

    public static void syncAreaGravityTo(ServerPlayerEntity player) {
        ensureServerAreasLoaded(player.getServer());
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced && field.world.equals(player.getWorld().getRegistryKey())) {
                ServerPlayNetworking.send(player, areaPacket(field));
            }
        }
    }

    public static boolean canCropSurviveInverted(BlockState cropState, BlockView world, BlockPos cropPos) {
        if (!(world instanceof World realWorld)) {
            return false;
        }
        return isInInvertedArea(realWorld.getRegistryKey(), Vec3d.ofCenter(cropPos))
                && world.getBlockState(cropPos.up()).isOf(Blocks.FARMLAND);
    }

    private static ActionResult useInvertedBlockSurface(PlayerEntity player, World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
        if (!isInInvertedArea(world.getRegistryKey(), hit.getPos())) {
            return ActionResult.PASS;
        }

        ActionResult tallPlantResult = placeInvertedTallPlant(player, world, hand, hit);
        if (tallPlantResult.isAccepted()) {
            return tallPlantResult;
        }

        ActionResult doorResult = placeInvertedCeilingDoor(player, world, hand, hit);
        if (doorResult.isAccepted()) {
            return doorResult;
        }

        if (hit.getSide() != Direction.DOWN) {
            return ActionResult.PASS;
        }

        BlockPos farmlandPos = hit.getBlockPos();
        if (!world.getBlockState(farmlandPos).isOf(Blocks.FARMLAND)) {
            return ActionResult.PASS;
        }

        BlockState crop = cropForSeed(player.getStackInHand(hand));
        if (crop == null) {
            return ActionResult.PASS;
        }

        BlockPos cropPos = farmlandPos.down();
        if (!world.getBlockState(cropPos).isAir()) {
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        world.setBlockState(cropPos, crop, Block.NOTIFY_ALL);
        if (!player.getAbilities().creativeMode) {
            player.getStackInHand(hand).decrement(1);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    private static ActionResult placeInvertedTallPlant(PlayerEntity player, World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
        if (hit.getSide() != Direction.DOWN) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof TallPlantBlock tallPlantBlock)) {
            return ActionResult.PASS;
        }

        BlockPos supportPos = hit.getBlockPos();
        BlockPos lowerPos = supportPos.down();
        BlockPos upperPos = supportPos.down(2);
        if (upperPos.getY() < world.getBottomY()
                || !world.getBlockState(lowerPos).isReplaceable()
                || !world.getBlockState(upperPos).isReplaceable()) {
            return ActionResult.PASS;
        }

        BlockState lowerState = tallPlantBlock.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        if (!lowerState.canPlaceAt(new InvertedSupportWorldView(world, lowerPos), lowerPos)) {
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockState upperState = tallPlantBlock.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        int flags = Block.NOTIFY_ALL | Block.FORCE_STATE;
        world.setBlockState(lowerPos, TallPlantBlock.withWaterloggedState(world, lowerPos, lowerState), flags);
        world.setBlockState(upperPos, TallPlantBlock.withWaterloggedState(world, upperPos, upperState), flags);

        BlockSoundGroup soundGroup = lowerState.getSoundGroup();
        world.playSound(null, lowerPos, lowerState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS,
                (soundGroup.getVolume() + 1.0F) / 2.0F, soundGroup.getPitch() * 0.8F);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    private static ActionResult placeInvertedCeilingDoor(PlayerEntity player, World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
        if (hit.getSide() != Direction.DOWN) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof DoorBlock doorBlock)) {
            return ActionResult.PASS;
        }

        BlockPos supportPos = hit.getBlockPos();
        BlockState supportState = world.getBlockState(supportPos);
        if (!supportState.isSideSolidFullSquare(world, supportPos, Direction.DOWN)) {
            return ActionResult.PASS;
        }

        BlockPos upperPos = supportPos.down();
        BlockPos lowerPos = supportPos.down(2);
        if (lowerPos.getY() < world.getBottomY() || !world.getBlockState(lowerPos).isReplaceable() || !world.getBlockState(upperPos).isReplaceable()) {
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        boolean powered = world.isReceivingRedstonePower(lowerPos) || world.isReceivingRedstonePower(upperPos);
        BlockState lowerState = doorBlock.getDefaultState();
        if (lowerState.contains(Properties.HORIZONTAL_FACING)) {
            lowerState = lowerState.with(Properties.HORIZONTAL_FACING, player.getHorizontalFacing());
        }
        if (lowerState.contains(Properties.DOOR_HINGE)) {
            lowerState = lowerState.with(Properties.DOOR_HINGE, DoorHinge.LEFT);
        }
        if (lowerState.contains(Properties.OPEN)) {
            lowerState = lowerState.with(Properties.OPEN, powered);
        }
        if (lowerState.contains(Properties.POWERED)) {
            lowerState = lowerState.with(Properties.POWERED, powered);
        }
        if (lowerState.contains(Properties.DOUBLE_BLOCK_HALF)) {
            lowerState = lowerState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        }

        BlockState upperState = lowerState;
        if (upperState.contains(Properties.DOUBLE_BLOCK_HALF)) {
            upperState = upperState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        }

        int flags = Block.NOTIFY_ALL | Block.FORCE_STATE;
        world.setBlockState(lowerPos, lowerState, flags);
        world.setBlockState(upperPos, upperState, flags);
        doorBlock.onPlaced(world, lowerPos, lowerState, player, stack);

        BlockSoundGroup soundGroup = lowerState.getSoundGroup();
        world.playSound(null, lowerPos, lowerState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS,
                (soundGroup.getVolume() + 1.0F) / 2.0F, soundGroup.getPitch() * 0.8F);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    private static BlockState cropForSeed(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.WHEAT_SEEDS) {
            return Blocks.WHEAT.getDefaultState();
        }
        if (item == Items.CARROT) {
            return Blocks.CARROTS.getDefaultState();
        }
        if (item == Items.POTATO) {
            return Blocks.POTATOES.getDefaultState();
        }
        if (item == Items.BEETROOT_SEEDS) {
            return Blocks.BEETROOTS.getDefaultState();
        }
        return null;
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
        for (Map.Entry<UUID, DirectedEntityGravity> entry : ENTITY_GRAVITY.entrySet()) {
            UUID entityId = entry.getKey();
            DirectedEntityGravity gravity = entry.getValue();
            if (!gravity.world().equals(world.getRegistryKey())) {
                continue;
            }

            Entity entity = world.getEntity(entityId);
            if (entity == null || !entity.isAlive()) {
                ENTITY_GRAVITY.remove(entityId, gravity);
                continue;
            }

            if (entity instanceof ServerPlayerEntity) {
                continue;
            }

            if (gravity.expired() || shouldIgnoreInvertedPull(entity)) {
                ENTITY_GRAVITY.remove(entityId, gravity);
                finishDirectedEntityGravity(entity, gravity);
                continue;
            }

            entity.setNoGravity(true);
            Vec3d force = composedForce(entity, gravity);
            Vec3d velocity = applyGroundFriction(entity, entity.getVelocity().add(force));
            entity.setVelocity(velocity);
            if (force.y > 0.0D) {
                entity.fallDistance = 0.0F;
            }
            DirectedEntityGravity ticked = gravity.ticked();
            if (ticked.expired()) {
                ENTITY_GRAVITY.remove(entityId, gravity);
                finishDirectedEntityGravity(entity, gravity);
            } else {
                ENTITY_GRAVITY.replace(entityId, gravity, ticked);
            }
        }
    }

    private static void tickAreaGravity(ServerWorld world) {
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced || !field.world.equals(world.getRegistryKey())) {
                continue;
            }
            if (field.ticks == INFINITE_AREA_TICKS) {
                continue;
            }
            field.ticks--;
            if (field.ticks <= 0) {
                AREA_FIELDS.remove(field);
                broadcastClearAreaGravity(world, field, false);
                resetInvertedPlayersOutsideAreas(world);
                saveServerAreas(world.getServer());
            }
        }
    }

    private static void ensureServerAreasLoaded(MinecraftServer server) {
        if (serverAreasLoaded) {
            return;
        }
        serverAreasLoaded = true;
        Path path = areaSavePath(server);
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray areas = root.getAsJsonArray("areas");
            if (areas == null) {
                return;
            }

            for (JsonElement element : areas) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject area = element.getAsJsonObject();
                RegistryKey<World> world = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(area.get("world").getAsString()));
                UUID id = area.has("id") ? UUID.fromString(area.get("id").getAsString()) : UUID.randomUUID();
                BlockPos center = new BlockPos(area.get("x").getAsInt(), area.get("y").getAsInt(), area.get("z").getAsInt());
                int radius = area.get("radius").getAsInt();
                int height = area.has("height") ? area.get("height").getAsInt() : DEFAULT_AREA_HEIGHT;
                GravityAreaSpec spec = area.has("shape")
                        ? new GravityAreaSpec(
                        GravityAreaSpec.Shape.byId(area.get("shape").getAsInt()),
                        GravityAreaSpec.Half.byId(area.has("half") ? area.get("half").getAsInt() : GravityAreaSpec.Half.LOWER.ordinal()),
                        area.has("sizeX") ? area.get("sizeX").getAsInt() : radius,
                        area.has("sizeY") ? area.get("sizeY").getAsInt() : height,
                        area.has("sizeZ") ? area.get("sizeZ").getAsInt() : radius
                )
                        : GravityAreaSpec.legacy(radius, height);
                int ticks = area.get("ticks").getAsInt();
                double gravity = area.get("gravity").getAsDouble();
                AREA_FIELDS.add(new AreaGravityField(id, world, center, spec, ticks, gravity, false));
            }
        } catch (RuntimeException | IOException ignored) {
            // Corrupt debug-area data should not prevent a world from loading.
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncAreaGravityTo(player);
        }
    }

    private static void saveServerAreas(MinecraftServer server) {
        if (!serverAreasLoaded) {
            return;
        }

        JsonObject root = new JsonObject();
        JsonArray areas = new JsonArray();
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced) {
                continue;
            }
            JsonObject area = new JsonObject();
            area.addProperty("id", field.id.toString());
            area.addProperty("world", field.world.getValue().toString());
            area.addProperty("x", field.center.getX());
            area.addProperty("y", field.center.getY());
            area.addProperty("z", field.center.getZ());
            area.addProperty("radius", field.spec.sizeX());
            area.addProperty("height", field.spec.sizeY());
            area.addProperty("shape", field.spec.shape().ordinal());
            area.addProperty("half", field.spec.half().ordinal());
            area.addProperty("sizeX", field.spec.sizeX());
            area.addProperty("sizeY", field.spec.sizeY());
            area.addProperty("sizeZ", field.spec.sizeZ());
            area.addProperty("ticks", field.ticks);
            area.addProperty("gravity", field.gravity);
            areas.add(area);
        }
        root.add("areas", areas);

        Path path = areaSavePath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                writer.write(root.toString());
            }
        } catch (IOException ignored) {
            // The active areas remain in memory even if disk persistence fails.
        }
    }

    private static Path areaSavePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("monvhua_gravity_areas.json");
    }

    private static void tickInvertedServerPlayerStates(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (shouldIgnoreInvertedPull(player)) {
                resetInvertedPlayerState(player);
                continue;
            }
            if (isInInvertedArea(player)) {
                SERVER_INVERTED_PLAYER_STATES.computeIfAbsent(player.getUuid(), uuid -> new InvertedPlayerState());
                player.setNoGravity(true);
                player.fallDistance = 0.0D;
            } else {
                resetInvertedPlayerIfInactive(player);
            }
        }
    }

    public static boolean cancelServerInvertedTravel(Entity entity) {
        if (shouldIgnoreInvertedPull(entity)) {
            resetInvertedPlayerState(entity);
            return false;
        }
        if (!isInInvertedArea(entity)) {
            resetInvertedPlayerIfInactive(entity);
            return false;
        }
        entity.setNoGravity(true);
        entity.fallDistance = 0.0D;
        return true;
    }

    public static boolean tickInvertedPlayer(Entity entity, PlayerInput input) {
        if (entity != null && input != null) {
            DirectedEntityGravity directed = ENTITY_GRAVITY.get(entity.getUuid());
            if (directed != null) {
                return tickDirectedPlayer(entity, input, directed);
            }
        }

        Map<UUID, InvertedPlayerState> stateMap = invertedPlayerStates(entity);
        if (entity == null || input == null || getInvertedAreaGravity(entity) <= 0.0D || shouldIgnoreInvertedPull(entity)) {
            if (entity != null) {
                resetInvertedPlayerState(entity);
            }
            return false;
        }

        InvertedPlayerState state = stateMap.computeIfAbsent(entity.getUuid(), uuid -> new InvertedPlayerState());
        entity.setNoGravity(true);
        entity.fallDistance = 0.0D;

        if (state.detachTicks > 0) {
            state.detachTicks--;
        }

        Vec3d velocity = entity.getVelocity();
        CeilingSupport support = state.detachTicks <= 0 && velocity.y >= -0.02D ? findCeilingSupport(entity) : null;

        if (support != null) {
            recoverInvertedStandingPose(entity, input, support);
            double targetY = support.bottomY - entity.getHeight() - INVERTED_CEILING_EPSILON;
            double delta = targetY - entity.getY();

            if (input.jump()) {
                state.detachTicks = INVERTED_JUMP_DETACH_TICKS;
                state.attached = false;
                Vec3d acceleration = inputAcceleration(entity, input, INVERTED_AIR_ACCELERATION);
                Vec3d moveVelocity = new Vec3d(velocity.x + acceleration.x, INVERTED_JUMP_SPEED, velocity.z + acceleration.z);
                moveWithoutSneakEdgeClip(entity, moveVelocity);
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
                moveAttachedToInvertedSupport(entity, moveVelocity);
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
            moveWithoutSneakEdgeClip(entity, moveVelocity);
            velocity = new Vec3d(moveVelocity.x * INVERTED_AIR_XZ_DRAG, moveVelocity.y * INVERTED_Y_DRAG, moveVelocity.z * INVERTED_AIR_XZ_DRAG);
            entity.setOnGround(false);
        }

        entity.setVelocity(velocity);
        return true;
    }

    private static boolean tickDirectedPlayer(Entity entity, PlayerInput input, DirectedEntityGravity directed) {
        if (directed == null) {
            return false;
        }
        if (!directed.world().equals(entity.getWorld().getRegistryKey())) {
            ENTITY_GRAVITY.remove(entity.getUuid(), directed);
            finishDirectedEntityGravity(entity, directed);
            return false;
        }

        if (directed.expired() || shouldIgnoreInvertedPull(entity)) {
            ENTITY_GRAVITY.remove(entity.getUuid(), directed);
            finishDirectedEntityGravity(entity, directed);
            return false;
        }

        entity.setNoGravity(true);
        entity.fallDistance = 0.0F;

        Vec3d force = composedForce(entity, directed);
        Vec3d magicForce = directed.directedForce();
        Vec3d velocity = entity.getVelocity();
        boolean grounded = entity.isOnGround();
        Vec3d inputForce = inputAcceleration(entity, input,
                grounded ? DIRECTED_PLAYER_GROUND_ACCELERATION : DIRECTED_PLAYER_AIR_ACCELERATION);
        Vec3d horizontalForce = new Vec3d(force.x, 0.0D, force.z);
        Vec3d next = velocity.add(force).add(inputForce);

        if (input.jump() && grounded) {
            double jumpSpeed = DIRECTED_PLAYER_BASE_JUMP_SPEED
                    + Math.clamp(force.y * DIRECTED_PLAYER_JUMP_FORCE_SCALE, -0.32D, 0.82D);
            next = new Vec3d(next.x, Math.max(next.y, Math.max(0.05D, jumpSpeed)), next.z);
            entity.setOnGround(false);
        }

        if (force.y > 0.0D) {
            Vec3d flightDirection = magicForce.lengthSquared() > 1.0E-8D ? magicForce.normalize() : normalizedDirection(force);
            double glideStrength = Math.clamp(force.y * 1.8D + horizontalForce.length() * 0.5D, 0.02D, 0.22D);
            Vec3d desired = flightDirection.multiply(Math.clamp(0.65D + magicForce.length() * 6.0D, 0.65D, 2.15D));
            next = next.add(desired.subtract(next).multiply(glideStrength));
            next = new Vec3d(next.x, Math.min(next.y, DIRECTED_PLAYER_MAX_UP_SPEED), next.z);
        } else {
            next = new Vec3d(next.x, Math.max(next.y, DIRECTED_PLAYER_MAX_DOWN_SPEED), next.z);
        }

        next = clampHorizontal(next, DIRECTED_PLAYER_MAX_HORIZONTAL_SPEED);
        moveWithoutSneakEdgeClip(entity, next);

        double xzDrag = grounded ? 1.0D : DIRECTED_PLAYER_AIR_XZ_DRAG;
        Vec3d stored = new Vec3d(next.x * xzDrag, next.y * DIRECTED_PLAYER_VERTICAL_DRAG, next.z * xzDrag);
        stored = applyGroundFriction(entity, stored);
        if (force.y > 0.0D) {
            stored = new Vec3d(stored.x, Math.min(stored.y, DIRECTED_PLAYER_MAX_UP_SPEED), stored.z);
        }
        entity.setVelocity(stored);

        DirectedEntityGravity ticked = directed.ticked();
        if (ticked.expired()) {
            ENTITY_GRAVITY.remove(entity.getUuid(), directed);
            finishDirectedEntityGravity(entity, directed);
        } else {
            ENTITY_GRAVITY.replace(entity.getUuid(), directed, ticked);
        }
        return true;
    }

    private static void recoverInvertedStandingPose(Entity entity, PlayerInput input, CeilingSupport support) {
        if (input.sneak() || !entity.isInPose(EntityPose.CROUCHING)) {
            return;
        }

        double standingHeight = entity.getDimensions(EntityPose.STANDING).height();
        if (standingHeight <= entity.getHeight() + 1.0E-4D) {
            return;
        }

        double targetY = support.bottomY - standingHeight - INVERTED_CEILING_EPSILON;
        Box currentBox = entity.getBoundingBox();
        Box standingBox = new Box(
                currentBox.minX,
                targetY,
                currentBox.minZ,
                currentBox.maxX,
                targetY + standingHeight,
                currentBox.maxZ
        );
        if (!entity.getWorld().isSpaceEmpty(entity, standingBox)) {
            return;
        }

        entity.setPose(EntityPose.STANDING);
        entity.setPosition(entity.getX(), targetY, entity.getZ());
    }

    private static void moveAttachedToInvertedSupport(Entity entity, Vec3d moveVelocity) {
        double requestedHorizontalSq = horizontalLengthSquared(moveVelocity);
        if (requestedHorizontalSq <= 1.0E-8D) {
            moveWithoutSneakEdgeClip(entity, moveVelocity);
            return;
        }

        double beforeX = entity.getX();
        double beforeY = entity.getY();
        double beforeZ = entity.getZ();
        moveWithoutSneakEdgeClip(entity, moveVelocity);

        double normalX = entity.getX();
        double normalY = entity.getY();
        double normalZ = entity.getZ();
        double normalHorizontalSq = horizontalDistanceSquared(beforeX, beforeZ, normalX, normalZ);
        if (normalHorizontalSq >= requestedHorizontalSq * 0.5D) {
            return;
        }

        entity.setPosition(beforeX, beforeY, beforeZ);
        moveWithoutSneakEdgeClip(entity, moveVelocity.add(0.0D, -INVERTED_STEP_HEIGHT, 0.0D));
        double steppedHorizontalSq = horizontalDistanceSquared(beforeX, beforeZ, entity.getX(), entity.getZ());
        CeilingSupport steppedSupport = findCeilingSupport(entity, INVERTED_STEP_HEIGHT + INVERTED_CEILING_PROBE);
        if (steppedHorizontalSq < normalHorizontalSq
                || steppedSupport == null
                || Math.abs(steppedSupport.bottomY - entity.getHeight() - INVERTED_CEILING_EPSILON - entity.getY()) > INVERTED_STEP_HEIGHT * 0.5D) {
            entity.setPosition(normalX, normalY, normalZ);
        } else {
            double targetY = steppedSupport.bottomY - entity.getHeight() - INVERTED_CEILING_EPSILON;
            entity.setPosition(entity.getX(), targetY, entity.getZ());
        }
    }

    private static void moveWithoutSneakEdgeClip(Entity entity, Vec3d movement) {
        if (!entity.isSneaking()) {
            entity.move(MovementType.SELF, movement);
            return;
        }

        entity.setSneaking(false);
        try {
            entity.move(MovementType.SELF, movement);
        } finally {
            entity.setSneaking(true);
        }
    }

    private static double horizontalLengthSquared(Vec3d velocity) {
        return velocity.x * velocity.x + velocity.z * velocity.z;
    }

    private static double horizontalDistanceSquared(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return dx * dx + dz * dz;
    }

    public static void resetInvertedPlayerIfInactive(Entity entity) {
        if (entity != null && !isInInvertedArea(entity)) {
            resetInvertedPlayerState(entity);
        }
    }

    private static void resetInvertedPlayerState(Entity entity) {
        InvertedPlayerState removed = invertedPlayerStates(entity).remove(entity.getUuid());
        if (removed == null) {
            return;
        }

        entity.setNoGravity(false);
        entity.fallDistance = 0.0D;

        Vec3d velocity = entity.getVelocity();
        if (velocity.y > 0.0D) {
            entity.setVelocity(velocity.x, 0.0D, velocity.z);
        }
    }

    private static void resetInvertedPlayersOutsideAreas(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            resetInvertedPlayerIfInactive(player);
        }
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
        return findCeilingSupport(entity, INVERTED_CEILING_PROBE);
    }

    private static CeilingSupport findCeilingSupport(Entity entity, double verticalReach) {
        Box box = entity.getBoundingBox();
        int minBlockY = (int) Math.floor(box.maxY - INVERTED_CEILING_PROBE);
        int maxBlockY = (int) Math.floor(box.maxY + verticalReach + INVERTED_CEILING_PROBE);
        double[][] samples = new double[][] {
                {entity.getX(), entity.getZ()},
                {box.minX + 0.05D, box.minZ + 0.05D},
                {box.minX + 0.05D, box.maxZ - 0.05D},
                {box.maxX - 0.05D, box.minZ + 0.05D},
                {box.maxX - 0.05D, box.maxZ - 0.05D}
        };

        CeilingSupport closest = null;
        double closestGap = Double.MAX_VALUE;
        for (double[] sample : samples) {
            for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
                BlockPos pos = BlockPos.ofFloored(sample[0], blockY, sample[1]);
                CeilingSupport support = findCeilingSupportAt(entity, pos, sample[0], sample[1]);
                if (support == null) {
                    continue;
                }

                double gap = support.bottomY - box.maxY;
                if (gap < -INVERTED_CEILING_PROBE || gap > verticalReach + INVERTED_CEILING_PROBE) {
                    continue;
                }
                if (gap < closestGap) {
                    closest = support;
                    closestGap = gap;
                }
            }
        }
        return closest;
    }

    private static CeilingSupport findCeilingSupportAt(Entity entity, BlockPos pos, double sampleX, double sampleZ) {
        BlockState state = entity.getWorld().getBlockState(pos);
        if (state.isAir()) {
            return null;
        }

        VoxelShape shape = state.getCollisionShape(entity.getWorld(), pos, ShapeContext.of(entity));
        if (shape.isEmpty()) {
            return null;
        }

        double localX = sampleX - pos.getX();
        double localZ = sampleZ - pos.getZ();
        for (Box bounds : shape.getBoundingBoxes()) {
            if (localX >= bounds.minX - INVERTED_CEILING_PROBE
                    && localX <= bounds.maxX + INVERTED_CEILING_PROBE
                    && localZ >= bounds.minZ - INVERTED_CEILING_PROBE
                    && localZ <= bounds.maxZ + INVERTED_CEILING_PROBE) {
                return new CeilingSupport(pos.getY() + bounds.minY);
            }
        }
        return null;
    }

    public static boolean isInInvertedArea(Entity entity) {
        return getInvertedAreaGravity(entity) > 0.0D;
    }

    public static boolean isInInvertedArea(RegistryKey<World> world, Vec3d pos) {
        return getInvertedAreaGravity(world, pos) > 0.0D;
    }

    public static Vec3d areaTopCenter(BlockPos center) {
        return new Vec3d(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
    }

    public static boolean isInvertedWalking(Entity entity) {
        InvertedPlayerState state = entity == null ? null : invertedPlayerStates(entity).get(entity.getUuid());
        return state != null && state.attached;
    }

    private static boolean shouldIgnoreInvertedPull(Entity entity) {
        return entity instanceof PlayerEntity player && player.getAbilities().creativeMode && player.getAbilities().flying;
    }

    public static Vec3d correctInvertedFlyingMovementInput(Entity entity, Vec3d movementInput) {
        if (movementInput == null || !isInInvertedArea(entity) || !isFlyingPlayer(entity)) {
            return movementInput;
        }
        return new Vec3d(-movementInput.x, movementInput.y, movementInput.z);
    }

    private static boolean isFlyingPlayer(Entity entity) {
        return entity instanceof PlayerEntity player && player.getAbilities().flying;
    }

    private static Map<UUID, InvertedPlayerState> invertedPlayerStates(Entity entity) {
        return entity != null && entity.getWorld().isClient() ? CLIENT_INVERTED_PLAYER_STATES : SERVER_INVERTED_PLAYER_STATES;
    }

    private static void broadcastAreaGravity(ServerWorld world, AreaGravityField field) {
        GravityPackets.AreaGravityS2C packet = areaPacket(field);
        double trackingDistanceSq = (field.maxExtent() + 96.0D) * (field.maxExtent() + 96.0D);
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.squaredDistanceTo(areaTopCenter(field.center)) <= trackingDistanceSq) {
                ServerPlayNetworking.send(target, packet);
            }
        }
    }

    private static GravityPackets.AreaGravityS2C areaPacket(AreaGravityField field) {
        return new GravityPackets.AreaGravityS2C(
                field.id,
                field.center,
                field.spec.shape().ordinal(),
                field.spec.half().ordinal(),
                field.spec.sizeX(),
                field.spec.sizeY(),
                field.spec.sizeZ(),
                field.ticks,
                field.gravity
        );
    }

    private static void broadcastClearAreaGravity(ServerWorld world, AreaGravityField field, boolean all) {
        GravityPackets.ClearAreaGravityS2C packet = new GravityPackets.ClearAreaGravityS2C(field.id, field.center, field.maxExtent(), all);
        for (ServerPlayerEntity target : world.getPlayers()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    private static void broadcastClearAllAreaGravity(ServerWorld world) {
        GravityPackets.ClearAreaGravityS2C packet = new GravityPackets.ClearAreaGravityS2C(new UUID(0L, 0L), BlockPos.ORIGIN, 0, true);
        for (ServerPlayerEntity target : world.getPlayers()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    public static boolean shouldSuppressVanillaGravity(Entity entity) {
        DirectedEntityGravity directed = ENTITY_GRAVITY.get(entity.getUuid());
        return directed != null && !directed.expired() && directed.world().equals(entity.getWorld().getRegistryKey())
                || isInInvertedArea(entity) && !shouldIgnoreInvertedPull(entity);
    }

    public static void addClientSyncedAreaGravity(UUID id, RegistryKey<World> world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity) {
        AREA_FIELDS.removeIf(field -> field.clientSynced && field.id.equals(id));
        AREA_FIELDS.add(new AreaGravityField(id, world, center.toImmutable(), spec, ticks, gravity, true));
    }

    public static void clearClientSyncedAreaGravity(UUID id, RegistryKey<World> world, boolean all) {
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced || !field.world.equals(world)) {
                continue;
            }
            if (all || field.id.equals(id)) {
                AREA_FIELDS.remove(field);
            }
        }
    }

    public static void tickClientSyncedAreaGravity() {
        for (AreaGravityField field : AREA_FIELDS) {
            if (!field.clientSynced) {
                continue;
            }
            if (field.ticks == INFINITE_AREA_TICKS) {
                continue;
            }
            field.ticks--;
            if (field.ticks <= 0) {
                AREA_FIELDS.remove(field);
            }
        }
    }

    public static List<AreaGravityView> getClientAreaGravityViews(RegistryKey<World> world) {
        List<AreaGravityView> views = new java.util.ArrayList<>();
        for (AreaGravityField field : AREA_FIELDS) {
            if (field.clientSynced && field.world.equals(world)) {
                views.add(new AreaGravityView(field.id, field.center, field.spec, field.ticks));
            }
        }
        return views;
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

    private static String describeSpec(GravityAreaSpec spec) {
        return spec.shape().name().toLowerCase(java.util.Locale.ROOT)
                + "/" + spec.half().name().toLowerCase(java.util.Locale.ROOT)
                + " x=" + spec.sizeX()
                + " y=" + spec.sizeY()
                + " z=" + spec.sizeZ();
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
        private final UUID id;
        private final RegistryKey<World> world;
        private final BlockPos center;
        private final GravityAreaSpec spec;
        private final double gravity;
        private final boolean clientSynced;
        private int ticks;

        private AreaGravityField(UUID id, RegistryKey<World> world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity, boolean clientSynced) {
            this.id = id == null ? UUID.randomUUID() : id;
            this.world = world;
            this.center = center.toImmutable();
            this.spec = spec == null ? GravityAreaSpec.legacy(AREA_RADIUS, DEFAULT_AREA_HEIGHT) : spec;
            this.ticks = ticks;
            this.gravity = gravity;
            this.clientSynced = clientSynced;
        }

        private boolean contains(Vec3d pos) {
            return this.spec.contains(this.center, pos);
        }

        private int maxExtent() {
            return this.spec.maxExtent();
        }
    }

    private static final class InvertedPlayerState {
        private boolean attached;
        private int detachTicks;
    }

    private record CeilingSupport(double bottomY) {
    }

}
