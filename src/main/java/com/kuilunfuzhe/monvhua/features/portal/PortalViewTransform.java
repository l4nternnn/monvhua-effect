package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PortalViewTransform {
    private PortalViewTransform() {
    }

    public static View compute(Vec3d eye, PortalFrame source, PortalFrame target, double minimumDepth) {
        if (source == null) {
            return null;
        }
        return compute(
                eye,
                source.normal().multiply(-1.0D),
                source.heightAxis(),
                source,
                target,
                minimumDepth,
                PortalViewConfig.REMOTE_VIEW_CENTER_LEAD_BLOCKS
        );
    }

    public static View compute(Vec3d eye, Vec3d cameraForward, Vec3d cameraUp,
                               PortalFrame source, PortalFrame target,
                               double minimumDepth, double leadBlocks) {
        if (eye == null || source == null || target == null) {
            return null;
        }

        Vec3d position = PortalTransform.mapPointForView(eye, source, target, minimumDepth);
        Vec3d forward = safeNormalize(PortalTransform.mapVector(cameraForward, source, target));
        Vec3d mappedUp = safeNormalize(PortalTransform.mapVector(cameraUp, source, target));
        if (forward == null) {
            return null;
        }

        Aperture aperture = apertureFor(target);
        Vec3d centerRay = safeNormalize(aperture.center().subtract(position));
        if (centerRay == null) {
            centerRay = forward;
        }
        Vec3d up = safeUp(forward, mappedUp, target.heightAxis());
        BlockPos remoteViewCenter = PortalRemoteViewPlanner.viewCenter(position, centerRay, leadBlocks);
        if (remoteViewCenter == null) {
            return null;
        }
        PortalClipPlane clipPlane = new PortalClipPlane(target.center(), target.normal());
        return new View(position, forward, up, centerRay, aperture, remoteViewCenter, clipPlane);
    }

    private static Vec3d safeUp(Vec3d forward, Vec3d requestedUp, Vec3d fallbackUp) {
        Vec3d up = requestedUp;
        if (up == null || Math.abs(forward.dotProduct(up)) > 0.999D) {
            up = safeNormalize(fallbackUp);
        }
        if (up == null || Math.abs(forward.dotProduct(up)) > 0.999D) {
            up = Math.abs(forward.y) < 0.999D ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d right = safeNormalize(forward.crossProduct(up));
        if (right == null) {
            return new Vec3d(0.0D, 1.0D, 0.0D);
        }
        Vec3d orthogonalUp = safeNormalize(right.crossProduct(forward));
        if (orthogonalUp == null) {
            return new Vec3d(0.0D, 1.0D, 0.0D);
        }
        return orthogonalUp;
    }

    public static Aperture apertureFor(PortalFrame frame) {
        if (frame == null) {
            return null;
        }
        double halfWidth = Math.max(
                0.01D,
                frame.width() * 0.5D - PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET
        );
        double halfHeight = Math.max(
                0.01D,
                frame.height() * 0.5D - PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET
        );
        Vec3d center = frame.center();
        Vec3d horizontal = frame.widthAxis();
        Vec3d vertical = frame.heightAxis();
        return new Aperture(
                center,
                center.subtract(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                center.add(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                center.add(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight)),
                center.subtract(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight))
        );
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

    public record View(Vec3d position, Vec3d forward, Vec3d up,
                       Vec3d centerRay, Aperture aperture,
                       BlockPos remoteViewCenter, PortalClipPlane clipPlane) {
        public BlockPos remoteViewCenter(double leadBlocks) {
            if (remoteViewCenter != null && Math.abs(leadBlocks - PortalViewConfig.REMOTE_VIEW_CENTER_LEAD_BLOCKS) < 1.0E-6D) {
                return remoteViewCenter;
            }
            return PortalRemoteViewPlanner.viewCenter(position, centerRay, leadBlocks);
        }
    }

    public record Aperture(Vec3d center, Vec3d bottomLeft, Vec3d bottomRight, Vec3d topRight, Vec3d topLeft) {
    }
}
