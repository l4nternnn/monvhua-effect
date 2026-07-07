package com.kuilunfuzhe.monvhua.features.hold_hands;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

final class HoldHandsSharedTargetSolver {
    private static final Vec3d LEFT_SHOULDER = new Vec3d(-0.34D, 1.42D, 0.0D);
    private static final Vec3d RIGHT_SHOULDER = new Vec3d(0.34D, 1.42D, 0.0D);
    private static final Vec3d LEFT_ELBOW = new Vec3d(-0.50D, 1.06D, 0.04D);
    private static final Vec3d RIGHT_ELBOW = new Vec3d(0.50D, 1.06D, 0.04D);
    private static final Vec3d LEFT_PALM = new Vec3d(-0.56D, 0.78D, 0.04D);
    private static final Vec3d RIGHT_PALM = new Vec3d(0.56D, 0.78D, 0.04D);

    private static final double DEFAULT_FOLLOW_SIDE_OFFSET = 1.008708D;
    private static final double DEFAULT_FOLLOW_FORWARD_OFFSET = 0.089465946D;
    private static final double STRETCH_DEADZONE = 0.08D;
    private static final double MAX_STRETCH_EXTRA_DISTANCE = 0.9D;
    private static final double TARGET_LIFT = 0.24D;
    private static final double BACK_SWING_DISTANCE = 0.34D;
    private static final double BACK_SWING_SPEED_SCALE = 1.65D;
    private static final double MOVEMENT_SWING_SPEED = 0.045D;
    private static final double TORSO_MIN_Y = 0.86D;
    private static final double TORSO_MAX_Y = 1.58D;
    private static final double TORSO_HALF_WIDTH = 0.34D;
    private static final double TORSO_HALF_DEPTH = 0.30D;
    private static final double BODY_CLEARANCE = 0.12D;
    private static final double MIN_TARGET_Y = 0.74D;
    private static final double MAX_TARGET_Y = 1.34D;
    private static final Map<Long, CachedTarget> TARGET_CACHE = new HashMap<>();

    private HoldHandsSharedTargetSolver() {
    }

    static Vec3d solve(Entity self, Entity partner, float selfBodyYaw,
                       HoldHandsSkeletalPose.HandSide selfSide, double defaultDistance) {
        Entity leader = selfSide == HoldHandsSkeletalPose.ACTIVE_ROLE_HAND ? self : partner;
        Entity follower = selfSide == HoldHandsSkeletalPose.ACTIVE_ROLE_HAND ? partner : self;
        float leaderYaw = bodyYawFor(leader, self, selfBodyYaw);
        float followerYaw = bodyYawFor(follower, self, selfBodyYaw);
        HoldHandsSkeletalPose.HandSide leaderSide = HoldHandsSkeletalPose.ACTIVE_ROLE_HAND;
        HoldHandsSkeletalPose.HandSide followerSide = HoldHandsSkeletalPose.PASSIVE_ROLE_HAND;
        long cacheKey = cacheKey(leader.getId(), follower.getId());
        long tick = self.getWorld().getTime();
        CachedTarget cached = TARGET_CACHE.get(cacheKey);
        if (cached != null && cached.tick() == tick) {
            return cached.target();
        }

        Vec3d leaderShoulder = bodyAnchor(leader.getPos(), leaderYaw, shoulderSocket(leaderSide));
        Vec3d followerShoulder = bodyAnchor(follower.getPos(), followerYaw, shoulderSocket(followerSide));
        double leaderReach = armReach(leaderSide);
        double followerReach = armReach(followerSide);

        float stretch = stretchRatio(follower.getPos().subtract(leader.getPos()), defaultDistance);
        Vec3d preference = preferredTarget(leader, follower, leaderYaw, followerYaw, leaderSide, followerSide, stretch);
        Vec3d target = closestPointInReachIntersection(leaderShoulder, leaderReach, followerShoulder, followerReach, preference);

        for (int i = 0; i < 2; i++) {
            target = keepOutsideBody(target, follower, followerYaw, followerSide);
            target = keepOutsideBody(target, leader, leaderYaw, leaderSide);
            target = clampHeight(target, leader, follower);
            target = clampToReach(target, leaderShoulder, leaderReach);
            target = clampToReach(target, followerShoulder, followerReach);
        }
        TARGET_CACHE.put(cacheKey, new CachedTarget(tick, target));
        return target;
    }

