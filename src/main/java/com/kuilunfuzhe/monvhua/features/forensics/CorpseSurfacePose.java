package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.NbtCompound;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Mirrors the ordinary split-limb model and TorsoBendFollower without client classes. */
public final class CorpseSurfacePose {
    private CorpseSurfacePose() {}

    public static Matrix4f transform(WoundGeometry.Surface surface, NbtCompound data) {
        String part = surface.part();
        boolean arm = part.endsWith("arm"), leg = part.endsWith("leg");
        boolean left = part.startsWith("left");
        Vector3f original = new Vector3f(arm ? (left ? 5 : -5) : leg ? (left ? 1.9f : -1.9f) : 0,
                arm ? 2 : leg ? 12 : 0, 0);
        Vector3f origin = new Vector3f(original);
        Vector3f rotation = new Vector3f();
        Vector3f torsoPose = angles(data, "pose_torso_");
        Vector3f torsoBend = angles(data, "bend_torso_");
        if (!leg) {
            Quaternionf pose = rotation(torsoPose);
            Vector3f torsoOrigin = pose.transform(new Vector3f(0, -12, 0)).add(0, 12, 0);
            origin.sub(0, 12, 0); pose.transform(origin); origin.add(0, 12, 0);
            Vector3f bendPivot = pose.transform(new Vector3f(0, 6, 0)).add(torsoOrigin);
            origin.sub(bendPivot); rotation(torsoBend).transform(origin); origin.add(bendPivot);
            rotation.add(torsoPose).add(torsoBend);
        }
        if (!part.equals("torso")) rotation.add(angles(data, "pose_" + part + "_"));
        float scale = Math.max(.1f, data.getFloat("pose_" + part + "_scale", 1));
        Matrix4f result = new Matrix4f().translate(origin.div(16)).rotate(rotation(rotation)).scale(scale);
        if (surface.name().endsWith("lower")) {
            float pivotY = arm ? 4 : 6;
            Vector3f bend = part.equals("torso") ? new Vector3f(torsoBend).negate() : angles(data, "bend_" + part + "_");
            result.translate(0, pivotY / 16, 0).rotate(rotation(bend)).translate(0, -pivotY / 16, 0);
        }
        return result.translate(-original.x / 16, -original.y / 16, -original.z / 16);
    }

    private static Vector3f angles(NbtCompound data, String prefix) {
        return new Vector3f(rad(data.getFloat(prefix + "pitch", 0)), rad(data.getFloat(prefix + "yaw", 0)),
                rad(data.getFloat(prefix + "roll", 0)));
    }
    private static float rad(float degrees) { return (float)Math.toRadians(degrees); }
    private static Quaternionf rotation(Vector3f r) { return new Quaternionf().rotateZ(r.z).rotateY(r.y).rotateX(r.x); }
}
