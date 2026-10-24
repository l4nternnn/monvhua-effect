package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

/** Stable tie-breaking in logical GUI pixels. Enumeration order never overrides an existing hover. */
public final class GizmoPick {
    public static double distanceToSegment(double x, double y, double ax, double ay, double bx, double by) {
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(bx) || !Double.isFinite(by)
                || !Double.isFinite(x) || !Double.isFinite(y)) return Double.POSITIVE_INFINITY;
        double dx = bx - ax, dy = by - ay;
        double length = dx * dx + dy * dy;
        double t = length < 0.001 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / length));
        return Math.hypot(x - ax - t * dx, y - ay - t * dy);
    }
    public static boolean insideQuad(double x, double y, double[] xs, double[] ys) {
        double sign = 0;
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) return false;
            double cross = (xs[j] - xs[i]) * (y - ys[i]) - (ys[j] - ys[i]) * (x - xs[i]);
            if (Math.abs(cross) > 1e-6) {
                if (sign != 0 && sign * cross < 0) return false;
                sign = cross;
            }
            area += xs[i] * ys[j] - xs[j] * ys[i];
        }
        return Math.abs(area) >= 16;
    }
    private GizmoPick() {}
    public static boolean prefer(double candidate, double best, boolean candidateHovered, boolean bestHovered) {
        if (!Double.isFinite(candidate)) return false;
        if (!Double.isFinite(best)) return true;
        if (candidate < best - 0.75) return true;
        return Math.abs(candidate - best) <= 0.75 && candidateHovered && !bestHovered;
    }
    public static boolean usableAxis(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y) && x * x + y * y >= 64;
    }
}
