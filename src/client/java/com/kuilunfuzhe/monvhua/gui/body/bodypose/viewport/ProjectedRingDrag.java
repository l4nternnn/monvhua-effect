package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Inverts the projected circle's plane, including perspective foreshortening.
 * The plane and fallback tangent are captured once, so moving geometry cannot feed back into a drag. */
public final class ProjectedRingDrag {
    private final Matrix3d inverse;
    private final double tangentX, tangentY, pixelsPerRadian, startX, startY;
    private double lastAngle;
    private double total;

    public record Point(double x, double y, double w) {}

    public ProjectedRingDrag(Point center, Point u, Point v, double mouseX, double mouseY,
                             double tangentX, double tangentY, double pixelsPerRadian) {
        startX = mouseX;
        startY = mouseY;
        this.tangentX = tangentX;
        this.tangentY = tangentY;
        this.pixelsPerRadian = Math.max(8, pixelsPerRadian);
        Vector3d c = homogeneous(center);
        Matrix3d plane = new Matrix3d(homogeneous(u).sub(c), homogeneous(v).sub(c), c);
        double ux = u.x - center.x, uy = u.y - center.y;
        double vx = v.x - center.x, vy = v.y - center.y;
        double area = Math.abs(ux * vy - uy * vx);
        double conditioning = area / Math.max(1, Math.hypot(ux, uy) * Math.hypot(vx, vy));
        Matrix3d candidate = conditioning > 0.08 && Math.abs(plane.determinant()) > 1e-9
                ? plane.invert() : null;
        double initial = angle(candidate, mouseX, mouseY);
        inverse = Double.isFinite(initial) ? candidate : null;
        lastAngle = initial;
    }

    public double radians(double x, double y) {
        if (inverse == null) {
            return ((x - startX) * tangentX + (y - startY) * tangentY) / pixelsPerRadian;
        }
        double next = angle(inverse, x, y);
        if (Double.isFinite(next)) {
            total += Math.atan2(Math.sin(next - lastAngle), Math.cos(next - lastAngle));
            lastAngle = next;
        }
        return total;
    }

    private static double angle(Matrix3d inverse, double x, double y) {
        if (inverse == null) return Double.NaN;
        Vector3d local = inverse.transform(new Vector3d(x, y, 1));
        if (!Double.isFinite(local.z) || Math.abs(local.z) < 1e-10) return Double.NaN;
        double u = local.x / local.z, v = local.y / local.z;
        if (!Double.isFinite(u) || !Double.isFinite(v) || u * u + v * v < 0.01) return Double.NaN;
        return Math.atan2(v, u);
    }

    private static Vector3d homogeneous(Point p) { return new Vector3d(p.x * p.w, p.y * p.w, p.w); }
}
