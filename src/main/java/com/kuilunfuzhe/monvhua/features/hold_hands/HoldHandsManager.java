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
    private static final double SHOULDER_SIDE_OFFSET = 0.34375D;
    private static final double ARM_REACH_LENGTH = 0.72D;
    private static final double HAND_CONNECTION_SLACK = 0.08D;
    private static final double FOLLOW_STOP_DISTANCE = 0.04D;
    private static final double FOLLOW_PULL_DISTANCE = 0.14D;
    private static final double FOLLOW_TELEPORT_DISTANCE = 3.0D;
    private static final double FOLLOW_MAX_SPEED = 0.68D;
    private static final double FOLLOW_SMOOTHING = 0.85D;
    private static final double MIN_PLAYER_BODY_DISTANCE = 0.82D;
    private static final double SIDE_LOCK_MIN_DISTANCE = 0.35D;
    private static final double SIDE_LOCK_STIFFNESS = 0.62D;
    private static final double SIDE_LOCK_MAX_SPEED = 0.42D;
    private static final double STRETCH_DEADZONE = 0.03D;
    private static final double MAX_STRETCH_EXTRA_DISTANCE = 0.38D;
    private static final double SOFT_CONNECTION_STIFFNESS = 0.95D;
    private static final double SOFT_CONNECTION_MAX_CORRECTION_SPEED = 0.55D;
    private static final double SOFT_CONNECTION_MIN_LEADER_VELOCITY_WEIGHT = 0.35D;
    private static final double HARD_CONNECTION_SLACK = 0.02D;
    private static final double HARD_CONNECTION_STIFFNESS = 1.65D;
    private static final double HARD_CONNECTION_MAX_CORRECTION_SPEED = 0.9D;
    private static final double BACKWARD_DOT_THRESHOLD = -0.015D;
    private static final double BACKWARD_SIDEWAYS_SPEED_SCALE = 0.85D;
    private static final double ARM_MIN_HORIZONTAL_ANGLE = 0.0D;
    private static final double ARM_MAX_HORIZONTAL_ANGLE = 135.0D;
    private static final double BODY_ROTATE_START_ANGLE = 110.0D;
    private static final double BODY_ROTATE_MAX_ANGLE = 135.0D;
    private static final float HOLD_BODY_YAW_STEP = 5.0F;
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
        float holdBodyYaw = initiator.getBodyYaw();
        ACTIVE.put(initiator.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.ACTIVE), target.getUuid(), defaultDistance, holdBodyYaw));
        ACTIVE.put(target.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.PASSIVE), initiator.getUuid(), defaultDistance, holdBodyYaw));
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

            float holdBodyYaw = updateHoldBodyYaw(leader, follower, data);
            syncFollowerBodyYaw(leader, follower, holdBodyYaw);
            movePairTowardHandConstraint(leader, follower, data.defaultDistance(), holdBodyYaw);
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

    private static float updateHoldBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, HoldHandData followerData) {
        float currentYaw = followerData.holdBodyYaw();
        float targetYaw = leader.getYaw();
        double armAngle = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        if (armAngle <= BODY_ROTATE_START_ANGLE) {
            return currentYaw;
        }

        double pressure = MathHelper.clamp((armAngle - BODY_ROTATE_START_ANGLE)
                / Math.max(0.000001D, BODY_ROTATE_MAX_ANGLE - BODY_ROTATE_START_ANGLE), 0.0D, 1.0D);
        float nextYaw = stepTowardsAngle(currentYaw, targetYaw, (float) (HOLD_BODY_YAW_STEP * pressure));
        setPairHoldBodyYaw(leader, follower, nextYaw);
        return nextYaw;
    }

    private static void setPairHoldBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        HoldHandData leaderData = ACTIVE.get(leader.getUuid());
        if (leaderData != null) {
            ACTIVE.put(leader.getUuid(), leaderData.withHoldBodyYaw(holdBodyYaw));
        }
        HoldHandData followerData = ACTIVE.get(follower.getUuid());
        if (followerData != null) {
            ACTIVE.put(follower.getUuid(), followerData.withHoldBodyYaw(holdBodyYaw));
        }
    }

    private static void syncFollowerBodyYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        float yaw = followerFacingYaw(leader, follower, holdBodyYaw);
        follower.setYaw(yaw);
        follower.setBodyYaw(yaw);
        follower.setHeadYaw(yaw);
    }

    private static void movePairTowardHandConstraint(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                     double defaultDistance, float holdBodyYaw) {
        applyFollowerSideLock(leader, follower, holdBodyYaw);
        boolean hardConnectionApplied = enforceFollowerHardConnection(leader, follower, defaultDistance, holdBodyYaw);

        Vec3d leaderToFollower = follower.getPos().subtract(leader.getPos());
        double leaderArmAngle = horizontalFollowerSlotAngleDegrees(holdBodyYaw, leaderToFollower);
        if (leaderArmAngle < ARM_MIN_HORIZONTAL_ANGLE || leaderArmAngle > ARM_MAX_HORIZONTAL_ANGLE) {
            moveFollowerAroundLeader(leader, follower, holdBodyYaw);
        } else {
            moveFollowerTowardDefaultSlot(leader, follower, holdBodyYaw);
        }

        if (!hardConnectionApplied) {
            applyFollowerSoftConnection(leader, follower, defaultDistance, holdBodyYaw);
        }
    }

    private static void applyFollowerSideLock(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d desiredOffset = getDefaultFollowerOffset(holdBodyYaw);
        Vec3d desiredSide = new Vec3d(desiredOffset.x, 0.0D, desiredOffset.z);
        if (desiredSide.horizontalLengthSquared() <= 0.000001D) {
            return;
        }

        Vec3d sideUnit = desiredSide.normalize();
        Vec3d leaderToFollower = follower.getPos().subtract(leader.getPos());
        Vec3d horizontal = new Vec3d(leaderToFollower.x, 0.0D, leaderToFollower.z);
        double sideDistance = horizontal.dotProduct(sideUnit);
        double correctionDistance = SIDE_LOCK_MIN_DISTANCE - sideDistance;
        if (correctionDistance <= 0.0D) {
            return;
        }

        Vec3d correction = sideUnit.multiply(Math.min(SIDE_LOCK_MAX_SPEED,
                correctionDistance * SIDE_LOCK_STIFFNESS));
        Vec3d currentVelocity = follower.getVelocity();
        follower.setVelocity(currentVelocity.x + correction.x, currentVelocity.y, currentVelocity.z + correction.z);
        follower.velocityModified = true;
    }

    private static boolean enforceFollowerHardConnection(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                         double defaultDistance, float holdBodyYaw) {
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
        if (currentDistance <= MIN_PLAYER_BODY_DISTANCE) {
            Vec3d followerVelocity = follower.getVelocity();
            Vec3d outwardCorrection = radialUnit.multiply((MIN_PLAYER_BODY_DISTANCE - currentDistance) * HARD_CONNECTION_STIFFNESS);
            follower.setVelocity(outwardCorrection.x, followerVelocity.y, outwardCorrection.z);
            follower.velocityModified = true;
            return true;
        }
        double overStretch = currentDistance - maxStretchDistance;
        double correctionSpeed = Math.min(HARD_CONNECTION_MAX_CORRECTION_SPEED,
                overStretch * HARD_CONNECTION_STIFFNESS);
        Vec3d inwardCorrection = radialUnit.multiply(-correctionSpeed);
        Vec3d leaderVelocity = followerDragVelocity(leader, follower, holdBodyYaw);
        Vec3d followerVelocity = follower.getVelocity();
        follower.setVelocity(leaderVelocity.x + inwardCorrection.x,
                followerVelocity.y,
                leaderVelocity.z + inwardCorrection.z);
        follower.velocityModified = true;
        return true;
    }

    private static void applyFollowerSoftConnection(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                                    double defaultDistance, float holdBodyYaw) {
        double currentDistance = horizontalDistance(leader.getPos(), follower.getPos());
        if (currentDistance <= MIN_PLAYER_BODY_DISTANCE) {
            return;
        }
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
        Vec3d leaderVelocity = followerDragVelocity(leader, follower, holdBodyYaw);
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

    private static boolean isLeaderMovingBackward(ServerPlayerEntity leader, float holdBodyYaw) {
        Vec3d velocity = leader.getVelocity();
        Vec3d horizontalVelocity = new Vec3d(velocity.x, 0.0D, velocity.z);
        if (horizontalVelocity.horizontalLengthSquared() <= 0.000001D) {
            return false;
        }
        return horizontalVelocity.dotProduct(forwardVector(holdBodyYaw)) < BACKWARD_DOT_THRESHOLD;
    }

    private static Vec3d followerDragVelocity(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d velocity = leader.getVelocity();
        Vec3d horizontalVelocity = new Vec3d(velocity.x, 0.0D, velocity.z);
        if (!isLeaderMovingBackward(leader, holdBodyYaw) || horizontalVelocity.horizontalLengthSquared() <= 0.000001D) {
            return horizontalVelocity;
        }

        Vec3d followerToLeader = leader.getPos().subtract(follower.getPos());
        Vec3d radial = new Vec3d(followerToLeader.x, 0.0D, followerToLeader.z);
        if (radial.horizontalLengthSquared() <= 0.000001D) {
            return Vec3d.ZERO;
        }

        Vec3d radialUnit = radial.normalize();
        double radialSpeed = horizontalVelocity.dotProduct(radialUnit);
        Vec3d tangentialVelocity = horizontalVelocity.subtract(radialUnit.multiply(radialSpeed));
        Vec3d sidewaysVelocity = tangentialVelocity.multiply(BACKWARD_SIDEWAYS_SPEED_SCALE);
        if (sidewaysVelocity.horizontalLengthSquared() <= 0.000001D) {
            sidewaysVelocity = sideVectorTowardLeader(leader, follower, holdBodyYaw)
                    .multiply(horizontalVelocity.horizontalLength() * BACKWARD_SIDEWAYS_SPEED_SCALE);
        }
        return sidewaysVelocity;
    }

    private static float followerFacingYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d toLeader = leader.getPos().subtract(follower.getPos());
        if (toLeader.horizontalLengthSquared() <= 0.000001D) {
            return holdBodyYaw;
        }

        if (isLeaderMovingBackward(leader, holdBodyYaw)) {
            return sidewaysDragYaw(leader, follower, holdBodyYaw);
        }

        float faceLeaderYaw = yawFromVector(toLeader);
        float faceLeaderPressure = MathHelper.clamp((float) (Math.abs(MathHelper.wrapDegrees(leader.getYaw() - holdBodyYaw))
                / BODY_ROTATE_MAX_ANGLE), 0.0F, 1.0F);
        return stepTowardsAngle(holdBodyYaw, faceLeaderYaw, 90.0F * faceLeaderPressure);
    }

    private static float sidewaysDragYaw(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d toLeader = leader.getPos().subtract(follower.getPos());
        if (toLeader.horizontalLengthSquared() <= 0.000001D) {
            return holdBodyYaw;
        }

        float faceLeaderYaw = yawFromVector(toLeader);
        float leftSideYaw = MathHelper.wrapDegrees(faceLeaderYaw - 90.0F);
        float rightSideYaw = MathHelper.wrapDegrees(faceLeaderYaw + 90.0F);
        float leaderYaw = MathHelper.wrapDegrees(holdBodyYaw);
        return Math.abs(MathHelper.wrapDegrees(leftSideYaw - leaderYaw))
                <= Math.abs(MathHelper.wrapDegrees(rightSideYaw - leaderYaw))
                ? leftSideYaw
                : rightSideYaw;
    }

    private static Vec3d sideVectorTowardLeader(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        float yaw = sidewaysDragYaw(leader, follower, holdBodyYaw);
        return forwardVector(yaw);
    }

    private static Vec3d forwardVector(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
    }

    private static float yawFromVector(Vec3d vector) {
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-vector.x, vector.z)));
    }

    private static float stepTowardsAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return MathHelper.wrapDegrees(target);
        }
        return MathHelper.wrapDegrees(current + Math.copySign(maxStep, delta));
    }

    private static void moveFollowerTowardDefaultSlot(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d desired = leader.getPos().add(getDefaultFollowerOffset(holdBodyYaw));
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

    private static void moveFollowerAroundLeader(ServerPlayerEntity leader, ServerPlayerEntity follower, float holdBodyYaw) {
        Vec3d radius = follower.getPos().subtract(leader.getPos());
        Vec3d horizontalRadius = new Vec3d(radius.x, 0.0D, radius.z);
        double radiusLength = horizontalRadius.horizontalLength();

        if (radiusLength <= ORBIT_MIN_RADIUS) {
            return;
        }

        if (radiusLength >= FOLLOW_TELEPORT_DISTANCE) {
            moveFollowerTowardDefaultSlot(leader, follower, holdBodyYaw);
            return;
        }

        Vec3d desiredDirection = getDefaultFollowerOffset(holdBodyYaw).normalize();
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
        double armLimitedDistance = DEFAULT_FOLLOW_SIDE_OFFSET
                + ARM_REACH_LENGTH * 2.0D
                - SHOULDER_SIDE_OFFSET * 2.0D
                + HAND_CONNECTION_SLACK;
        double legacyDistance = defaultDistance + MAX_STRETCH_EXTRA_DISTANCE;
        return Math.max(defaultDistance + STRETCH_DEADZONE, Math.min(legacyDistance, armLimitedDistance));
    }

    private static Vec3d getDefaultFollowerOffset(float holdBodyYaw) {
        double yawRad = Math.toRadians(holdBodyYaw);
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        return right.multiply(DEFAULT_FOLLOW_SIDE_OFFSET).add(forward.multiply(DEFAULT_FOLLOW_FORWARD_OFFSET));
    }

    private static double horizontalFollowerSlotAngleDegrees(float holdBodyYaw, Vec3d leaderToFollower) {
        Vec3d horizontal = new Vec3d(leaderToFollower.x, 0.0D, leaderToFollower.z);
        if (horizontal.horizontalLengthSquared() <= 0.000001D) {
            return 0.0D;
        }

        double defaultYaw = localHorizontalYawDegrees(holdBodyYaw, getDefaultFollowerOffset(holdBodyYaw));
        double currentYaw = localHorizontalYawDegrees(holdBodyYaw, horizontal);
        return MathHelper.wrapDegrees(currentYaw - defaultYaw);
    }

    private static double horizontalArmAngleDegrees(ServerPlayerEntity player, Vec3d vectorToPartner) {
        Vec3d horizontal = new Vec3d(vectorToPartner.x, 0.0D, vectorToPartner.z);
        if (horizontal.horizontalLengthSquared() <= 0.000001D) {
            return 0.0D;
        }

        Vec3d defaultPartnerOffset = getDefaultFollowerOffset(player.getBodyYaw()).multiply(-1.0D);
        double defaultYaw = localHorizontalYawDegrees(player, defaultPartnerOffset);
        double currentYaw = localHorizontalYawDegrees(player, horizontal);
        return MathHelper.wrapDegrees(currentYaw - defaultYaw);
    }

    private static double localHorizontalYawDegrees(ServerPlayerEntity player, Vec3d horizontal) {
        return localHorizontalYawDegrees(player.getBodyYaw(), horizontal);
    }

    private static double localHorizontalYawDegrees(float bodyYaw, Vec3d horizontal) {
        double yawRad = Math.toRadians(bodyYaw);
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        double localRight = horizontal.dotProduct(right);
        double localForward = horizontal.dotProduct(forward);
        return MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(localRight, localForward)));
    }

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, UUID partnerUuid, double defaultDistance,
                                float holdBodyYaw) {
        private HoldHandData withHoldBodyYaw(float holdBodyYaw) {
            return new HoldHandData(handSide, partnerUuid, defaultDistance, holdBodyYaw);
        }
    }
}
