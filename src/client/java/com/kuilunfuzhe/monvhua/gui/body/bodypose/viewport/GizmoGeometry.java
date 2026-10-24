package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import org.joml.Vector3f;

/** Geometry shared by the world mesh, viewport overlay and input projection. */
public final class GizmoGeometry {
    public static final float AXIS_LENGTH = 4.0F / 3.0F;
    public static final float RING_RADIUS = 2.45F / 3.0F;
    public static final int RING_SEGMENTS = 72;

    private GizmoGeometry() {}

    public static Vector3f planeCorner(int axis, int corner) {
        float u = corner == 0 || corner == 3 ? 0.22F : 0.48F;
        float v = corner < 2 ? 0.22F : 0.48F;
        return switch (axis) {
            case 0 -> new Vector3f(0, u, v);
            case 1 -> new Vector3f(u, 0, v);
            case 2 -> new Vector3f(u, v, 0);
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }

    public static Vector3f ringPoint(int axis, float angle, float radius) {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;
        return switch (axis) {
            case 0 -> new Vector3f(0, c, s);
            case 1 -> new Vector3f(c, 0, s);
            case 2 -> new Vector3f(c, s, 0);
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }
}
