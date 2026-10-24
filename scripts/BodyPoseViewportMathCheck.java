import com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport.*;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** Standalone regression checks; no Minecraft client or GPU is required. */
public final class BodyPoseViewportMathCheck {
    private static int checks;
    public static void main(String[] args) {
        checkGizmoFramesAndPlanes();
        for (boolean ortho : new boolean[]{false, true}) {
            PoseViewportFrame frame = new PoseViewportFrame(37, 21, 800, 500, 100,
                    31, -17, 0, 0, 0, 24, ortho);
            var center = frame.project(0, 0, 0);
            near(center.x(), 468, 0.001, "viewport origin and horizontal pan");
            near(center.y(), 254, 0.001, "viewport origin and vertical pan");
            var point = frame.project(1, 2, 12);
            near(point.x(), 468 + (ortho ? 100 : 200), 0.001, "projection X");
            near(point.y(), 254 + (ortho ? 200 : 400), 0.001, "projection Y");
            check(!Float.isFinite(frame.project(0, 0, 25).x()), "behind camera must not be pickable");
            check(!Float.isFinite(frame.project(0, 0, 23.99f).x()), "near plane must not be clamped");
            check(!Float.isFinite(frame.project(0, 0, -600).x()), "far plane must not be pickable");

            // All three ring planes with both oblique camera and model rotations.
            for (int axis = 0; axis < 3; axis++) {
                for (int camera = 0; camera < 3; camera++) {
                    frame = new PoseViewportFrame(37, 21, 800, 500, 100, 31, -17,
                            28 + camera * 7, 38 + camera * 9, 13, 24, ortho);
                    Vector3f c = new Vector3f(1, -0.4f, 2);
                    Vector3f u = switch (axis) {
                        case 0 -> new Vector3f(0, 1, 0);
                        default -> new Vector3f(1, 0, 0);
                    };
                    Vector3f v = axis == 2 ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
                    u.rotateZ(0.17f).rotateY(0.11f);
                    v.rotateZ(0.17f).rotateY(0.11f);
                    var pc = project(frame, c, ortho);
                    var pu = project(frame, new Vector3f(c).add(u), ortho);
                    var pv = project(frame, new Vector3f(c).add(v), ortho);
                    ProjectedRingDrag drag = new ProjectedRingDrag(pc, pu, pv, pu.x(), pu.y(), 1, 0, 40);
                    for (int i = 1; i <= 144; i++) {
                        double angle = i * Math.PI / 36;
                        Vector3f p = new Vector3f(c).fma((float) Math.cos(angle), u).fma((float) Math.sin(angle), v);
                        var screen = project(frame, p, ortho);
                        near(drag.radians(screen.x(), screen.y()), angle, 0.0001, "tilted ring / continuous turns");
                    }
                }
            }
        }
        var edge = new ProjectedRingDrag(new ProjectedRingDrag.Point(10, 20, 1),
                new ProjectedRingDrag.Point(50, 20, 1), new ProjectedRingDrag.Point(80, 20, 1),
                50, 20, 1, 0, 40);
        near(edge.radians(90, 20), 1, 1e-9, "edge-on tangent fallback");
        check(!GizmoPick.prefer(4, 4, false, true), "equal distance preserves hover");
        check(GizmoPick.prefer(4.5, 4, true, false), "hover hysteresis");
        check(GizmoPick.prefer(1, 4, false, true), "closer handle wins");
        check(!GizmoPick.prefer(Double.NaN, 4, true, false), "invalid candidate");
        check(!GizmoPick.usableAxis(1, 1), "foreshortened axis");
        check(!GizmoPick.usableAxis(Double.NaN, 20), "invalid axis");
        DragAdjustment adjustment = new DragAdjustment(false);
        near(adjustment.update(10, false, false, 5), 10, 0, "normal drag");
        near(adjustment.update(20, true, false, 5), 11, 1e-9, "fine drag");
        near(adjustment.update(20, true, true, 5), 11, 1e-9, "snap press continuity");
        near(adjustment.update(70, true, true, 5), 16, 1e-9, "relative snap");
        near(adjustment.update(70, false, false, 5), 16, 1e-9, "snap release continuity");
        near(adjustment.update(71, false, false, 5), 17, 1e-9, "resume drag");
        System.out.println("Body pose viewport: " + checks + " checks passed");
    }

