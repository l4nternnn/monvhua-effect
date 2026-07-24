package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalClipPlane;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class PortalObliqueClipProjection {
    private PortalObliqueClipProjection() {
    }

    static Matrix4f apply(Matrix4f projection, Vec3d cameraPosition,
                          Quaternionf cameraRotation, PortalClipPlane plane) {
        if (projection == null || cameraPosition == null || cameraRotation == null || plane == null) {
            return projection;
        }
        Vector4f cameraPlane = toCameraPlane(cameraPosition, cameraRotation, plane);
        if (cameraPlane == null) {
            return projection;
        }

        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        if (!isFinite(inverseProjection)) {
            return projection;
        }

        Vector4f q = new Vector4f(
                sign(cameraPlane.x),
                sign(cameraPlane.y),
                1.0F,
                1.0F
        );
        inverseProjection.transform(q);

        float denominator = cameraPlane.dot(q);
        if (!Float.isFinite(denominator) || Math.abs(denominator) < 1.0E-5F) {
            return projection;
        }

        Vector4f replacement = cameraPlane.mul(2.0F / denominator, new Vector4f());
        Matrix4f clipped = new Matrix4f(projection);
        clipped.m02(replacement.x - clipped.m03());
        clipped.m12(replacement.y - clipped.m13());
        clipped.m22(replacement.z - clipped.m23());
        clipped.m32(replacement.w - clipped.m33());
        return isFinite(clipped) ? clipped : projection;
    }

    private static Vector4f toCameraPlane(Vec3d cameraPosition, Quaternionf cameraRotation,
                                          PortalClipPlane plane) {
        Vec3d point = plane.point();
        Vec3d normal = plane.normal();
        if (point == null || normal == null || !isFinite(point) || !isFinite(normal)
                || normal.lengthSquared() < 1.0E-10D) {
            return null;
        }

        Quaternionf worldToCamera = new Quaternionf(cameraRotation).conjugate();
        Vector3f cameraPoint = new Vector3f(
                (float) (point.x - cameraPosition.x),
                (float) (point.y - cameraPosition.y),
                (float) (point.z - cameraPosition.z)
        ).rotate(worldToCamera);
        Vector3f cameraNormal = new Vector3f(
                (float) normal.x,
                (float) normal.y,
                (float) normal.z
        ).normalize().rotate(worldToCamera).normalize();

        float d = -cameraNormal.dot(cameraPoint);
        if (d > 0.0F) {
            cameraNormal.negate();
            d = -d;
        }
        float minimumDistance = (float) PortalViewConfig.MIN_PROJECTION_DEPTH;
        if (Math.abs(d) < minimumDistance) {
            d = -minimumDistance;
        }

        if (!Float.isFinite(cameraNormal.x) || !Float.isFinite(cameraNormal.y)
                || !Float.isFinite(cameraNormal.z) || !Float.isFinite(d)) {
            return null;
        }
        return new Vector4f(cameraNormal.x, cameraNormal.y, cameraNormal.z, d);
    }

    private static float sign(float value) {
        return value < 0.0F ? -1.0F : 1.0F;
    }

    private static boolean isFinite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean isFinite(Matrix4f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01())
                && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
                && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21())
                && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31())
                && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }
}
