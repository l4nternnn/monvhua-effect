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
        return mapPoint(
                position,
                PortalFrame.centered(sourceCenter, sourceFacing, 1, 1),
                PortalFrame.centered(targetCenter, targetFacing, 1, 1)
        );
    }

    public static Vec3d mapPoint(Vec3d position, PortalFrame source, PortalFrame target) {
        return target.center().add(mapVector(position.subtract(source.center()), source, target));
    }

    public static Vec3d mapVector(Vec3d vector, PortalFrame source, PortalFrame target) {
        double width = vector.dotProduct(source.widthAxis());
        double height = vector.dotProduct(source.heightAxis());
        double normal = vector.dotProduct(source.normal());
        return target.widthAxis().multiply(-width)
                .add(target.heightAxis().multiply(height))
                .add(target.contentNormal().multiply(normal));
    }

    public static Vec3d mapVector(Vec3d vector, Direction sourceFacing, Direction targetFacing) {
        return mapVector(
                vector,
                PortalFrame.centered(Vec3d.ZERO, sourceFacing, 1, 1),
                PortalFrame.centered(Vec3d.ZERO, targetFacing, 1, 1)
        );
    }

    public static Vec3d mapVectorFrontToFront(Vec3d vector, Direction sourceFacing, Direction targetFacing) {
        return mapVector(vector, sourceFacing, targetFacing);
    }

    public static float mapYaw(float yaw, Direction sourceFacing, Direction targetFacing) {
        return mapRotation(yaw, 0.0F, sourceFacing, targetFacing).yaw();
    }

    public static Rotation mapRotation(float yaw, float pitch, Direction sourceFacing, Direction targetFacing) {
        return rotationFromVector(mapVector(Vec3d.fromPolar(pitch, yaw), sourceFacing, targetFacing));
    }

    public static Rotation mapRotationFrontToFront(float yaw, float pitch, Direction sourceFacing, Direction targetFacing) {
        return mapRotation(yaw, pitch, sourceFacing, targetFacing);
    }

    public static Rotation mapRotation(float yaw, float pitch, PortalFrame source, PortalFrame target) {
        return rotationFromVector(mapVector(Vec3d.fromPolar(pitch, yaw), source, target));
    }

    public static Vec3d normal(Direction direction) {
        return PortalFrame.normal(direction);
    }

    public static Vec3d surfaceWidthAxis(Direction facing) {
        return PortalFrame.gridWidthAxis(facing);
    }

    public static Vec3d surfaceHeightAxis(Direction facing) {
        return PortalFrame.gridHeightAxis(facing);
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

    public record Rotation(float yaw, float pitch) {
    }
}
