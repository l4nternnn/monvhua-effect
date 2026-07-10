package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class PortalTransform {
    private PortalTransform() {
    }

    public static float yawDelta(Direction sourceFacing, Direction targetFacing) {
        return MathHelper.wrapDegrees(
                targetFacing.getOpposite().getPositiveHorizontalDegrees()
                        - sourceFacing.getPositiveHorizontalDegrees()
        );
    }

    public static Vec3d mapPosition(Vec3d position,
                                    Vec3d sourceCenter,
                                    Direction sourceFacing,
                                    Vec3d targetCenter,
                                    Direction targetFacing) {
        return targetCenter.add(mapVector(position.subtract(sourceCenter), sourceFacing, targetFacing));
    }

    public static Vec3d mapVector(Vec3d vector, Direction sourceFacing, Direction targetFacing) {
        return rotateHorizontal(vector, yawDelta(sourceFacing, targetFacing));
    }

    public static float mapYaw(float yaw, Direction sourceFacing, Direction targetFacing) {
        return MathHelper.wrapDegrees(yaw + yawDelta(sourceFacing, targetFacing));
    }

    private static Vec3d rotateHorizontal(Vec3d vector, float degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3d(
                vector.x * cos - vector.z * sin,
                vector.y,
                vector.x * sin + vector.z * cos
        );
    }
}
