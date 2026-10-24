package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Ray/plane intersection expressed as an inverse planar projective transform. */
public final class ProjectedPlaneDrag {
    public record Delta(double u, double v) {}
    private final Matrix3d inverse;
    private final Delta start;

    public ProjectedPlaneDrag(ProjectedRingDrag.Point c, ProjectedRingDrag.Point u,
                              ProjectedRingDrag.Point v, double x, double y) {
        double ux = u.x() - c.x(), uy = u.y() - c.y();
        double vx = v.x() - c.x(), vy = v.y() - c.y();
        double conditioning = Math.abs(ux * vy - uy * vx)
                / Math.max(1, Math.hypot(ux, uy) * Math.hypot(vx, vy));
        Vector3d origin = homogeneous(c);
        Matrix3d m = new Matrix3d(homogeneous(u).sub(origin), homogeneous(v).sub(origin), origin);
        inverse = conditioning > 0.08 && Math.abs(m.determinant()) > 1e-9 ? m.invert() : null;
        start = local(x, y);
    }

    public boolean usable() { return start != null; }
    public Delta delta(double x, double y) {
        Delta now = local(x, y);
        return start == null || now == null ? null : new Delta(now.u - start.u, now.v - start.v);
    }
    private Delta local(double x, double y) {
        if (inverse == null) return null;
        Vector3d p = inverse.transform(new Vector3d(x, y, 1));
        if (!Double.isFinite(p.z) || p.z <= 1e-10) return null;
        double u = p.x / p.z, v = p.y / p.z;
        return Double.isFinite(u) && Double.isFinite(v) ? new Delta(u, v) : null;
    }
    private static Vector3d homogeneous(ProjectedRingDrag.Point p) {
        return new Vector3d(p.x() * p.w(), p.y() * p.w(), p.w());
    }
}
