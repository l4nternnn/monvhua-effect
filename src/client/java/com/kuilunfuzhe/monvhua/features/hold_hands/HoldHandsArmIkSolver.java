package com.kuilunfuzhe.monvhua.features.hold_hands;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

final class HoldHandsArmIkSolver {
    private static final float MIN_HORIZONTAL_DEGREES = -90.0F;
    private static final float MAX_HORIZONTAL_DEGREES = 135.0F;
    private static final float MIN_PITCH_DEGREES = -90.0F;
    private static final float MAX_PITCH_DEGREES = 90.0F;
    private static final float MIN_ELBOW_DEGREES = 0.0F;
    private static final float MAX_ELBOW_DEGREES = 92.0F;
    private static final float TARGET_LIFT = 0.34F;
    private static final float STRETCH_DEADZONE = 0.08F;
    private static final float MAX_STRETCH_EXTRA_DISTANCE = 0.9F;

    private static final Vec3d LEFT_SHOULDER = new Vec3d(-0.34D, 1.42D, 0.0D);
    private static final Vec3d RIGHT_SHOULDER = new Vec3d(0.34D, 1.42D, 0.0D);
    private static final Vec3d LEFT_ELBOW = new Vec3d(-0.50D, 1.06D, 0.04D);
    private static final Vec3d RIGHT_ELBOW = new Vec3d(0.50D, 1.06D, 0.04D);
    private static final Vec3d LEFT_PALM = new Vec3d(-0.56D, 0.78D, 0.04D);
    private static final Vec3d RIGHT_PALM = new Vec3d(0.56D, 0.78D, 0.04D);

    private static final double DEFAULT_FOLLOW_SIDE_OFFSET = 1.008708D;
    private static final double DEFAULT_FOLLOW_FORWARD_OFFSET = 0.089465946D;
    private static final double MIN_TARGET_LENGTH = 0.08D;

    private HoldHandsArmIkSolver() {
    }

    static Map<String, float[]> solve(Entity self, Entity partner, float bodyYaw,
                                      HoldHandsSkeletalPose.HandSide side, double defaultDistance) {
        Map<String, float[]> rotations = new LinkedHashMap<>(HoldHandsSkeletalPose.rotationsForHand(side));
        if (self == null || partner == null || side == null) {
            return rotations;
        }

        Vec3d delta = partner.getPos().subtract(self.getPos());
        if (delta.horizontalLengthSquared() <= 0.000001D) {
            return rotations;
        }

        float stretch = stretchRatio(delta, defaultDistance);
        Vec3d shoulderWorld = bodyAnchor(self.getPos(), bodyYaw, shoulderSocket(side));
        Vec3d targetWorld = HoldHandsSharedTargetSolver.solve(self, partner, bodyYaw, side, defaultDistance);
        Vec3d localTarget = worldVectorToBodyLocal(targetWorld.subtract(shoulderWorld), bodyYaw);
        if (localTarget.lengthSquared() <= MIN_TARGET_LENGTH * MIN_TARGET_LENGTH) {
            return rotations;
        }

        IkPose pose = solveLocalArm(side, localTarget, stretch);
        String upperBone = side == HoldHandsSkeletalPose.HandSide.LEFT
                ? HoldHandsSkeletalPose.LEFT_ARM_UPPER_BONE
                : HoldHandsSkeletalPose.RIGHT_ARM_UPPER_BONE;
        String lowerBone = side == HoldHandsSkeletalPose.HandSide.LEFT
                ? HoldHandsSkeletalPose.LEFT_ARM_LOWER_BONE
                : HoldHandsSkeletalPose.RIGHT_ARM_LOWER_BONE;

        float[] upper = rotations.get(upperBone);
        if (upper != null && upper.length >= 3) {
            upper[0] += pose.upperPitch();
            upper[1] += pose.upperYaw();
            upper[2] += pose.upperRoll();
        }
        float[] lower = rotations.get(lowerBone);
        if (lower != null && lower.length >= 3) {
            lower[0] += pose.lowerPitch();
            lower[1] += pose.lowerYaw();
            lower[2] += pose.lowerRoll();
        }
        return rotations;
    }

