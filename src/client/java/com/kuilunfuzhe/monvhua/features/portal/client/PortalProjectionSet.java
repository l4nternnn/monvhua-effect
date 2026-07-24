package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalClipPlane;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewTransform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PortalProjectionSet {
    private final Matrix4f renderProjection;
    private final Matrix4f cullingProjection;

    private PortalProjectionSet(Matrix4f renderProjection, Matrix4f cullingProjection) {
        this.renderProjection = renderProjection;
        this.cullingProjection = cullingProjection;
    }

    static PortalProjectionSet create(Matrix4f mainProjection,
                                      float aspect,
                                      boolean matchMainProjection,
                                      Vec3d cameraPosition,
                                      Quaternionf cameraRotation,
                                      PortalViewTransform.Aperture aperture,
                                      PortalClipPlane clipPlane) {
        Matrix4f relaxedProjection = matchMainProjection
                ? new Matrix4f(mainProjection)
                : projectionForAspect(mainProjection, aspect);
        if (matchMainProjection || aperture == null) {
            return new PortalProjectionSet(new Matrix4f(relaxedProjection), relaxedProjection);
        }

        Matrix4f apertureProjection = PortalApertureProjection.create(cameraPosition, cameraRotation, aperture);
        Matrix4f renderProjection = apertureProjection == null
                ? new Matrix4f(relaxedProjection)
                : apertureProjection;
        if (PortalViewConfig.PORTAL_OBLIQUE_CLIP_ENABLED && clipPlane != null) {
            renderProjection = PortalObliqueClipProjection.apply(
                    renderProjection,
                    cameraPosition,
                    cameraRotation,
                    clipPlane
            );
        }
        return new PortalProjectionSet(renderProjection, relaxedProjection);
    }

    Matrix4f renderProjection() {
        return renderProjection;
    }

    Matrix4f cullingProjection() {
        return cullingProjection;
    }

    private static Matrix4f projectionForAspect(Matrix4f original, float aspect) {
        Matrix4f projection = new Matrix4f(original);
        float safeAspect = Math.max(0.05F, aspect);
        if (Math.abs(projection.m11()) > 1.0E-5F) {
            projection.m00(projection.m11() / safeAspect);
        }
        return projection;
    }
}
