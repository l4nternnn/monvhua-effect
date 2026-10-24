package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Snapshot in skeleton units, before any UI/world reflection. */
public final class BonePoseFrame {
    private final Vector3f pivot, effective;
    private final Matrix4f inverseParent;
    private final Quaternionf orientation, local;
    private final boolean modelOffset;

    public BonePoseFrame(Vector3f pivot, Matrix4f parent, Quaternionf orientation,
                         Vector3f effective, boolean modelOffset) {
        this.pivot = new Vector3f(pivot);
        this.effective = new Vector3f(effective);
        this.orientation = new Quaternionf(orientation).normalize();
        inverseParent = new Matrix4f(parent).invert();
        this.modelOffset = modelOffset;
        local = rotation(effective);
    }

    public Vector3f pivot() { return new Vector3f(pivot); }
    public Quaternionf orientation() { return new Quaternionf(orientation); }
    public Vector3f offsetDelta(Vector3f localDelta) {
        Vector3f model = orientation.transform(new Vector3f(localDelta));
        return modelOffset ? model : inverseParent.transformDirection(model);
    }

    public Vector3f rotatedEditor(Vector3f startEditor, int axis, float degrees) {
        return rotatedEditor(startEditor, axis, degrees, startEditor);
    }

    public Vector3f rotatedEditor(Vector3f startEditor, int axis, float degrees, Vector3f previousEditor) {
        Vector3f direction = new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
        Quaternionf target = new Quaternionf(local).mul(new Quaternionf().rotationAxis(
                (float) Math.toRadians(degrees), direction));
        Vector3f reference = new Vector3f(previousEditor).sub(startEditor).add(effective);
        Vector3f targetEuler = nearestEuler(target, reference);
        return targetEuler.sub(effective).add(startEditor);
    }

    public static Quaternionf rotation(Vector3f degrees) {
        return new Quaternionf().rotateZ((float) Math.toRadians(degrees.z))
                .rotateY((float) Math.toRadians(degrees.y)).rotateX((float) Math.toRadians(degrees.x));
    }

    /** Shared forward evaluator used by the actual skeletal renderer and regression checks. */
    public static Matrix4f evaluate(Matrix4f parent, Vector3f origin, Vector3f offset,
                                    Vector3f effective, float scale) {
        return new Matrix4f(parent).translate(origin).translate(offset).rotate(rotation(effective)).scale(scale);
    }

    /** Decompose Rz Ry Rx and select the equivalent solution nearest the starting pose. */
    public static Vector3f nearestEuler(Quaternionf rotation, Vector3f reference) {
        Matrix4f m = new Matrix4f().rotation(rotation);
        double cosineY = Math.hypot(m.m00(), m.m01());
        double y = Math.atan2(-m.m02(), cosineY);
        double x, z;
        if (cosineY < 1e-4) {
            x = Math.toRadians(reference.x);
            z = Math.atan2(-m.m10(), m.m11()) + (y >= 0 ? x : -x);
        } else {
            x = Math.atan2(m.m12(), m.m22());
            z = Math.atan2(m.m01(), m.m00());
        }
        Vector3f first = near(new Vector3f((float) Math.toDegrees(x), (float) Math.toDegrees(y),
                (float) Math.toDegrees(z)), reference);
        Vector3f second = near(new Vector3f((float) Math.toDegrees(x) + 180,
                180 - (float) Math.toDegrees(y), (float) Math.toDegrees(z) + 180), reference);
        return first.distanceSquared(reference) <= second.distanceSquared(reference) ? first : second;
    }

    private static Vector3f near(Vector3f value, Vector3f reference) {
        for (int i = 0; i < 3; i++) {
            float angle = value.get(i);
            value.setComponent(i, angle + 360 * Math.round((reference.get(i) - angle) / 360));
        }
        return value;
    }
}
