package com.kuilunfuzhe.monvhua.features.gravity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.item.config.GravityConfig;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import com.kuilunfuzhe.monvhua.WitchStage;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityMagic {
    public static final double WORLD_GRAVITY = 0.08D;
    public static final double DEFAULT_GRAVITY = WORLD_GRAVITY;
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
    private static final double NORMAL_WORLD_GRAVITY = WORLD_GRAVITY;
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
    private static final int BLOCK_SELECT_RADIUS = 2;
    private static final int MAX_SELECTED_BLOCKS = 512;
    private static final int MAX_BLOCK_GROUP_SPAWNS_PER_TICK = 48;
    private static final int HELD_BLOCK_LIFETIME_TICKS = 20 * 180;
    private static final int THROWN_BLOCK_LIFETIME_TICKS = 20 * 90;
    private static final double HELD_BLOCK_BASE_HEIGHT_OFFSET = 3.0D;
    private static final double THROW_BASE_SPEED = 0.95D;
    private static final double THROW_FORCE_SPEED_SCALE = 8.0D;
    private static final double THROW_FORCE_ACCELERATION_SCALE = 0.05D;
    private static final double THROW_MAX_SPEED = 3.0D;
    private static final double THROW_DAMAGE_PADDING = 0.35D;
    private static final int THROW_DAMAGE_COOLDOWN_TICKS = 10;
    private static final int BLOCK_GROUP_MIN_STAGE = 6;
    private static final int SELF_FORCE_DAMAGE_BLOCK_COUNT = 2;
    private static final double SELF_FORCE_DAMAGE_REFERENCE_HARDNESS = 1.5D;
    private static final double SELF_FORCE_GLIDE_COLLISION_MIN_SPEED_LOSS = 0.3D;
    private static final int SELF_FORCE_IMPACT_COOLDOWN_TICKS = 10;
    private static final double EXTRACT_SPEED_MULTIPLIER = 0.8D;
    private static final Identifier EXTRACT_SPEED_MODIFIER_ID = Identifier.of("monvhua", "gravity_extract_speed_multiplier");
    private static final int QUAKE_HINT_TICKS = 60;
    private static final double SELF_FORCE_MAX_SPEED = 1.85D;
    private static final double SELF_FORCE_HORIZONTAL_DAMPING = 0.985D;
    private static final double SELF_FORCE_LANDING_MIN_FALL_SPEED = 0.42D;
    private static final double SELF_FORCE_LANDING_MIN_PROBE_DISTANCE = 0.85D;
    private static final double SELF_FORCE_LANDING_MAX_PROBE_DISTANCE = 7.5D;
    private static final double SELF_FORCE_LANDING_SPEED_LOOKAHEAD = 1.8D;
    private static final double SELF_FORCE_LANDING_EDGE_PADDING = 0.08D;
    private static final int NON_NORMAL_FALL_RESET_TICKS = 20 * 3;
    private static final double NON_NORMAL_FALL_MIN_SPEED = 0.08D;
    private static final double MAX_GRAVITY_ENERGY = 100.0D;
    private static final int ENERGY_SYNC_INTERVAL_TICKS = 10;
    private static final Map<UUID, LaunchMode> PLAYER_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PLAYER_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PLAYER_ENERGY = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ENERGY_SYNC_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, ExtractingBlockGroup> EXTRACTING_BLOCK_GROUPS = new ConcurrentHashMap<>();
    private static final Map<UUID, SelfForceMotion> SELF_FORCE_MOTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SELF_FORCE_RECOVERY_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SELF_FORCE_IMPACT_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> NON_NORMAL_FALL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, SurfaceDistanceAnchor> SURFACE_DISTANCE_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT_DIRECTED_RECOVERY_TICKS = new ConcurrentHashMap<>();
    private static final Set<UUID> THROWN_GROUP_IMPACTS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, DirectedEntityGravity> ENTITY_GRAVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, HeldBlockGroup> HELD_BLOCK_GROUPS = new ConcurrentHashMap<>();
    private static final Map<UUID, ThrownBlockData> THROWN_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> THROWN_DAMAGE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> SERVER_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, InvertedPlayerState> CLIENT_INVERTED_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> SURFACE_LOGIC_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FORCE_LOGIC_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Direction> SERVER_SURFACE_GRAVITY_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Direction> CLIENT_SURFACE_GRAVITY_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, SurfaceGravityEngine.SurfaceState> SERVER_SURFACE_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, SurfaceGravityEngine.SurfaceState> CLIENT_SURFACE_PLAYER_STATES = new ConcurrentHashMap<>();
    private static final List<AreaGravityField> AREA_FIELDS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, java.util.ArrayDeque<AreaEdit>> DEBUG_UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.ArrayDeque<AreaEdit>> DEBUG_REDO = new ConcurrentHashMap<>();
    private static boolean serverAreasLoaded = false;

    public enum LaunchMode {
        UP,
        RIGHT
    }

    public enum LogicMode {
        FORCE,
        SURFACE
    }

    private static final LogicMode DEFAULT_LOGIC_MODE = LogicMode.SURFACE;

    public record AreaGravityView(UUID id, BlockPos center, GravityAreaSpec spec, int ticks) {
    }

    private record AreaSnapshot(UUID id, RegistryKey<World> world, BlockPos center, GravityAreaSpec spec, int ticks, double gravity) {
    }

    private record AreaEdit(List<AreaSnapshot> added, List<AreaSnapshot> removed) {
    }

    private record SurfaceDistanceAnchor(RegistryKey<World> world, Direction direction, Vec3d pos) {
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

    private record SelfForceMotion(RegistryKey<World> world, Vec3d direction, double acceleration, int ticks, Vec3d previousVelocity) {
        private SelfForceMotion {
            direction = normalizedDirection(direction);
            acceleration = Math.max(0.0D, acceleration);
            ticks = Math.max(0, ticks);
            previousVelocity = previousVelocity == null ? Vec3d.ZERO : previousVelocity;
        }

        private SelfForceMotion ticked() {
            return new SelfForceMotion(world, direction, acceleration, ticks - 1, previousVelocity);
        }

        private SelfForceMotion withPreviousVelocity(Vec3d velocity) {
            return new SelfForceMotion(world, direction, acceleration, ticks, velocity);
        }
    }

    private record HeldBlock(GravityBlockEntity entity, Vec3d offset, double massKg, double hardness) {
    }

    private record SelectedBlock(BlockPos pos, BlockState state, double massKg, double hardness) {
    }

    private record ExtractingBlockPlan(BlockPos sourcePos, BlockState sourceState, Vec3d animationStart, BlockState animationState, Vec3d offset, Vec3d target,
                                       double massKg, double hardness, int spawnTick) {
    }

    private static final class ExtractingBlockGroup {
        private final RegistryKey<World> world;
        private final List<HeldBlock> blocks;
        private final List<ExtractingBlockPlan> pendingBlocks;
        private final double massKg;
        private final int totalTicks;
        private final int flyTicks;
        private final double sphereRadius;
        private int age;

        private ExtractingBlockGroup(RegistryKey<World> world, List<ExtractingBlockPlan> pendingBlocks,
                                     double massKg, int totalTicks) {
            this.world = world;
            this.blocks = new ArrayList<>();
            this.pendingBlocks = new ArrayList<>(pendingBlocks);
            this.massKg = Math.max(0.0D, massKg);
            this.totalTicks = Math.max(1, totalTicks);
            this.flyTicks = Math.max(1, this.totalTicks / 5);
            this.sphereRadius = sphereRadiusForCount(pendingBlocks.size());
        }
    }

    private static final class HeldBlockGroup {
        private final UUID id = UUID.randomUUID();
        private final RegistryKey<World> world;
        private final List<HeldBlock> blocks;
        private final double massKg;
        private final double averageHardness;
        private final double sphereRadius;
        private int age;
        private double pitch;
        private double yaw;
        private double roll;
        private final double pitchVelocity;
        private final double yawVelocity;
        private final double rollVelocity;

        private HeldBlockGroup(RegistryKey<World> world, List<HeldBlock> blocks, double massKg, ServerWorld serverWorld) {
            this.world = world;
            this.blocks = new ArrayList<>(blocks);
            this.massKg = Math.max(0.0D, massKg);
            this.averageHardness = averageHardness(blocks);
            this.sphereRadius = sphereRadiusForCount(blocks.size());
            this.pitchVelocity = signedAngularVelocity(serverWorld, 0.34D, 0.82D);
            this.yawVelocity = signedAngularVelocity(serverWorld, 0.42D, 1.05D);
            this.rollVelocity = signedAngularVelocity(serverWorld, 0.28D, 0.76D);
        }

        private void tickAngles() {
            this.age++;
            this.pitch += this.pitchVelocity;
            this.yaw += this.yawVelocity;
            this.roll += this.rollVelocity;
        }
    }

    private static final class ThrownBlockData {
        private final UUID groupId;
        private final UUID ownerUuid;
        private final RegistryKey<World> world;
        private final double groupMassKg;
        private final double averageHardness;
        private final Vec3d forceDirection;
        private final double acceleration;
        private int forceTicks;

        private ThrownBlockData(UUID groupId, UUID ownerUuid, RegistryKey<World> world, double groupMassKg, double averageHardness,
                                Vec3d forceDirection, double acceleration, int forceTicks) {
            this.groupId = groupId;
            this.ownerUuid = ownerUuid;
            this.world = world;
            this.groupMassKg = Math.max(0.0D, groupMassKg);
            this.averageHardness = Math.max(0.0D, averageHardness);
            this.forceDirection = normalizedDirection(forceDirection);
            this.acceleration = Math.max(0.0D, acceleration);
            this.forceTicks = Math.max(0, forceTicks);
        }
    }

    private GravityMagic() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.AdjustGravityC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                double gravity = adjustTargetGravity(player, packet.entityId(), packet.gravity());
                player.sendMessage(Text.literal("\u00a7b[重力] 力=" + formatGravityMultiplier(gravity)), true);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.SelectBlocksC2S.ID, (packet, context) -> {
            context.server().execute(() -> selectHeldBlocks(context.player(), packet.center()));
        });
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.DebugAreaActionC2S.ID, (packet, context) -> {
            context.server().execute(() -> handleDebugAreaAction(context.player(), packet));
        });
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.SurfaceLookC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (isSurfaceLogicMode(player) && hasSurfaceGravity(player)) {
                    setSurfaceLook(player, packet.localYaw(), packet.localPitch());
                    syncSurfaceLook(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.RequestConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> syncConfigTo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(GravityPackets.UpdateConfigC2S.ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (!player.hasPermissionLevel(2) && !player.isCreative()) {
                    player.sendMessage(Text.literal("\u00a7c没有权限修改重力配置"), true);
                    return;
                }
                GravityConfig config = GravityConfig.fromJson(packet.json());
                GravityConfig.setInstance(config);
                for (ServerPlayerEntity target : context.server().getPlayerManager().getPlayerList()) {
                    syncConfigTo(target);
                }
                player.sendMessage(Text.literal("\u00a7a重力配置已更新"), true);
            });
        });

        ServerTickEvents.START_SERVER_TICK.register(GravityMagic::tickNaturalLandingRecovery);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ensureServerAreasLoaded(server);
            tickSelfForceMotions(server);
            tickSelfForceRecovery(server);
            tickNonNormalFallReset(server);
            tickPlayerGravityEnergy(server);
            tickExtractingBlockGroups(server);
            tickHeldBlockGroups(server);
            tickSelfForceImpactCooldowns();
            tickThrownDamageCooldowns();
            for (ServerWorld world : server.getWorlds()) {
                tickAreaGravity(world);
                tickInvertedServerPlayerStates(world);
                applyEntityGravity(world);
                tickThrownBlocks(world);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            cleanupPlayerGravity(player, true);
            DirectedEntityGravity directed = ENTITY_GRAVITY.remove(player.getUuid());
            if (directed != null) {
                finishDirectedEntityGravity(player, directed);
            }
            resetInvertedPlayerState(player);
            clearSurfaceLogic(player);
        });
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer
                    && SERVER_SURFACE_GRAVITY_PLAYERS.containsKey(trackedPlayer.getUuid())) {
                syncSurfaceGravityTo(trackedPlayer, player);
            }
        });
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (entity instanceof ServerPlayerEntity trackedPlayer
                    && SERVER_SURFACE_GRAVITY_PLAYERS.containsKey(trackedPlayer.getUuid())) {
                syncSurfaceGravityClearTo(trackedPlayer, player);
            }
        });

        UseBlockCallback.EVENT.register(GravityMagic::useInvertedBlockSurface);
        UseEntityCallback.EVENT.register(GravityMagic::useGravityWandOnEntity);
    }

    public static void syncConfigTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new GravityPackets.ConfigS2C(GravityConfig.getInstance().toJson()));
        syncEnergyTo(player);
        syncSurfaceLogicMode(player);
        syncSurfaceGravityTo(player, player);
    }

    public static LogicMode getLogicMode(ServerPlayerEntity player) {
        if (player == null) {
            return DEFAULT_LOGIC_MODE;
        }
        UUID uuid = player.getUuid();
        if (FORCE_LOGIC_PLAYERS.contains(uuid)) {
            return LogicMode.FORCE;
        }
        if (SURFACE_LOGIC_PLAYERS.contains(uuid)) {
            return LogicMode.SURFACE;
        }
        return DEFAULT_LOGIC_MODE;
    }

    public static LogicMode setLogicMode(ServerPlayerEntity player, LogicMode mode) {
        if (player == null) {
            return DEFAULT_LOGIC_MODE;
        }
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            return getLogicMode(player);
        }
        LogicMode next = mode == LogicMode.SURFACE ? LogicMode.SURFACE : LogicMode.FORCE;
        if (next == LogicMode.SURFACE) {
            SURFACE_LOGIC_PLAYERS.add(player.getUuid());
            FORCE_LOGIC_PLAYERS.remove(player.getUuid());
            cleanupPlayerGravity(player, false);
        } else {
            SURFACE_LOGIC_PLAYERS.remove(player.getUuid());
            FORCE_LOGIC_PLAYERS.add(player.getUuid());
            clearSurfaceGravity(player);
        }
        syncSurfaceLogicMode(player);
        return next;
    }

    public static LogicMode toggleLogicMode(ServerPlayerEntity player) {
        return setLogicMode(player, getLogicMode(player) == LogicMode.SURFACE ? LogicMode.FORCE : LogicMode.SURFACE);
    }

    public static boolean isSurfaceLogicMode(ServerPlayerEntity player) {
        return getLogicMode(player) == LogicMode.SURFACE;
    }

    public static boolean isSurfaceLogicMode(Entity entity) {
        if (entity == null) {
            return false;
        }
        UUID uuid = entity.getUuid();
        if (FORCE_LOGIC_PLAYERS.contains(uuid)) {
            return false;
        }
        if (SURFACE_LOGIC_PLAYERS.contains(uuid)) {
            return true;
        }
        return entity instanceof PlayerEntity && DEFAULT_LOGIC_MODE == LogicMode.SURFACE;
    }

    public static ActionResult useActiveBlockGroup(ServerPlayerEntity player) {
        if (player == null) {
            return ActionResult.FAIL;
        }
        if (EXTRACTING_BLOCK_GROUPS.containsKey(player.getUuid())) {
            sendBlockGroupLockMessage(player);
            return ActionResult.FAIL;
        }
        if (HELD_BLOCK_GROUPS.containsKey(player.getUuid())) {
            return throwHeldBlocks(player) ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static boolean hasBlockGroupLock(ServerPlayerEntity player) {
        return player != null && (EXTRACTING_BLOCK_GROUPS.containsKey(player.getUuid())
                || HELD_BLOCK_GROUPS.containsKey(player.getUuid()));
    }

    private static void sendBlockGroupLockMessage(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (EXTRACTING_BLOCK_GROUPS.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§c[重力] 正在汇聚方块，无法切换重力"), true);
        } else if (HELD_BLOCK_GROUPS.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§c[重力] 请先扔出方块群，再进行方向切换"), true);
        }
    }

    public static Direction getSurfaceGravityDirection(Entity entity) {
        if (entity == null) {
            return null;
        }
        return surfaceGravityPlayers(entity).get(entity.getUuid());
    }

    private static Map<UUID, Direction> surfaceGravityPlayers(Entity entity) {
        return entity != null && entity.getWorld().isClient()
                ? CLIENT_SURFACE_GRAVITY_PLAYERS
                : SERVER_SURFACE_GRAVITY_PLAYERS;
    }

    private static void applySurfaceGravityTransition(Entity entity, Direction direction) {
        if (entity == null || direction == null) {
            return;
        }
        surfaceGravityPlayers(entity).put(entity.getUuid(), direction);
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        if (state != null) {
            state.setDownDirection(direction);
        }
        if (!entity.getWorld().isClient() && entity instanceof ServerPlayerEntity player) {
            syncSurfaceGravity(player);
        }
    }

    public static SurfaceGravityEngine.SurfaceState getSurfaceState(Entity entity) {
        if (entity == null) {
            return null;
        }
        return surfacePlayerStates(entity).get(entity.getUuid());
    }

    public static SurfaceView getSurfaceView(Entity entity) {
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        Direction direction = getSurfaceGravityDirection(entity);
        if (state == null || direction == null) {
            return null;
        }
        return new SurfaceView(direction, state.localYaw(), state.localPitch());
    }

    public static Vec3d getSurfaceEyePos(Entity entity, float tickProgress) {
        Direction direction = getSurfaceGravityDirection(entity);
        if (direction == null) {
            return entity == null ? Vec3d.ZERO : entity.getCameraPosVec(tickProgress);
        }
        if (direction == Direction.DOWN) {
            return entity.getCameraPosVec(tickProgress);
        }
        Box currentBox = entity.getBoundingBox();
        Vec3d currentAnchor = SurfaceGravityCollision.anchorFromBox(direction, currentBox);
        Vec3d previousAnchor = new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
        Vec3d interpolatedAnchor = new Vec3d(
                MathHelper.lerp(tickProgress, previousAnchor.x, currentAnchor.x),
                MathHelper.lerp(tickProgress, previousAnchor.y, currentAnchor.y),
                MathHelper.lerp(tickProgress, previousAnchor.z, currentAnchor.z)
        );
        Box box = currentBox.offset(interpolatedAnchor.subtract(currentAnchor));
        return SurfaceGravityCollision.eyePosFromBox(entity, direction, box, surfaceEyeHeight(entity, tickProgress));
    }

    public static float getSurfaceCrouchProgress(Entity entity, float tickProgress) {
        Direction direction = getSurfaceGravityDirection(entity);
        if (entity == null || direction == null || direction == Direction.DOWN) {
            return 0.0F;
        }
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        if (state == null) {
            return entity.isInPose(EntityPose.CROUCHING) || entity.isSneaking() ? 1.0F : 0.0F;
        }
        state.updateCrouch(entity.isInPose(EntityPose.CROUCHING) || entity.isSneaking(), entity.age);
        return state.crouchProgress(tickProgress);
    }

    private static double surfaceEyeHeight(Entity entity, float tickProgress) {
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        if (state == null) {
            return entity.getDimensions(entity.getPose()).eyeHeight();
        }
        state.updateCrouch(entity.isInPose(EntityPose.CROUCHING) || entity.isSneaking(), entity.age);
        float crouchProgress = state.crouchProgress(tickProgress);
        double standingEye = entity.getDimensions(EntityPose.STANDING).eyeHeight();
        double crouchingEye = entity.getDimensions(EntityPose.CROUCHING).eyeHeight();
        return MathHelper.lerp(crouchProgress, standingEye, crouchingEye);
    }

    public static Vec3d getSurfaceLook(Entity entity) {
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        Direction direction = getSurfaceGravityDirection(entity);
        if (direction == null) {
            return entity == null ? new Vec3d(0.0D, 0.0D, 1.0D) : entity.getRotationVec(1.0F);
        }
        if (state == null) {
            return SurfaceGravityBasis.look(direction, entity.getYaw(), entity.getPitch());
        }
        return SurfaceGravityBasis.look(direction, state.localYaw(), state.localPitch());
    }

    public static void setSurfaceLook(Entity entity, float localYaw, float localPitch) {
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        if (state != null) {
            state.setLook(localYaw, localPitch);
            entity.setYaw(localYaw);
            entity.setPitch(Math.clamp(localPitch, -89.0F, 89.0F));
        }
    }

    public static void setSurfaceLookSnapshot(Entity entity, float localYaw, float localPitch) {
        SurfaceGravityEngine.SurfaceState state = getSurfaceState(entity);
        if (state != null) {
            setSurfaceLook(entity, localYaw, localPitch);
            state.snapBodyYawToLook();
        }
    }

    public static void setClientSurfaceGravity(Entity entity, Direction direction) {
        setClientSurfaceGravity(entity, direction, null);
    }

    public static void setClientSurfaceGravity(Entity entity, Direction direction, Vec3d anchor) {
        if (entity == null) {
            return;
        }
        if (direction == null) {
            Direction oldDirection = CLIENT_SURFACE_GRAVITY_PLAYERS.get(entity.getUuid());
            Vec3d oldEye = oldDirection == null ? null : SurfaceGravityCollision.eyePosFromBox(entity, oldDirection, entity.getBoundingBox());
            CLIENT_SURFACE_GRAVITY_PLAYERS.remove(entity.getUuid());
            CLIENT_SURFACE_PLAYER_STATES.remove(entity.getUuid());
            entity.setNoGravity(false);
            if (oldEye != null) {
                SurfaceGravityCollision.moveKeepingEye(entity, Direction.DOWN, oldEye);
            } else {
                SurfaceGravityCollision.restoreVanillaBox(entity);
            }
        } else {
            Direction oldDirection = CLIENT_SURFACE_GRAVITY_PLAYERS.get(entity.getUuid());
            Vec3d currentEye = oldDirection == null ? entity.getCameraPosVec(1.0F) : SurfaceGravityCollision.eyePosFromBox(entity, oldDirection, entity.getBoundingBox());
            Vec3d currentLook = oldDirection == null ? entity.getRotationVec(1.0F) : getSurfaceLook(entity);
            SurfaceGravityBasis.LocalView localView = SurfaceGravityBasis.localView(direction, currentLook);
            CLIENT_SURFACE_GRAVITY_PLAYERS.put(entity.getUuid(), direction);
            SurfaceGravityEngine.SurfaceState state = CLIENT_SURFACE_PLAYER_STATES.computeIfAbsent(
                    entity.getUuid(),
                    uuid -> new SurfaceGravityEngine.SurfaceState(direction, localView.yaw(), localView.pitch())
            );
            state.setLook(localView.yaw(), localView.pitch());
            state.setDownDirection(direction);
            if (anchor != null) {
                SurfaceGravityCollision.setAnchorAndRefreshBox(entity, direction, anchor);
            } else {
                SurfaceGravityCollision.moveKeepingEye(entity, direction, currentEye);
            }
        }
    }

    public static void setClientSurfaceLogicMode(Entity entity, boolean enabled) {
        if (entity == null) {
            return;
        }
        if (enabled) {
            SURFACE_LOGIC_PLAYERS.add(entity.getUuid());
            FORCE_LOGIC_PLAYERS.remove(entity.getUuid());
        } else {
            SURFACE_LOGIC_PLAYERS.remove(entity.getUuid());
            FORCE_LOGIC_PLAYERS.add(entity.getUuid());
            setClientSurfaceGravity(entity, null);
        }
    }



    public static LaunchMode getMode(ServerPlayerEntity player) {
        return PLAYER_MODES.getOrDefault(player.getUuid(), LaunchMode.UP);
    }

    public static double getSelectedGravity(ServerPlayerEntity player) {
        return clampGravityForStage(player, PLAYER_GRAVITY.getOrDefault(player.getUuid(), DEFAULT_GRAVITY));
    }

    public static double setSelectedGravity(ServerPlayerEntity player, double gravity) {
        double clamped = clampGravityForStage(player, gravity);
        PLAYER_GRAVITY.put(player.getUuid(), clamped);
        return clamped;
    }

    public static double adjustTargetGravity(ServerPlayerEntity player, int entityId, double gravity) {
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            if (entityId >= 0) {
                Entity entity = player.getWorld().getEntityById(entityId);
                if (entity instanceof GravityBlockEntity gravityBlock) {
                    return gravityBlock.getGravityAmount();
                }
            }
            return getSelectedGravity(player);
        }
        double clamped = clampGravityForStage(player, gravity);
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

    public static void selectHeldBlocks(ServerPlayerEntity player, BlockPos center) {
        if (player == null || center == null || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!isHoldingGravityWand(player)) {
            return;
        }
        if (player.getEyePos().squaredDistanceTo(center.toCenterPos()) > LIGHTEN_ENTITY_REACH * LIGHTEN_ENTITY_REACH) {
            player.sendMessage(Text.literal("\u00a7c距离 地面太远了"), true);
            return;
        }

        GravityConfig config = GravityConfig.getInstance();
        int stage = gravityStage(player);
        if (!canUseBlockGroup(stage)) {
            player.sendMessage(Text.literal("\u00a7c[重力] 方块汇聚需要达到阶段6"), true);
            return;
        }
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            return;
        }
        if (!canStartBlockGathering(player)) {
            player.sendMessage(Text.literal("§c[重力] 请到地面进行汇聚"), true);
            return;
        }
        int blockLimit = Math.min(config.getMaxPickBlocks(stage), MAX_SELECTED_BLOCKS);
        double maxHardness = config.getMaxPickHardness(stage);
        int searchRadius = selectionSearchRadius(blockLimit);
        List<SelectedBlock> candidates = new ArrayList<>();
        int radiusSq = searchRadius * searchRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = -searchRadius; y <= searchRadius && candidates.size() < blockLimit; y++) {
            for (int x = -searchRadius; x <= searchRadius && candidates.size() < blockLimit; x++) {
                for (int z = -searchRadius; z <= searchRadius && candidates.size() < blockLimit; z++) {
                    if (x * x + y * y + z * z > radiusSq) {
                        continue;
                    }
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockPos pos = mutable.toImmutable();
                    BlockState state = world.getBlockState(pos);
                    if (!canSelectBlock(world, pos, state, maxHardness)) {
                        continue;
                    }

                    double hardness = state.getHardness(world, pos);
                    candidates.add(new SelectedBlock(pos, state, hardness * 10.0D, hardness));
                }
            }
        }

        if (candidates.isEmpty()) {
            player.sendMessage(Text.literal("\u00a7c[重力] 没有可选择的方块"), true);
            return;
        }

        int extractTicks = config.getBlockExtractTicks(stage);
        if (!hasEnergy(player)) {
            player.sendMessage(Text.literal("\u00a7c[重力魔法] 没有足够的能量"), true);
            return;
        }

        cleanupPlayerGravity(player, true);
        List<Vec3d> sphereOffsets = sphereOffsets(candidates.size());
        List<Integer> extractionDelays = stagedExtractionDelays(candidates.size(), extractTicks);
        double sphereRadius = sphereRadiusForCount(candidates.size());
        Vec3d anchor = heldBlockAnchor(player, sphereRadius);
        List<SelectedBlock> animationSources = animationSurfaceSources(world, center, candidates, 30);
        double totalMassKg = 0.0D;
        List<ExtractingBlockPlan> plans = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            SelectedBlock candidate = candidates.get(i);
            SelectedBlock animationSource = animationSources.get(i % animationSources.size());
            Vec3d offset = sphereOffsets.get(i);
            Vec3d targetPos = anchor.add(offset);
            plans.add(new ExtractingBlockPlan(candidate.pos(), candidate.state(),
                    Vec3d.ofCenter(animationSource.pos()), animationSource.state(), offset, targetPos,
                    candidate.massKg(), candidate.hardness(), extractionDelays.get(i)));
            totalMassKg += candidate.massKg();
        }

        if (plans.isEmpty()) {
            player.sendMessage(Text.literal("\u00a7c[重力] 无法汇聚方块群"), true);
            return;
        }

        EXTRACTING_BLOCK_GROUPS.put(player.getUuid(), new ExtractingBlockGroup(world.getRegistryKey(), plans, totalMassKg, extractTicks));
        syncExtractPose(player, extractTicks);
        applyExtractingSpeedPenalty(player);
        if (plans.size() >= blockLimit) {
            broadcastQuakeHint(player);
        }
        player.sendMessage(Text.literal("§e我需要专心汇聚...，这会使我无暇顾及其它"), true);
        player.sendMessage(Text.literal("\u00a7b[重力] 正在汇聚 " + plans.size()
                + " 个方块，阶段=" + stage
                + "，质量=" + format(totalMassKg) + "kg"), true);
    }

    public static boolean throwHeldBlocks(ServerPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        if (!canUseBlockGroup(gravityStage(player))) {
            clearHeldBlockGroup(player.getUuid(), true);
            player.sendMessage(Text.literal("\u00a7c[重力] 方块汇聚需要达到阶段6"), true);
            return false;
        }

        HeldBlockGroup group = HELD_BLOCK_GROUPS.remove(player.getUuid());
        if (group == null || group.blocks.isEmpty()) {
            return false;
        }
        if (!group.world.equals(world.getRegistryKey())) {
            for (HeldBlock held : group.blocks) {
                GravityBlockEntity block = held.entity();
                if (block != null && block.isAlive()) {
                    block.discard();
                }
            }
            return false;
        }

        Vec3d direction = normalizedDirection(player.getRotationVec(1.0F));
        double force = getSelectedGravity(player);
        double initialSpeed = Math.clamp(THROW_BASE_SPEED + force * THROW_FORCE_SPEED_SCALE, THROW_BASE_SPEED, THROW_MAX_SPEED);
        Vec3d velocity = clampSpeed(player.getVelocity().add(direction.multiply(initialSpeed)), THROW_MAX_SPEED);
        int forceTicks = Math.min(GravityConfig.getInstance().getForceDurationTicks(), THROWN_BLOCK_LIFETIME_TICKS);
        double acceleration = force * THROW_FORCE_ACCELERATION_SCALE;
        int thrown = 0;

        for (HeldBlock held : group.blocks) {
            GravityBlockEntity block = held.entity();
            if (block == null || !block.isAlive()) {
                continue;
            }
            block.setTemporary(THROWN_BLOCK_LIFETIME_TICKS);
            block.setPlaceOrDropOnSettle(false);
            block.setMaxAgeTicks(THROWN_BLOCK_LIFETIME_TICKS);
            block.setGravityY(-DEFAULT_GRAVITY);
            block.setNoGravity(true);
            block.clearRenderGroup();
            block.setVelocity(velocity);
            THROWN_BLOCKS.put(block.getUuid(), new ThrownBlockData(
                    group.id,
                    player.getUuid(),
                    world.getRegistryKey(),
                    group.massKg,
                    group.averageHardness,
                    direction,
                    acceleration,
                    forceTicks
            ));
            thrown++;
        }

        if (thrown <= 0) {
            return false;
        }
        player.sendMessage(Text.literal("\u00a7b[重力] 已投掷 " + thrown
                + " 个方块，质量=" + format(group.massKg) + "kg"), true);
        return true;
    }

    public static boolean applySelfGravityForce(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            return false;
        }
        Vec3d direction = normalizedDirection(player.getRotationVec(1.0F));
        if (!hasEnergy(player)) {
            player.sendMessage(Text.literal("\u00a7c[重力] 没有足够能量"), true);
            return false;
        }
        startSelfForceMotion(player, direction);
        return true;
    }

    private static void damageSelfForce(ServerPlayerEntity player, Vec3d direction) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double force = getSelectedGravity(player);
        double speed = Math.clamp(THROW_BASE_SPEED + force * THROW_FORCE_SPEED_SCALE, THROW_BASE_SPEED, THROW_MAX_SPEED);
        Vec3d projectedVelocity = clampSpeed(player.getVelocity().add(normalizedDirection(direction).multiply(speed)), THROW_MAX_SPEED);
        double massKg = 10.0D * SELF_FORCE_DAMAGE_REFERENCE_HARDNESS * SELF_FORCE_DAMAGE_BLOCK_COUNT;
        float damage = (float) (kineticDamage(massKg, projectedVelocity.length() * 20.0D) * SELF_FORCE_DAMAGE_REFERENCE_HARDNESS);
        if (damage > 0.05F) {
            player.damage(world, world.getDamageSources().magic(), damage);
        }
    }

    private static boolean isHoldingGravityWand(ServerPlayerEntity player) {
        return player.getMainHandStack().getItem() == GravityItems.GRAVITY_WAND
                || player.getOffHandStack().getItem() == GravityItems.GRAVITY_WAND;
    }

    private static boolean canStartBlockGathering(ServerPlayerEntity player) {
        if (player == null || !player.isOnGround() || hasNonNormalSurfaceGravity(player)) {
            return false;
        }
        UUID uuid = player.getUuid();
        return !ENTITY_GRAVITY.containsKey(uuid)
                && !SELF_FORCE_MOTIONS.containsKey(uuid)
                && !SELF_FORCE_RECOVERY_TICKS.containsKey(uuid)
                && !SERVER_INVERTED_PLAYER_STATES.containsKey(uuid)
                && !isInInvertedArea(player);
    }

    private static Vec3d heldBlockAnchor(ServerPlayerEntity player, double sphereRadius) {
        return new Vec3d(player.getX(), player.getY() + player.getHeight() + HELD_BLOCK_BASE_HEIGHT_OFFSET + Math.max(0.0D, sphereRadius), player.getZ());
    }

    private static List<SelectedBlock> animationSurfaceSources(ServerWorld world, BlockPos center, List<SelectedBlock> selected, int radius) {
        List<SelectedBlock> pool = new ArrayList<>(selected.size());
        Set<BlockPos> usedPositions = new HashSet<>();
        for (SelectedBlock block : selected) {
            if (isWorldLitSurface(world, block.pos(), block.state()) && usedPositions.add(block.pos())) {
                pool.add(block);
            }
        }

        List<SelectedBlock> surfaceCandidates = new ArrayList<>();
        int radiusSq = radius * radius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radiusSq) {
                    continue;
                }
                int worldX = center.getX() + x;
                int worldZ = center.getZ() + z;
                int topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1;
                if (topY < world.getBottomY()) {
                    continue;
                }
                mutable.set(worldX, topY, worldZ);
                BlockPos pos = mutable.toImmutable();
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || !isWorldLitSurface(world, pos, state) || !canMove(world, pos, state)) {
                    continue;
                }
                if (!usedPositions.add(pos)) {
                    continue;
                }
                double hardness = Math.max(0.1D, state.getHardness(world, pos));
                surfaceCandidates.add(new SelectedBlock(pos, state, hardness * 10.0D, hardness));
            }
        }
        for (int i = surfaceCandidates.size() - 1; i > 0; i--) {
            int j = world.random.nextInt(i + 1);
            SelectedBlock swap = surfaceCandidates.get(i);
            surfaceCandidates.set(i, surfaceCandidates.get(j));
            surfaceCandidates.set(j, swap);
        }
        pool.addAll(surfaceCandidates);

        if (pool.isEmpty()) {
            return selected;
        }

        List<SelectedBlock> result = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            result.add(pool.get(i % pool.size()));
        }
        return result;
    }

    private static boolean isWorldLitSurface(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        BlockPos above = pos.up();
        return world.isSkyVisible(above) || world.getLightLevel(above) >= 12;
    }

    private static double blockMassKg(ServerWorld world, BlockPos pos, BlockState state) {
        return Math.max(0.0D, state.getHardness(world, pos)) * 10.0D;
    }

    private static int gravityStage(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) {
            return 1;
        }
        var score = scoreboard.getScore(player, objective);
        int value = score == null ? 0 : score.getScore();
        return Math.clamp(WitchStage.fromScore(value).ordinal() + 1, 1, 7);
    }

    private static boolean canUseBlockGroup(int stage) {
        return stage >= BLOCK_GROUP_MIN_STAGE;
    }

    private static int selectionSearchRadius(int blockLimit) {
        int limit = Math.max(1, blockLimit);
        return Math.max(BLOCK_SELECT_RADIUS, Math.min(6, (int) Math.ceil(Math.cbrt(limit)) + 1));
    }

    private static boolean canSelectBlock(ServerWorld world, BlockPos pos, BlockState state, double maxHardness) {
        if (!canMove(world, pos, state)) {
            return false;
        }
        double hardness = state.getHardness(world, pos);
        return hardness <= maxHardness;
    }

    private static double averageHardness(List<HeldBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (HeldBlock block : blocks) {
            total += block.hardness();
        }
        return total / blocks.size();
    }

    private static float kineticDamage(double massKg, double speedMetersPerSecond) {
        if (massKg <= 0.0D || speedMetersPerSecond <= 0.0D) {
            return 0.0F;
        }
        double energyJoules = 0.5D * massKg * speedMetersPerSecond * speedMetersPerSecond;
        double energyKilojoules = energyJoules / 1000.0D;
        double kjPerHalfHeart = GravityConfig.getInstance().damageKilojoulesPerHalfHeart;
        return (float) (energyKilojoules / Math.max(0.1D, kjPerHalfHeart));
    }

    public static boolean lightenLookedAtEntity(ServerPlayerEntity player) {
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            return false;
        }
        if (isSurfaceLogicMode(player)) {
            player.sendMessage(Text.literal("§c[重力] 表面模式需要右键点击方块面"), true);
            return false;
        }
        if (player.isSneaking()) {
            return applySelfGravityForce(player);
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
        if (hasBlockGroupLock(player)) {
            sendBlockGroupLockMessage(player);
            return false;
        }

        double gravity = getSelectedGravity(player);
        Vec3d normalizedDirection = normalizedDirection(direction);
        Vec3d netForce = previewComposedForce(entity, normalizedDirection, gravity);
        int ticks = GravityConfig.getInstance().getForceDurationTicks();
        setDirectedEntityGravity(entity, normalizedDirection, gravity, ticks);
        player.sendMessage(Text.literal("\u00a7b[重力] 施力目标 -> " + entity.getName().getString()
                + " 力=" + formatGravityMultiplier(gravity)
                + " 合力=" + formatGravityMultiplier(netForce.length())
                + " 持续=" + ticks + "游戏刻"), true);
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
            CLIENT_DIRECTED_RECOVERY_TICKS.put(entity.getUuid(), 20);
            CLIENT_INVERTED_PLAYER_STATES.remove(entity.getUuid());
            entity.setNoGravity(false);
            Vec3d velocity = entity.getVelocity();
            double recoveryY = Math.clamp(velocity.y, -0.08D, -0.04D);
            if (Math.abs(recoveryY - velocity.y) > 1.0E-5D) {
                entity.setVelocity(velocity.x * 0.35D, recoveryY, velocity.z * 0.35D);
            }
            return;
        }
        CLIENT_DIRECTED_RECOVERY_TICKS.remove(entity.getUuid());
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

    private static Vec3d clampSpeed(Vec3d velocity, double maxSpeed) {
        double lengthSq = velocity.lengthSquared();
        double maxSq = maxSpeed * maxSpeed;
        if (lengthSq <= maxSq || lengthSq < 1.0E-8D) {
            return velocity;
        }
        return velocity.multiply(maxSpeed / Math.sqrt(lengthSq));
    }

    private static double signedAngularVelocity(ServerWorld world, double min, double max) {
        double speed = min + world.random.nextDouble() * Math.max(0.0D, max - min);
        return world.random.nextBoolean() ? speed : -speed;
    }

    private static List<Vec3d> sphereOffsets(int count) {
        List<Vec3d> offsets = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return offsets;
        }
        if (count == 1) {
            offsets.add(Vec3d.ZERO);
            return offsets;
        }

        double sphereRadius = sphereRadiusForCount(count);
        double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5D) / count;
            double y = 1.0D - 2.0D * t;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double theta = i * goldenAngle;
            double radius = sphereRadius * Math.cbrt(t);
            offsets.add(new Vec3d(
                    Math.cos(theta) * horizontal * radius,
                    y * radius,
                    Math.sin(theta) * horizontal * radius
            ));
        }
        return offsets;
    }

    private static double sphereRadiusForCount(int count) {
        return Math.max(1.0D, Math.cbrt(Math.max(1, count) * 0.75D / Math.PI));
    }

    private static Vec3d rotateOffset(Vec3d offset, double pitchDegrees, double yawDegrees, double rollDegrees) {
        double x = offset.x;
        double y = offset.y;
        double z = offset.z;

        double pitch = Math.toRadians(pitchDegrees);
        double pitchCos = Math.cos(pitch);
        double pitchSin = Math.sin(pitch);
        double yAfterPitch = y * pitchCos - z * pitchSin;
        double zAfterPitch = y * pitchSin + z * pitchCos;
        y = yAfterPitch;
        z = zAfterPitch;

        double yaw = Math.toRadians(yawDegrees);
        double yawCos = Math.cos(yaw);
        double yawSin = Math.sin(yaw);
        double xAfterYaw = x * yawCos + z * yawSin;
        double zAfterYaw = -x * yawSin + z * yawCos;
        x = xAfterYaw;
        z = zAfterYaw;

        double roll = Math.toRadians(rollDegrees);
        double rollCos = Math.cos(roll);
        double rollSin = Math.sin(roll);
        double xAfterRoll = x * rollCos - y * rollSin;
        double yAfterRoll = x * rollSin + y * rollCos;
        return new Vec3d(xAfterRoll, yAfterRoll, z);
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
        if (entity instanceof ServerPlayerEntity player) {
            if (!directed.previousNoGravity()) {
                beginSelfForceRecovery(player);
            }
        }
        syncClearDirectedEntityGravity(entity);
    }

    private static ActionResult useGravityWandOnEntity(PlayerEntity player, World world, Hand hand, Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
        if (player.getStackInHand(hand).getItem() != GravityItems.GRAVITY_WAND) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ActionResult blockGroupResult = useActiveBlockGroup(serverPlayer);
            if (blockGroupResult != ActionResult.PASS) {
                return blockGroupResult;
            }
            if (isSurfaceLogicMode(serverPlayer)) {
                if (serverPlayer.isSneaking()) {
                    clearSurfaceGravity(serverPlayer);
                    return ActionResult.SUCCESS_SERVER;
                }
                serverPlayer.sendMessage(Text.literal("§c[重力] 表面模式只能选择方块面，不能选择实体"), true);
                return ActionResult.FAIL;
            }
            if (serverPlayer.isSneaking()) {
                return applySelfGravityForce(serverPlayer) ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
            }
            if (throwHeldBlocks(serverPlayer)) {
                return ActionResult.SUCCESS_SERVER;
            }
            serverPlayer.sendMessage(Text.literal("\u00a7c[重力] 请先按住控制键并用鼠标中键选中方块"), true);
        }
        return ActionResult.FAIL;
    }

    public static ActionResult useGravityWand(ServerPlayerEntity player, BlockHitResult hit) {
        if (player == null) {
            return ActionResult.FAIL;
        }
        ActionResult blockGroupResult = useActiveBlockGroup(player);
        if (blockGroupResult != ActionResult.PASS) {
            return blockGroupResult;
        }
        if (!isSurfaceLogicMode(player)) {
            return ActionResult.PASS;
        }
        if (player.isSneaking()) {
            clearSurfaceGravity(player);
            player.sendMessage(Text.literal("§b[重力] 已清除表面重力"), true);
            return ActionResult.SUCCESS_SERVER;
        }
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("§c[重力] 请右键点击方块面来选择表面"), true);
            return ActionResult.FAIL;
        }

        Direction gravityDirection = hit.getSide().getOpposite();
        if (gravityDirection == Direction.DOWN) {
            clearSurfaceGravity(player);
            player.sendMessage(Text.literal("\u00A7b[重力] 已回到正常重力"), true);
            return ActionResult.SUCCESS_SERVER;
        }
        Direction oldDirection = SERVER_SURFACE_GRAVITY_PLAYERS.get(player.getUuid());
        Vec3d currentEye = oldDirection == null ? player.getEyePos() : SurfaceGravityCollision.eyePosFromBox(player, oldDirection, player.getBoundingBox());
        Vec3d currentLook = oldDirection == null ? player.getRotationVec(1.0F) : getSurfaceLook(player);
        SurfaceGravityBasis.LocalView localView = SurfaceGravityBasis.localView(gravityDirection, currentLook);
        SERVER_SURFACE_GRAVITY_PLAYERS.put(player.getUuid(), gravityDirection);
        SurfaceGravityEngine.SurfaceState surfaceState = surfacePlayerStates(player)
                .computeIfAbsent(player.getUuid(), uuid -> new SurfaceGravityEngine.SurfaceState(gravityDirection, localView.yaw(), localView.pitch()));
        surfaceState.setLook(localView.yaw(), localView.pitch());
        surfaceState.setDownDirection(gravityDirection);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        SurfaceGravityCollision.moveKeepingEyeOnSurface(player, gravityDirection, currentEye, hit.getPos());
        syncSurfaceGravity(player);
        player.sendMessage(Text.literal("§b[重力] 表面重力方向 -> " + directionName(gravityDirection)), true);
        return ActionResult.SUCCESS_SERVER;
    }

    private static void clearSurfaceLogic(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        SURFACE_LOGIC_PLAYERS.remove(player.getUuid());
        FORCE_LOGIC_PLAYERS.remove(player.getUuid());
        clearSurfaceGravity(player);
    }

    public static void clearSurfaceGravity(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        NON_NORMAL_FALL_TICKS.remove(player.getUuid());
        SURFACE_DISTANCE_ANCHORS.remove(player.getUuid());
        Direction oldDirection = SERVER_SURFACE_GRAVITY_PLAYERS.get(player.getUuid());
        Vec3d oldEye = oldDirection == null ? null : SurfaceGravityCollision.eyePosFromBox(player, oldDirection, player.getBoundingBox());
        SERVER_SURFACE_GRAVITY_PLAYERS.remove(player.getUuid());
        SERVER_SURFACE_PLAYER_STATES.remove(player.getUuid());
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        if (oldEye != null) {
            SurfaceGravityCollision.moveKeepingEye(player, Direction.DOWN, oldEye);
        } else {
            SurfaceGravityCollision.restoreVanillaBox(player);
        }
        syncSurfaceGravity(player);
    }

    private static void syncSurfaceLogicMode(ServerPlayerEntity player) {
        if (player != null) {
            ServerPlayNetworking.send(player, new GravityPackets.LogicModeS2C(isSurfaceLogicMode(player)));
        }
    }

    private static void syncSurfaceGravity(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        GravityPackets.SurfaceGravityS2C packet = surfaceGravityPacket(player);
        ServerPlayNetworking.send(player, packet);
        for (ServerPlayerEntity target : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    private static void syncSurfaceGravityTo(ServerPlayerEntity player, ServerPlayerEntity target) {
        if (player == null || target == null) {
            return;
        }
        ServerPlayNetworking.send(target, surfaceGravityPacket(player));
    }

    private static void syncSurfaceGravityClearTo(ServerPlayerEntity player, ServerPlayerEntity target) {
        if (player == null || target == null) {
            return;
        }
        Vec3d anchor = player.getPos();
        ServerPlayNetworking.send(target, new GravityPackets.SurfaceGravityS2C(
                player.getId(),
                -1,
                anchor.x,
                anchor.y,
                anchor.z,
                player.getYaw(),
                player.getPitch()
        ));
    }

    private static GravityPackets.SurfaceGravityS2C surfaceGravityPacket(ServerPlayerEntity player) {
        Direction direction = SERVER_SURFACE_GRAVITY_PLAYERS.get(player.getUuid());
        SurfaceGravityEngine.SurfaceState state = SERVER_SURFACE_PLAYER_STATES.get(player.getUuid());
        Vec3d anchor = player.getPos();
        return new GravityPackets.SurfaceGravityS2C(
                player.getId(),
                direction == null ? -1 : direction.ordinal(),
                anchor.x,
                anchor.y,
                anchor.z,
                state == null ? player.getYaw() : state.localYaw(),
                state == null ? player.getPitch() : state.localPitch()
        );
    }

    private static void syncSurfaceLook(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Direction direction = SERVER_SURFACE_GRAVITY_PLAYERS.get(player.getUuid());
        SurfaceGravityEngine.SurfaceState state = SERVER_SURFACE_PLAYER_STATES.get(player.getUuid());
        if (direction == null || state == null) {
            return;
        }
        GravityPackets.SurfaceLookS2C packet = new GravityPackets.SurfaceLookS2C(
                player.getId(),
                state.localYaw(),
                state.localPitch()
        );
        for (ServerPlayerEntity target : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(target, packet);
        }
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
            player.sendMessage(Text.literal("\u00a7b[重力] 已发射 " + launched + " 个方块，模式="
                    + displayName(mode) + "，力=" + formatGravityMultiplier(gravity)), true);
        } else {
            player.sendMessage(Text.literal("\u00a7c[重力] 无可挪动方块"), true);
        }
        return launched;
    }

    public static int activateAreaGravity(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        double gravity = getSelectedGravity(player);
        addAreaGravity(world, origin, AREA_RADIUS, DEFAULT_AREA_HEIGHT, AREA_DURATION_TICKS, gravity);
        player.sendMessage(Text.literal("\u00a7b[重力] 已创建反转行走区域，半径=" + AREA_RADIUS
                + " 高度=" + DEFAULT_AREA_HEIGHT + " 持续=" + AREA_DURATION_TICKS + "游戏刻"), true);
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
        player.sendMessage(Text.literal("\u00a7e[重力调试] 已放置 " + describeSpec(spec) + " 于 " + center.toShortString()), true);
    }

    private static void moveNearestDebugArea(ServerPlayerEntity player, ServerWorld world, BlockPos center) {
        ensureServerAreasLoaded(world.getServer());
        AreaGravityField field = nearestArea(world, player.getPos());
        if (field == null) {
            player.sendMessage(Text.literal("\u00a7c[重力调试] 没有可移动的区域"), true);
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
        player.sendMessage(Text.literal("\u00a7e[重力调试] 已移动最近的区域"), true);
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
            player.sendMessage(Text.literal("\u00a7c[重力调试] 选区内没有区域"), true);
            return;
        }

        saveServerAreas(world.getServer());
        pushDebugEdit(player, new AreaEdit(List.of(), removed));
        player.sendMessage(Text.literal("\u00a7e[重力调试] 已删除 " + removed.size() + " 个区域"), true);
    }

    private static void undoDebugAreaEdit(ServerPlayerEntity player, ServerWorld world) {
        AreaEdit edit = popEdit(DEBUG_UNDO, player.getUuid());
        if (edit == null) {
            player.sendMessage(Text.literal("\u00a7c[重力调试] 没有可撤销的操作"), true);
            return;
        }
        applyInverseEdit(world, edit);
        pushEdit(DEBUG_REDO, player.getUuid(), edit);
        player.sendMessage(Text.literal("\u00a7e[重力调试] 已撤销"), true);
    }

    private static void redoDebugAreaEdit(ServerPlayerEntity player, ServerWorld world) {
        AreaEdit edit = popEdit(DEBUG_REDO, player.getUuid());
        if (edit == null) {
            player.sendMessage(Text.literal("\u00a7c[重力调试] 没有可重做的操作"), true);
            return;
        }
        applyEdit(world, edit);
        pushEdit(DEBUG_UNDO, player.getUuid(), edit);
        player.sendMessage(Text.literal("\u00a7e[重力调试] 已重做"), true);
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
        if (state.isAir() || !state.getFluidState().isEmpty() || isGravityImmune(state)) {
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

    private static void startSelfForceMotion(ServerPlayerEntity player, Vec3d direction) {
        UUID uuid = player.getUuid();
        DirectedEntityGravity directed = ENTITY_GRAVITY.remove(uuid);
        if (directed != null) {
            finishDirectedEntityGravity(player, directed);
        }
        SELF_FORCE_RECOVERY_TICKS.remove(uuid);
        SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
        SERVER_INVERTED_PLAYER_STATES.remove(uuid);
        player.setNoGravity(false);
        double force = getSelectedGravity(player);
        int ticks = GravityConfig.getInstance().getForceDurationTicks();
        SELF_FORCE_MOTIONS.put(uuid, new SelfForceMotion(player.getWorld().getRegistryKey(), direction, force, ticks, player.getVelocity()));
        syncClearDirectedEntityGravity(player);
        player.sendMessage(Text.literal("\u00a7b[重力] 自身受力，力=" + formatGravityMultiplier(force)), true);
    }

    private static void tickSelfForceMotions(MinecraftServer server) {
        for (Map.Entry<UUID, SelfForceMotion> entry : SELF_FORCE_MOTIONS.entrySet()) {
            UUID uuid = entry.getKey();
            SelfForceMotion motion = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || !player.isAlive() || motion.ticks() <= 0
                    || !motion.world().equals(player.getWorld().getRegistryKey()) || !hasEnergy(player)) {
                SELF_FORCE_MOTIONS.remove(uuid, motion);
                SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
                continue;
            }

            damageSelfForceImpact(player, motion);
            player.setNoGravity(false);
            Vec3d velocity = player.getVelocity().add(motion.direction().multiply(motion.acceleration()));
            velocity = clampSpeed(velocity, SELF_FORCE_MAX_SPEED);
            velocity = new Vec3d(velocity.x * SELF_FORCE_HORIZONTAL_DAMPING, velocity.y, velocity.z * SELF_FORCE_HORIZONTAL_DAMPING);
            if (shouldBeginLandingRecovery(player, velocity)) {
                SELF_FORCE_MOTIONS.remove(uuid, motion);
                SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
                beginSelfForceRecovery(player, velocity);
                continue;
            }
            player.setVelocity(velocity);
            player.velocityModified = true;
            player.fallDistance = 0.0F;

            SelfForceMotion ticked = motion.withPreviousVelocity(velocity).ticked();
            if (ticked.ticks() <= 0) {
                SELF_FORCE_MOTIONS.remove(uuid, motion);
                SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
            } else {
                SELF_FORCE_MOTIONS.replace(uuid, motion, ticked);
            }
        }
    }

    private static void damageSelfForceImpact(ServerPlayerEntity player, SelfForceMotion motion) {
        if (!(player.getWorld() instanceof ServerWorld world) || !player.horizontalCollision) {
            return;
        }

        UUID uuid = player.getUuid();
        if (SELF_FORCE_IMPACT_COOLDOWNS.containsKey(uuid)) {
            return;
        }

        Vec3d previousVelocity = motion.previousVelocity();
        Vec3d currentVelocity = player.getVelocity();
        double previousHorizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
        double currentHorizontalSpeed = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
        double speedLoss = previousHorizontalSpeed - currentHorizontalSpeed;
        if (speedLoss <= SELF_FORCE_GLIDE_COLLISION_MIN_SPEED_LOSS) {
            return;
        }

        float damage = (float) (speedLoss * 10.0D - 3.0D);
        if (damage <= 0.0F) {
            return;
        }

        if (player.damage(world, world.getDamageSources().flyIntoWall(), damage)) {
            SELF_FORCE_IMPACT_COOLDOWNS.put(uuid, SELF_FORCE_IMPACT_COOLDOWN_TICKS);
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.multiply(0.35D));
            player.velocityModified = true;
        }
    }

    private static void tickNaturalLandingRecovery(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Vec3d velocity = player.getVelocity();
            if (!shouldBeginLandingRecovery(player, velocity)) {
                continue;
            }
            DirectedEntityGravity directed = ENTITY_GRAVITY.remove(player.getUuid());
            if (directed != null) {
                player.setNoGravity(directed.previousNoGravity());
            }
            SELF_FORCE_MOTIONS.remove(player.getUuid());
            SELF_FORCE_IMPACT_COOLDOWNS.remove(player.getUuid());
            beginSelfForceRecovery(player, velocity);
        }
    }

    private static boolean shouldBeginLandingRecovery(ServerPlayerEntity player, Vec3d nextVelocity) {
        if (player == null || nextVelocity == null || player.isOnGround() || !player.isAlive()) {
            return false;
        }
        if (!isHoldingGravityWand(player)) {
            return false;
        }
        if (player.isSpectator() || player.getAbilities().flying || player.isTouchingWater() || player.isInLava()) {
            return false;
        }
        if (SELF_FORCE_RECOVERY_TICKS.containsKey(player.getUuid()) || hasNonNormalSurfaceGravity(player)) {
            return false;
        }

        double fallSpeed = -nextVelocity.y;
        if (fallSpeed < SELF_FORCE_LANDING_MIN_FALL_SPEED) {
            return false;
        }

        double probeDistance = Math.clamp(
                fallSpeed * SELF_FORCE_LANDING_SPEED_LOOKAHEAD + 0.35D,
                SELF_FORCE_LANDING_MIN_PROBE_DISTANCE,
                SELF_FORCE_LANDING_MAX_PROBE_DISTANCE
        );
        return hasLandingCollisionBelow(player, probeDistance);
    }

    private static boolean hasLandingCollisionBelow(ServerPlayerEntity player, double distance) {
        if (!(player.getWorld() instanceof ServerWorld world) || distance <= 0.0D) {
            return false;
        }

        Box box = player.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double halfX = Math.max(0.0D, (box.maxX - box.minX) * 0.5D - SELF_FORCE_LANDING_EDGE_PADDING);
        double halfZ = Math.max(0.0D, (box.maxZ - box.minZ) * 0.5D - SELF_FORCE_LANDING_EDGE_PADDING);
        double startY = box.minY + 0.04D;

        return raycastLandingCollision(world, player, centerX, startY, centerZ, distance)
                || raycastLandingCollision(world, player, centerX - halfX, startY, centerZ - halfZ, distance)
                || raycastLandingCollision(world, player, centerX - halfX, startY, centerZ + halfZ, distance)
                || raycastLandingCollision(world, player, centerX + halfX, startY, centerZ - halfZ, distance)
                || raycastLandingCollision(world, player, centerX + halfX, startY, centerZ + halfZ, distance);
    }

    private static boolean raycastLandingCollision(ServerWorld world, ServerPlayerEntity player, double x, double y, double z, double distance) {
        Vec3d start = new Vec3d(x, y, z);
        Vec3d end = new Vec3d(x, y - distance, z);
        BlockHitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static void tickPlayerGravityEnergy(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            double energy = getEnergy(player);
            int stage = gravityStage(player);
            GravityConfig config = GravityConfig.getInstance();
            boolean selfForceActive = SELF_FORCE_MOTIONS.containsKey(uuid);
            boolean extractingActive = EXTRACTING_BLOCK_GROUPS.containsKey(uuid);
            boolean heldActive = HELD_BLOCK_GROUPS.containsKey(uuid);
            Direction surfaceDirection = SERVER_SURFACE_GRAVITY_PLAYERS.get(uuid);
            double drain = 0.0D;
            if (selfForceActive) {
                drain += config.getSelfForceDrain(stage) / 20.0D;
            }
            if (extractingActive) {
                drain += config.getBlockExtractDrain(stage) / 20.0D;
            }
            if (heldActive) {
                drain += config.getBlockHoldDrain(stage) / 20.0D;
            }
            drain += surfaceMovementDrain(player, surfaceDirection, config.getSurfaceMoveDrainPerBlock());
            double regen = drain > 0.0D ? 0.0D : GravityConfig.getInstance().getEnergyRegen(stage) / 20.0D;
            double next = Math.clamp(energy + regen - drain, 0.0D, MAX_GRAVITY_ENERGY);
            PLAYER_ENERGY.put(uuid, next);
            if (drain > 0.0D && next <= 0.0D) {
                DirectedEntityGravity directed = ENTITY_GRAVITY.remove(uuid);
                if (directed != null) {
                    finishDirectedEntityGravity(player, directed);
                }
                SELF_FORCE_MOTIONS.remove(uuid);
                SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
                clearExtractingBlockGroup(uuid, true);
                clearHeldBlockGroup(uuid, true);
                syncExtractPose(player, 0);
                removeExtractingSpeedPenalty(player);
                if (surfaceDirection != null) {
                    clearSurfaceGravity(player);
                }
                player.sendMessage(Text.literal("\u00a7c[重力] 能量已耗尽"), true);
            }
            int syncTicks = ENERGY_SYNC_TICKS.merge(uuid, 1, Integer::sum);
            if (syncTicks >= ENERGY_SYNC_INTERVAL_TICKS || Math.abs(next - energy) > 0.5D) {
                ENERGY_SYNC_TICKS.put(uuid, 0);
                syncEnergyTo(player);
            }
        }
    }

    private static double surfaceMovementDrain(ServerPlayerEntity player, Direction surfaceDirection, float drainPerBlock) {
        if (player == null || surfaceDirection == null || surfaceDirection == Direction.DOWN || drainPerBlock <= 0.0F) {
            if (player != null) {
                SURFACE_DISTANCE_ANCHORS.remove(player.getUuid());
            }
            return 0.0D;
        }

        UUID uuid = player.getUuid();
        SurfaceDistanceAnchor current = new SurfaceDistanceAnchor(player.getWorld().getRegistryKey(), surfaceDirection, player.getPos());
        SurfaceDistanceAnchor previous = SURFACE_DISTANCE_ANCHORS.put(uuid, current);
        if (previous == null || previous.direction() != surfaceDirection || !previous.world().equals(current.world())) {
            return 0.0D;
        }

        double distance = surfacePlaneDistance(previous.pos(), current.pos(), surfaceDirection);
        if (!Double.isFinite(distance) || distance <= 1.0E-6D) {
            return 0.0D;
        }
        return distance * drainPerBlock;
    }

    private static double surfacePlaneDistance(Vec3d from, Vec3d to, Direction downDirection) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        return switch (downDirection.getAxis()) {
            case X -> Math.sqrt(dy * dy + dz * dz);
            case Y -> Math.sqrt(dx * dx + dz * dz);
            case Z -> Math.sqrt(dx * dx + dy * dy);
        };
    }

    private static void beginSelfForceRecovery(ServerPlayerEntity player) {
        beginSelfForceRecovery(player, player == null ? Vec3d.ZERO : player.getVelocity());
    }

    private static void beginSelfForceRecovery(ServerPlayerEntity player, Vec3d entryVelocity) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        if (!isHoldingGravityWand(player)) {
            SELF_FORCE_RECOVERY_TICKS.remove(uuid);
            player.setNoGravity(false);
            player.fallDistance = 0.0F;
            syncClearDirectedEntityGravity(player);
            return;
        }
        SELF_FORCE_RECOVERY_TICKS.put(uuid, 80);
        SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
        SERVER_INVERTED_PLAYER_STATES.remove(uuid);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        Vec3d velocity = entryVelocity == null ? player.getVelocity() : entryVelocity;
        double recoveryY = Math.clamp(velocity.y, -0.08D, -0.04D);
        player.setVelocity(velocity.x * 0.35D, recoveryY, velocity.z * 0.35D);
        player.velocityModified = true;
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getBoundingBox().minY + 0.08D, player.getZ(),
                    18, 0.28D, 0.04D, 0.28D, 0.035D);
        }
        syncClearDirectedEntityGravity(player);
    }

    private static void tickSelfForceRecovery(MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : SELF_FORCE_RECOVERY_TICKS.entrySet()) {
            UUID uuid = entry.getKey();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                SELF_FORCE_RECOVERY_TICKS.remove(uuid, entry.getValue());
                continue;
            }
            if (!isHoldingGravityWand(player)) {
                SELF_FORCE_RECOVERY_TICKS.remove(uuid, entry.getValue());
                player.setNoGravity(false);
                player.fallDistance = 0.0F;
                syncClearDirectedEntityGravity(player);
                continue;
            }
            ENTITY_GRAVITY.remove(uuid);
            SERVER_INVERTED_PLAYER_STATES.remove(uuid);
            player.setNoGravity(false);
            player.fallDistance = 0.0F;
            Vec3d velocity = player.getVelocity();
            double recoveryY = Math.clamp(velocity.y, -0.08D, -0.04D);
            if (Math.abs(recoveryY - velocity.y) > 1.0E-5D) {
                player.setVelocity(velocity.x * 0.75D, recoveryY, velocity.z * 0.75D);
                player.velocityModified = true;
            }
            syncClearDirectedEntityGravity(player);
            int next = entry.getValue() - 1;
            if (player.isOnGround() || next <= 0) {
                SELF_FORCE_RECOVERY_TICKS.remove(uuid, entry.getValue());
            } else {
                SELF_FORCE_RECOVERY_TICKS.replace(uuid, entry.getValue(), next);
            }
        }
    }

    private static void tickNonNormalFallReset(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!isNonNormalFalling(player)) {
                NON_NORMAL_FALL_TICKS.remove(uuid);
                continue;
            }

            int ticks = NON_NORMAL_FALL_TICKS.merge(uuid, 1, Integer::sum);
            if (ticks < NON_NORMAL_FALL_RESET_TICKS) {
                continue;
            }

            resetPlayerToNormalGravity(player);
            NON_NORMAL_FALL_TICKS.remove(uuid);
            player.sendMessage(Text.literal("§c[重力] 非正常下落过久，将回归正常"), true);
        }
    }

    private static boolean isNonNormalFalling(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isOnGround()) {
            return false;
        }
        if (player.isSpectator() || player.getAbilities().flying || player.isTouchingWater() || player.isInLava() || player.hasVehicle()) {
            return false;
        }

        UUID uuid = player.getUuid();
        Direction surfaceDirection = SERVER_SURFACE_GRAVITY_PLAYERS.get(uuid);
        if (surfaceDirection != null && surfaceDirection != Direction.DOWN) {
            return isFallingAlong(player, SurfaceGravityBasis.directionVector(surfaceDirection));
        }

        if (ENTITY_GRAVITY.containsKey(uuid) || SELF_FORCE_MOTIONS.containsKey(uuid)) {
            return player.getVelocity().y < -NON_NORMAL_FALL_MIN_SPEED;
        }
        return false;
    }

    private static boolean isFallingAlong(ServerPlayerEntity player, Vec3d down) {
        if (down == null || down.lengthSquared() <= 1.0E-8D) {
            return false;
        }
        return player.getVelocity().dotProduct(down.normalize()) > NON_NORMAL_FALL_MIN_SPEED;
    }

    private static void resetPlayerToNormalGravity(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        ENTITY_GRAVITY.remove(uuid);
        SELF_FORCE_MOTIONS.remove(uuid);
        SELF_FORCE_RECOVERY_TICKS.remove(uuid);
        SELF_FORCE_IMPACT_COOLDOWNS.remove(uuid);
        SERVER_INVERTED_PLAYER_STATES.remove(uuid);
        clearSurfaceGravity(player);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        syncClearDirectedEntityGravity(player);
    }

    private static void tickExtractingBlockGroups(MinecraftServer server) {
        for (Map.Entry<UUID, ExtractingBlockGroup> entry : EXTRACTING_BLOCK_GROUPS.entrySet()) {
            UUID ownerUuid = entry.getKey();
            ExtractingBlockGroup group = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerUuid);
            if (player == null || !player.isAlive() || !isHoldingGravityWand(player)
                    || !group.world.equals(player.getWorld().getRegistryKey())) {
                clearExtractingBlockGroup(ownerUuid, true);
                if (player != null) {
                    syncExtractPose(player, 0);
                    removeExtractingSpeedPenalty(player);
                }
                continue;
            }
            applyExtractingSpeedPenalty(player);
            group.age++;
            spawnDueExtractingBlocks(worldFor(server, group.world), player, group);
            group.blocks.removeIf(held -> held.entity() == null || !held.entity().isAlive());
            if (group.blocks.isEmpty() && group.pendingBlocks.isEmpty()) {
                EXTRACTING_BLOCK_GROUPS.remove(ownerUuid, group);
                syncExtractPose(player, 0);
                removeExtractingSpeedPenalty(player);
                continue;
            }
            boolean complete = group.age >= group.totalTicks && group.pendingBlocks.isEmpty();
            for (HeldBlock held : group.blocks) {
                if (held.entity().isExtracting()) {
                    complete = false;
                    break;
                }
            }
            if (!complete) {
                continue;
            }
            EXTRACTING_BLOCK_GROUPS.remove(ownerUuid, group);
            HeldBlockGroup heldGroup = new HeldBlockGroup(group.world, group.blocks, group.massKg, player.getWorld());
            HELD_BLOCK_GROUPS.put(ownerUuid, heldGroup);
            syncExtractPose(player, 0);
            removeExtractingSpeedPenalty(player);
            player.sendMessage(Text.literal("\u00a7b[重力] 方块汇聚完成"), true);
        }
    }

    private static ServerWorld worldFor(MinecraftServer server, RegistryKey<World> key) {
        return server.getWorld(key);
    }

    private static void spawnDueExtractingBlocks(ServerWorld world, ServerPlayerEntity player, ExtractingBlockGroup group) {
        if (world == null || player == null || group.pendingBlocks.isEmpty()) {
            return;
        }
        int flyTicks = group.flyTicks;
        int spawned = 0;
        Iterator<ExtractingBlockPlan> iterator = group.pendingBlocks.iterator();
        while (iterator.hasNext()) {
            ExtractingBlockPlan plan = iterator.next();
            if (plan.spawnTick() > group.age) {
                continue;
            }
            if (spawned >= MAX_BLOCK_GROUP_SPAWNS_PER_TICK) {
                break;
            }
            GravityBlockEntity block = new GravityBlockEntity(
                    world,
                    plan.animationStart().x,
                    plan.animationStart().y,
                    plan.animationStart().z,
                    plan.animationState(),
                    Vec3d.ZERO,
                    0.0D
            );
            block.setOwnerUuid(player.getUuid());
            block.setGravityY(0.0D);
            block.setNoGravity(true);
            block.setTemporary(group.totalTicks + HELD_BLOCK_LIFETIME_TICKS);
            block.setPlaceOrDropOnSettle(false);
            block.setExtractionTarget(plan.target(), 0, flyTicks);
            block.setRenderGroup(heldBlockAnchor(player, group.sphereRadius), Math.max(1.0D, group.sphereRadius + 0.75D));
            block.setRenderGroupOwnerId(player.getId());
            block.setSlowFreeSpin(
                    (float) signedAngularVelocity(world, 0.18D, 0.58D),
                    (float) signedAngularVelocity(world, 0.22D, 0.66D),
                    (float) signedAngularVelocity(world, 0.16D, 0.50D)
            );
            if (world.spawnEntity(block)) {
                group.blocks.add(new HeldBlock(block, plan.offset(), plan.massKg(), plan.hardness()));
                spawned++;
            }
            iterator.remove();
        }
    }

    private static void tickHeldBlockGroups(MinecraftServer server) {
        for (Map.Entry<UUID, HeldBlockGroup> entry : HELD_BLOCK_GROUPS.entrySet()) {
            UUID ownerUuid = entry.getKey();
            HeldBlockGroup group = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerUuid);
            if (player == null || !player.isAlive() || !isHoldingGravityWand(player)
                    || !group.world.equals(player.getWorld().getRegistryKey())) {
                clearHeldBlockGroup(ownerUuid, true);
                continue;
            }

            group.tickAngles();
            if (group.age > HELD_BLOCK_LIFETIME_TICKS) {
                clearHeldBlockGroup(ownerUuid, true);
                player.sendMessage(Text.literal("\u00a7c[重力] 方块群已消散"), true);
                continue;
            }

            Vec3d anchor = heldBlockAnchor(player, group.sphereRadius);
            Iterator<HeldBlock> iterator = group.blocks.iterator();
            while (iterator.hasNext()) {
                HeldBlock held = iterator.next();
                GravityBlockEntity block = held.entity();
                if (block == null || !block.isAlive()) {
                    iterator.remove();
                    continue;
                }
                Vec3d position = anchor.add(rotateOffset(held.offset(), group.pitch, group.yaw, group.roll));
                block.refreshPositionAndAngles(position.x, position.y, position.z, block.getYaw(), block.getPitch());
                block.setVelocity(Vec3d.ZERO);
                block.setNoGravity(true);
                block.setGravityY(0.0D);
                block.fallDistance = 0.0F;
            }

            if (group.blocks.isEmpty()) {
                HELD_BLOCK_GROUPS.remove(ownerUuid, group);
            }
        }
    }

    private static void clearHeldBlockGroup(UUID ownerUuid, boolean discard) {
        HeldBlockGroup group = HELD_BLOCK_GROUPS.remove(ownerUuid);
        if (group == null || !discard) {
            return;
        }
        for (HeldBlock held : group.blocks) {
            GravityBlockEntity block = held.entity();
            if (block != null && block.isAlive()) {
                block.discard();
            }
        }
    }

    private static void applyExtractingSpeedPenalty(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(EXTRACT_SPEED_MODIFIER_ID);
        speed.addTemporaryModifier(new EntityAttributeModifier(
                EXTRACT_SPEED_MODIFIER_ID,
                EXTRACT_SPEED_MULTIPLIER - 1.0D,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void removeExtractingSpeedPenalty(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(EXTRACT_SPEED_MODIFIER_ID);
        }
    }

    private static void broadcastQuakeHint(ServerPlayerEntity caster) {
        MinecraftServer server = caster.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (target.getWorld() != caster.getWorld()) {
                continue;
            }
            ServerPlayNetworking.send(target, new GravityPackets.QuakeHintS2C(quakeDirection(caster, target), QUAKE_HINT_TICKS));
        }
    }

    private static String quakeDirection(ServerPlayerEntity caster, ServerPlayerEntity target) {
        Vec3d toCaster = caster.getPos().subtract(target.getPos());
        Vec3d flat = new Vec3d(toCaster.x, 0.0D, toCaster.z);
        if (flat.lengthSquared() < 1.0E-6D) {
            return "脚下";
        }
        Vec3d look = target.getRotationVec(1.0F);
        Vec3d forward = new Vec3d(look.x, 0.0D, look.z);
        if (forward.lengthSquared() < 1.0E-6D) {
            forward = Vec3d.fromPolar(0.0F, target.getYaw());
            forward = new Vec3d(forward.x, 0.0D, forward.z);
        }
        forward = forward.normalize();
        Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
        Vec3d dir = flat.normalize();
        double angle = Math.atan2(dir.dotProduct(right), dir.dotProduct(forward));
        int sector = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0D)), 8);
        return switch (sector) {
            case 0 -> "正前方";
            case 1 -> "右前方";
            case 2 -> "右方";
            case 3 -> "右后方";
            case 4 -> "正后方";
            case 5 -> "左后方";
            case 6 -> "左方";
            default -> "左前方";
        };
    }

    private static void clearExtractingBlockGroup(UUID ownerUuid, boolean discard) {
        ExtractingBlockGroup group = EXTRACTING_BLOCK_GROUPS.remove(ownerUuid);
        if (group == null || !discard) {
            return;
        }
        for (HeldBlock held : group.blocks) {
            GravityBlockEntity block = held.entity();
            if (block != null && block.isAlive()) {
                block.discard();
            }
        }
    }

    private static void cleanupPlayerGravity(ServerPlayerEntity player, boolean discardBlocks) {
        if (player == null) {
            return;
        }
        UUID ownerUuid = player.getUuid();
        SELF_FORCE_MOTIONS.remove(ownerUuid);
        SELF_FORCE_RECOVERY_TICKS.remove(ownerUuid);
        SELF_FORCE_IMPACT_COOLDOWNS.remove(ownerUuid);
        NON_NORMAL_FALL_TICKS.remove(ownerUuid);
        SURFACE_DISTANCE_ANCHORS.remove(ownerUuid);
        clearExtractingBlockGroup(ownerUuid, discardBlocks);
        clearHeldBlockGroup(ownerUuid, discardBlocks);
        for (ServerWorld world : player.getServer().getWorlds()) {
            for (GravityBlockEntity block : world.getEntitiesByClass(GravityBlockEntity.class, new Box(-30000000, -2048, -30000000, 30000000, 4096, 30000000),
                    block -> ownerUuid.equals(block.getOwnerUuid()))) {
                if (discardBlocks) {
                    block.discard();
                }
            }
        }
        syncExtractPose(player, 0);
        removeExtractingSpeedPenalty(player);
    }

    private static List<Integer> stagedExtractionDelays(int count, int totalTicks) {
        List<Integer> delays = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return delays;
        }
        if (count == 1 || totalTicks <= 1) {
            delays.add(0);
            return delays;
        }
        int flyTicks = Math.max(1, totalTicks / 2);
        int maxDelay = Math.max(0, totalTicks - flyTicks);
        int[] weights = {1, 3, 5, 3, 1};
        int assigned = 0;
        for (int stage = 0; stage < weights.length; stage++) {
            int stageCount;
            if (stage == weights.length - 1) {
                stageCount = count - assigned;
            } else {
                stageCount = (int) Math.round(count * (weights[stage] / 13.0D));
                stageCount = Math.clamp(stageCount, 0, count - assigned);
            }
            int stageStart = Math.round(maxDelay * (stage / 5.0F));
            int stageEnd = Math.round(maxDelay * ((stage + 1) / 5.0F));
            for (int i = 0; i < stageCount; i++) {
                double local = stageCount <= 1 ? 0.5D : (i + 0.5D) / stageCount;
                delays.add(Math.clamp((int) Math.round(stageStart + (stageEnd - stageStart) * local), 0, maxDelay));
            }
            assigned += stageCount;
        }
        delays.sort(Integer::compareTo);
        return delays;
    }

    public static double getEnergy(ServerPlayerEntity player) {
        if (player == null) {
            return MAX_GRAVITY_ENERGY;
        }
        return PLAYER_ENERGY.computeIfAbsent(player.getUuid(), ignored -> MAX_GRAVITY_ENERGY);
    }

    private static boolean consumeEnergy(ServerPlayerEntity player, double amount) {
        if (player == null || amount <= 0.0D || player.isCreative()) {
            syncEnergyTo(player);
            return true;
        }
        double energy = getEnergy(player);
        if (energy + 1.0E-6D < amount) {
            syncEnergyTo(player);
            return false;
        }
        PLAYER_ENERGY.put(player.getUuid(), Math.max(0.0D, energy - amount));
        syncEnergyTo(player);
        return true;
    }

    private static boolean hasEnergy(ServerPlayerEntity player) {
        return player == null || player.isCreative() || getEnergy(player) > 0.0D;
    }

    private static void syncEnergyTo(ServerPlayerEntity player) {
        if (player != null) {
            ServerPlayNetworking.send(player, new GravityPackets.EnergyS2C(getEnergy(player), MAX_GRAVITY_ENERGY));
        }
    }

    private static void syncExtractPose(ServerPlayerEntity player, int ticks) {
        if (player == null || player.getServer() == null) {
            return;
        }
        GravityPackets.ExtractPoseS2C packet = new GravityPackets.ExtractPoseS2C(player.getId(), Math.max(0, ticks));
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    private static void tickThrownDamageCooldowns() {
        THROWN_DAMAGE_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= 1);
        THROWN_DAMAGE_COOLDOWNS.replaceAll((key, ticks) -> ticks - 1);
    }

    private static void tickSelfForceImpactCooldowns() {
        SELF_FORCE_IMPACT_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= 1);
        SELF_FORCE_IMPACT_COOLDOWNS.replaceAll((key, ticks) -> ticks - 1);
    }

    private static void tickThrownBlocks(ServerWorld world) {
        for (Map.Entry<UUID, ThrownBlockData> entry : THROWN_BLOCKS.entrySet()) {
            UUID blockUuid = entry.getKey();
            ThrownBlockData data = entry.getValue();
            if (!data.world.equals(world.getRegistryKey())) {
                continue;
            }

            Entity entity = world.getEntity(blockUuid);
            if (!(entity instanceof GravityBlockEntity block) || !block.isAlive()) {
                THROWN_BLOCKS.remove(blockUuid, data);
                if (THROWN_BLOCKS.values().stream().noneMatch(other -> other.groupId.equals(data.groupId))) {
                    THROWN_GROUP_IMPACTS.remove(data.groupId);
                }
                continue;
            }

            if (data.forceTicks > 0 && data.acceleration > 0.0D) {
                block.setVelocity(clampSpeed(block.getVelocity().add(data.forceDirection.multiply(data.acceleration)), THROW_MAX_SPEED));
                data.forceTicks--;
            }

            if ((block.isOnGround() || block.verticalCollision) && THROWN_GROUP_IMPACTS.add(data.groupId)) {
                spawnThrownBlockImpactParticles(world, block.getPos(), data);
            }
            damageThrownBlockTargets(world, block, data);
        }
    }

    private static void spawnThrownBlockImpactParticles(ServerWorld world, Vec3d pos, ThrownBlockData data) {
        double radius = Math.clamp(1.5D + Math.sqrt(Math.max(1.0D, data.groupMassKg)) * 0.12D, 2.0D, 8.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.15D, pos.z, 4, radius * 0.16D, 0.08D, radius * 0.16D, 0.02D);
        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y + 0.1D, pos.z, 80, radius * 0.55D, 0.18D, radius * 0.55D, 0.08D);
        world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.08D, pos.z, 48, radius * 0.45D, 0.04D, radius * 0.45D, 0.05D);
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2.0D * i / 64.0D;
            double r = radius * (0.35D + 0.65D * ((i % 8) / 7.0D));
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.08D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void damageThrownBlockTargets(ServerWorld world, GravityBlockEntity block, ThrownBlockData data) {
        Vec3d velocity = block.getVelocity();
        double speedMetersPerSecond = velocity.length() * 20.0D;
        if (speedMetersPerSecond < 1.0D || data.groupMassKg <= 0.0D) {
            return;
        }

        float damage = (float) (kineticDamage(data.groupMassKg, speedMetersPerSecond) * Math.max(0.0D, data.averageHardness));
        if (damage <= 0.0F) {
            return;
        }
        damage = Math.max(1.0F, damage);

        Box damageBox = block.getBoundingBox().expand(THROW_DAMAGE_PADDING);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, damageBox,
                target -> target.isAlive()
                        && !target.getUuid().equals(data.ownerUuid)
                        && !(target instanceof PlayerEntity targetPlayer && targetPlayer.isSpectator()));
        for (LivingEntity target : targets) {
            String cooldownKey = data.groupId + ":" + target.getUuid();
            if (THROWN_DAMAGE_COOLDOWNS.containsKey(cooldownKey)) {
                continue;
            }
            if (target.damage(world, world.getDamageSources().fallingBlock(block), damage)) {
                THROWN_DAMAGE_COOLDOWNS.put(cooldownKey, THROW_DAMAGE_COOLDOWN_TICKS);
                block.setVelocity(block.getVelocity().multiply(0.65D));
            }
        }
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
        if (entity != null && SELF_FORCE_MOTIONS.containsKey(entity.getUuid())) {
            entity.setNoGravity(false);
            return false;
        }
        if (isRecoveringDirectedPlayer(entity)) {
            tickClientDirectedRecovery(entity);
            entity.setNoGravity(false);
            return false;
        }
        if (shouldIgnoreInvertedPull(entity)) {
            resetInvertedPlayerState(entity);
            return false;
        }
        if (hasSurfaceGravity(entity)) {
            entity.setNoGravity(true);
            entity.fallDistance = 0.0D;
            return true;
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
        if (entity != null && SELF_FORCE_MOTIONS.containsKey(entity.getUuid())) {
            entity.setNoGravity(false);
            return false;
        }
        if (isRecoveringDirectedPlayer(entity)) {
            tickClientDirectedRecovery(entity);
            ENTITY_GRAVITY.remove(entity.getUuid());
            invertedPlayerStates(entity).remove(entity.getUuid());
            entity.setNoGravity(false);
            return false;
        }
        if (entity != null && input != null) {
            DirectedEntityGravity directed = ENTITY_GRAVITY.get(entity.getUuid());
            if (directed != null) {
                return tickDirectedPlayer(entity, input, directed);
            }
            if (getSurfaceGravityDirection(entity) == null) {
                initializeNormalSurfaceGravity(entity);
            }
            Direction surfaceDirection = getSurfaceGravityDirection(entity);
            if (surfaceDirection != null) {
                SurfaceGravityEngine.SurfaceState surfaceState = surfacePlayerStates(entity).computeIfAbsent(
                        entity.getUuid(),
                        uuid -> new SurfaceGravityEngine.SurfaceState(surfaceDirection, entity.getYaw(), entity.getPitch())
                );
                surfaceState.setDownDirection(surfaceDirection);
                if (surfaceDirection == Direction.DOWN) {
                    surfaceState.setLook(entity.getYaw(), entity.getPitch());
                }
                boolean handled = SurfaceGravityEngine.tick(entity, input, surfaceState);
                Direction nextSurfaceDirection = surfaceState.downDirection();
                if (handled && nextSurfaceDirection != surfaceDirection) {
                    applySurfaceGravityTransition(entity, nextSurfaceDirection);
                }
                return handled;
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
        if (entity instanceof ServerPlayerEntity player && shouldBeginLandingRecovery(player, next)) {
            ENTITY_GRAVITY.remove(entity.getUuid(), directed);
            beginSelfForceRecovery(player, next);
            return true;
        }
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
        tickClientDirectedRecovery(entity);
        if (hasSurfaceGravity(entity)) {
            return;
        }
        if (entity != null && !isInInvertedArea(entity)) {
            resetInvertedPlayerState(entity);
        }
    }

    private static boolean isRecoveringDirectedPlayer(Entity entity) {
        if (entity == null) {
            return false;
        }
        UUID uuid = entity.getUuid();
        return SELF_FORCE_RECOVERY_TICKS.containsKey(uuid) || CLIENT_DIRECTED_RECOVERY_TICKS.containsKey(uuid);
    }

    private static void tickClientDirectedRecovery(Entity entity) {
        if (entity == null || !entity.getWorld().isClient()) {
            return;
        }
        UUID uuid = entity.getUuid();
        Integer ticks = CLIENT_DIRECTED_RECOVERY_TICKS.get(uuid);
        if (ticks == null) {
            return;
        }
        ENTITY_GRAVITY.remove(uuid);
        CLIENT_INVERTED_PLAYER_STATES.remove(uuid);
        entity.setNoGravity(false);
        Vec3d velocity = entity.getVelocity();
        double recoveryY = Math.clamp(velocity.y, -0.08D, -0.04D);
        if (Math.abs(recoveryY - velocity.y) > 1.0E-5D) {
            entity.setVelocity(velocity.x * 0.65D, recoveryY, velocity.z * 0.65D);
        }
        if (ticks <= 1 || entity.isOnGround()) {
            CLIENT_DIRECTED_RECOVERY_TICKS.remove(uuid);
        } else {
            CLIENT_DIRECTED_RECOVERY_TICKS.put(uuid, ticks - 1);
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

    private static boolean hasSurfaceGravity(Entity entity) {
        return entity != null && surfaceGravityPlayers(entity).containsKey(entity.getUuid());
    }

    public static boolean hasNonNormalSurfaceGravity(Entity entity) {
        Direction direction = getSurfaceGravityDirection(entity);
        return direction != null && direction != Direction.DOWN;
    }

    private static void initializeNormalSurfaceGravity(Entity entity) {
        if (!isSurfaceLogicMode(entity)
                || entity.isTouchingWater()
                || entity.isInLava()
                || entity.hasVehicle()
                || entity instanceof PlayerEntity player && player.getAbilities().flying) {
            return;
        }
        Vec3d worldLook = entity.getRotationVec(1.0F);
        SurfaceGravityBasis.LocalView localView = SurfaceGravityBasis.localView(Direction.DOWN, worldLook);
        surfaceGravityPlayers(entity).put(entity.getUuid(), Direction.DOWN);
        SurfaceGravityEngine.SurfaceState state = surfacePlayerStates(entity).computeIfAbsent(
                entity.getUuid(),
                uuid -> new SurfaceGravityEngine.SurfaceState(Direction.DOWN, localView.yaw(), localView.pitch())
        );
        state.setLook(localView.yaw(), localView.pitch());
        state.setDownDirection(Direction.DOWN);
        if (!entity.getWorld().isClient() && entity instanceof ServerPlayerEntity player) {
            syncSurfaceGravity(player);
        }
    }

    public static void clearClientSurfaceGravity(Entity entity) {
        if (entity == null) {
            return;
        }
        Direction oldDirection = CLIENT_SURFACE_GRAVITY_PLAYERS.get(entity.getUuid());
        Vec3d oldEye = oldDirection == null ? null : SurfaceGravityCollision.eyePosFromBox(entity, oldDirection, entity.getBoundingBox());
        CLIENT_SURFACE_GRAVITY_PLAYERS.remove(entity.getUuid());
        CLIENT_SURFACE_PLAYER_STATES.remove(entity.getUuid());
        entity.setNoGravity(false);
        entity.fallDistance = 0.0F;
        if (oldEye != null) {
            SurfaceGravityCollision.moveKeepingEye(entity, Direction.DOWN, oldEye);
        } else {
            SurfaceGravityCollision.restoreVanillaBox(entity);
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

    private static Map<UUID, SurfaceGravityEngine.SurfaceState> surfacePlayerStates(Entity entity) {
        return entity != null && entity.getWorld().isClient() ? CLIENT_SURFACE_PLAYER_STATES : SERVER_SURFACE_PLAYER_STATES;
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
        return hasSurfaceGravity(entity) && !shouldIgnoreInvertedPull(entity)
                || directed != null && !directed.expired() && directed.world().equals(entity.getWorld().getRegistryKey())
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

    private static double clampGravityForStage(ServerPlayerEntity player, double gravity) {
        double maxForce = gravityFromMultiplier(GravityConfig.getInstance().getMaxForce(gravityStage(player)));
        return Math.clamp(clampGravity(gravity), MIN_GRAVITY, Math.max(MIN_GRAVITY, maxForce));
    }

    public static double gravityFromMultiplier(double multiplier) {
        return clampGravity(Math.max(0.0D, multiplier) * WORLD_GRAVITY);
    }

    public static double gravityMultiplier(double gravity) {
        return WORLD_GRAVITY <= 0.0D ? 0.0D : clampGravity(gravity) / WORLD_GRAVITY;
    }

    public static String formatGravityMultiplier(double gravity) {
        return String.format("%.2fxG", gravityMultiplier(gravity));
    }

    public static String format(double gravity) {
        return String.format("%.3f", gravity);
    }

    private static String displayName(LaunchMode mode) {
        return mode == LaunchMode.UP ? "向上" : "向右";
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case DOWN -> "下";
            case UP -> "上";
            case NORTH -> "北";
            case SOUTH -> "南";
            case WEST -> "西";
            case EAST -> "东";
        };
    }

    private static String describeSpec(GravityAreaSpec spec) {
        return shapeName(spec.shape())
                + "/" + halfName(spec.half())
                + " X=" + spec.sizeX()
                + " Y=" + spec.sizeY()
                + " Z=" + spec.sizeZ();
    }

    private static String shapeName(GravityAreaSpec.Shape shape) {
        return switch (shape) {
            case SPHERE -> "球体";
            case BOX -> "长方体";
            case CUBE -> "立方体";
        };
    }

    private static String halfName(GravityAreaSpec.Half half) {
        return switch (half) {
            case FULL -> "完整";
            case UPPER -> "上半";
            case LOWER -> "下半";
        };
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

    public record SurfaceView(Direction downDirection, float localYaw, float localPitch) {
    }

}
