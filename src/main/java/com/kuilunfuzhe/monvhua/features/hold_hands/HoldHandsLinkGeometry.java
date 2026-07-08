package com.kuilunfuzhe.monvhua.features.hold_hands;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class HoldHandsLinkGeometry {
    public static final Vec3d LEFT_SHOULDER = new Vec3d(-0.34D, 1.42D, 0.0D);
    public static final Vec3d RIGHT_SHOULDER = new Vec3d(0.34D, 1.42D, 0.0D);
    public static final Vec3d LEFT_PALM = new Vec3d(-0.56D, 0.78D, 0.04D);
    public static final Vec3d RIGHT_PALM = new Vec3d(0.56D, 0.78D, 0.04D);

    public static final double ARM_REACH = 0.72D;
    public static final double MIN_ARM_YAW = 0.0D;
    public static final double MAX_ARM_YAW = 165.0D;
    public static final double MIN_ARM_PITCH = -90.0D;
    public static final double MAX_ARM_PITCH = 90.0D;
    public static final double NATURAL_MAX_SHOULDER_DISTANCE = 1.26D;

    public static final double DEFAULT_FOLLOW_SIDE_OFFSET = -1.008708D;
    public static final double DEFAULT_FOLLOW_FORWARD_OFFSET = 0.089465946D;
    public static final double LINK_POSITION_STIFFNESS = 1.35D;
    public static final double LINK_MAX_SPEED = 1.65D;
    public static final double LINK_TELEPORT_DISTANCE = 3.0D;
    public static final double MIN_BODY_DISTANCE = 0.82D;
    public static final double WORLD_GRAVITY_ACCELERATION = -0.08D;

    private static final double ENDPOINT_MAX_HORIZONTAL_TRAIL = 0.38D;
    private static final double ENDPOINT_STRETCH_LIFT = 0.24D;
    private static final double ENDPOINT_FLIGHT_LIFT = 0.46D;
    private static final double ENDPOINT_GRAVITY_SCALE = 0.85D;

    private HoldHandsLinkGeometry() {
    }

    public static Vec3d shoulderSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_SHOULDER : RIGHT_SHOULDER;
    }

    public static Vec3d palmSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_PALM : RIGHT_PALM;
    }

    public static Vec3d bodyAnchor(Vec3d feetPos, float bodyYaw, Vec3d local) {
        return feetPos.add(bodyLocalToWorldVector(local, bodyYaw));
    }

    public static Vec3d shoulderWorld(Vec3d feetPos, float bodyYaw, HoldHandsSkeletalPose.HandSide side) {
        return bodyAnchor(feetPos, bodyYaw, shoulderSocket(side));
    }

    public static Vec3d palmWorld(Vec3d feetPos, float bodyYaw, HoldHandsSkeletalPose.HandSide side) {
        return bodyAnchor(feetPos, bodyYaw, palmSocket(side));
    }

    public static Vec3d defaultFollowerOffset(float leaderYaw) {
        return bodyLocalToWorldVector(new Vec3d(DEFAULT_FOLLOW_SIDE_OFFSET, 0.0D, DEFAULT_FOLLOW_FORWARD_OFFSET), leaderYaw);
    }

    public static Vec3d defaultEndpoint(Vec3d leaderFeet, float leaderYaw) {
        Vec3d leaderPalm = palmWorld(leaderFeet, leaderYaw, HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerPalm = palmWorld(leaderFeet.add(defaultFollowerOffset(leaderYaw)), leaderYaw,
                HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        return leaderPalm.add(followerPalm).multiply(0.5D);
    }

    public static Vec3d dynamicEndpoint(Vec3d leaderFeet, Vec3d followerFeet,
                                        Vec3d leaderVelocity, Vec3d followerVelocity,
                                        float leaderYaw, float stretch) {
        return dynamicEndpoint(leaderFeet, followerFeet, leaderVelocity, followerVelocity,
                leaderYaw, leaderYaw, stretch);
    }

    public static Vec3d dynamicEndpoint(Vec3d leaderFeet, Vec3d followerFeet,
                                        Vec3d leaderVelocity, Vec3d followerVelocity,
                                        float leaderYaw, float followerYaw, float stretch) {
        Vec3d base = palmWorld(leaderFeet, leaderYaw, HoldHandsSkeletalPose.ACTIVE_ROLE_HAND)
                .add(palmWorld(followerFeet, followerYaw, HoldHandsSkeletalPose.PASSIVE_ROLE_HAND))
                .multiply(0.5D);
        Vec3d leaderShoulder = shoulderWorld(leaderFeet, leaderYaw, HoldHandsSkeletalPose.ACTIVE_ROLE_HAND);
        Vec3d followerShoulder = shoulderWorld(followerFeet, followerYaw, HoldHandsSkeletalPose.PASSIVE_ROLE_HAND);
        Vec3d midpoint = leaderShoulder.add(followerShoulder).multiply(0.5D);

        Vec3d leaderMotion = leaderVelocity == null ? Vec3d.ZERO : leaderVelocity;
        Vec3d followerMotion = followerVelocity == null ? Vec3d.ZERO : followerVelocity;
        Vec3d averageMotion = leaderMotion.add(followerMotion).multiply(0.5D);
        Vec3d relativeMotion = leaderMotion.subtract(followerMotion);

        Vec3d horizontalTrail = Vec3d.ZERO;

        double speedWeight = smoothUnit((new Vec3d(averageMotion.x, 0.0D, averageMotion.z).length() - 0.025D) / 0.20D);
        double verticalWeight = smoothUnit((Math.abs(averageMotion.y) - 0.012D) / 0.08D);
        double gravitySag = WORLD_GRAVITY_ACCELERATION * ENDPOINT_GRAVITY_SCALE * (1.0D + stretch * 0.35D);
        double verticalMotion = smoothScalar(averageMotion.y, 0.012D, 0.12D) * 0.90D
                + smoothScalar(relativeMotion.y, 0.012D, 0.12D) * 0.22D;
        double flightLift = verticalWeight * ENDPOINT_FLIGHT_LIFT;
        double stretchLift = stretch * ENDPOINT_STRETCH_LIFT;

        Vec3d dynamicOffset = horizontalTrail.add(0.0D,
                verticalMotion + gravitySag + flightLift + stretchLift, 0.0D);
        double weight = MathHelper.clamp(0.14D + stretch * 0.54D + speedWeight * 0.04D
                + verticalWeight * 0.42D, 0.0D, 1.0D);
        return base.lerp(midpoint.add(dynamicOffset), weight);
    }

    public static Vec3d hardLinkedEndpoint(Vec3d firstShoulder, double firstReach,
                                           Vec3d secondShoulder, double secondReach,
                                           Vec3d desired) {
        Vec3d between = secondShoulder.subtract(firstShoulder);
        double distance = between.length();
        if (distance <= 0.000001D) {
            return pointOnSphere(desired, firstShoulder, Math.min(firstReach, secondReach));
        }

        Vec3d axis = between.multiply(1.0D / distance);
        double maxReach = firstReach + secondReach;
        if (distance >= maxReach) {
            Vec3d firstLimit = firstShoulder.add(axis.multiply(firstReach));
            Vec3d secondLimit = secondShoulder.subtract(axis.multiply(secondReach));
            return firstLimit.add(secondLimit).multiply(0.5D);
        }

        double minReach = Math.abs(firstReach - secondReach);
        if (distance <= minReach) {
            Vec3d firstLimit = pointOnSphere(desired, firstShoulder, firstReach);
            Vec3d secondLimit = pointOnSphere(desired, secondShoulder, secondReach);
            return firstLimit.add(secondLimit).multiply(0.5D);
        }

        double along = (firstReach * firstReach - secondReach * secondReach + distance * distance)
                / (2.0D * distance);
        double height = Math.sqrt(Math.max(0.0D, firstReach * firstReach - along * along));
        Vec3d base = firstShoulder.add(axis.multiply(along));
        Vec3d preferred = desired.subtract(base);
        Vec3d bend = preferred.subtract(axis.multiply(preferred.dotProduct(axis)));
        if (bend.lengthSquared() <= 0.000001D) {
            bend = new Vec3d(axis.z, 0.0D, -axis.x);
            if (bend.lengthSquared() <= 0.000001D) {
                bend = new Vec3d(1.0D, 0.0D, 0.0D);
            }
        }
        return base.add(bend.normalize().multiply(height));
    }

    public static Vec3d constrainEndpointForArm(Vec3d target, Vec3d shoulder, float bodyYaw,
                                                HoldHandsSkeletalPose.HandSide side, double reach) {
        Vec3d local = worldVectorToBodyLocal(target.subtract(shoulder), bodyYaw);
        ArmAngles angles = constrainedAngles(local, side);
        double length = MathHelper.clamp(local.length(), 0.001D, reach);
        return shoulder.add(bodyLocalToWorldVector(vectorFromAngles(angles.yaw(), angles.pitch(), length), bodyYaw));
    }

    public static ArmAngles constrainedAngles(Vec3d localTarget, HoldHandsSkeletalPose.HandSide side) {
        double horizontal = Math.sqrt(localTarget.x * localTarget.x + localTarget.z * localTarget.z);
        double yaw = Math.toDegrees(Math.atan2(localTarget.x, localTarget.z));
        double pitch = Math.toDegrees(Math.atan2(localTarget.y, horizontal));
        double sideSign = side == HoldHandsSkeletalPose.HandSide.LEFT ? -1.0D : 1.0D;
        double sideYaw = MathHelper.clamp(yaw * sideSign, MIN_ARM_YAW, MAX_ARM_YAW);
        return new ArmAngles(sideYaw * sideSign, MathHelper.clamp(pitch, MIN_ARM_PITCH, MAX_ARM_PITCH));
    }

    public static Vec3d bodyLocalToWorldVector(Vec3d local, float bodyYaw) {
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        return new Vec3d(
                local.x * rightX + local.z * forwardX,
                local.y,
                local.x * rightZ + local.z * forwardZ
        );
    }

    public static Vec3d worldVectorToBodyLocal(Vec3d world, float bodyYaw) {
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        return new Vec3d(
                world.x * rightX + world.z * rightZ,
                world.y,
                world.x * forwardX + world.z * forwardZ
        );
    }

    public static double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static Vec3d clampSpeed(Vec3d velocity, double maxSpeed) {
        double length = velocity.length();
        if (length <= maxSpeed || length <= 0.000001D) {
            return velocity;
        }
        return velocity.multiply(maxSpeed / length);
    }

    private static Vec3d clampVector(Vec3d vector, double maxLength) {
        double length = vector.length();
        if (length <= maxLength || length <= 0.000001D) {
            return vector;
        }
        return vector.multiply(maxLength / length);
    }

    private static Vec3d smoothVelocity(Vec3d velocity, double deadZone, double fullSpeed) {
        double length = velocity.length();
        if (length <= deadZone || length <= 0.000001D) {
            return Vec3d.ZERO;
        }
        double weight = smoothUnit((length - deadZone) / Math.max(0.000001D, fullSpeed - deadZone));
        return velocity.multiply(weight);
    }

    private static double smoothScalar(double value, double deadZone, double fullSpeed) {
        double abs = Math.abs(value);
        if (abs <= deadZone) {
            return 0.0D;
        }
        double weight = smoothUnit((abs - deadZone) / Math.max(0.000001D, fullSpeed - deadZone));
        return Math.copySign(abs * weight, value);
    }

    private static double smoothUnit(double value) {
        double t = MathHelper.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static Vec3d pointOnSphere(Vec3d desired, Vec3d center, double radius) {
        Vec3d delta = desired.subtract(center);
        if (delta.lengthSquared() <= 0.000001D) {
            return center.add(0.0D, -radius, 0.0D);
        }
        return center.add(delta.normalize().multiply(radius));
    }

    private static Vec3d vectorFromAngles(double yaw, double pitch, double length) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double flat = Math.cos(pitchRad) * length;
        return new Vec3d(
                Math.sin(yawRad) * flat,
                Math.sin(pitchRad) * length,
                Math.cos(yawRad) * flat
        );
    }

    public record ArmAngles(double yaw, double pitch) {
    }
}
