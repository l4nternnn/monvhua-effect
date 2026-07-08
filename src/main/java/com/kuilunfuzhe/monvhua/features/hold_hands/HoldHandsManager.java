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
    private static final double FOLLOW_POSITION_STEP_DEADBAND = 0.20D;
    private static final double TAUT_POSITION_STEP_DEADBAND = 0.08D;
    private static final double FOLLOW_POSITION_MIN_STEP = 0.08D;
    private static final double FOLLOW_POSITION_MAX_STEP = 0.44D;
    private static final double TAUT_POSITION_MAX_STEP = 0.62D;
    private static final double SHARED_POINT_DEADBAND = 0.018D;
    private static final double SHARED_POINT_GROUNDED_ALPHA = 0.16D;
    private static final double SHARED_POINT_AIRBORNE_ALPHA = 0.55D;
    private static final double SHARED_POINT_FAST_DISTANCE = 0.50D;
    private static final double FOLLOW_YAW_PROGRESS_PER_BLOCK = 0.125D;
    private static final double FOLLOW_YAW_MOVE_DEADBAND = 0.003D;
    private static final float FOLLOW_YAW_MAX_STEP = 8.0F;

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
        float holdBodyYaw = initiator.getBodyYaw();
        double defaultDistance = Math.max(0.001D, HoldHandsLinkGeometry.horizontalDistance(initiator.getPos(), target.getPos()));
        Vec3d sharedHandPoint = solveSharedHandPoint(initiator, target, defaultDistance);
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
            applyFollowerBodyYaw(follower, holdBodyYaw);
            Vec3d sharedHandPoint = enforceRigidLink(leader, follower, holdBodyYaw, data.defaultDistance());
            setPairSharedHandPoint(leader, follower, sharedHandPoint);
            sync(leader, true);
            sync(follower, true);
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
        float holdBodyYaw = data != null ? data.holdBodyYaw() : 0.0F;
        Vec3d sharedHandPoint = data != null ? data.sharedHandPoint() : Vec3d.ZERO;
        return new HoldHandsSyncS2CPacket(player.getId(), active, side, partnerId, defaultDistance, holdBodyYaw,
                (float) sharedHandPoint.x, (float) sharedHandPoint.y, (float) sharedHandPoint.z);
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
        float targetYaw = leader.getBodyYaw();
        Vec3d previousLeaderPos = followerData.lastLeaderPos();
        Vec3d currentLeaderPos = leader.getPos();
        double moved = horizontalDistance(previousLeaderPos, currentLeaderPos);
        float nextYaw = currentYaw;
        if (moved > FOLLOW_YAW_MOVE_DEADBAND) {
            double progress = MathHelper.clamp(moved * FOLLOW_YAW_PROGRESS_PER_BLOCK, 0.0D, 1.0D);
            float maxStep = (float) Math.min(FOLLOW_YAW_MAX_STEP, Math.max(0.0D,
                    Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw)) * progress));
            nextYaw = stepTowardsAngle(currentYaw, targetYaw, maxStep);
        }

        setPairHoldBodyYaw(leader, follower, nextYaw, currentLeaderPos);
        if (Math.abs(MathHelper.wrapDegrees(nextYaw - currentYaw)) > 0.001F) {
            sync(leader, true);
            sync(follower, true);
        }
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
        follower.setYaw(yaw);
        follower.setBodyYaw(yaw);
        follower.setHeadYaw(yaw);
    }

    private static void setPairSharedHandPoint(ServerPlayerEntity leader, ServerPlayerEntity follower, Vec3d sharedHandPoint) {
        HoldHandData leaderData = ACTIVE.get(leader.getUuid());
        HoldHandData followerData = ACTIVE.get(follower.getUuid());
        Vec3d previous = leaderData != null ? leaderData.sharedHandPoint()
                : followerData != null ? followerData.sharedHandPoint() : null;
        Vec3d stabilized = stabilizeSharedHandPoint(previous, sharedHandPoint, leader, follower);
        if (leaderData != null) {
            ACTIVE.put(leader.getUuid(), leaderData.withSharedHandPoint(stabilized));
        }
        if (followerData != null) {
            ACTIVE.put(follower.getUuid(), followerData.withSharedHandPoint(stabilized));
        }
    }

    private static Vec3d stabilizeSharedHandPoint(Vec3d previous, Vec3d target,
                                                  ServerPlayerEntity leader, ServerPlayerEntity follower) {
        if (target == null) {
            return Vec3d.ZERO;
        }
        if (previous == null || previous.lengthSquared() <= 0.000001D) {
            return target;
        }

        double distance = previous.distanceTo(target);
        if (distance < SHARED_POINT_DEADBAND) {
            return previous;
        }

        boolean airborne = (leader != null && (!leader.isOnGround() || leader.getAbilities().flying))
                || (follower != null && (!follower.isOnGround() || follower.getAbilities().flying));
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

    private static Vec3d enforceRigidLink(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                          float holdBodyYaw, double defaultDistance) {
        float leaderBodyYaw = leader.getBodyYaw();
        float followerBodyYaw = follower.getBodyYaw();
        Vec3d leaderShoulder = HoldHandsLinkGeometry.shoulderWorld(leader.getPos(), leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = HoldHandsLinkGeometry.shoulderWorld(follower.getPos(), followerBodyYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        double shoulderDistance = leaderShoulder.distanceTo(followerShoulder);
        double maxLinkDistance = HoldHandsLinkGeometry.NATURAL_MAX_SHOULDER_DISTANCE;
        double bodyDistance = HoldHandsLinkGeometry.horizontalDistance(leader.getPos(), follower.getPos());
        double tautness = MathHelper.clamp(shoulderDistance / Math.max(0.000001D, maxLinkDistance), 0.0D, 1.0D);
        boolean tautLink = tautness >= TAUT_FORCE_START;

        Vec3d desiredFeet = desiredFollowerFeet(leader, follower, holdBodyYaw);
        if (shoulderDistance > maxLinkDistance + LINK_SLACK || tautLink) {
            desiredFeet = projectFollowerToShoulderDistance(leader, follower, leaderBodyYaw, followerBodyYaw,
                    maxLinkDistance * (tautLink ? TAUT_TARGET_DISTANCE_BIAS : 1.0D));
        } else if (shoulderDistance < LINK_SLACK || bodyDistance < HoldHandsLinkGeometry.MIN_BODY_DISTANCE) {
            desiredFeet = projectFollowerToMinimumBodyDistance(leader, follower, holdBodyYaw);
        }

        Vec3d delta = desiredFeet.subtract(follower.getPos());
        if (delta.length() >= HoldHandsLinkGeometry.LINK_TELEPORT_DISTANCE) {
            follower.requestTeleport(desiredFeet.x, desiredFeet.y, desiredFeet.z);
            follower.setVelocity(leader.getVelocity());
            follower.fallDistance = leader.fallDistance;
            follower.velocityModified = true;
            return solveSharedHandPoint(leader.getPos(), desiredFeet, leader.getVelocity(), leader.getVelocity(),
                    leaderBodyYaw, followerBodyYaw, defaultDistance);
        }

        delta = applyFollowerPositionStep(leader, follower, desiredFeet, delta, tautLink);
        Vec3d predictedFollowerFeet = desiredFeet.subtract(delta);

        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d correction = HoldHandsLinkGeometry.clampSpeed(
                delta.multiply(tautLink ? TAUT_POSITION_STIFFNESS : FOLLOW_POSITION_STIFFNESS),
                tautLink ? TAUT_MAX_SPEED : FOLLOW_MAX_CORRECTION_SPEED);
        Vec3d targetVelocity = leaderVelocity.add(correction);
        follower.setVelocity(targetVelocity);
        follower.fallDistance = leader.fallDistance;
        follower.velocityModified = true;
        return solveSharedHandPoint(leader.getPos(), predictedFollowerFeet, leaderVelocity, targetVelocity,
                leaderBodyYaw, followerBodyYaw, defaultDistance);
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

        follower.requestTeleport(nextX, nextY, nextZ);
        return desiredFeet.subtract(new Vec3d(nextX, nextY, nextZ));
    }

    private static Vec3d solveSharedHandPoint(ServerPlayerEntity leader, ServerPlayerEntity follower,
                                              double defaultDistance) {
        return solveSharedHandPoint(leader.getPos(), follower.getPos(), leader.getVelocity(), follower.getVelocity(),
                leader.getBodyYaw(), follower.getBodyYaw(), defaultDistance);
    }

    private static Vec3d solveSharedHandPoint(Vec3d leaderFeet, Vec3d followerFeet,
                                              Vec3d leaderVelocity, Vec3d followerVelocity,
                                              float leaderBodyYaw, float followerBodyYaw, double defaultDistance) {
        Vec3d leaderShoulder = HoldHandsLinkGeometry.shoulderWorld(leaderFeet, leaderBodyYaw,
                HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = HoldHandsLinkGeometry.shoulderWorld(followerFeet, followerBodyYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        float stretch = stretchRatio(leaderFeet, followerFeet, defaultDistance);
        Vec3d desired = HoldHandsLinkGeometry.dynamicEndpoint(leaderFeet, followerFeet,
                leaderVelocity, followerVelocity, leaderBodyYaw, followerBodyYaw, stretch);
        Vec3d target = HoldHandsLinkGeometry.hardLinkedEndpoint(leaderShoulder, HoldHandsLinkGeometry.ARM_REACH,
                followerShoulder, HoldHandsLinkGeometry.ARM_REACH, desired);

        for (int i = 0; i < 3; i++) {
            Vec3d leaderTarget = HoldHandsLinkGeometry.constrainEndpointForArm(target, leaderShoulder, leaderBodyYaw,
                    HoldHandsSkeletalPose.ACTIVE_ROLE_HAND, HoldHandsLinkGeometry.ARM_REACH);
            Vec3d followerTarget = HoldHandsLinkGeometry.constrainEndpointForArm(target, followerShoulder, followerBodyYaw,
                    HoldHandsSkeletalPose.PASSIVE_ROLE_HAND, HoldHandsLinkGeometry.ARM_REACH);
            desired = leaderTarget.add(followerTarget).multiply(0.5D);
            target = HoldHandsLinkGeometry.hardLinkedEndpoint(leaderShoulder, HoldHandsLinkGeometry.ARM_REACH,
                    followerShoulder, HoldHandsLinkGeometry.ARM_REACH, desired);
        }

        return target;
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

    private static boolean shouldUseVerticalPositionLead(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        return (leader != null && (!leader.isOnGround() || leader.getAbilities().flying))
                || (follower != null && (!follower.isOnGround() || follower.getAbilities().flying));
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
}
