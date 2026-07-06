package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldHandsManager {
    private static final double MODEL_HIRO_TO_EMA_SIDE_OFFSET = -1.008708D;
    private static final double MODEL_HIRO_TO_EMA_FORWARD_OFFSET = -0.089465946D;
    private static final double DEFAULT_FOLLOW_SIDE_OFFSET = -MODEL_HIRO_TO_EMA_SIDE_OFFSET;
    private static final double DEFAULT_FOLLOW_FORWARD_OFFSET = -MODEL_HIRO_TO_EMA_FORWARD_OFFSET;
    private static final double FOLLOW_STOP_DISTANCE = 0.04D;
    private static final double FOLLOW_PULL_DISTANCE = 0.14D;
    private static final double FOLLOW_TELEPORT_DISTANCE = 3.0D;
    private static final double FOLLOW_MAX_SPEED = 0.68D;
    private static final double FOLLOW_SMOOTHING = 0.85D;
    private static final double STRETCH_DEADZONE = 0.03D;
    private static final double MAX_STRETCH_EXTRA_DISTANCE = 0.38D;
    private static final double SOFT_CONNECTION_STIFFNESS = 0.95D;
    private static final double SOFT_CONNECTION_MAX_CORRECTION_SPEED = 0.55D;
    private static final double SOFT_CONNECTION_MIN_LEADER_VELOCITY_WEIGHT = 0.35D;
    private static final double HARD_CONNECTION_SLACK = 0.02D;
    private static final double HARD_CONNECTION_STIFFNESS = 1.65D;
    private static final double HARD_CONNECTION_MAX_CORRECTION_SPEED = 0.9D;
    private static final double ARM_MIN_HORIZONTAL_ANGLE = 0.0D;
    private static final double ARM_MAX_HORIZONTAL_ANGLE = 135.0D;
    private static final double ORBIT_MAX_SPEED = 0.28D;
    private static final double ORBIT_SMOOTHING = 0.55D;
    private static final double ORBIT_MIN_RADIUS = 0.65D;

    private static final Map<UUID, HoldHandData> ACTIVE = new ConcurrentHashMap<>();

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
        double defaultDistance = Math.max(0.001D, horizontalDistance(initiator.getPos(), target.getPos()));
        ACTIVE.put(initiator.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.ACTIVE), target.getUuid(), defaultDistance));
        ACTIVE.put(target.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.PASSIVE), initiator.getUuid(), defaultDistance));
        sync(initiator, true);
        sync(target, true);
    }

    private static void stopPair(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        HoldHandData data = ACTIVE.remove(player.getUuid());
        sync(player, false);
        if (data == null) {
            return;
        }

        ServerPlayerEntity partner = getPlayer(player.getServer(), data.partnerUuid());
        if (partner != null) {
            ACTIVE.remove(partner.getUuid());
            sync(partner, false);
        }
    }

    public static void cleanupForDisconnect(ServerPlayerEntity player) {
        stopPair(player);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity follower : server.getPlayerManager().getPlayerList()) {
            HoldHandData data = ACTIVE.get(follower.getUuid());
            if (data == null || !HoldHandsSkeletalPose.isFollowerHand(data.handSide())) {
                continue;
            }

            ServerPlayerEntity leader = getPlayer(server, data.partnerUuid());
            if (!canKeepHolding(follower, leader)) {
                stopPair(follower);
                continue;
            }

            syncFollowerBodyYaw(leader, follower);
            movePairTowardHandConstraint(leader, follower, data.defaultDistance());
        }
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
        float defaultDistance = data != null ? (float) data.defaultDistance() : 0.0F;
        return new HoldHandsSyncS2CPacket(player.getId(), active, side, partnerId, defaultDistance);
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

    private static void syncFollowerBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        float yaw = leader.getYaw();
        follower.setYaw(yaw);
        follower.setBodyYaw(yaw);
        follower.setHeadYaw(yaw);
    }

    private static void movePairTowardHandConstraint(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                     double defaultDistance) {
        if (enforceFollowerHardConnection(leader, follower, defaultDistance)) {
            return;
        }

        Vec3d leaderToFollower = follower.getPos().subtract(leader.getPos());
        double leaderArmAngle = horizontalFollowerSlotAngleDegrees(leader, leaderToFollower);
        if (leaderArmAngle < ARM_MIN_HORIZONTAL_ANGLE || leaderArmAngle > ARM_MAX_HORIZONTAL_ANGLE) {
            moveFollowerAroundLeader(leader, follower);
        } else {
            moveFollowerTowardDefaultSlot(leader, follower);
        }

        applyFollowerSoftConnection(leader, follower, defaultDistance);
    }

    private static boolean enforceFollowerHardConnection(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                         double defaultDistance) {
        double currentDistance = horizontalDistance(leader.getPos(), follower.getPos());
        double maxStretchDistance = maxStretchDistance(defaultDistance);
        if (currentDistance <= maxStretchDistance + HARD_CONNECTION_SLACK) {
            return false;
        }

        Vec3d leaderToFollower = follower.getPos().subtract(leader.getPos());
        Vec3d radial = new Vec3d(leaderToFollower.x, 0.0D, leaderToFollower.z);
        double radialLength = radial.horizontalLength();
        if (radialLength <= 0.000001D) {
            return false;
        }

        Vec3d radialUnit = radial.multiply(1.0D / radialLength);
        double overStretch = currentDistance - maxStretchDistance;
        double correctionSpeed = Math.min(HARD_CONNECTION_MAX_CORRECTION_SPEED,
                overStretch * HARD_CONNECTION_STIFFNESS);
        Vec3d inwardCorrection = radialUnit.multiply(-correctionSpeed);
        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d followerVelocity = follower.getVelocity();
        follower.setVelocity(leaderVelocity.x + inwardCorrection.x,
                followerVelocity.y,
                leaderVelocity.z + inwardCorrection.z);
        follower.velocityModified = true;
        return true;
    }

    private static void applyFollowerSoftConnection(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                    double defaultDistance) {
        double currentDistance = horizontalDistance(leader.getPos(), follower.getPos());
        double targetDistance = defaultDistance + STRETCH_DEADZONE;
        if (currentDistance <= targetDistance) {
            return;
        }

        Vec3d leaderToFollower = follower.getPos().subtract(leader.getPos());
        Vec3d radial = new Vec3d(leaderToFollower.x, 0.0D, leaderToFollower.z);
        double radialLength = radial.horizontalLength();
        if (radialLength <= 0.000001D) {
            return;
        }

        Vec3d radialUnit = radial.multiply(1.0D / radialLength);
        double overStretch = currentDistance - targetDistance;
        double correctionSpeed = Math.min(SOFT_CONNECTION_MAX_CORRECTION_SPEED,
                overStretch * SOFT_CONNECTION_STIFFNESS);
        Vec3d inwardCorrection = radialUnit.multiply(-correctionSpeed);
        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d followerVelocity = follower.getVelocity();
        double leaderVelocityWeight = MathHelper.clamp(overStretch / Math.max(0.000001D, MAX_STRETCH_EXTRA_DISTANCE),
                0.0D, 1.0D);
        leaderVelocityWeight = Math.max(SOFT_CONNECTION_MIN_LEADER_VELOCITY_WEIGHT, leaderVelocityWeight);

        follower.setVelocity(
                followerVelocity.x * (1.0D - leaderVelocityWeight) + leaderVelocity.x * leaderVelocityWeight + inwardCorrection.x,
                followerVelocity.y,
                followerVelocity.z * (1.0D - leaderVelocityWeight) + leaderVelocity.z * leaderVelocityWeight + inwardCorrection.z
        );
        follower.velocityModified = true;
    }

    private static void moveFollowerTowardDefaultSlot(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        Vec3d desired = leader.getPos().add(getDefaultFollowerOffset(leader));
        Vec3d delta = desired.subtract(follower.getPos());
        double distance = delta.horizontalLength();

        if (distance <= FOLLOW_STOP_DISTANCE) {
            return;
        }

        if (distance >= FOLLOW_TELEPORT_DISTANCE) {
            follower.requestTeleport(desired.x, follower.getY(), desired.z);
            follower.setVelocity(Vec3d.ZERO);
            return;
        }

        if (distance < FOLLOW_PULL_DISTANCE) {
            return;
        }

        Vec3d horizontalPull = clampHorizontalSpeed(new Vec3d(delta.x, 0.0D, delta.z)
                .multiply(FOLLOW_SMOOTHING), FOLLOW_MAX_SPEED);
        Vec3d currentVelocity = follower.getVelocity();
        follower.setVelocity(horizontalPull.x, currentVelocity.y, horizontalPull.z);
        follower.velocityModified = true;
    }

    private static void moveFollowerAroundLeader(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        Vec3d radius = follower.getPos().subtract(leader.getPos());
        Vec3d horizontalRadius = new Vec3d(radius.x, 0.0D, radius.z);
        double radiusLength = horizontalRadius.horizontalLength();

        if (radiusLength <= ORBIT_MIN_RADIUS) {
            return;
        }

        if (radiusLength >= FOLLOW_TELEPORT_DISTANCE) {
            moveFollowerTowardDefaultSlot(leader, follower);
            return;
        }

        Vec3d desiredDirection = getDefaultFollowerOffset(leader).normalize();
        Vec3d desired = leader.getPos().add(desiredDirection.multiply(radiusLength));
        Vec3d delta = desired.subtract(follower.getPos());
        Vec3d radiusUnit = horizontalRadius.normalize();
        double radialDelta = delta.dotProduct(radiusUnit);
        Vec3d tangentialDelta = new Vec3d(delta.x, 0.0D, delta.z).subtract(radiusUnit.multiply(radialDelta));

        if (tangentialDelta.horizontalLength() <= FOLLOW_STOP_DISTANCE) {
            return;
        }

        Vec3d horizontalPull = clampHorizontalSpeed(tangentialDelta.multiply(ORBIT_SMOOTHING), ORBIT_MAX_SPEED);
        Vec3d currentVelocity = follower.getVelocity();
        follower.setVelocity(horizontalPull.x, currentVelocity.y, horizontalPull.z);
        follower.velocityModified = true;
    }

    private static Vec3d clampHorizontalSpeed(Vec3d velocity, double maxSpeed) {
        double length = velocity.horizontalLength();
        if (length <= maxSpeed || length <= 0.000001D) {
            return velocity;
        }
        double scale = maxSpeed / length;
        return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    private static double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double maxStretchDistance(double defaultDistance) {
        return Math.max(defaultDistance + MAX_STRETCH_EXTRA_DISTANCE, defaultDistance + STRETCH_DEADZONE);
    }

    private static Vec3d getDefaultFollowerOffset(ServerPlayerEntity leader) {
        double yawRad = Math.toRadians(leader.getYaw());
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        return right.multiply(DEFAULT_FOLLOW_SIDE_OFFSET).add(forward.multiply(DEFAULT_FOLLOW_FORWARD_OFFSET));
    }

    private static double horizontalFollowerSlotAngleDegrees(ServerPlayerEntity leader, Vec3d leaderToFollower) {
        Vec3d horizontal = new Vec3d(leaderToFollower.x, 0.0D, leaderToFollower.z);
        if (horizontal.horizontalLengthSquared() <= 0.000001D) {
            return 0.0D;
        }

        double defaultYaw = localHorizontalYawDegrees(leader, getDefaultFollowerOffset(leader));
        double currentYaw = localHorizontalYawDegrees(leader, horizontal);
        return MathHelper.wrapDegrees(currentYaw - defaultYaw);
    }

    private static double horizontalArmAngleDegrees(ServerPlayerEntity player, Vec3d vectorToPartner) {
        Vec3d horizontal = new Vec3d(vectorToPartner.x, 0.0D, vectorToPartner.z);
        if (horizontal.horizontalLengthSquared() <= 0.000001D) {
            return 0.0D;
        }

        Vec3d defaultPartnerOffset = getDefaultFollowerOffset(player).multiply(-1.0D);
        double defaultYaw = localHorizontalYawDegrees(player, defaultPartnerOffset);
        double currentYaw = localHorizontalYawDegrees(player, horizontal);
        return MathHelper.wrapDegrees(currentYaw - defaultYaw);
    }

    private static double localHorizontalYawDegrees(ServerPlayerEntity player, Vec3d horizontal) {
        double yawRad = Math.toRadians(player.getYaw());
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        double localRight = horizontal.dotProduct(right);
        double localForward = horizontal.dotProduct(forward);
        return MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(localRight, localForward)));
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, UUID partnerUuid, double defaultDistance) {
    }
}