    private static Vec3d preferredTarget(Entity leader, Entity follower, float leaderYaw, float followerYaw,
                                         HoldHandsSkeletalPose.HandSide leaderSide,
                                         HoldHandsSkeletalPose.HandSide followerSide, float stretch) {
        Vec3d leaderPalm = palmAnchor(leader.getPos(), leaderYaw, leaderSide);
        Vec3d followerPalm = palmAnchor(follower.getPos(), followerYaw, followerSide);
        Vec3d defaultLeaderPalm = palmAnchor(leader.getPos(), leaderYaw, leaderSide);
        Vec3d defaultFollowerPalm = palmAnchor(leader.getPos().add(defaultFollowerOffset(leaderYaw)), leaderYaw, followerSide);

        Vec3d defaultShared = defaultLeaderPalm.add(defaultFollowerPalm).multiply(0.5D);
        Vec3d currentShared = leaderPalm.add(followerPalm).multiply(0.5D)
                .add(backSwingOffset(leader, follower, leaderYaw, stretch))
                .add(0.0D, stretch * TARGET_LIFT, 0.0D);
        double movement = movementFactor(leader);
        double dynamicWeight = MathHelper.clamp(0.35D + Math.max(stretch, movement) * 0.65D, 0.0D, 1.0D);
        return defaultShared.lerp(currentShared, dynamicWeight);
    }

    private static Vec3d backSwingOffset(Entity leader, Entity follower, float leaderYaw, float stretch) {
        Vec3d leaderVelocity = leader.getVelocity();
        Vec3d horizontalVelocity = new Vec3d(leaderVelocity.x, 0.0D, leaderVelocity.z);
        Vec3d defaultFollower = defaultFollowerOffset(leaderYaw);
        Vec3d currentFollower = follower.getPos().subtract(leader.getPos());
        Vec3d lag = new Vec3d(currentFollower.x - defaultFollower.x, 0.0D, currentFollower.z - defaultFollower.z);
        Vec3d desired = horizontalVelocity.multiply(-BACK_SWING_SPEED_SCALE).add(lag.multiply(0.35D));
        double length = desired.horizontalLength();
        if (length <= 0.000001D) {
            return Vec3d.ZERO;
        }
        double scale = Math.min(BACK_SWING_DISTANCE, length) / length;
        return desired.multiply(scale * Math.max(MathHelper.clamp(stretch, 0.0F, 1.0F), movementFactor(leader)));
    }

    private static double movementFactor(Entity leader) {
        Vec3d velocity = leader.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        return MathHelper.clamp(speed / MOVEMENT_SWING_SPEED, 0.0D, 1.0D);
    }

    private static Vec3d closestPointInReachIntersection(Vec3d firstCenter, double firstRadius,
                                                         Vec3d secondCenter, double secondRadius,
                                                         Vec3d preference) {
        Vec3d between = secondCenter.subtract(firstCenter);
        double distance = between.length();
        if (distance <= 0.000001D) {
            return clampToReach(preference, firstCenter, Math.min(firstRadius, secondRadius));
        }

        Vec3d axis = between.multiply(1.0D / distance);
        if (distance >= firstRadius + secondRadius) {
            Vec3d firstLimit = firstCenter.add(axis.multiply(firstRadius));
            Vec3d secondLimit = secondCenter.subtract(axis.multiply(secondRadius));
            return firstLimit.add(secondLimit).multiply(0.5D);
        }

        if (distance <= Math.abs(firstRadius - secondRadius)) {
            double radius = Math.min(firstRadius, secondRadius);
            Vec3d smallerCenter = firstRadius <= secondRadius ? firstCenter : secondCenter;
            return clampToReach(preference, smallerCenter, radius);
        }

        double along = (distance * distance + firstRadius * firstRadius - secondRadius * secondRadius)
                / (2.0D * distance);
        Vec3d circleCenter = firstCenter.add(axis.multiply(along));
        double circleRadius = Math.sqrt(Math.max(0.0D, firstRadius * firstRadius - along * along));
        Vec3d preferenceDelta = preference.subtract(circleCenter);
        Vec3d planeDelta = preferenceDelta.subtract(axis.multiply(preferenceDelta.dotProduct(axis)));
        if (planeDelta.lengthSquared() <= 0.000001D) {
            planeDelta = new Vec3d(axis.z, 0.0D, -axis.x);
            if (planeDelta.lengthSquared() <= 0.000001D) {
                planeDelta = new Vec3d(1.0D, 0.0D, 0.0D);
            }
        }
        return circleCenter.add(planeDelta.normalize().multiply(circleRadius));
    }

