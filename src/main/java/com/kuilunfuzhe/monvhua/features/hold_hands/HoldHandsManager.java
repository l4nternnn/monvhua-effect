package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.mixin.ServerPlayNetworkHandlerAccessor;
import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsInputC2SPacket;
import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HoldHandsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HoldHandsManager.class);
    private static final double BODY_ROTATE_START_ANGLE = 110.0D;
    private static final double BODY_ROTATE_MAX_ANGLE = 135.0D;
    private static final float HOLD_BODY_YAW_STEP = 5.0F;
    private static final double LINK_SLACK = 0.015D;
    private static final double RIGID_POSITION_EPSILON = 0.015D;
    private static final double RIGID_TELEPORT_EPSILON = 0.85D;
    private static final double TAUT_FORCE_START = 0.92D;
    private static final double TAUT_TARGET_DISTANCE_BIAS = 0.985D;
    private static final double FOLLOW_POSITION_STIFFNESS = 1.35D;
    private static final double FOLLOW_MAX_CORRECTION_SPEED = 2.15D;
    private static final double TAUT_POSITION_STIFFNESS = 1.75D;
    private static final double TAUT_MAX_SPEED = 3.10D;
    private static final double MUTUAL_POSITION_STIFFNESS = 1.05D;
    private static final double MUTUAL_MAX_CORRECTION_SPEED = 1.75D;
    private static final double MUTUAL_RELATIVE_DAMPING = 0.72D;
    private static final double MUTUAL_TAUT_RELATIVE_DAMPING = 0.88D;
    private static final double MUTUAL_TELEPORT_DISTANCE = 4.0D;
    private static final double ANCHOR_POSITION_STIFFNESS = 0.42D;
    private static final double ANCHOR_VELOCITY_ALPHA = 0.58D;
    private static final double ANCHOR_MAX_HORIZONTAL_SPEED = 1.35D;
    private static final double ANCHOR_MAX_VERTICAL_SPEED = 0.55D;
    private static final double ANCHOR_MAX_ACCELERATION = 0.26D;
    private static final double ANCHOR_PLAYER_STIFFNESS = 0.62D;
    private static final double ANCHOR_PLAYER_MAX_CORRECTION_SPEED = 0.72D;
    private static final double ANCHOR_PLAYER_VERTICAL_STIFFNESS = 0.28D;
    private static final double ANCHOR_PLAYER_MAX_VERTICAL_CORRECTION_SPEED = 0.16D;
    private static final double ANCHOR_PLAYER_TELEPORT_DISTANCE = 3.25D;
    private static final double ANCHOR_WALK_INPUT_SPEED = 0.11D;
    private static final double ANCHOR_SPRINT_INPUT_SPEED = 0.16D;
    private static final double ANCHOR_SNEAK_INPUT_SPEED = 0.055D;
    private static final double ANCHOR_FLY_VERTICAL_INPUT_SPEED = 0.14D;
    private static final double ANCHOR_JUMP_INPUT_SPEED = 0.18D;
    private static final double PAIR_INTENT_FILTER_ALPHA = 0.32D;
    private static final double PAIR_CENTER_STIFFNESS = 0.18D;
    private static final double PAIR_CENTER_MAX_MEASURED_CORRECTION = 0.08D;
    private static final double PAIR_MAX_HORIZONTAL_SPEED = 0.30D;
    private static final double PAIR_MAX_VERTICAL_SPEED = 0.18D;
    private static final double PAIR_MAX_ACCELERATION = 0.09D;
    private static final double PAIR_POSITION_DEADBAND = 0.055D;
    private static final double PAIR_POSITION_STIFFNESS = 0.64D;
    private static final double PAIR_MAX_CORRECTION_SPEED = 0.82D;
    private static final double PAIR_VERTICAL_STIFFNESS = 0.30D;
    private static final double PAIR_MAX_VERTICAL_CORRECTION_SPEED = 0.18D;
    private static final double PAIR_TELEPORT_DISTANCE = 7.0D;
    private static final int PAIR_TELEPORT_CONFIRM_TICKS = 4;
    private static final int BOOTSTRAP_TICKS = 6;
    private static final double BOOTSTRAP_INTENT_ALPHA = 0.72D;
    private static final double NORMAL_INTENT_ALPHA = 0.42D;
    private static final double BOOTSTRAP_MAX_IMPULSE = 0.65D;
    private static final double NORMAL_MAX_IMPULSE = 0.45D;
    private static final double BOOTSTRAP_MAX_ACCELERATION = 0.18D;
    private static final double NORMAL_MAX_ACCELERATION = 0.12D;
    private static final double BOOTSTRAP_MAX_CENTER_SPEED = 0.40D;
    private static final double NORMAL_MAX_CENTER_SPEED = 0.30D;
    // These are per-tick gains. Values intended for a seconds-based solver
    // (for example k=7, c=4.2) are unstable when applied directly to velocity
    // every Minecraft tick.
    private static final double SPRING_STIFFNESS = 0.32D;
    private static final double SPRING_DAMPING = 0.95D;
    private static final double RELATIVE_INPUT_GAIN = 0.32D;
    private static final double MAX_RELATIVE_CORRECTION = 0.80D;
    private static final double MAX_RELATIVE_SPEED = 2.0D;
    private static final double MAX_STRETCH = 1.20D;
    private static final double RELATIVE_POSITION_DEADBAND = 0.012D;
    private static final double RELATIVE_SPEED_DEADBAND = 0.018D;
    private static final double CENTER_VELOCITY_DEADBAND = 0.006D;
    private static final double SETTLE_POSITION_EPSILON = 0.03D;
    private static final double SETTLE_SPEED_EPSILON = 0.03D;
    private static final double MAX_PATH_CHECK_DISTANCE = 8.0D;
    private static final double TENSION_SPEED_WEIGHT = 0.18D;
    private static final int STRAIN_CONFIRM_TICKS = 10;
    private static final int TELEPORT_WAIT_TIMEOUT_TICKS = 40;
    private static final int RECOVERY_TICKS = 3;
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final int MAX_INPUT_SEQUENCE_JUMP = 240;
    private static final int PAIR_HISTORY_TICKS = 40;
    private static final int PAIR_VERTICAL_CONFIRM_TICKS = 3;
    private static final double FOLLOW_POSITION_STEP_DEADBAND = 0.20D;
    private static final double TAUT_POSITION_STEP_DEADBAND = 0.08D;
    private static final double FOLLOW_POSITION_MIN_STEP = 0.08D;
    private static final double FOLLOW_POSITION_MAX_STEP = 0.44D;
    private static final double TAUT_POSITION_MAX_STEP = 0.62D;
    private static final double SHARED_POINT_DEADBAND = 0.018D;
    private static final double SHARED_POINT_GROUNDED_Y_DEADBAND = 0.035D;
    private static final double SHARED_POINT_GROUNDED_ALPHA = 0.16D;
    private static final double SHARED_POINT_AIRBORNE_ALPHA = 0.55D;
    private static final double SHARED_POINT_FAST_DISTANCE = 0.50D;
    private static final double GROUNDED_ENDPOINT_MAX_LIFT = 0.28D;
    private static final double GROUNDED_ENDPOINT_MAX_DROP = 0.28D;
    private static final double VERTICAL_LEAD_MIN_SPEED = 0.16D;
    private static final double VERTICAL_LEAD_MIN_HEIGHT = 0.62D;
    private static final int ANCHOR_DEBUG_INTERVAL_TICKS = 20;
    private static final double FOLLOW_YAW_PROGRESS_PER_BLOCK = 0.125D;
    private static final double FOLLOW_YAW_MOVE_DEADBAND = 0.003D;
    private static final float FOLLOW_YAW_MAX_STEP = 8.0F;

    private static final Map<UUID, HoldHandData> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, HoldAnchorState> ANCHORS = new ConcurrentHashMap<>();
    private static final Map<String, TeleportGuard> TELEPORT_GUARDS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RECOVERY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> INPUT_ARRIVAL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SNEAK_INPUT_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> INPUTS_THIS_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, InputSample> LATEST_INPUTS = new ConcurrentHashMap<>();
    private static final Map<String, ArrayDeque<HoldPairHistoryEntry>> PAIR_HISTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SYNC_SEQUENCES = new ConcurrentHashMap<>();
    private static final Map<UUID, ReachabilityCache> REACHABILITY_CACHE = new ConcurrentHashMap<>();
    private static long lastServerTickNanos;
    private static double tickIntervalEmaMs = 50.0D;
    private static double currentConstraintScale = 1.0D;

    private HoldHandsManager() {
    }

    public static boolean isHoldingHands(ServerPlayerEntity player) {
        return player != null && ACTIVE.containsKey(player.getUuid());
    }

    public static void togglePair(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        if (initiator == null || target == null || initiator == target) {
            return;
        }

        HoldHandData initiatorData = ACTIVE.get(initiator.getUuid());
        if (initiatorData != null && target.getUuid().equals(initiatorData.partnerUuid())) {
            stopPair(initiator);
            initiator.sendMessage(Text.literal("已解除牵手"), true);
            target.sendMessage(Text.literal("已解除牵手"), true);
            return;
        }

        stopPair(initiator);
        stopPair(target);
        startPair(initiator, target);
        initiator.sendMessage(Text.literal("已与 " + target.getName().getString() + " 牵手"), true);
        target.sendMessage(Text.literal("已与 " + initiator.getName().getString() + " 牵手"), true);
    }

    private static void startPair(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        float holdBodyYaw = initiator.getBodyYaw();
        double defaultDistance = Math.max(0.001D, HoldHandsLinkGeometry.horizontalDistance(initiator.getPos(), target.getPos()));
        Vec3d sharedHandPoint = solveSharedHandPoint(initiator, target, defaultDistance);
        String key = pairKey(initiator.getUuid(), target.getUuid());
        TELEPORT_GUARDS.remove(key);
        RECOVERY.remove(key);
        PAIR_HISTORY.remove(key);
        LATEST_INPUTS.remove(initiator.getUuid());
        LATEST_INPUTS.remove(target.getUuid());
        INPUT_ARRIVAL_TICKS.remove(initiator.getUuid());
        INPUT_ARRIVAL_TICKS.remove(target.getUuid());
        INPUTS_THIS_TICK.remove(initiator.getUuid());
        INPUTS_THIS_TICK.remove(target.getUuid());
        REACHABILITY_CACHE.remove(initiator.getUuid());
        REACHABILITY_CACHE.remove(target.getUuid());
        Vec3d measuredCenter = initiator.getPos().add(target.getPos()).multiply(0.5D);
        ANCHORS.put(key,
                new HoldAnchorState(sharedHandPoint, Vec3d.ZERO, measuredCenter, Vec3d.ZERO,
                        Vec3d.ZERO, Vec3d.ZERO, 0, 0, 0, 0.0D,
                        defaultDistance, 0.0D, 0));
        ACTIVE.put(initiator.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.ACTIVE), target.getUuid(),
                defaultDistance, holdBodyYaw, sharedHandPoint, initiator.getPos()));
        ACTIVE.put(target.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.PASSIVE), initiator.getUuid(),
                defaultDistance, holdBodyYaw, sharedHandPoint, initiator.getPos()));
        sync(initiator, true);
        sync(target, true);
    }

    private static void stopPair(ServerPlayerEntity player) {
        stopPair(player, "manual_or_lifecycle");
    }

    private static void stopPair(ServerPlayerEntity player, String reason) {
        if (player == null) {
            return;
        }

        HoldHandData data = ACTIVE.remove(player.getUuid());
        if (data != null) {
            LOGGER.info("[HoldHands] stop pair player={} partner={} reason={}",
                    player.getName().getString(), data.partnerUuid(), reason);
        }
        sync(player, false);
        if (data == null) {
            return;
        }

        ServerPlayerEntity partner = getPlayer(player.getServer(), data.partnerUuid());
        if (partner != null) {
            String key = pairKey(player.getUuid(), partner.getUuid());
            ANCHORS.remove(key);
            TELEPORT_GUARDS.remove(key);
            RECOVERY.remove(key);
            PAIR_HISTORY.remove(key);
            REACHABILITY_CACHE.remove(player.getUuid());
            REACHABILITY_CACHE.remove(partner.getUuid());
            ACTIVE.remove(partner.getUuid());
            sync(partner, false);
        } else {
            String key = pairKey(player.getUuid(), data.partnerUuid());
            ANCHORS.remove(key);
            TELEPORT_GUARDS.remove(key);
            RECOVERY.remove(key);
            PAIR_HISTORY.remove(key);
            REACHABILITY_CACHE.remove(player.getUuid());
            REACHABILITY_CACHE.remove(data.partnerUuid());
        }
    }

    public static void cleanupForDisconnect(ServerPlayerEntity player) {
        stopPair(player);
        if (player != null) {
            INPUT_ARRIVAL_TICKS.remove(player.getUuid());
            SNEAK_INPUT_TICKS.remove(player.getUuid());
            INPUTS_THIS_TICK.remove(player.getUuid());
            LATEST_INPUTS.remove(player.getUuid());
            REACHABILITY_CACHE.remove(player.getUuid());
        }
    }

    public static void tick(MinecraftServer server) {
        updateServerLoad(server);
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }

        Set<UUID> processed = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (processed.contains(player.getUuid())) {
                continue;
            }

            HoldHandData data = ACTIVE.get(player.getUuid());
            if (data == null) {
                continue;
            }

            ServerPlayerEntity partner = getPlayer(server, data.partnerUuid());
            if (!canKeepHolding(player, partner)) {
                stopPair(player, "partner_missing_dead_or_world_changed");
                continue;
            }

            HoldHandData partnerData = ACTIVE.get(partner.getUuid());
            if (partnerData == null) {
                stopPair(player, "partner_state_missing");
                continue;
            }

            ServerPlayerEntity active = data.handSide() == HoldHandsSkeletalPose.ACTIVE_ROLE_HAND ? player : partner;
            ServerPlayerEntity passive = active == player ? partner : player;
            HoldHandData activeData = ACTIVE.get(active.getUuid());
            HoldHandData passiveData = ACTIVE.get(passive.getUuid());
            if (activeData == null || passiveData == null) {
                stopPair(player, "active_or_passive_state_missing");
                continue;
            }

            processed.add(active.getUuid());
            processed.add(passive.getUuid());

            String pairKey = pairKey(active.getUuid(), passive.getUuid());
            long worldTick = active.getWorld().getTime();
            if (hasPendingTeleport(active) || hasPendingTeleport(passive)) {
                TeleportGuard guard = TELEPORT_GUARDS.get(pairKey);
                if (guard == null) {
                    // Another feature may own the vanilla pending teleport. Do not
                    // attach a hold-hands timeout to a teleport we did not create.
                    if (shouldSync(active)) {
                        sync(active, true);
                        sync(passive, true);
                    }
                    continue;
                } else if (worldTick - guard.sentTick() > TELEPORT_WAIT_TIMEOUT_TICKS
                        && worldTick - guard.lastWarningTick() > TELEPORT_WAIT_TIMEOUT_TICKS) {
                    LOGGER.warn("[HoldHands] pending teleport still unconfirmed for pair {}", pairKey);
                    TELEPORT_GUARDS.put(pairKey, guard.withLastWarningTick(worldTick));
                }
                if (shouldSync(active)) {
                    sync(active, true);
                    sync(passive, true);
                }
                continue;
            }

            if (TELEPORT_GUARDS.remove(pairKey) != null) {
                RECOVERY.put(pairKey, RECOVERY_TICKS);
            }

            Integer recoveryTicks = RECOVERY.get(pairKey);
            if (recoveryTicks != null && recoveryTicks > 0) {
                Vec3d recoveryPoint = solveSharedHandPoint(active, passive,
                        activeData.defaultDistance());
                setPairSharedHandPoint(active, passive, recoveryPoint);
                recordPairHistory(pairKey, active, passive, recoveryPoint);
                RECOVERY.put(pairKey, recoveryTicks - 1);
                if (shouldSync(active)) {
                    sync(active, true);
                    sync(passive, true);
                }
                continue;
            }

            float holdBodyYaw = passiveData.holdBodyYaw();
            applyFollowerBodyYaw(passive, holdBodyYaw);
            Vec3d previousSharedHandPoint = activeData.sharedHandPoint() != null
                    ? activeData.sharedHandPoint()
                    : passiveData.sharedHandPoint();
            Vec3d sharedHandPoint = enforceAnchorRigidLink(active, passive, holdBodyYaw,
                    activeData.defaultDistance(), previousSharedHandPoint);
            setPairSharedHandPoint(active, passive, sharedHandPoint);
            if (shouldSync(active)) {
                sync(active, true);
                sync(passive, true);
            }
        }
    }

    /** Called from the vanilla input handler after the packet has reached the server thread. */
    public static void onInputReceived(ServerPlayerEntity player, PlayerInput input) {
        if (player != null && input != null && player.getWorld() != null) {
            INPUT_ARRIVAL_TICKS.put(player.getUuid(), player.getWorld().getTime());
            if (input.sneak()) {
                SNEAK_INPUT_TICKS.put(player.getUuid(), player.getWorld().getTime());
            } else {
                SNEAK_INPUT_TICKS.remove(player.getUuid());
            }
        }
    }

    public static boolean hasRecentSneakInput(ServerPlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        Long tick = SNEAK_INPUT_TICKS.get(player.getUuid());
        return tick != null && player.getWorld().getTime() - tick <= 5L;
    }

    public static void onInputPacketReceived(ServerPlayerEntity player, HoldHandsInputC2SPacket packet) {
        if (player == null || packet == null || !isHoldingHands(player)
                || packet.sequence() < 0 || !Float.isFinite(packet.yaw())) {
            return;
        }
        InputSample previous = LATEST_INPUTS.get(player.getUuid());
        if (previous != null && packet.sequence() <= previous.sequence()) {
            return;
        }
        if (previous != null && packet.sequence() - previous.sequence() > MAX_INPUT_SEQUENCE_JUMP) {
            return;
        }
        long serverTick = player.getWorld().getTime();
        if (packet.clientTick() < 0L || Math.abs(packet.clientTick() - serverTick) > 1200L) {
            return;
        }
        Long lastArrival = INPUT_ARRIVAL_TICKS.get(player.getUuid());
        int count = lastArrival != null && lastArrival == serverTick
                ? INPUTS_THIS_TICK.getOrDefault(player.getUuid(), 0) : 0;
        if (count >= 3) {
            return;
        }
        PlayerInput input = new PlayerInput(packet.forward(), packet.backward(), packet.left(), packet.right(),
                packet.jump(), packet.sneak(), packet.sprint());
        LATEST_INPUTS.put(player.getUuid(), new InputSample(packet.sequence(), serverTick,
                MathHelper.wrapDegrees(packet.yaw()), input));
        INPUT_ARRIVAL_TICKS.put(player.getUuid(), serverTick);
        INPUTS_THIS_TICK.put(player.getUuid(), count + 1);
    }

    public static void syncAllTo(ServerPlayerEntity receiver) {
        MinecraftServer server = receiver.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            HoldHandData data = ACTIVE.get(player.getUuid());
            if (data != null) {
                ServerPlayNetworking.send(receiver, createPacket(server, player, true, data));
            }
        }
    }

    private static void sync(ServerPlayerEntity player, boolean active) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        HoldHandData data = ACTIVE.get(player.getUuid());
        HoldHandsSyncS2CPacket packet = createPacket(server, player, active, data);
        for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(receiver, packet);
        }
    }

    private static HoldHandsSyncS2CPacket createPacket(MinecraftServer server, ServerPlayerEntity player,
                                                       boolean active, HoldHandData data) {
        int side = data != null && data.handSide() == HoldHandsSkeletalPose.HandSide.LEFT
                ? HoldHandsSyncS2CPacket.HAND_LEFT
                : HoldHandsSyncS2CPacket.HAND_RIGHT;
        ServerPlayerEntity partner = data != null ? getPlayer(server, data.partnerUuid()) : null;
        int partnerId = partner != null ? partner.getId() : HoldHandsSyncS2CPacket.NO_PARTNER;
        float defaultDistance = data != null && Double.isFinite(data.defaultDistance())
                ? (float) MathHelper.clamp(data.defaultDistance(), HoldHandsLinkGeometry.MIN_BODY_DISTANCE,
                HoldHandsLinkGeometry.NATURAL_MAX_SHOULDER_DISTANCE) : 0.0F;
        float holdBodyYaw = data != null && Float.isFinite(data.holdBodyYaw())
                ? MathHelper.wrapDegrees(data.holdBodyYaw()) : 0.0F;
        Vec3d sharedHandPoint = data != null && finiteVec(data.sharedHandPoint()) ? data.sharedHandPoint() : Vec3d.ZERO;
        long sequence = SYNC_SEQUENCES.merge(player.getUuid(), 1L, Long::sum);
        Vec3d velocity = finiteVec(player.getVelocity())
                ? HoldHandsLinkGeometry.clampSpeed(player.getVelocity(), 4.0D) : Vec3d.ZERO;
        HoldAnchorState anchor = data != null && partner != null
                ? ANCHORS.get(pairKey(player.getUuid(), partner.getUuid())) : null;
        float tension = anchor == null ? 0.0F : (float) MathHelper.clamp(anchor.tension(), 0.0D, 1.0D);
        float relativeDistance = anchor == null ? defaultDistance : (float) MathHelper.clamp(anchor.relativeDistance(), 0.0D, 8.0D);
        float relativeSpeed = anchor == null ? 0.0F : (float) MathHelper.clamp(anchor.relativeSpeed(), 0.0D, 8.0D);
        return new HoldHandsSyncS2CPacket(player.getId(), active, sequence, side, partnerId, defaultDistance, holdBodyYaw,
                (float) sharedHandPoint.x, (float) sharedHandPoint.y, (float) sharedHandPoint.z,
                player.getWorld().getTime(), (float) velocity.x, (float) velocity.y, (float) velocity.z,
                tension, relativeDistance, relativeSpeed);
    }

    private static boolean shouldSync(ServerPlayerEntity player) {
        return player != null && player.getWorld().getTime() % SYNC_INTERVAL_TICKS == 0L;
    }

    private static boolean hasPendingTeleport(ServerPlayerEntity player) {
        return player != null && player.networkHandler instanceof ServerPlayNetworkHandlerAccessor accessor
                && accessor.monvhua$getRequestedTeleportPos() != null;
    }

    private static void updateServerLoad(MinecraftServer server) {
        long now = System.nanoTime();
        if (lastServerTickNanos == 0L) {
            lastServerTickNanos = now;
            tickIntervalEmaMs = 50.0D;
            currentConstraintScale = 1.0D;
            return;
        }
        double intervalMs = (now - lastServerTickNanos) / 1_000_000.0D;
        lastServerTickNanos = now;
        if (!Double.isFinite(intervalMs)) {
            intervalMs = 50.0D;
        }
        intervalMs = MathHelper.clamp(intervalMs, 1.0D, 1000.0D);
        tickIntervalEmaMs = tickIntervalEmaMs * 0.9D + intervalMs * 0.1D;
        currentConstraintScale = tickIntervalEmaMs <= 60.0D ? 1.0D
                : tickIntervalEmaMs >= 200.0D ? 0.25D
                : MathHelper.clamp(1.0D - (tickIntervalEmaMs - 60.0D) / 140.0D * 0.75D, 0.25D, 1.0D);
    }

    private static double constraintScale() {
        return currentConstraintScale;
    }

    private static ServerPlayerEntity getPlayer(MinecraftServer server, UUID uuid) {
        return server != null && uuid != null ? server.getPlayerManager().getPlayer(uuid) : null;
    }

    private static boolean canKeepHolding(ServerPlayerEntity follower, ServerPlayerEntity leader) {
        return follower != null
                && leader != null
                && follower.isAlive()
                && leader.isAlive()
                && follower.getWorld() == leader.getWorld();
    }

    private static float updateHoldBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                           HoldHandData followerData, double pullDistance) {
        float currentYaw = followerData == null ? follower.getBodyYaw() : followerData.holdBodyYaw();
        float targetYaw = leader.getBodyYaw();
        float nextYaw = currentYaw;
        float delta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        if (Math.abs(delta) <= 1.25F) {
            nextYaw = MathHelper.wrapDegrees(targetYaw);
        } else if (pullDistance > FOLLOW_YAW_MOVE_DEADBAND) {
            double progress = MathHelper.clamp(pullDistance * FOLLOW_YAW_PROGRESS_PER_BLOCK, 0.0D, 1.0D);
            float maxStep = (float) Math.min(FOLLOW_YAW_MAX_STEP, Math.max(0.0D,
                    Math.abs(delta) * progress));
            nextYaw = stepTowardsAngle(currentYaw, targetYaw, maxStep);
        }

        setPairHoldBodyYaw(leader, follower, nextYaw, leader.getPos());
        return nextYaw;
    }

    private static void setPairHoldBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        setPairHoldBodyYaw(leader, follower, holdBodyYaw, leader.getPos());
    }

    private static void setPairHoldBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                           float holdBodyYaw, Vec3d lastLeaderPos) {
        HoldHandData leaderData = ACTIVE.get(leader.getUuid());
        if (leaderData != null) {
            ACTIVE.put(leader.getUuid(), leaderData.withHoldBodyYaw(holdBodyYaw, lastLeaderPos));
        }
        HoldHandData followerData = ACTIVE.get(follower.getUuid());
        if (followerData != null) {
            ACTIVE.put(follower.getUuid(), followerData.withHoldBodyYaw(holdBodyYaw, lastLeaderPos));
        }
    }

    private static void applyFollowerBodyYaw(ServerPlayerEntity follower, float holdBodyYaw) {
        float yaw = MathHelper.wrapDegrees(holdBodyYaw);
        follower.setBodyYaw(yaw);
    }

    private static void setPairSharedHandPoint(ServerPlayerEntity leader, ServerPlayerEntity follower, Vec3d sharedHandPoint) {
        HoldHandData leaderData = ACTIVE.get(leader.getUuid());
        HoldHandData followerData = ACTIVE.get(follower.getUuid());
        Vec3d stabilized = finiteVec(sharedHandPoint) ? clampWorldPosition(sharedHandPoint) : Vec3d.ZERO;
        if (leaderData != null) {
            ACTIVE.put(leader.getUuid(), leaderData.withSharedHandPoint(stabilized));
        }
        if (followerData != null) {
            ACTIVE.put(follower.getUuid(), followerData.withSharedHandPoint(stabilized));
        }
    }

    private static boolean finiteVec(Vec3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Vec3d stabilizeSharedHandPoint(Vec3d previous, Vec3d target,
                                                  ServerPlayerEntity leader, ServerPlayerEntity follower) {
        if (target == null) {
            return Vec3d.ZERO;
        }
        if (previous == null || previous.lengthSquared() <= 0.000001D) {
            return target;
        }

        boolean airborne = (leader != null && (!leader.isOnGround() || leader.getAbilities().flying))
                || (follower != null && (!follower.isOnGround() || follower.getAbilities().flying));
        if (!airborne && Math.abs(target.y - previous.y) < SHARED_POINT_GROUNDED_Y_DEADBAND) {
            target = new Vec3d(target.x, previous.y, target.z);
        }

        double distance = previous.distanceTo(target);
        if (distance < SHARED_POINT_DEADBAND) {
            return previous;
        }

        double distanceT = smoothUnit((distance - SHARED_POINT_DEADBAND)
                / Math.max(0.000001D, SHARED_POINT_FAST_DISTANCE - SHARED_POINT_DEADBAND));
        double speedT = smoothUnit((averageSpeed(leader, follower) - 0.08D) / 0.42D);
        double baseAlpha = airborne ? SHARED_POINT_AIRBORNE_ALPHA : SHARED_POINT_GROUNDED_ALPHA;
        double maxAlpha = airborne ? 0.78D : 0.42D;
        double alpha = baseAlpha + (maxAlpha - baseAlpha) * Math.max(distanceT, speedT * 0.85D);
        if (distance >= 1.20D) {
            alpha = 1.0D;
        }
        return previous.lerp(target, alpha);
    }

    private static double averageSpeed(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        Vec3d leaderVelocity = leader == null ? Vec3d.ZERO : leader.getVelocity();
        Vec3d followerVelocity = follower == null ? Vec3d.ZERO : follower.getVelocity();
        return leaderVelocity.add(followerVelocity).multiply(0.5D).length();
    }

    private static Vec3d enforceAnchorRigidLink(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                float holdBodyYaw, double defaultDistance,
                                                Vec3d previousSharedHandPoint) {
        float leaderBodyYaw = leader.getBodyYaw();
        float followerBodyYaw = holdBodyYaw;
        String key = pairKey(leader.getUuid(), follower.getUuid());
        HoldAnchorState state = ANCHORS.get(key);
        Vec3d measuredCenter = leader.getPos().add(follower.getPos()).multiply(0.5D);
        if (state == null || state.center() == null || state.position() == null) {
            Vec3d start = previousSharedHandPoint != null && previousSharedHandPoint.lengthSquared() > 0.000001D
                    ? previousSharedHandPoint
                    : solveSharedHandPoint(leader, follower, defaultDistance);
            state = new HoldAnchorState(start, Vec3d.ZERO, measuredCenter, Vec3d.ZERO,
                    Vec3d.ZERO, Vec3d.ZERO, 0, 0, 0, 0.0D,
                    defaultDistance, 0.0D, 0);
        }

        int bootstrapTicks = Math.min(BOOTSTRAP_TICKS, state.bootstrapTicks() + 1);
        boolean bootstrap = state.bootstrapTicks() < BOOTSTRAP_TICKS;

        String rawVerticalLeadReason = verticalLeadReason(leader, follower);
        boolean verticalCandidate = !"none".equals(rawVerticalLeadReason);
        int verticalTicks = verticalCandidate
                ? Math.min(PAIR_VERTICAL_CONFIRM_TICKS, state.verticalTicks() + 1)
                : Math.max(0, state.verticalTicks() - 1);
        boolean useVerticalLead = verticalTicks >= PAIR_VERTICAL_CONFIRM_TICKS;
        String verticalLeadReason = useVerticalLead ? rawVerticalLeadReason
                : verticalCandidate ? "pending:" + rawVerticalLeadReason : "none";

        Vec3d leaderIntent = filterPairIntent(state.leaderIntent(), inputIntentVelocity(leader), useVerticalLead, bootstrap);
        Vec3d followerIntent = filterPairIntent(state.followerIntent(), inputIntentVelocity(follower), useVerticalLead, bootstrap);
        Vec3d pairVelocity = pairIntentVelocity(leaderIntent, followerIntent, useVerticalLead);
        PairCenterResult centerResult = updatePairCenter(state, measuredCenter, pairVelocity, useVerticalLead, bootstrap);
        Vec3d center = centerResult.center();
        Vec3d centerVelocity = centerResult.velocity();

        Vec3d pairOffset = desiredPairOffsetForAnchor(holdBodyYaw, defaultDistance, leader, follower, useVerticalLead);
        Vec3d leaderTarget = center.subtract(pairOffset.multiply(0.5D));
        Vec3d followerTarget = center.add(pairOffset.multiply(0.5D));
        if (!useVerticalLead) {
            leaderTarget = new Vec3d(leaderTarget.x, leader.getY(), leaderTarget.z);
            followerTarget = new Vec3d(followerTarget.x, follower.getY(), followerTarget.z);
        }
        leaderTarget = resolveReachableFeet(leader, leaderTarget);
        followerTarget = resolveReachableFeet(follower, followerTarget);

        int teleportTicks = constraintScale() < 0.5D
                ? 0
                : needsPairTeleport(leader, follower, leaderTarget, followerTarget)
                ? state.teleportTicks() + 1 : 0;
        Vec3d leaderCorrection;
        Vec3d followerCorrection;
        Vec3d actualOffset = follower.getPos().subtract(leader.getPos());
        Vec3d relativeVelocity = follower.getVelocity().subtract(leader.getVelocity());
        if (teleportTicks >= PAIR_TELEPORT_CONFIRM_TICKS) {
            TELEPORT_GUARDS.put(key, new TeleportGuard(leader.getWorld().getTime(), leaderTarget, followerTarget));
            leader.requestTeleport(leaderTarget.x, leaderTarget.y, leaderTarget.z);
            follower.requestTeleport(followerTarget.x, followerTarget.y, followerTarget.z);
            applyBoundedVelocityImpulse(leader, centerVelocity, 0.35D);
            applyBoundedVelocityImpulse(follower, centerVelocity, 0.35D);
            leaderCorrection = leaderTarget.subtract(leader.getPos());
            followerCorrection = followerTarget.subtract(follower.getPos());
            teleportTicks = 0;
        } else {
            Vec3d relativeInput = followerIntent.subtract(leaderIntent).multiply(RELATIVE_INPUT_GAIN);
            Vec3d relativeCorrection = solveSpringCorrection(actualOffset, pairOffset, relativeVelocity,
                    relativeInput, useVerticalLead);
            leaderCorrection = applyPairSpringCorrection(leader, centerVelocity, relativeCorrection,
                    false, useVerticalLead, bootstrap);
            followerCorrection = applyPairSpringCorrection(follower, centerVelocity, relativeCorrection,
                    true, useVerticalLead, bootstrap);
        }

        double followerPullDistance = Math.max(horizontalDistance(follower.getPos(), followerTarget),
                horizontalDistance(Vec3d.ZERO, centerVelocity));
        HoldHandData followerData = ACTIVE.get(follower.getUuid());
        followerBodyYaw = updateHoldBodyYaw(leader, follower, followerData, followerPullDistance);
        applyFollowerBodyYaw(follower, followerBodyYaw);

        Vec3d desiredEndpoint = solveSharedHandPoint(leaderTarget, followerTarget,
                endpointMotion(leaderIntent, useVerticalLead),
                endpointMotion(followerIntent, useVerticalLead),
                leaderBodyYaw, followerBodyYaw, defaultDistance, previousSharedHandPoint);
        Vec3d anchorPosition = stabilizeAnchorEndpoint(state.position(), desiredEndpoint, useVerticalLead);
        Vec3d anchorVelocity = state.position() == null ? Vec3d.ZERO : anchorPosition.subtract(state.position());
        actualOffset = follower.getPos().subtract(leader.getPos());
        relativeVelocity = follower.getVelocity().subtract(leader.getVelocity());
        double relativeDistance = finiteVec(actualOffset)
                ? actualOffset.length() : defaultDistance;
        double relativeSpeed = finiteVec(relativeVelocity)
                ? Math.min(MAX_RELATIVE_SPEED, relativeVelocity.length()) : 0.0D;
        double stretch = Math.max(0.0D, relativeDistance - Math.max(0.001D, defaultDistance));
        double tension = MathHelper.clamp(stretch / MAX_STRETCH + relativeSpeed * TENSION_SPEED_WEIGHT, 0.0D, 1.0D);
        boolean noPairInput = new Vec3d(leaderIntent.x, 0.0D, leaderIntent.z).lengthSquared()
                + new Vec3d(followerIntent.x, 0.0D, followerIntent.z).lengthSquared() <= 0.0004D;
        Vec3d settleError = actualOffset.subtract(pairOffset);
        if (!useVerticalLead) {
            settleError = new Vec3d(settleError.x, 0.0D, settleError.z);
        }
        boolean settled = noPairInput
                && new Vec3d(centerVelocity.x, 0.0D, centerVelocity.z).length() <= SETTLE_SPEED_EPSILON
                && relativeSpeed <= SETTLE_SPEED_EPSILON
                && settleError.length() <= SETTLE_POSITION_EPSILON;
        if (settled) {
            zeroHorizontalVelocity(leader);
            zeroHorizontalVelocity(follower);
            center = measuredCenter;
            centerVelocity = Vec3d.ZERO;
        }
        int strainTicks = tension >= 0.85D ? Math.min(STRAIN_CONFIRM_TICKS, state.strainTicks() + 1)
                : Math.max(0, state.strainTicks() - 2);
        ANCHORS.put(key, new HoldAnchorState(anchorPosition, anchorVelocity, center, centerVelocity,
                leaderIntent, followerIntent, verticalTicks, teleportTicks, bootstrapTicks, tension,
                relativeDistance, relativeSpeed, strainTicks));
        recordPairHistory(key, leader, follower, anchorPosition);

        logAnchorDebug(key, leader, follower, anchorPosition, centerVelocity, desiredEndpoint,
                leaderTarget, followerTarget, leaderIntent, followerIntent, useVerticalLead,
                verticalLeadReason, leaderCorrection, followerCorrection,
                relativeDistance, relativeSpeed, tension, settled);
        if (useVerticalLead) {
            leader.fallDistance = Math.min(leader.fallDistance, follower.fallDistance);
            follower.fallDistance = Math.min(follower.fallDistance, leader.fallDistance);
        }

        return anchorPosition;
    }

    private static void zeroHorizontalVelocity(ServerPlayerEntity player) {
        if (player == null || !finiteVec(player.getVelocity())) {
            return;
        }
        Vec3d velocity = player.getVelocity();
        if (Math.abs(velocity.x) > 0.0001D || Math.abs(velocity.z) > 0.0001D) {
            player.setVelocity(0.0D, velocity.y, 0.0D);
            player.velocityModified = true;
        }
    }

    private static Vec3d filterPairIntent(Vec3d previous, Vec3d raw, boolean useVerticalLead, boolean bootstrap) {
        Vec3d previousIntent = previous == null ? Vec3d.ZERO : previous;
        Vec3d target = raw == null ? Vec3d.ZERO : raw;
        if (!useVerticalLead) {
            previousIntent = new Vec3d(previousIntent.x, 0.0D, previousIntent.z);
            target = new Vec3d(target.x, 0.0D, target.z);
        }
        double alpha = bootstrap ? BOOTSTRAP_INTENT_ALPHA : NORMAL_INTENT_ALPHA;
        return previousIntent.lerp(target, alpha);
    }

    private static Vec3d pairIntentVelocity(Vec3d leaderIntent, Vec3d followerIntent, boolean useVerticalLead) {
        Vec3d combined = (leaderIntent == null ? Vec3d.ZERO : leaderIntent)
                .add(followerIntent == null ? Vec3d.ZERO : followerIntent);
        Vec3d horizontal = HoldHandsLinkGeometry.clampSpeed(new Vec3d(combined.x, 0.0D, combined.z),
                PAIR_MAX_HORIZONTAL_SPEED);
        double y = useVerticalLead ? MathHelper.clamp(combined.y, -PAIR_MAX_VERTICAL_SPEED, PAIR_MAX_VERTICAL_SPEED) : 0.0D;
        return new Vec3d(horizontal.x, y, horizontal.z);
    }

    private static PairCenterResult updatePairCenter(HoldAnchorState state, Vec3d measuredCenter,
                                                     Vec3d pairVelocity, boolean useVerticalLead,
                                                     boolean bootstrap) {
        Vec3d center = state.center() == null ? measuredCenter : state.center();
        Vec3d previousVelocity = state.centerVelocity() == null ? Vec3d.ZERO : state.centerVelocity();
        Vec3d measuredError = measuredCenter.subtract(center);
        Vec3d measuredCorrection = HoldHandsLinkGeometry.clampSpeed(
                new Vec3d(measuredError.x, 0.0D, measuredError.z)
                        .multiply(PAIR_CENTER_STIFFNESS * constraintScale()),
                PAIR_CENTER_MAX_MEASURED_CORRECTION);
        double yCorrection = 0.0D;
        if (useVerticalLead) {
            yCorrection = MathHelper.clamp(measuredError.y * PAIR_CENTER_STIFFNESS * constraintScale(),
                    -PAIR_CENTER_MAX_MEASURED_CORRECTION, PAIR_CENTER_MAX_MEASURED_CORRECTION);
        }

        Vec3d targetVelocity = clampPairVelocity(pairVelocity.add(measuredCorrection).add(0.0D, yCorrection, 0.0D),
                useVerticalLead, bootstrap);
        double maxAcceleration = bootstrap ? BOOTSTRAP_MAX_ACCELERATION : NORMAL_MAX_ACCELERATION;
        Vec3d velocityDelta = HoldHandsLinkGeometry.clampSpeed(targetVelocity.subtract(previousVelocity),
                maxAcceleration * constraintScale());
        Vec3d nextVelocity = clampPairVelocity(previousVelocity.add(velocityDelta), useVerticalLead, bootstrap);
        if (pairVelocity.lengthSquared() <= CENTER_VELOCITY_DEADBAND * CENTER_VELOCITY_DEADBAND
                && measuredError.lengthSquared() <= 0.02D * 0.02D
                && nextVelocity.lengthSquared() <= CENTER_VELOCITY_DEADBAND * CENTER_VELOCITY_DEADBAND) {
            nextVelocity = Vec3d.ZERO;
        }
        Vec3d nextCenter = center.add(nextVelocity);
        if (!useVerticalLead) {
            nextVelocity = new Vec3d(nextVelocity.x, 0.0D, nextVelocity.z);
            nextCenter = new Vec3d(nextCenter.x, measuredCenter.y, nextCenter.z);
        }
        return new PairCenterResult(nextCenter, nextVelocity);
    }

    private static Vec3d desiredPairOffsetForAnchor(float holdBodyYaw, double defaultDistance,
                                                    ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                    boolean useVerticalLead) {
        Vec3d base = HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw);
        Vec3d horizontal = new Vec3d(base.x, 0.0D, base.z);
        if (horizontal.lengthSquared() <= 0.000001D) {
            horizontal = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        double distance = MathHelper.clamp(defaultDistance,
                HoldHandsLinkGeometry.MIN_BODY_DISTANCE, HoldHandsLinkGeometry.NATURAL_MAX_SHOULDER_DISTANCE);
        double y = useVerticalLead ? MathHelper.clamp(follower.getY() - leader.getY(),
                -HoldHandsLinkGeometry.ARM_REACH, HoldHandsLinkGeometry.ARM_REACH) : 0.0D;
        Vec3d direction = horizontal.normalize();
        return new Vec3d(direction.x * distance, y, direction.z * distance);
    }

    private static boolean needsPairTeleport(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                             Vec3d leaderTarget, Vec3d followerTarget) {
        return leader.getPos().distanceTo(leaderTarget) >= PAIR_TELEPORT_DISTANCE
                || follower.getPos().distanceTo(followerTarget) >= PAIR_TELEPORT_DISTANCE;
    }

    private static Vec3d solveSpringCorrection(Vec3d actualOffset, Vec3d desiredOffset,
                                                Vec3d relativeVelocity, Vec3d relativeInput,
                                                boolean useVerticalLead) {
        if (!finiteVec(actualOffset) || !finiteVec(desiredOffset) || !finiteVec(relativeVelocity)) {
            return Vec3d.ZERO;
        }
        Vec3d error = actualOffset.subtract(desiredOffset);
        if (error.lengthSquared() <= RELATIVE_POSITION_DEADBAND * RELATIVE_POSITION_DEADBAND) {
            error = Vec3d.ZERO;
        }
        if (!useVerticalLead) {
            error = new Vec3d(error.x, 0.0D, error.z);
            relativeVelocity = new Vec3d(relativeVelocity.x, 0.0D, relativeVelocity.z);
        }
        if (relativeVelocity.lengthSquared() <= RELATIVE_SPEED_DEADBAND * RELATIVE_SPEED_DEADBAND) {
            relativeVelocity = Vec3d.ZERO;
        }
        Vec3d correction = error.multiply(-SPRING_STIFFNESS)
                .add(relativeVelocity.multiply(-SPRING_DAMPING));
        if (finiteVec(relativeInput)) {
            correction = correction.add(relativeInput);
        }
        if (correction.lengthSquared() <= 0.0001D) {
            return Vec3d.ZERO;
        }
        return HoldHandsLinkGeometry.clampSpeed(correction, MAX_RELATIVE_CORRECTION);
    }

    private static Vec3d applyPairSpringCorrection(ServerPlayerEntity player, Vec3d centerVelocity,
                                                   Vec3d relativeCorrection, boolean follower,
                                                   boolean useVerticalLead, boolean bootstrap) {
        if (player == null || !finiteVec(centerVelocity) || !finiteVec(relativeCorrection)) {
            return Vec3d.ZERO;
        }
        Vec3d desired = centerVelocity.add(relativeCorrection.multiply(follower ? 0.5D : -0.5D));
        Vec3d current = player.getVelocity();
        if (!finiteVec(current)) {
            current = Vec3d.ZERO;
        }
        Vec3d impulse = desired.subtract(current).multiply(0.42D * constraintScale());
        if (!useVerticalLead) {
            impulse = new Vec3d(impulse.x, 0.0D, impulse.z);
        }
        double maxImpulse = bootstrap ? BOOTSTRAP_MAX_IMPULSE : NORMAL_MAX_IMPULSE;
        Vec3d horizontal = HoldHandsLinkGeometry.clampSpeed(new Vec3d(impulse.x, 0.0D, impulse.z), maxImpulse);
        double vertical = useVerticalLead
                ? MathHelper.clamp(impulse.y, -maxImpulse * 0.5D, maxImpulse * 0.5D) : 0.0D;
        Vec3d bounded = new Vec3d(horizontal.x, vertical, horizontal.z);
        Vec3d nextVelocity = current.add(bounded);
        if (finiteVec(nextVelocity)) {
            nextVelocity = HoldHandsLinkGeometry.clampSpeed(nextVelocity, 3.5D);
            player.setVelocity(nextVelocity);
            player.velocityModified = true;
        }
        return bounded;
    }

    private static Vec3d applyPairPlayerCorrection(ServerPlayerEntity player, Vec3d targetFeet,
                                                   Vec3d centerVelocity, boolean useVerticalLead) {
        Vec3d delta = targetFeet.subtract(player.getPos());
        Vec3d horizontalDelta = new Vec3d(delta.x, 0.0D, delta.z);
        Vec3d correction = Vec3d.ZERO;
        if (horizontalDelta.length() > PAIR_POSITION_DEADBAND) {
            correction = HoldHandsLinkGeometry.clampSpeed(horizontalDelta.multiply(PAIR_POSITION_STIFFNESS),
                    PAIR_MAX_CORRECTION_SPEED);
        }
        Vec3d current = player.getVelocity();
        Vec3d currentHorizontal = new Vec3d(current.x, 0.0D, current.z);
        Vec3d desiredHorizontal = new Vec3d(centerVelocity.x, 0.0D, centerVelocity.z);
        Vec3d centerImpulse = desiredHorizontal.subtract(currentHorizontal).multiply(0.35D * constraintScale());
        Vec3d impulse = centerImpulse.add(correction.multiply(constraintScale()));
        double verticalImpulse = 0.0D;
        if (useVerticalLead) {
            double correctionY = MathHelper.clamp(delta.y * PAIR_VERTICAL_STIFFNESS,
                    -PAIR_MAX_VERTICAL_CORRECTION_SPEED, PAIR_MAX_VERTICAL_CORRECTION_SPEED);
            verticalImpulse = MathHelper.clamp((centerVelocity.y - current.y) * 0.25D
                            + correctionY * constraintScale(),
                    -PAIR_MAX_VERTICAL_CORRECTION_SPEED, PAIR_MAX_VERTICAL_CORRECTION_SPEED);
        }
        impulse = HoldHandsLinkGeometry.clampSpeed(new Vec3d(impulse.x, 0.0D, impulse.z), 0.45D);
        Vec3d nextVelocity = current.add(new Vec3d(impulse.x, verticalImpulse, impulse.z));
        if (nextVelocity.squaredDistanceTo(current) > 0.000001D) {
            player.setVelocity(nextVelocity);
            player.velocityModified = true;
        }
        return impulse.add(0.0D, verticalImpulse, 0.0D);
    }

    private static Vec3d clampPairVelocity(Vec3d velocity, boolean useVerticalLead, boolean bootstrap) {
        Vec3d horizontal = HoldHandsLinkGeometry.clampSpeed(
                new Vec3d(velocity.x, 0.0D, velocity.z).multiply(constraintScale()),
                bootstrap ? BOOTSTRAP_MAX_CENTER_SPEED : NORMAL_MAX_CENTER_SPEED);
        double y = useVerticalLead
                ? MathHelper.clamp(velocity.y, -PAIR_MAX_VERTICAL_SPEED, PAIR_MAX_VERTICAL_SPEED)
                : 0.0D;
        return new Vec3d(horizontal.x, y, horizontal.z);
    }

    private static Vec3d stabilizeAnchorEndpoint(Vec3d previous, Vec3d target, boolean useVerticalLead) {
        if (target == null) {
            return previous == null ? Vec3d.ZERO : previous;
        }
        if (previous == null || previous.lengthSquared() <= 0.000001D) {
            return target;
        }
        double distance = previous.distanceTo(target);
        if (distance <= SHARED_POINT_DEADBAND) {
            return previous;
        }
        if (distance >= 1.20D) {
            return target;
        }
        double alpha = useVerticalLead ? 0.58D : 0.34D;
        return previous.lerp(target, alpha);
    }

    private static Vec3d inputIntentVelocity(ServerPlayerEntity player) {
        if (player == null) {
            return Vec3d.ZERO;
        }

        InputSample sample = LATEST_INPUTS.get(player.getUuid());
        double freshness = inputFreshness(player, sample);
        PlayerInput input = sample != null && freshness > 0.0D
                ? sample.input() : player.getPlayerInput();
        double forwardAmount = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        double sideAmount = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        Vec3d horizontal = Vec3d.ZERO;
        if (forwardAmount != 0.0D || sideAmount != 0.0D) {
            double yaw = Math.toRadians(sample != null && freshness > 0.0D
                    ? sample.yaw() : player.getYaw());
            Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
            horizontal = forward.multiply(forwardAmount).add(right.multiply(sideAmount));
            if (horizontal.lengthSquared() > 1.0D) {
                horizontal = horizontal.normalize();
            }
            horizontal = horizontal.multiply(input.sneak()
                    ? ANCHOR_SNEAK_INPUT_SPEED
                    : input.sprint() ? ANCHOR_SPRINT_INPUT_SPEED : ANCHOR_WALK_INPUT_SPEED);
        }

        double vertical = 0.0D;
        if (player.getAbilities().flying) {
            vertical += input.jump() ? ANCHOR_FLY_VERTICAL_INPUT_SPEED : 0.0D;
            vertical -= input.sneak() ? ANCHOR_FLY_VERTICAL_INPUT_SPEED : 0.0D;
        } else if (input.jump() && player.isOnGround()) {
            vertical = ANCHOR_JUMP_INPUT_SPEED;
        }

        return new Vec3d(horizontal.x, vertical, horizontal.z).multiply(freshness);
    }

    /** Resolves a server-authoritative target without moving a player through solid blocks. */
    private static Vec3d resolveReachableFeet(ServerPlayerEntity player, Vec3d desired) {
        if (player == null || desired == null || !Double.isFinite(desired.x)
                || !Double.isFinite(desired.y) || !Double.isFinite(desired.z)) {
            return player == null ? Vec3d.ZERO : player.getPos();
        }
        desired = clampWorldPosition(desired);
        Vec3d current = player.getPos();
        long tick = player.getWorld().getTime();
        if (isSafeFeetPosition(player, desired) && isClearPath(player, current, desired)) {
            REACHABILITY_CACHE.put(player.getUuid(), new ReachabilityCache(tick, desired, desired));
            return desired;
        }
        ReachabilityCache cached = REACHABILITY_CACHE.get(player.getUuid());
        if (cached != null && tick - cached.tick() <= 2L && cached.desired() != null
                && cached.desired().distanceTo(desired) <= 0.15D
                && finiteVec(cached.resolved()) && isSafeFeetPosition(player, cached.resolved())
                && isClearPath(player, current, cached.resolved())) {
            return cached.resolved();
        }
        double[] horizontal = {0.22D, 0.48D, 0.76D};
        Vec3d best = current;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double radius : horizontal) {
            for (int axis = 0; axis < 8; axis++) {
                double angle = axis * Math.PI / 4.0D;
                Vec3d candidate = desired.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                if (!finiteVec(candidate) || !isSafeFeetPosition(player, candidate)
                        || !isClearPath(player, current, candidate)) {
                    continue;
                }
                double distance = candidate.squaredDistanceTo(desired);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (!finiteVec(best)) {
            best = current;
        }
        REACHABILITY_CACHE.put(player.getUuid(), new ReachabilityCache(tick, desired, best));
        return best;
    }

    private static Vec3d clampWorldPosition(Vec3d position) {
        if (!finiteVec(position)) {
            return Vec3d.ZERO;
        }
        return new Vec3d(MathHelper.clamp(position.x, -30_000_000.0D, 30_000_000.0D),
                MathHelper.clamp(position.y, -30_000_000.0D, 30_000_000.0D),
                MathHelper.clamp(position.z, -30_000_000.0D, 30_000_000.0D));
    }

    private static boolean isSafeFeetPosition(ServerPlayerEntity player, Vec3d feet) {
        if (player == null || player.getWorld() == null || !finiteVec(feet)) {
            return false;
        }
        Box box = player.getBoundingBox().offset(feet.subtract(player.getPos()));
        return player.getWorld().isSpaceEmpty(player, box);
    }

    private static boolean isClearPath(ServerPlayerEntity player, Vec3d from, Vec3d to) {
        if (player == null || !finiteVec(from) || !finiteVec(to)) {
            return false;
        }
        double distance = from.distanceTo(to);
        if (!Double.isFinite(distance) || distance > MAX_PATH_CHECK_DISTANCE) {
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(distance / 0.45D));
        for (int i = 1; i <= steps; i++) {
            Vec3d point = from.lerp(to, i / (double) steps);
            if (!isSafeFeetPosition(player, point)) {
                return false;
            }
        }
        return true;
    }

    private static void recordPairHistory(String key, ServerPlayerEntity active,
                                          ServerPlayerEntity passive, Vec3d sharedHandPoint) {
        if (key == null || active == null || passive == null || sharedHandPoint == null) {
            return;
        }
        ArrayDeque<HoldPairHistoryEntry> history = PAIR_HISTORY.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new HoldPairHistoryEntry(active.getWorld().getTime(), active.getPos(), passive.getPos(),
                    active.getVelocity(), passive.getVelocity(), sharedHandPoint));
            while (history.size() > PAIR_HISTORY_TICKS) {
                history.removeFirst();
            }
        }
    }

    private static double inputFreshness(ServerPlayerEntity player, InputSample sample) {
        if (player == null || player.getWorld() == null) {
            return 0.0D;
        }
        long receivedAt = sample != null
                ? sample.arrivalTick()
                : INPUT_ARRIVAL_TICKS.getOrDefault(player.getUuid(), Long.MIN_VALUE);
        if (receivedAt == Long.MIN_VALUE) {
            return 1.0D;
        }
        long age = Math.max(0L, player.getWorld().getTime() - receivedAt);
        if (age <= 2L) {
            return 1.0D;
        }
        if (age >= 10L) {
            return 0.0D;
        }
        return 1.0D - (age - 2L) / 8.0D;
    }

    private static void logAnchorDebug(String key, ServerPlayerEntity leader, ServerPlayerEntity follower,
                                       Vec3d anchorPosition, Vec3d anchorVelocity, Vec3d desiredEndpoint,
                                       Vec3d leaderTarget, Vec3d followerTarget,
                                       Vec3d leaderIntent, Vec3d followerIntent, boolean useVerticalLead,
                                       String verticalLeadReason,
                                       Vec3d leaderCorrection, Vec3d followerCorrection,
                                       double relativeDistance, double relativeSpeed,
                                       double tension, boolean settled) {
        if (leader == null || leader.getWorld() == null || leader.getWorld().getTime() % ANCHOR_DEBUG_INTERVAL_TICKS != 0L) {
            return;
        }

        Vec3d anchorError = desiredEndpoint == null ? Vec3d.ZERO : desiredEndpoint.subtract(anchorPosition);
        Vec3d leaderDelta = leaderTarget == null ? Vec3d.ZERO : leaderTarget.subtract(leader.getPos());
        Vec3d followerDelta = followerTarget == null ? Vec3d.ZERO : followerTarget.subtract(follower.getPos());
        LOGGER.info("[HoldHandsAnchor] key={} vertical={} reason={} settled={} relDistance={} relSpeed={} tension={} "
                        + "heightDiff={} targetFeetDistance={} "
                        + "anchor={} anchorVel={} endpointError={} "
                        + "leader={} leaderState={} leaderVel={} leaderIntent={} leaderDelta={} leaderCorrection={} "
                        + "follower={} followerState={} followerVel={} followerIntent={} followerDelta={} followerCorrection={}",
                key, useVerticalLead, verticalLeadReason, settled,
                String.format("%.3f", relativeDistance), String.format("%.3f", relativeSpeed),
                String.format("%.3f", tension),
                String.format("%.3f", Math.abs(leader.getY() - follower.getY())),
                String.format("%.3f", horizontalDistance(leaderTarget, followerTarget)),
                shortVec(anchorPosition), shortVec(anchorVelocity), shortVec(anchorError),
                leader.getName().getString(), playerInputState(leader), shortVec(leader.getVelocity()), shortVec(leaderIntent),
                shortVec(leaderDelta), shortVec(leaderCorrection),
                follower.getName().getString(), playerInputState(follower), shortVec(follower.getVelocity()), shortVec(followerIntent),
                shortVec(followerDelta), shortVec(followerCorrection));
    }

    private static String shortVec(Vec3d value) {
        if (value == null) {
            return "null";
        }
        return String.format("(%.3f, %.3f, %.3f)", value.x, value.y, value.z);
    }

    private static String playerInputState(ServerPlayerEntity player) {
        if (player == null) {
            return "null";
        }
        PlayerInput input = player.getPlayerInput();
        return "flying=" + player.getAbilities().flying
                + ",onGround=" + player.isOnGround()
                + ",jump=" + input.jump()
                + ",sneak=" + input.sneak()
                + ",forward=" + input.forward()
                + ",backward=" + input.backward()
                + ",left=" + input.left()
                + ",right=" + input.right()
                + ",sprint=" + input.sprint();
    }

    private static boolean hasExplicitVerticalInput(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        PlayerInput input = player.getPlayerInput();
        if (player.getAbilities().flying) {
            return input.jump() || input.sneak();
        }
        return input.jump() && player.isOnGround();
    }

    private static String verticalLeadReason(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        String leaderReason = verticalLeadReasonFor(leader, follower);
        if (!"none".equals(leaderReason)) {
            return "leader:" + leaderReason;
        }
        String followerReason = verticalLeadReasonFor(follower, leader);
        if (!"none".equals(followerReason)) {
            return "follower:" + followerReason;
        }
        return "none";
    }

    private static String verticalLeadReasonFor(ServerPlayerEntity player, ServerPlayerEntity partner) {
        if (player == null) {
            return "none";
        }
        PlayerInput input = player.getPlayerInput();
        if (player.getAbilities().flying && input.jump()) {
            return "fly_jump";
        }
        if (player.getAbilities().flying && input.sneak()) {
            return "fly_sneak";
        }
        if (!player.getAbilities().flying && input.jump() && player.isOnGround()) {
            return "ground_jump";
        }
        if (partner != null && Math.abs(player.getY() - partner.getY()) >= VERTICAL_LEAD_MIN_HEIGHT
                && (!player.isOnGround() || !partner.isOnGround())) {
            return "air_height_diff";
        }
        return "none";
    }

    private static Vec3d enforceMutualRigidLink(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                float holdBodyYaw, double defaultDistance,
                                                Vec3d previousSharedHandPoint) {
        float leaderBodyYaw = leader.getBodyYaw();
        float followerBodyYaw = holdBodyYaw;
        Vec3d leaderShoulder = HoldHandsLinkGeometry.shoulderWorld(leader.getPos(), leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = HoldHandsLinkGeometry.shoulderWorld(follower.getPos(), followerBodyYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        double shoulderDistance = leaderShoulder.distanceTo(followerShoulder);
        double maxLinkDistance = HoldHandsLinkGeometry.NATURAL_MAX_SHOULDER_DISTANCE;
        double bodyDistance = HoldHandsLinkGeometry.horizontalDistance(leader.getPos(), follower.getPos());
        double tautness = MathHelper.clamp(shoulderDistance / Math.max(0.000001D, maxLinkDistance), 0.0D, 1.0D);
        boolean tautLink = tautness >= TAUT_FORCE_START;

        Vec3d desiredOffset = desiredPairOffset(leader, follower, holdBodyYaw);
        Vec3d currentOffset = follower.getPos().subtract(leader.getPos());
        Vec3d offsetError = desiredOffset.subtract(currentOffset);
        if (bodyDistance < HoldHandsLinkGeometry.MIN_BODY_DISTANCE) {
            offsetError = pushApartError(leader, follower, holdBodyYaw, HoldHandsLinkGeometry.MIN_BODY_DISTANCE);
        }

        if (offsetError.length() >= MUTUAL_TELEPORT_DISTANCE) {
            Vec3d center = leader.getPos().add(follower.getPos()).multiply(0.5D);
            Vec3d leaderTarget = center.subtract(desiredOffset.multiply(0.5D));
            Vec3d followerTarget = center.add(desiredOffset.multiply(0.5D));
            leader.requestTeleport(leaderTarget.x, leaderTarget.y, leaderTarget.z);
            follower.requestTeleport(followerTarget.x, followerTarget.y, followerTarget.z);
            Vec3d sharedVelocity = leader.getVelocity().add(follower.getVelocity());
            leader.setVelocity(sharedVelocity);
            follower.setVelocity(sharedVelocity);
            leader.velocityModified = true;
            follower.velocityModified = true;
            boolean useVerticalEndpoint = shouldUseVerticalPositionLead(leader, follower);
            return solveSharedHandPoint(leaderTarget, followerTarget,
                    endpointMotion(sharedVelocity, useVerticalEndpoint),
                    endpointMotion(sharedVelocity, useVerticalEndpoint),
                    leaderBodyYaw, followerBodyYaw, defaultDistance, previousSharedHandPoint);
        }

        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d followerVelocity = follower.getVelocity();
        Vec3d sharedVelocity = leaderVelocity.add(followerVelocity);
        Vec3d correction = HoldHandsLinkGeometry.clampSpeed(
                offsetError.multiply(tautLink ? TAUT_POSITION_STIFFNESS : MUTUAL_POSITION_STIFFNESS),
                tautLink ? TAUT_MAX_SPEED : MUTUAL_MAX_CORRECTION_SPEED);
        Vec3d damping = relativeDampingCorrection(offsetError, followerVelocity.subtract(leaderVelocity), tautLink);
        correction = HoldHandsLinkGeometry.clampSpeed(correction.add(damping),
                tautLink ? TAUT_MAX_SPEED : MUTUAL_MAX_CORRECTION_SPEED);

        Vec3d leaderTargetVelocity = sharedVelocity.subtract(correction.multiply(0.5D));
        Vec3d followerTargetVelocity = sharedVelocity.add(correction.multiply(0.5D));
        applyBoundedVelocityImpulse(leader, leaderTargetVelocity, 0.35D);
        applyBoundedVelocityImpulse(follower, followerTargetVelocity, 0.35D);
        if (shouldUseVerticalPositionLead(leader, follower)) {
            follower.fallDistance = Math.min(follower.fallDistance, leader.fallDistance);
        }

        Vec3d predictedLeaderFeet = leader.getPos().add(leaderTargetVelocity);
        Vec3d predictedFollowerFeet = follower.getPos().add(followerTargetVelocity);
        boolean useVerticalEndpoint = shouldUseVerticalPositionLead(leader, follower);
        return solveSharedHandPoint(predictedLeaderFeet, predictedFollowerFeet,
                endpointMotion(leaderTargetVelocity, useVerticalEndpoint),
                endpointMotion(followerTargetVelocity, useVerticalEndpoint),
                leaderBodyYaw, followerBodyYaw, defaultDistance, previousSharedHandPoint);
    }

    private static Vec3d desiredPairOffset(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d horizontalOffset = HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw);
        double verticalOffset = shouldUseVerticalPositionLead(leader, follower)
                ? MathHelper.clamp(follower.getY() - leader.getY(), -HoldHandsLinkGeometry.ARM_REACH, HoldHandsLinkGeometry.ARM_REACH)
                : 0.0D;
        return new Vec3d(horizontalOffset.x, verticalOffset, horizontalOffset.z);
    }

    private static Vec3d pushApartError(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                        float holdBodyYaw, double minimumDistance) {
        Vec3d currentOffset = follower.getPos().subtract(leader.getPos());
        Vec3d horizontal = new Vec3d(currentOffset.x, 0.0D, currentOffset.z);
        Vec3d axis = horizontal.horizontalLengthSquared() <= 0.000001D
                ? HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw).normalize()
                : horizontal.normalize();
        Vec3d desiredOffset = axis.multiply(minimumDistance);
        return desiredOffset.subtract(horizontal);
    }

    private static Vec3d relativeDampingCorrection(Vec3d offsetError, Vec3d relativeVelocity, boolean tautLink) {
        if (offsetError == null || relativeVelocity == null || offsetError.lengthSquared() <= 0.000001D) {
            return Vec3d.ZERO;
        }

        Vec3d axis = offsetError.normalize();
        double alongVelocity = relativeVelocity.dotProduct(axis);
        double damping = tautLink ? MUTUAL_TAUT_RELATIVE_DAMPING : MUTUAL_RELATIVE_DAMPING;
        return axis.multiply(-alongVelocity * damping);
    }

    private static Vec3d applyFollowerPositionStep(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                   Vec3d desiredFeet, Vec3d delta, boolean tautLink) {
        double horizontalError = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double deadband = tautLink ? TAUT_POSITION_STEP_DEADBAND : FOLLOW_POSITION_STEP_DEADBAND;
        if (horizontalError <= deadband) {
            return delta;
        }

        Vec3d leaderVelocity = leader.getVelocity();
        double leaderSpeed = Math.sqrt(leaderVelocity.x * leaderVelocity.x + leaderVelocity.z * leaderVelocity.z);
        double maxStep = MathHelper.clamp(FOLLOW_POSITION_MIN_STEP + leaderSpeed * 1.35D + horizontalError * 0.18D,
                FOLLOW_POSITION_MIN_STEP, tautLink ? TAUT_POSITION_MAX_STEP : FOLLOW_POSITION_MAX_STEP);
        double step = Math.min(horizontalError - deadband, maxStep);
        double scale = step / Math.max(0.000001D, horizontalError);

        Vec3d current = follower.getPos();
        double nextX = current.x + delta.x * scale;
        double nextY = current.y;
        double nextZ = current.z + delta.z * scale;
        if (shouldUseVerticalPositionLead(leader, follower) && Math.abs(delta.y) > 0.12D) {
            nextY += MathHelper.clamp(delta.y, -maxStep, maxStep);
        }

        Vec3d reachable = resolveReachableFeet(follower, new Vec3d(nextX, nextY, nextZ));
        follower.requestTeleport(reachable.x, reachable.y, reachable.z);
        return desiredFeet.subtract(new Vec3d(nextX, nextY, nextZ));
    }

    private static void applyBoundedVelocityImpulse(ServerPlayerEntity player, Vec3d desiredVelocity,
                                                     double gain) {
        if (player == null || desiredVelocity == null) {
            return;
        }
        Vec3d current = player.getVelocity();
        Vec3d delta = desiredVelocity.subtract(current).multiply(gain * constraintScale());
        Vec3d impulse = HoldHandsLinkGeometry.clampSpeed(delta, 0.45D);
        if (impulse.lengthSquared() <= 0.000001D) {
            return;
        }
        player.setVelocity(current.add(impulse));
        player.velocityModified = true;
    }

    private static Vec3d solveSharedHandPoint(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                              double defaultDistance) {
        boolean useVerticalEndpoint = shouldUseVerticalPositionLead(leader, follower);
        return solveSharedHandPoint(leader.getPos(), follower.getPos(),
                endpointMotion(inputIntentVelocity(leader), useVerticalEndpoint),
                endpointMotion(inputIntentVelocity(follower), useVerticalEndpoint),
                leader.getBodyYaw(), follower.getBodyYaw(), defaultDistance, null);
    }

    private static Vec3d solveSharedHandPoint(Vec3d leaderFeet, Vec3d followerFeet,
                                               Vec3d leaderVelocity, Vec3d followerVelocity,
                                               float leaderBodyYaw, float followerBodyYaw, double defaultDistance,
                                               Vec3d previousSharedHandPoint) {
        Vec3d leaderShoulder = HoldHandsLinkGeometry.shoulderWorld(leaderFeet, leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = HoldHandsLinkGeometry.shoulderWorld(followerFeet, followerBodyYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        float stretch = stretchRatio(leaderFeet, followerFeet, defaultDistance);
        Vec3d desired = HoldHandsLinkGeometry.dynamicEndpoint(leaderFeet, followerFeet,
                leaderVelocity, followerVelocity, leaderBodyYaw, followerBodyYaw, stretch);
        boolean verticalEndpoint = usesVerticalEndpointMotion(leaderVelocity, followerVelocity);
        if (!verticalEndpoint) {
            desired = clampGroundedEndpointHeight(desired, leaderFeet, followerFeet, leaderBodyYaw, followerBodyYaw);
        }
        Vec3d reference = verticalEndpoint ? previousSharedHandPoint
                : clampGroundedEndpointHeight(previousSharedHandPoint, leaderFeet, followerFeet,
                leaderBodyYaw, followerBodyYaw);
        Vec3d target = HoldHandsLinkGeometry.hardLinkedEndpoint(leaderShoulder, HoldHandsLinkGeometry.ARM_REACH,
                followerShoulder, HoldHandsLinkGeometry.ARM_REACH, desired, reference);

        for (int i = 0; i < 3; i++) {
            Vec3d leaderTarget = HoldHandsLinkGeometry.constrainEndpointForArm(target, leaderShoulder, leaderBodyYaw,
                    HoldHandsSkeletalPose.ACTIVE_ROLE_HAND, HoldHandsLinkGeometry.ARM_REACH);
            Vec3d followerTarget = HoldHandsLinkGeometry.constrainEndpointForArm(target, followerShoulder, followerBodyYaw,
                    HoldHandsSkeletalPose.PASSIVE_ROLE_HAND, HoldHandsLinkGeometry.ARM_REACH);
            desired = leaderTarget.add(followerTarget).multiply(0.5D);
            if (!verticalEndpoint) {
                desired = clampGroundedEndpointHeight(desired, leaderFeet, followerFeet,
                        leaderBodyYaw, followerBodyYaw);
            }
            target = HoldHandsLinkGeometry.hardLinkedEndpoint(leaderShoulder, HoldHandsLinkGeometry.ARM_REACH,
                    followerShoulder, HoldHandsLinkGeometry.ARM_REACH, desired, target);
        }

        return target;
    }

    private static Vec3d clampGroundedEndpointHeight(Vec3d point, Vec3d leaderFeet, Vec3d followerFeet,
                                                     float leaderBodyYaw, float followerBodyYaw) {
        if (point == null) {
            return null;
        }

        double naturalY = HoldHandsLinkGeometry.palmWorld(leaderFeet, leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND)
                .add(HoldHandsLinkGeometry.palmWorld(followerFeet, followerBodyYaw,
                        HoldHandsSkeletalPose.PASSIVE_ROLE_HAND))
                .multiply(0.5D).y;
        double minY = naturalY - GROUNDED_ENDPOINT_MAX_DROP;
        double maxY = naturalY + GROUNDED_ENDPOINT_MAX_LIFT;
        return new Vec3d(point.x, MathHelper.clamp(point.y, minY, maxY), point.z);
    }

    private static float stretchRatio(Vec3d leaderFeet, Vec3d followerFeet, double defaultDistance) {
        double distance = leaderFeet.distanceTo(followerFeet);
        double stretchStart = Math.max(HoldHandsLinkGeometry.MIN_BODY_DISTANCE, defaultDistance) + 0.08D;
        if (distance <= stretchStart) {
            return 0.0F;
        }

        double stretchRange = Math.max(0.000001D, HoldHandsLinkGeometry.ARM_REACH);
        return MathHelper.clamp((float) ((distance - stretchStart) / stretchRange), 0.0F, 1.0F);
    }

    private static Vec3d desiredFollowerFeet(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d defaultFeet = leader.getPos().add(HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw));
        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d positionalVelocity = shouldUseVerticalPositionLead(leader, follower)
                ? leaderVelocity
                : new Vec3d(leaderVelocity.x, 0.0D, leaderVelocity.z);
        positionalVelocity = smoothVelocityLead(positionalVelocity);
        if (positionalVelocity.lengthSquared() <= 0.000001D) {
            return defaultFeet;
        }

        return defaultFeet.add(positionalVelocity.multiply(0.55D));
    }

    private static Vec3d smoothVelocityLead(Vec3d velocity) {
        double speed = velocity.length();
        if (speed <= 0.025D) {
            return Vec3d.ZERO;
        }
        double t = MathHelper.clamp((speed - 0.025D) / 0.22D, 0.0D, 1.0D);
        double weight = t * t * (3.0D - 2.0D * t);
        return velocity.multiply(weight);
    }

    private static Vec3d endpointMotion(Vec3d velocity, boolean includeVertical) {
        if (velocity == null) {
            return Vec3d.ZERO;
        }
        return includeVertical ? velocity : new Vec3d(velocity.x, 0.0D, velocity.z);
    }

    private static boolean shouldUseVerticalPositionLead(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        return !"none".equals(verticalLeadReason(leader, follower));
    }

    private static boolean usesVerticalPositionLead(ServerPlayerEntity player, ServerPlayerEntity partner) {
        return !"none".equals(verticalLeadReasonFor(player, partner));
    }

    private static boolean usesVerticalEndpointMotion(Vec3d leaderVelocity, Vec3d followerVelocity) {
        double leaderY = leaderVelocity == null ? 0.0D : Math.abs(leaderVelocity.y);
        double followerY = followerVelocity == null ? 0.0D : Math.abs(followerVelocity.y);
        return Math.max(leaderY, followerY) >= VERTICAL_LEAD_MIN_SPEED;
    }

    private static Vec3d projectFollowerToShoulderDistance(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                           float leaderBodyYaw, float followerBodyYaw, double distance) {
        Vec3d leaderShoulder = HoldHandsLinkGeometry.shoulderWorld(leader.getPos(), leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = HoldHandsLinkGeometry.shoulderWorld(follower.getPos(), followerBodyYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        Vec3d axis = followerShoulder.subtract(leaderShoulder);
        if (axis.lengthSquared() <= 0.000001D) {
            axis = HoldHandsLinkGeometry.defaultFollowerOffset(leaderBodyYaw).normalize();
        } else {
            axis = axis.normalize();
        }

        Vec3d targetShoulder = leaderShoulder.add(axis.multiply(distance));
        Vec3d followerShoulderLocal = HoldHandsLinkGeometry.shoulderSocket(HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        return targetShoulder.subtract(HoldHandsLinkGeometry.bodyLocalToWorldVector(followerShoulderLocal, followerBodyYaw));
    }

    private static Vec3d projectFollowerToMinimumBodyDistance(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                              float holdBodyYaw) {
        Vec3d offset = follower.getPos().subtract(leader.getPos());
        Vec3d horizontal = new Vec3d(offset.x, 0.0D, offset.z);
        Vec3d axis = horizontal.horizontalLengthSquared() <= 0.000001D
                ? HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw).normalize()
                : horizontal.normalize();
        return leader.getPos().add(axis.multiply(HoldHandsLinkGeometry.MIN_BODY_DISTANCE));
    }

    private static float stepTowardsAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return MathHelper.wrapDegrees(target);
        }
        return MathHelper.wrapDegrees(current + Math.copySign(maxStep, delta));
    }

    private static double horizontalDistance(Vec3d first, Vec3d second) {
        if (first == null || second == null) {
            return 0.0D;
        }
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String pairKey(UUID first, UUID second) {
        if (first == null || second == null) {
            return String.valueOf(first) + "|" + second;
        }

        return first.compareTo(second) <= 0
                ? first + "|" + second
                : second + "|" + first;
    }

    private static double smoothUnit(double value) {
        double t = MathHelper.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, UUID partnerUuid, double defaultDistance,
                                float holdBodyYaw, Vec3d sharedHandPoint, Vec3d lastLeaderPos) {
        private HoldHandData withHoldBodyYaw(float holdBodyYaw, Vec3d lastLeaderPos) {
            return new HoldHandData(handSide, partnerUuid, defaultDistance, holdBodyYaw, sharedHandPoint,
                    lastLeaderPos == null ? this.lastLeaderPos : lastLeaderPos);
        }

        private HoldHandData withSharedHandPoint(Vec3d sharedHandPoint) {
            return new HoldHandData(handSide, partnerUuid, defaultDistance, holdBodyYaw,
                    sharedHandPoint == null ? Vec3d.ZERO : sharedHandPoint, lastLeaderPos);
        }
    }

    private record PairCenterResult(Vec3d center, Vec3d velocity) {
    }

    private record InputSample(int sequence, long arrivalTick, float yaw, PlayerInput input) {
    }

    private record HoldPairHistoryEntry(long serverTick, Vec3d activePosition, Vec3d passivePosition,
                                        Vec3d activeVelocity, Vec3d passiveVelocity, Vec3d sharedHandPoint) {
    }

    private record TeleportGuard(long sentTick, Vec3d leaderTarget, Vec3d followerTarget,
                                 long lastWarningTick) {
        private TeleportGuard(long sentTick, Vec3d leaderTarget, Vec3d followerTarget) {
            this(sentTick, leaderTarget, followerTarget, Long.MIN_VALUE);
        }

        private TeleportGuard withLastWarningTick(long tick) {
            return new TeleportGuard(sentTick, leaderTarget, followerTarget, tick);
        }
    }

    private record HoldAnchorState(Vec3d position, Vec3d velocity, Vec3d center, Vec3d centerVelocity,
                                   Vec3d leaderIntent, Vec3d followerIntent,
                                   int verticalTicks, int teleportTicks, int bootstrapTicks,
                                   double tension, double relativeDistance, double relativeSpeed,
                                   int strainTicks) {
        private HoldAnchorState(Vec3d position, Vec3d velocity) {
            this(position, velocity, null, Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO,
                    0, 0, 0, 0.0D, 0.0D, 0.0D, 0);
        }
    }

    private record ReachabilityCache(long tick, Vec3d desired, Vec3d resolved) {
    }
}
