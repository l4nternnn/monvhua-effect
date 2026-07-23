package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PortalViewTransform {
    private PortalViewTransform() {
    }

    public static View compute(Vec3d eye, PortalFrame source, PortalFrame target, double minimumDepth) {
        if (eye == null || source == null || target == null) {
            return null;
        }

        Vec3d local = eye.subtract(source.center());
        double width = local.dotProduct(source.widthAxis());
        double height = local.dotProduct(source.heightAxis());
        double depth = local.dotProduct(source.normal());
        double safeDepth = signedMinimum(depth, minimumDepth);

        Vec3d position = target.center()
                .add(target.widthAxis().multiply(width))
                .add(target.heightAxis().multiply(height))
                .add(target.contentNormal().multiply(safeDepth));
        Vec3d forward = safeNormalize(target.normal());
        Vec3d up = safeNormalize(target.heightAxis());
        if (forward == null || up == null || Math.abs(forward.dotProduct(up)) > 0.999D) {
            return null;
        }

        Aperture aperture = apertureFor(target);
        Vec3d centerRay = safeNormalize(aperture.center().subtract(position));
        if (centerRay == null || !allCornersInFront(position, forward, aperture)) {
            return null;
        }
        return new View(position, forward, up, centerRay, aperture);
    }

    public static Aperture apertureFor(PortalFrame frame) {
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

    private static boolean allCornersInFront(Vec3d position, Vec3d forward, Aperture aperture) {
        double minDepth = PortalViewConfig.MIN_PROJECTION_DEPTH;
        return aperture.bottomLeft().subtract(position).dotProduct(forward) > minDepth
                && aperture.bottomRight().subtract(position).dotProduct(forward) > minDepth
                && aperture.topRight().subtract(position).dotProduct(forward) > minDepth
                && aperture.topLeft().subtract(position).dotProduct(forward) > minDepth;
    }

    private static double signedMinimum(double value, double minimumMagnitude) {
        double safeMinimum = Math.max(0.0D, minimumMagnitude);
        if (Math.abs(value) >= safeMinimum) {
            return value;
        }
        return value < 0.0D ? -safeMinimum : safeMinimum;
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

    public record View(Vec3d position, Vec3d forward, Vec3d up, Vec3d centerRay, Aperture aperture) {
        public BlockPos remoteViewCenter(double leadBlocks) {
            double safeLead = Math.max(0.0D, leadBlocks);
            return BlockPos.ofFloored(position.add(centerRay.multiply(safeLead)));
        }
    }

    public record Aperture(Vec3d center, Vec3d bottomLeft, Vec3d bottomRight, Vec3d topRight, Vec3d topLeft) {
    }
}
