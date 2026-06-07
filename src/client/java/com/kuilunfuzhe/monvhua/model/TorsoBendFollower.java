package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TorsoBendFollower {
    private static final float TORSO_BEND_PIVOT_Y = 6.0F;
    private static final float TORSO_POSE_PIVOT_Y = 12.0F;

    private TorsoBendFollower() {
    }

    public static void applyPose(PlayerEntityModel model, float posePitch, float poseYaw, float poseRoll, float scale) {
        if (model == null) {
            return;
        }

        if (hasBend(posePitch, poseYaw, poseRoll)) {
            float pitch = degreesToRadians(posePitch);
            float yaw = degreesToRadians(poseYaw);
            float roll = degreesToRadians(poseRoll);
            Vector3f pivot = getBodyPivot(model.body, TORSO_POSE_PIVOT_Y);
            Quaternionf poseRotation = createModelPartRotation(pitch, yaw, roll);

            rotateRootPartAroundPivot(model.body, pivot, poseRotation, pitch, yaw, roll);
            rotateRootPartAroundPivot(model.head, pivot, poseRotation, pitch, yaw, roll);
            rotateRootPartAroundPivot(model.leftArm, pivot, poseRotation, pitch, yaw, roll);
            rotateRootPartAroundPivot(model.rightArm, pivot, poseRotation, pitch, yaw, roll);
        }

        setUniformScale(model.body, scale);
    }

    public static void apply(PlayerEntityModel model, float bendPitch, float bendYaw, float bendRoll) {
        if (model == null || !hasBend(bendPitch, bendYaw, bendRoll)) {
            return;
        }

        float pitch = degreesToRadians(bendPitch);
        float yaw = degreesToRadians(bendYaw);
        float roll = degreesToRadians(bendRoll);
        Vector3f pivot = getBodyPivot(model.body, TORSO_BEND_PIVOT_Y);
        Quaternionf bendRotation = createModelPartRotation(pitch, yaw, roll);

        rotateRootPartAroundPivot(model.body, pivot, bendRotation, pitch, yaw, roll);
        rotateRootPartAroundPivot(model.head, pivot, bendRotation, pitch, yaw, roll);
        rotateRootPartAroundPivot(model.leftArm, pivot, bendRotation, pitch, yaw, roll);
        rotateRootPartAroundPivot(model.rightArm, pivot, bendRotation, pitch, yaw, roll);

        ModelPart waist = getChild(model.body, CombinedBodyModelData.WAIST);
        if (waist != null) {
            rotatePart(waist, -pitch, -yaw, -roll);
        }
        ModelPart waistBlend = getChild(model.body, CombinedBodyModelData.WAIST_BLEND);
        if (waistBlend != null) {
            rotatePart(waistBlend, -pitch * 0.5F, -yaw * 0.5F, -roll * 0.5F);
        }
    }

    private static Vector3f getBodyPivot(ModelPart body, float localY) {
        Vector3f pivot = new Vector3f(0.0F, localY, 0.0F);
        if (body == null) {
            return pivot;
        }
        createModelPartRotation(body.pitch, body.yaw, body.roll).transform(pivot);
        pivot.add(body.originX, body.originY, body.originZ);
        return pivot;
    }

    private static void rotateRootPartAroundPivot(ModelPart part, Vector3f pivot, Quaternionf rotation,
                                                  float pitch, float yaw, float roll) {
        if (part == null) {
            return;
        }
        Vector3f origin = new Vector3f(part.originX - pivot.x, part.originY - pivot.y, part.originZ - pivot.z);
        rotation.transform(origin);
        part.originX = pivot.x + origin.x;
        part.originY = pivot.y + origin.y;
        part.originZ = pivot.z + origin.z;
        rotatePart(part, pitch, yaw, roll);
    }

    private static void rotatePart(ModelPart part, float pitch, float yaw, float roll) {
        part.pitch += pitch;
        part.yaw += yaw;
        part.roll += roll;
    }

    private static void setUniformScale(ModelPart part, float scale) {
        if (part == null) {
            return;
        }
        part.xScale *= scale;
        part.yScale *= scale;
        part.zScale *= scale;
    }

    private static Quaternionf createModelPartRotation(float pitch, float yaw, float roll) {
        return new Quaternionf().rotateZ(roll).rotateY(yaw).rotateX(pitch);
    }

    private static ModelPart getChild(ModelPart part, String childName) {
        return part != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private static boolean hasBend(float bendPitch, float bendYaw, float bendRoll) {
        return bendPitch != 0.0F || bendYaw != 0.0F || bendRoll != 0.0F;
    }

    private static float degreesToRadians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
