package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewTransform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        if (!bottomLeft.valid() || !bottomRight.valid() || !topRight.valid() || !topLeft.valid()) {
            return null;
        }

        float apertureDepth = Math.min(
                Math.min(bottomLeft.depth(), bottomRight.depth()),
                Math.min(topRight.depth(), topLeft.depth())
        );
        float near = Math.max(
                (float) PortalViewConfig.MIN_PROJECTION_DEPTH,
                apertureDepth - (float) PortalViewConfig.PORTAL_NEAR_PLANE_BIAS
        );
        float far = Math.max(PortalViewConfig.PORTAL_MINIMUM_FAR_PLANE, near + 1024.0F);

        float left = min(projectedX(bottomLeft, near), projectedX(bottomRight, near),
                projectedX(topRight, near), projectedX(topLeft, near));
        float right = max(projectedX(bottomLeft, near), projectedX(bottomRight, near),
                projectedX(topRight, near), projectedX(topLeft, near));
        float bottom = min(projectedY(bottomLeft, near), projectedY(bottomRight, near),
                projectedY(topRight, near), projectedY(topLeft, near));
        float top = max(projectedY(bottomLeft, near), projectedY(bottomRight, near),
                projectedY(topRight, near), projectedY(topLeft, near));

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
        float u = (projectedX(point, bounds.near()) - bounds.left())
                / (bounds.right() - bounds.left());
        float v = (projectedY(point, bounds.near()) - bounds.bottom())
                / (bounds.top() - bounds.bottom());
        return new CornerUv(u, v, point.depth());
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private record CameraPoint(float x, float y, float depth) {
        private boolean valid() {
            return Float.isFinite(x) && Float.isFinite(y)
                    && Float.isFinite(depth)
                    && depth > PortalViewConfig.MIN_PROJECTION_DEPTH;
        }
    }

    record CornerUv(float u, float v, float textureW) {
    }

    record CornerUvs(CornerUv bottomLeft, CornerUv bottomRight, CornerUv topRight, CornerUv topLeft) {
    }

    private record ProjectionBounds(float near, float far,
                                    float left, float right, float bottom, float top,
                                    CameraPoint bottomLeft, CameraPoint bottomRight,
                                    CameraPoint topRight, CameraPoint topLeft) {
    }
}
