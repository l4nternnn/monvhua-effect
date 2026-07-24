package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalFrame;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.minecraft.util.math.Vec3d;

final class PortalSelector {
    private PortalSelector() {
    }

    static boolean isCrosshairInside(PortalBlockEntity portal, Vec3d origin, Vec3d direction) {
        if (portal == null || origin == null || direction == null || direction.lengthSquared() < 1.0E-10D) {
            return false;
        }
        PortalFrame frame = portal.getFrame();
        Vec3d ray = direction.normalize();
        double denominator = ray.dotProduct(frame.normal());
        if (Math.abs(denominator) < 1.0E-6D) {
            return false;
        }
        double distance = frame.center().subtract(origin).dotProduct(frame.normal()) / denominator;
        if (distance < PortalViewConfig.MIN_PROJECTION_DEPTH
                || distance > PortalViewConfig.PORTAL_AIM_RETAIN_DISTANCE) {
            return false;
        }
        Vec3d hit = origin.add(ray.multiply(distance));
        return frame.containsProjection(hit, Math.max(
                PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET,
                PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET
        ));
    }
}
