import com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport.*;
import org.joml.*;
import java.nio.file.*;
import java.util.*;

/** Uses actual bbmodel hierarchy exported by the PowerShell runner and the production evaluator. */
public final class BonePoseDirectionCheck {
    record Bone(String name, String parent, Vector3f origin, Vector3f rest) {}
    static final Map<String, Bone> bones = new LinkedHashMap<>();
    static int checks;
    public static void main(String[] args) throws Exception {
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] p = line.replace("\ufeff", "").split("\t");
            bones.put(p[0], new Bone(p[0], p[1], vector(p, 2), vector(p, 5)));
        }
        require(bones.get("left_arm_on").rest.z == -180, "real arm rest rotation");
        for (int scenario = 0; scenario < 3; scenario++) {
            float scale = new float[]{1, 0.8f, 1.15f}[scenario];
            Vector3f editor = new Vector3f(17 * scenario, -29 * scenario, 31 * scenario);
            for (Bone bone : bones.values()) {
                Matrix4f parent = world(bone.parent, editor, scale);
                Quaternionf orientation = orientation(bone.name, editor);
                Vector3f effective = new Vector3f(bone.rest).add(editor);
                BonePoseFrame frame = new BonePoseFrame(new Vector3f(), parent, orientation, effective, false);
                Matrix4f before = BonePoseFrame.evaluate(parent, bone.origin, new Vector3f(), effective, scale);
                for (int axis = 0; axis < 3; axis++) {
                    for (float sign : new float[]{-1, 1}) {
                        Vector3f local = new Vector3f().setComponent(axis, sign * 2);
                        Vector3f offset = frame.offsetDelta(local);
                        Matrix4f after = BonePoseFrame.evaluate(parent, bone.origin, offset, effective, scale);
                        Vector3f moved = after.getTranslation(new Vector3f()).sub(before.getTranslation(new Vector3f()));
                        Vector3f expected = orientation.transform(new Vector3f(local));
                        require(moved.distance(expected) < 0.001, bone.name + " axis displacement");
                        Vector3f next = frame.rotatedEditor(editor, axis, sign * 37);
                        Quaternionf actual = BonePoseFrame.rotation(new Vector3f(bone.rest).add(next));
                        Quaternionf wanted = BonePoseFrame.rotation(effective).mul(new Quaternionf().rotationAxis(
                                (float) java.lang.Math.toRadians(sign * 37), new Vector3f().setComponent(axis, 1)));
                        require(1 - java.lang.Math.abs(actual.dot(wanted)) < 0.00001, bone.name + " local rotation");
                        Vector3f wrapped = new Vector3f(next);
                        for (int i = 0; i < 3; i++) wrapped.setComponent(i,
                                wrapped.get(i) - 360 * (float) java.lang.Math.floor((wrapped.get(i) + 180) / 360));
                        Matrix4f restored = BonePoseFrame.evaluate(parent, bone.origin, new Vector3f(),
                                new Vector3f(bone.rest).add(wrapped), scale);
                        Matrix4f expectedWorld = new Matrix4f(parent).translate(bone.origin).rotate(wanted).scale(scale);
                        Vector3f marker = new Vector3f(0.3f, 1, -0.2f);
                        require(restored.transformPosition(new Vector3f(marker)).distance(
                                expectedWorld.transformPosition(new Vector3f(marker))) < 0.001, "wrapped persistence marker");
                    }
                }
            }
        }
        for (float pitch : new float[]{-120, 0, 57}) {
            for (float yaw : new float[]{-90, -89.999f, 89.999f, 90}) {
                Vector3f source = new Vector3f(pitch, yaw, 41);
                Quaternionf q = BonePoseFrame.rotation(source);
                Vector3f recovered = BonePoseFrame.nearestEuler(q, source);
                require(1 - java.lang.Math.abs(q.dot(BonePoseFrame.rotation(recovered))) < 0.00001, "Euler singularity");
            }
        }
        BonePoseFrame rotating = new BonePoseFrame(new Vector3f(), new Matrix4f(), new Quaternionf(), new Vector3f(), false);
        Quaternionf eyeOrientation = new Quaternionf().rotateXYZ(0.4f, 0.7f, -0.3f);
        BonePoseFrame eye = new BonePoseFrame(new Vector3f(), new Matrix4f().rotateY(1).scale(3),
                eyeOrientation, new Vector3f(), true);
        Vector3f eyeDelta = new Vector3f(1, 2, 3);
        require(eye.offsetDelta(eyeDelta).distance(eyeOrientation.transform(new Vector3f(eyeDelta))) < 0.0001,
                "model-space eye offset must not invert parent");
        Vector3f previous = new Vector3f();
        for (int degrees = 0; degrees <= 1080; degrees += 5) {
            Vector3f next = rotating.rotatedEditor(new Vector3f(), 0, degrees, previous);
            require(java.lang.Math.abs(next.x - degrees) < 0.001, "continuous multiple turns");
            previous = next;
        }
        // A reflected UI basis must still recover the positive skeleton angle from the visible ring.
        PoseViewportFrame camera = new PoseViewportFrame(0, 0, 800, 600, 100, 0, 0, 0, 0, 0, 24, false);
        for (int axis = 0; axis < 3; axis++) {
            Matrix4f view = new Matrix4f().rotateXYZ(0.6f, 0.7f, 0.2f).scale(1.4f, -1.4f, 1.4f);
            var c = project(camera, new Vector3f());
            var u = project(camera, view.transformPosition(GizmoGeometry.ringPoint(axis, 0, 1)));
            var v = project(camera, view.transformPosition(GizmoGeometry.ringPoint(axis, (float)(java.lang.Math.PI / 2), 1)));
            ProjectedRingDrag drag = new ProjectedRingDrag(c, u, v, u.x(), u.y(), 1, 0, 100);
            var p = project(camera, view.transformPosition(GizmoGeometry.ringPoint(axis, 0.2f, 1)));
            require(java.lang.Math.abs(drag.radians(p.x(), p.y()) - 0.2) < 0.0001, "reflected projected ring direction");
        }
        System.out.println("Bone pose direction: " + checks + " checks passed (" + bones.size() + " actual bones)");
    }
    static Matrix4f world(String name, Vector3f editor, float scale) {
        Bone b = bones.get(name);
        return b == null ? new Matrix4f() : BonePoseFrame.evaluate(world(b.parent, editor, scale), b.origin,
                new Vector3f(), new Vector3f(b.rest).add(editor), scale);
    }
    static Quaternionf orientation(String name, Vector3f editor) {
        Bone b = bones.get(name);
        return b == null ? new Quaternionf() : orientation(b.parent, editor).mul(BonePoseFrame.rotation(new Vector3f(b.rest).add(editor)));
    }
    static Vector3f vector(String[] p, int i) {
        return new Vector3f(Float.parseFloat(p[i]), Float.parseFloat(p[i + 1]), Float.parseFloat(p[i + 2]));
    }
    static ProjectedRingDrag.Point project(PoseViewportFrame camera, Vector3f p) {
        var s = camera.project(p.x, p.y, p.z);
        return new ProjectedRingDrag.Point(s.x(), s.y(), s.depth());
    }
    static void require(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
}