    private static IkPose solveLocalArm(HoldHandsSkeletalPose.HandSide side, Vec3d localTarget, float stretch) {
        double sideSign = side == HoldHandsSkeletalPose.HandSide.LEFT ? -1.0D : 1.0D;
        Vec3d target = limitTarget(side, localTarget);
        Vec3d defaultUpper = new Vec3d(0.0D, -1.0D, 0.0D);
        double upperLength = elbowSocket(side).subtract(shoulderSocket(side)).length();
        double lowerLength = palmSocket(side).subtract(elbowSocket(side)).length();
        double distance = MathHelper.clamp(target.length(),
                Math.abs(upperLength - lowerLength) + 0.001D,
                upperLength + lowerLength - 0.001D);

        Vec3d targetDir = target.normalize();
        Vec3d pole = new Vec3d(sideSign, -0.18D, -0.28D).normalize();
        Vec3d poleOnPlane = pole.subtract(targetDir.multiply(pole.dotProduct(targetDir)));
        if (poleOnPlane.lengthSquared() <= 0.000001D) {
            poleOnPlane = new Vec3d(sideSign, 0.0D, 0.0D);
        } else {
            poleOnPlane = poleOnPlane.normalize();
        }

        double adjacent = (upperLength * upperLength + distance * distance - lowerLength * lowerLength)
                / (2.0D * upperLength * distance);
        adjacent = MathHelper.clamp(adjacent, -1.0D, 1.0D);
        double along = MathHelper.clamp(upperLength * adjacent, 0.0D, upperLength);
        double perpendicular = Math.sqrt(Math.max(0.0D, upperLength * upperLength - along * along));
        Vec3d elbow = targetDir.multiply(along).add(poleOnPlane.multiply(perpendicular));

        Vec3d upperDir = elbow.lengthSquared() <= 0.000001D ? targetDir : elbow.normalize();
        Vec3d lowerDir = target.subtract(elbow);
        lowerDir = lowerDir.lengthSquared() <= 0.000001D ? targetDir : lowerDir.normalize();

        Aim upperAim = aimFromDirection(upperDir, side);
        Aim lowerAim = aimFromDirection(lowerDir, side);
        double elbowBend = Math.toDegrees(Math.acos(MathHelper.clamp(upperDir.dotProduct(lowerDir), -1.0D, 1.0D)));
        elbowBend = MathHelper.clamp(elbowBend, MIN_ELBOW_DEGREES, MAX_ELBOW_DEGREES);

        float upperPitch = MathHelper.clamp(upperAim.pitch() + stretch * TARGET_LIFT * 55.0F,
                MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
        float upperYaw = MathHelper.clamp(upperAim.yaw(), MIN_HORIZONTAL_DEGREES, MAX_HORIZONTAL_DEGREES);
        float lowerPitch = MathHelper.clamp((float) (lowerAim.pitch() - elbowBend * 0.22D),
                -28.0F, 28.0F);
        return new IkPose(upperPitch, upperYaw, 0.0F, lowerPitch, 0.0F, 0.0F);
    }

    private static Vec3d limitTarget(HoldHandsSkeletalPose.HandSide side, Vec3d localTarget) {
        double horizontal = Math.sqrt(localTarget.x * localTarget.x + localTarget.z * localTarget.z);
        float yaw = MathHelper.clamp((float) Math.toDegrees(Math.atan2(localTarget.x, localTarget.z)),
                MIN_HORIZONTAL_DEGREES, MAX_HORIZONTAL_DEGREES);
        float pitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(localTarget.y, horizontal)),
                MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
        double length = localTarget.length();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double flat = Math.cos(pitchRad) * length;
        return new Vec3d(
                Math.sin(yawRad) * flat,
                Math.sin(pitchRad) * length,
                Math.cos(yawRad) * flat
        );
    }

    private static Aim aimFromDirection(Vec3d direction, HoldHandsSkeletalPose.HandSide side) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        float pitch = (float) Math.toDegrees(Math.atan2(direction.y, horizontal));
        float sideMirror = side == HoldHandsSkeletalPose.HandSide.LEFT ? 1.0F : -1.0F;
        return new Aim(pitch, yaw * sideMirror);
    }

    private static Vec3d sharedPalmTarget(Entity self, Entity partner,
                                          HoldHandsSkeletalPose.HandSide side, float stretch) {
        Entity leader = side == HoldHandsSkeletalPose.ACTIVE_ROLE_HAND ? self : partner;
        Entity follower = side == HoldHandsSkeletalPose.ACTIVE_ROLE_HAND ? partner : self;
        HoldHandsSkeletalPose.HandSide leaderSide = HoldHandsSkeletalPose.ACTIVE_ROLE_HAND;
        HoldHandsSkeletalPose.HandSide followerSide = HoldHandsSkeletalPose.PASSIVE_ROLE_HAND;

        Vec3d leaderPalm = palmAnchor(leader.getPos(), leader.getYaw(), leaderSide);
        Vec3d followerPalm = palmAnchor(follower.getPos(), follower.getYaw(), followerSide);
        Vec3d defaultLeaderPalm = palmAnchor(leader.getPos(), leader.getYaw(), leaderSide);
        Vec3d defaultFollowerPalm = palmAnchor(leader.getPos().add(defaultFollowerOffset(leader.getYaw())),
                leader.getYaw(), followerSide);

        Vec3d defaultShared = defaultLeaderPalm.add(defaultFollowerPalm).multiply(0.5D);
        Vec3d currentShared = leaderPalm.add(followerPalm).multiply(0.5D).add(0.0D, stretch * TARGET_LIFT, 0.0D);
        return defaultShared.lerp(currentShared, MathHelper.clamp(stretch, 0.0F, 1.0F));
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

    private static Vec3d shoulderSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_SHOULDER : RIGHT_SHOULDER;
    }

    private static Vec3d elbowSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_ELBOW : RIGHT_ELBOW;
    }

    private static Vec3d palmSocket(HoldHandsSkeletalPose.HandSide side) {
        return side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_PALM : RIGHT_PALM;
    }

    private record Aim(float pitch, float yaw) {
    }

    private record IkPose(float upperPitch, float upperYaw, float upperRoll,
                          float lowerPitch, float lowerYaw, float lowerRoll) {
    }
}
