package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class PortalTransform {
    private PortalTransform() {
    }

    public static float yawDelta(Direction sourceFacing, Direction targetFacing) {
        return MathHelper.wrapDegrees(mapYaw(0.0F, sourceFacing, targetFacing));
    }

    public static Vec3d mapPosition(Vec3d position,
                                    Vec3d sourceCenter,
                                    Direction sourceFacing,
                                    Vec3d targetCenter,
                                    Direction targetFacing) {
        return targetCenter.add(mapVector(position.subtract(sourceCenter), sourceFacing, targetFacing));
    }

    public static Vec3d mapVector(Vec3d vector, Direction sourceFacing, Direction targetFacing) {
        Basis source = basisFor(sourceFacing);
        Basis target = basisFor(targetFacing);
        double right = vector.dotProduct(source.right());
        double up = vector.dotProduct(source.up());
        double front = vector.dotProduct(source.front());
        return target.right().multiply(right)
                .add(target.up().multiply(up))
                .add(target.front().multiply(front));
    }

    public static Vec3d mapVectorFrontToFront(Vec3d vector, Direction sourceFacing, Direction targetFacing) {
        Basis source = basisFor(sourceFacing);
        Basis target = basisFor(targetFacing);
        double right = vector.dotProduct(source.right());
        double up = vector.dotProduct(source.up());
        double front = vector.dotProduct(source.front());
        return target.right().multiply(right)
                .add(target.up().multiply(up))
                .add(target.front().multiply(-front));
    }

    public static float mapYaw(float yaw, Direction sourceFacing, Direction targetFacing) {
        return mapRotation(yaw, 0.0F, sourceFacing, targetFacing).yaw();
    }

    public static Rotation mapRotation(float yaw, float pitch, Direction sourceFacing, Direction targetFacing) {
        return rotationFromVector(mapVector(Vec3d.fromPolar(pitch, yaw), sourceFacing, targetFacing));
    }

    public static Rotation mapRotationFrontToFront(float yaw, float pitch, Direction sourceFacing, Direction targetFacing) {
        return rotationFromVector(mapVectorFrontToFront(Vec3d.fromPolar(pitch, yaw), sourceFacing, targetFacing));
    }

    public static Vec3d normal(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    public static Vec3d surfaceWidthAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.X
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(1.0D, 0.0D, 0.0D);
    }

    public static Vec3d surfaceHeightAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(0.0D, 1.0D, 0.0D);
    }

    public static Rotation rotationFromVector(Vec3d vector) {
        double horizontal = Math.sqrt(vector.x * vector.x + vector.z * vector.z);
        if (horizontal < 1.0E-6D) {
            if (Math.abs(vector.y) < 1.0E-6D) {
                return new Rotation(0.0F, 0.0F);
            }
            return new Rotation(0.0F, vector.y > 0.0D ? -90.0F : 90.0F);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-vector.x, vector.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-vector.y, horizontal));
        return new Rotation(
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0F, 90.0F)
        );
    }

    private static Basis basisFor(Direction frontDirection) {
        Vec3d front = normal(frontDirection);
        Vec3d preferredUp = frontDirection.getAxis() == Direction.Axis.Y
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = preferredUp.crossProduct(front).normalize();
        Vec3d up = front.crossProduct(right).normalize();
        return new Basis(right, up, front);
    }

    public record Rotation(float yaw, float pitch) {
    }

    private record Basis(Vec3d right, Vec3d up, Vec3d front) {
    }
}