    private static Vec3d keepOutsideBody(Vec3d worldTarget, Entity player, float bodyYaw,
                                         HoldHandsSkeletalPose.HandSide handSide) {
        Vec3d local = worldVectorToBodyLocal(worldTarget.subtract(player.getPos()), bodyYaw);
        double clampedY = MathHelper.clamp(local.y, MIN_TARGET_Y, MAX_TARGET_Y);
        double sideSign = Math.signum(palmSocket(handSide).x);
        if (sideSign == 0.0D) {
            sideSign = handSide == HoldHandsSkeletalPose.HandSide.LEFT ? -1.0D : 1.0D;
        }

        boolean insideTorsoHeight = clampedY >= TORSO_MIN_Y && clampedY <= TORSO_MAX_Y;
        boolean insideTorsoWidth = Math.abs(local.x) < TORSO_HALF_WIDTH + BODY_CLEARANCE;
        boolean insideTorsoDepth = Math.abs(local.z) < TORSO_HALF_DEPTH + BODY_CLEARANCE;
        double x = local.x;
        if (insideTorsoHeight && insideTorsoWidth && insideTorsoDepth) {
            x = sideSign * (TORSO_HALF_WIDTH + BODY_CLEARANCE);
        } else if (Math.signum(x) != sideSign && Math.abs(x) < TORSO_HALF_WIDTH) {
            x = sideSign * TORSO_HALF_WIDTH;
        }

        Vec3d adjustedLocal = new Vec3d(x, clampedY, local.z);
        return player.getPos().add(bodyLocalToWorldVector(adjustedLocal, bodyYaw));
    }

    private static Vec3d clampHeight(Vec3d target, Entity first, Entity second) {
        double baseY = Math.max(first.getY(), second.getY());
        double y = MathHelper.clamp(target.y, baseY + MIN_TARGET_Y, baseY + MAX_TARGET_Y);
        return new Vec3d(target.x, y, target.z);
    }

    private static Vec3d clampToReach(Vec3d target, Vec3d shoulder, double reach) {
        Vec3d delta = target.subtract(shoulder);
        double length = delta.length();
        if (length <= reach || length <= 0.000001D) {
            return target;
        }
        return shoulder.add(delta.multiply(reach / length));
    }

    private static Vec3d palmAnchor(Vec3d feetPos, float bodyYaw, HoldHandsSkeletalPose.HandSide side) {
        return bodyAnchor(feetPos, bodyYaw, palmSocket(side));
    }

    private static Vec3d bodyAnchor(Vec3d feetPos, float bodyYaw, Vec3d local) {
        return feetPos.add(bodyLocalToWorldVector(local, bodyYaw));
    }

    private static Vec3d bodyLocalToWorldVector(Vec3d local, float bodyYaw) {
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        return new Vec3d(
                local.x * rightX + local.z * forwardX,
                local.y,
                local.x * rightZ + local.z * forwardZ
        );
    }

    private static Vec3d worldVectorToBodyLocal(Vec3d world, float bodyYaw) {
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        return new Vec3d(
                world.x * rightX + world.z * rightZ,
                world.y,
                world.x * forwardX + world.z * forwardZ
        );
    }

    private static Vec3d defaultFollowerOffset(float leaderYaw) {
        double yawRad = Math.toRadians(leaderYaw);
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        return right.multiply(DEFAULT_FOLLOW_SIDE_OFFSET).add(forward.multiply(DEFAULT_FOLLOW_FORWARD_OFFSET));
    }

    private static float stretchRatio(Vec3d delta, double defaultDistance) {
        double currentDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double stretchStart = Math.max(0.000001D, defaultDistance) + STRETCH_DEADZONE;
        if (currentDistance <= stretchStart) {
            return 0.0F;
        }

        double stretchLength = Math.max(0.000001D, MAX_STRETCH_EXTRA_DISTANCE - STRETCH_DEADZONE);
        return MathHelper.clamp((float) ((currentDistance - stretchStart) / stretchLength), 0.0F, 1.0F);
    }

    private static float bodyYawFor(Entity entity, Entity self, float selfBodyYaw) {
        return entity == self ? selfBodyYaw : entity.getYaw();
    }

    private static double armReach(HoldHandsSkeletalPose.HandSide side) {
        return elbowSocket(side).subtract(shoulderSocket(side)).length()
                + palmSocket(side).subtract(elbowSocket(side)).length();
    }

    private static Vec3d shoulderSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_SHOULDER : RIGHT_SHOULDER;
    }

    private static Vec3d elbowSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_ELBOW : RIGHT_ELBOW;
    }

    private static Vec3d palmSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_PALM : RIGHT_PALM;
    }

    private static long cacheKey(int firstId, int secondId) {
        int min = Math.min(firstId, secondId);
        int max = Math.max(firstId, secondId);
        return ((long) min << 32) ^ (max & 0xffffffffL);
    }

    private record CachedTarget(long tick, Vec3d target) {
    }
}
