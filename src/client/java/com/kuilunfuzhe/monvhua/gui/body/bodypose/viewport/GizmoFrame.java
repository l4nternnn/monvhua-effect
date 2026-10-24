package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import java.util.ArrayList;
import java.util.List;

/** Immutable projected geometry. T must be an immutable screen point. */
public final class GizmoFrame<T> {
    @FunctionalInterface
    public interface Projector<T> { T project(float x, float y, float z); }

    private final String target;
    private final BonePoseFrame bone;
    private final boolean rotation;
    private final T center;
    private final List<T> ends;
    private final List<List<T>> rings;
    private final List<List<T>> planes;

    public GizmoFrame(String target, boolean rotation, Projector<T> projector) {
        this(target, rotation, projector, null);
    }

    public GizmoFrame(String target, boolean rotation, Projector<T> projector, BonePoseFrame bone) {
        this.bone = bone;
        this.target = target;
        this.rotation = rotation;
        center = projector.project(0, 0, 0);
        var axes = new ArrayList<T>(3);
        var circles = new ArrayList<List<T>>(3);
        var quads = new ArrayList<List<T>>(3);
        for (int axis = 0; axis < 3; axis++) {
            float length = GizmoGeometry.AXIS_LENGTH;
            axes.add(projector.project(axis == 0 ? length : 0, axis == 1 ? length : 0, axis == 2 ? length : 0));
            var points = new ArrayList<T>(GizmoGeometry.RING_SEGMENTS + 1);
            for (int i = 0; i <= GizmoGeometry.RING_SEGMENTS; i++) {
                var p = GizmoGeometry.ringPoint(axis,
                        (float) (Math.PI * 2 * i / GizmoGeometry.RING_SEGMENTS), GizmoGeometry.RING_RADIUS);
                points.add(projector.project(p.x, p.y, p.z));
            }
            circles.add(List.copyOf(points));
            var corners = new ArrayList<T>(4);
            for (int i = 0; i < 4; i++) {
                var p = GizmoGeometry.planeCorner(axis, i);
                corners.add(projector.project(p.x, p.y, p.z));
            }
            quads.add(List.copyOf(corners));
        }
        ends = List.copyOf(axes);
        rings = List.copyOf(circles);
        planes = List.copyOf(quads);
    }

    public boolean matches(String target, boolean rotation) {
        return this.target.equals(target) && this.rotation == rotation;
    }
    public T center() { return center; }
    public BonePoseFrame bone() { return bone; }
    public T end(int axis) { return ends.get(axis); }
    public T ring(int axis, int segment) { return rings.get(axis).get(segment); }
    public T plane(int axis, int corner) { return planes.get(axis).get(corner); }
}
