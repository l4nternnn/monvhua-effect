package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewTransform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PortalApertureProjection {
    private PortalApertureProjection() {
    }

    static Matrix4f create(Vec3d cameraPosition, Quaternionf cameraRotation,
                            PortalViewTransform.Aperture aperture) {
        ProjectionBounds bounds = projectionBounds(cameraPosition, cameraRotation, aperture);
        if (bounds == null) {
            return null;
        }

        return new Matrix4f().setFrustum(
                bounds.left(),
                bounds.right(),
                bounds.bottom(),
                bounds.top(),
                bounds.near(),
                bounds.far()
        );
    }

    static CornerUvs textureCoordinates(Vec3d cameraPosition, Quaternionf cameraRotation,
                                         PortalViewTransform.Aperture aperture) {
        ProjectionBounds bounds = projectionBounds(cameraPosition, cameraRotation, aperture);
        if (bounds == null) {
            return null;
        }

        return new CornerUvs(
                textureCoordinate(bounds.bottomLeft(), bounds),
                textureCoordinate(bounds.bottomRight(), bounds),
                textureCoordinate(bounds.topRight(), bounds),
                textureCoordinate(bounds.topLeft(), bounds)
        );
    }

    static String describeFailure(Vec3d cameraPosition, Quaternionf cameraRotation,
                                  PortalViewTransform.Aperture aperture) {
        if (cameraPosition == null || cameraRotation == null || aperture == null) {
            return "invalid_projection_inputs";
        }

        Quaternionf worldToCamera = new Quaternionf(cameraRotation).conjugate();
        CameraPoint bottomLeft = toCamera(cameraPosition, worldToCamera, aperture.bottomLeft());
        CameraPoint bottomRight = toCamera(cameraPosition, worldToCamera, aperture.bottomRight());
        CameraPoint topRight = toCamera(cameraPosition, worldToCamera, aperture.topRight());
        CameraPoint topLeft = toCamera(cameraPosition, worldToCamera, aperture.topLeft());
        String depths = depthSummary(bottomLeft, bottomRight, topRight, topLeft);
        if (!bottomLeft.finite() || !bottomRight.finite() || !topRight.finite() || !topLeft.finite()) {
            return "invalid_aperture_corner " + depths;
        }
        int validCorners = validCount(bottomLeft, bottomRight, topRight, topLeft);
        List<CameraPoint> clipped = clipApertureCameraPolygon(List.of(bottomLeft, bottomRight, topRight, topLeft));
        if (clipped.size() < 3) {
            return "aperture_fully_behind_or_too_close validCorners=" + validCorners + "/4 " + depths;
        }
        return "degenerate_clipped_projected_bounds validCorners=" + validCorners
                + "/4 clippedCorners=" + clipped.size() + " " + depths;
    }

    private static ProjectionBounds projectionBounds(Vec3d cameraPosition, Quaternionf cameraRotation,
                                                     PortalViewTransform.Aperture aperture) {
        if (cameraPosition == null || cameraRotation == null || aperture == null) {
            return null;
        }

        Quaternionf worldToCamera = new Quaternionf(cameraRotation).conjugate();
        CameraPoint bottomLeft = toCamera(cameraPosition, worldToCamera, aperture.bottomLeft());
        CameraPoint bottomRight = toCamera(cameraPosition, worldToCamera, aperture.bottomRight());
        CameraPoint topRight = toCamera(cameraPosition, worldToCamera, aperture.topRight());
        CameraPoint topLeft = toCamera(cameraPosition, worldToCamera, aperture.topLeft());
        if (!bottomLeft.finite() || !bottomRight.finite() || !topRight.finite() || !topLeft.finite()) {
            return null;
        }

        List<CameraPoint> clipped = clipApertureCameraPolygon(List.of(bottomLeft, bottomRight, topRight, topLeft));
        if (clipped.size() < 3) {
            return null;
        }

        float apertureDepth = Float.POSITIVE_INFINITY;
        for (CameraPoint point : clipped) {
            apertureDepth = Math.min(apertureDepth, point.depth());
        }
        float near = Math.max(
                (float) PortalViewConfig.MIN_PROJECTION_DEPTH,
                apertureDepth - (float) PortalViewConfig.PORTAL_NEAR_PLANE_BIAS
        );
        float far = Math.max(PortalViewConfig.PORTAL_MINIMUM_FAR_PLANE, near + 1024.0F);

        float left = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.POSITIVE_INFINITY;
        float top = Float.NEGATIVE_INFINITY;
        for (CameraPoint point : clipped) {
            float projectedX = projectedX(point, near);
            float projectedY = projectedY(point, near);
            left = Math.min(left, projectedX);
            right = Math.max(right, projectedX);
            bottom = Math.min(bottom, projectedY);
            top = Math.max(top, projectedY);
        }

        if (!Float.isFinite(left) || !Float.isFinite(right)
                || !Float.isFinite(bottom) || !Float.isFinite(top)
                || right - left < 1.0E-4F || top - bottom < 1.0E-4F) {
            return null;
        }
        return new ProjectionBounds(
                near,
                far,
                left,
                right,
                bottom,
                top,
                bottomLeft,
                bottomRight,
                topRight,
                topLeft
        );
    }

    private static CameraPoint toCamera(Vec3d cameraPosition, Quaternionf worldToCamera, Vec3d worldPoint) {
        Vector3f point = new Vector3f(
                (float) (worldPoint.x - cameraPosition.x),
                (float) (worldPoint.y - cameraPosition.y),
                (float) (worldPoint.z - cameraPosition.z)
        ).rotate(worldToCamera);
        float depth = -point.z;
        return new CameraPoint(point.x, point.y, depth);
    }

    private static float projectedX(CameraPoint point, float near) {
        return point.x() * near / point.depth();
    }

    private static float projectedY(CameraPoint point, float near) {
        return point.y() * near / point.depth();
    }

    private static CornerUv textureCoordinate(CameraPoint point, ProjectionBounds bounds) {
        float uRange = bounds.right() - bounds.left();
        float vRange = bounds.top() - bounds.bottom();
        float uNumerator = (point.x() * bounds.near() - bounds.left() * point.depth()) / uRange;
        float vNumerator = (point.y() * bounds.near() - bounds.bottom() * point.depth()) / vRange;
        float u = Math.abs(point.depth()) > 1.0E-6F ? uNumerator / point.depth() : 0.0F;
        float v = Math.abs(point.depth()) > 1.0E-6F ? vNumerator / point.depth() : 0.0F;
        return new CornerUv(u, v, point.depth(), uNumerator, vNumerator);
    }

    private static List<CameraPoint> clipApertureCameraPolygon(List<CameraPoint> vertices) {
        List<CameraPoint> clipped = new ArrayList<>();
        if (vertices.isEmpty()) {
            return clipped;
        }

        CameraPoint previous = vertices.getLast();
        boolean previousInside = isInsideProjectionNearPlane(previous);
        for (CameraPoint current : vertices) {
            boolean currentInside = isInsideProjectionNearPlane(current);
            if (previousInside != currentInside) {
                clipped.add(interpolateAtProjectionNearPlane(previous, current));
            }
            if (currentInside) {
                clipped.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private static boolean isInsideProjectionNearPlane(CameraPoint point) {
        return point.finite()
                && point.depth() >= PortalViewConfig.MIN_PROJECTION_DEPTH;
    }

    private static CameraPoint interpolateAtProjectionNearPlane(CameraPoint start, CameraPoint end) {
        float nearDepth = (float) PortalViewConfig.MIN_PROJECTION_DEPTH;
        float denominator = end.depth() - start.depth();
        float t = Math.abs(denominator) < 1.0E-6F
                ? 0.0F
                : clamp01((nearDepth - start.depth()) / denominator);
        return new CameraPoint(
                lerp(t, start.x(), end.x()),
                lerp(t, start.y(), end.y()),
                nearDepth
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static int validCount(CameraPoint bottomLeft, CameraPoint bottomRight,
                                  CameraPoint topRight, CameraPoint topLeft) {
        int count = 0;
        if (bottomLeft.valid()) {
            count++;
        }
        if (bottomRight.valid()) {
            count++;
        }
        if (topRight.valid()) {
            count++;
        }
        if (topLeft.valid()) {
            count++;
        }
        return count;
    }

    private static String depthSummary(CameraPoint bottomLeft, CameraPoint bottomRight,
                                       CameraPoint topRight, CameraPoint topLeft) {
        return String.format(
                Locale.ROOT,
                "depths=bl:%.4f,br:%.4f,tr:%.4f,tl:%.4f,minDepth:%.4f",
                bottomLeft.depth(),
                bottomRight.depth(),
                topRight.depth(),
                topLeft.depth(),
                min(bottomLeft.depth(), bottomRight.depth(), topRight.depth(), topLeft.depth())
        );
    }

    private record CameraPoint(float x, float y, float depth) {
        private boolean finite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(depth);
        }

        private boolean valid() {
            return finite()
                    && depth >= PortalViewConfig.MIN_PROJECTION_DEPTH;
        }
    }

    record CornerUv(float u, float v, float textureW, float uNumerator, float vNumerator) {
        CornerUv(float u, float v, float textureW) {
            this(u, v, textureW, u * textureW, v * textureW);
        }
    }

    record CornerUvs(CornerUv bottomLeft, CornerUv bottomRight, CornerUv topRight, CornerUv topLeft) {
    }

    private record ProjectionBounds(float near, float far,
                                    float left, float right, float bottom, float top,
                                    CameraPoint bottomLeft, CameraPoint bottomRight,
                                    CameraPoint topRight, CameraPoint topLeft) {
    }
}
