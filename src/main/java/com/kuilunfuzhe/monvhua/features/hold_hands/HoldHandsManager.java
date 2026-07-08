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
    private static final double TAUT_POSITION_STIFFNESS = 1.75D;
    private static final double TAUT_MAX_SPEED = 2.25D;
    private static final double SHARED_POINT_DEADBAND = 0.018D;
    private static final double SHARED_POINT_GROUNDED_ALPHA = 0.16D;
    private static final double SHARED_POINT_AIRBORNE_ALPHA = 0.55D;
    private static final double SHARED_POINT_FAST_DISTANCE = 0.50D;

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
                defaultDistance, holdBodyYaw, sharedHandPoint));
        ACTIVE.put(target.getUuid(), new HoldHandData(
                HoldHandsSkeletalPose.handForRole(HoldHandsSkeletalPose.HoldRole.PASSIVE), initiator.getUuid(),
                defaultDistance, holdBodyYaw, sharedHandPoint));
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
        float targetYaw = leader.getYaw();
        double armAngle = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        if (armAngle <= BODY_ROTATE_START_ANGLE) {
            return currentYaw;
        }

        double pressure = MathHelper.clamp((armAngle - BODY_ROTATE_START_ANGLE)
                / Math.max(0.000001D, BODY_ROTATE_MAX_ANGLE - BODY_ROTATE_START_ANGLE), 0.0D, 1.0D);
        float nextYaw = stepTowardsAngle(currentYaw, targetYaw, (float) (HOLD_BODY_YAW_STEP * pressure));
        setPairHoldBodyYaw(leader, follower, nextYaw);
        sync(leader, true);
        sync(follower, true);
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
        double alpha = distance >= SHARED_POINT_FAST_DISTANCE ? 1.0D
                : airborne ? SHARED_POINT_AIRBORNE_ALPHA : SHARED_POINT_GROUNDED_ALPHA;
        return previous.lerp(target, alpha);
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

        if (delta.length() > RIGID_TELEPORT_EPSILON) {
            follower.requestTeleport(desiredFeet.x, desiredFeet.y, desiredFeet.z);
        }

        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d correction = HoldHandsLinkGeometry.clampSpeed(
                delta.multiply(tautLink ? TAUT_POSITION_STIFFNESS : HoldHandsLinkGeometry.LINK_POSITION_STIFFNESS),
                tautLink ? TAUT_MAX_SPEED : HoldHandsLinkGeometry.LINK_MAX_SPEED);
        Vec3d targetVelocity = leaderVelocity.add(correction);
        follower.setVelocity(targetVelocity);
        follower.fallDistance = leader.fallDistance;
        follower.velocityModified = true;
        return solveSharedHandPoint(leader.getPos(), desiredFeet, leaderVelocity, targetVelocity,
                leaderBodyYaw, followerBodyYaw, defaultDistance);
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

        Vec3d currentOffset = follower.getPos().subtract(leader.getPos());
        Vec3d lag = currentOffset.subtract(HoldHandsLinkGeometry.defaultFollowerOffset(holdBodyYaw));
        return defaultFeet.add(positionalVelocity.multiply(0.55D)).add(lag.multiply(0.10D));
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

    private record HoldHandData(HoldHandsSkeletalPose.HandSide handSide, UUID partnerUuid, double defaultDistance,
                                float holdBodyYaw, Vec3d sharedHandPoint) {
        private HoldHandData withHoldBodyYaw(float holdBodyYaw) {
            return new HoldHandData(handSide, partnerUuid, defaultDistance, holdBodyYaw, sharedHandPoint);
        }

        private HoldHandData withSharedHandPoint(Vec3d sharedHandPoint) {
            return new HoldHandData(handSide, partnerUuid, defaultDistance, holdBodyYaw,
                    sharedHandPoint == null ? Vec3d.ZERO : sharedHandPoint);
        }
    }
}
