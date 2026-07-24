package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PortalRemoteViewPlanner {
    private PortalRemoteViewPlanner() {
    }

    public static BlockPos viewCenter(Vec3d remoteCameraPosition, Vec3d remoteForward, double leadBlocks) {
        Vec3d forward = safeNormalize(remoteForward);
        if (remoteCameraPosition == null || forward == null) {
            return null;
        }
        double safeLead = Math.max(0.0D, leadBlocks);
        return BlockPos.ofFloored(remoteCameraPosition.add(forward.multiply(safeLead)));
    }

    private static Vec3d safeNormalize(Vec3d vector) {
        if (vector == null || !isFinite(vector) || vector.lengthSquared() < 1.0E-10D) {
            return null;
        }
        Vec3d normalized = vector.normalize();
        return isFinite(normalized) ? normalized : null;
    }

    private static boolean isFinite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