    private static void checkGizmoFramesAndPlanes() {
        DragPointer pointer = new DragPointer(10, 20, 3);
        check(pointer.update(12, 20) == null, "click does not change pose");
        near(pointer.update(14, 20).x(), 11, 0, "consume start dead zone");
        near(pointer.update(10, 20).x(), 7, 0, "dead zone is not reapplied on reversal");
        pointer.end();
        check(pointer.update(100, 100) == null, "ended transaction ignores motion");
        check(!GizmoPick.insideQuad(19, 1, new double[]{10, 20, 10, 0}, new double[]{0, 10, 20, 10}),
                "quad bounding box corner is not a handle");
        check(GizmoPick.insideQuad(10, 10, new double[]{10, 20, 10, 0}, new double[]{0, 10, 20, 10}),
                "quad interior is selectable");
        for (boolean ortho : new boolean[]{true, false}) {
            PoseViewportFrame camera = new PoseViewportFrame(17, 29, 800, 600, 150, 0, 0, 19, 31, 0, 24, ortho);
            Matrix4f transform = new Matrix4f().translate(0.4f, -0.2f, 1.7f).rotateXYZ(0.37f, 0.61f, -0.23f);
            GizmoFrame<ProjectedRingDrag.Point> frame = new GizmoFrame<>("arm", false, (x, y, z) ->
                    project(camera, transform.transformPosition(new Vector3f(x, y, z)), ortho));
            check(!frame.matches("leg", false), "selection rejects stale frame");
            check(!frame.matches("arm", true), "tool rejects stale frame");
            for (int axis = 0; axis < 3; axis++) {
                for (int i = 1; i <= GizmoGeometry.RING_SEGMENTS; i++) {
                    // Sample the mesh's shared centerline, then pass it through the production picker.
                    Vector3f mesh = GizmoGeometry.ringPoint(axis,
                            (float) (2 * Math.PI * i / GizmoGeometry.RING_SEGMENTS), GizmoGeometry.RING_RADIUS);
                    var rendered = project(camera, transform.transformPosition(mesh), ortho);
                    var a = frame.ring(axis, i - 1);
                    var b = frame.ring(axis, i);
                    near(GizmoPick.distanceToSegment(rendered.x(), rendered.y(), a.x(), a.y(), b.x(), b.y()),
                            0, 0.001, "rendered ring and production hit segment agree");
                }
                Vector3f u = GizmoGeometry.ringPoint(axis, 0, GizmoGeometry.RING_RADIUS);
                Vector3f v = GizmoGeometry.ringPoint(axis, (float) (Math.PI / 2), GizmoGeometry.RING_RADIUS);
                var grab = project(camera, transform.transformPosition(new Vector3f(u).mul(0.4f).fma(0.35f, v)), ortho);
                var drag = new ProjectedPlaneDrag(frame.center(), frame.ring(axis, 0),
                        frame.ring(axis, GizmoGeometry.RING_SEGMENTS / 4), grab.x(), grab.y());
                if (!drag.usable()) continue;
                near(drag.delta(grab.x(), grab.y()).u(), 0, 1e-8, "preserve plane grab offset");
                var target = project(camera, transform.transformPosition(new Vector3f(u).mul(1.1f).fma(-0.2f, v)), ortho);
                near(drag.delta(target.x(), target.y()).u(), 0.7, 0.0001, "plane movement U");
                near(drag.delta(target.x(), target.y()).v(), -0.55, 0.0001, "plane movement V");
            }
            var before = frame.ring(0, 0);
            transform.rotateXYZ(0.9f, 0.3f, 0.7f);
            near(frame.ring(0, 0).x(), before.x(), 0, "input retains displayed snapshot until next render");
            var next = new GizmoFrame<ProjectedRingDrag.Point>("arm", false, (x, y, z) ->
                    project(camera, transform.transformPosition(new Vector3f(x, y, z)), ortho));
            check(Math.hypot(next.ring(0, 0).x() - before.x(), next.ring(0, 0).y() - before.y()) > 8,
                    "next rendered frame follows model rotation");
            boolean rejectedOldPosition = false;
            for (int old = 0; old < GizmoGeometry.RING_SEGMENTS; old++) {
                var p = frame.ring(0, old);
                double distance = Double.POSITIVE_INFINITY;
                for (int i = 1; i <= GizmoGeometry.RING_SEGMENTS; i++) {
                    var a = next.ring(0, i - 1);
                    var b = next.ring(0, i);
                    distance = Math.min(distance, GizmoPick.distanceToSegment(p.x(), p.y(), a.x(), a.y(), b.x(), b.y()));
                }
                rejectedOldPosition |= distance > 8;
            }
            check(rejectedOldPosition, "old ring position outside new contour must miss");
        }
        var edge = new ProjectedPlaneDrag(new ProjectedRingDrag.Point(0, 0, 1),
                new ProjectedRingDrag.Point(10, 0, 1), new ProjectedRingDrag.Point(20, 0, 1), 0, 0);
        check(!edge.usable(), "edge-on plane cannot start a drag");
    }

    private static ProjectedRingDrag.Point project(PoseViewportFrame frame, Vector3f point, boolean ortho) {
        var p = frame.project(point.x, point.y, point.z);
        return new ProjectedRingDrag.Point(p.x(), p.y(), ortho ? 1 : p.depth());
    }
    private static void near(double actual, double expected, double tolerance, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
