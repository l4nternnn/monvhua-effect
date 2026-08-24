package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class HoldHandsRigidArmSegmentRenderer {
    private static final double MIN_SEGMENT_LENGTH = 0.04D;
    private static final float ARM_PIXEL_LENGTH = 12.0F;
    private static final float VANILLA_ARM_SCALE = 1.0F;
    private static final double MAX_RENDER_ARM_LENGTH = 1.45D;
    private static final double HARD_RENDER_ARM_LENGTH = 2.20D;
    private static final BakedArm DEFAULT_LEFT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.LEFT, false);
    private static final BakedArm DEFAULT_RIGHT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.RIGHT, false);
    private static final BakedArm SLIM_LEFT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.LEFT, true);
    private static final BakedArm SLIM_RIGHT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.RIGHT, true);
    private static final Map<Long, Quaternionf> LAST_ROTATIONS = new ConcurrentHashMap<>();
    private static final Map<Long, Vec3d> LAST_VALID_TARGETS = new ConcurrentHashMap<>();

    private HoldHandsRigidArmSegmentRenderer() {
    }

    static boolean render(PlayerEntityRenderState state, MatrixStack matrices,
                          VertexConsumerProvider vertexConsumers, Identifier texture, int light,
                          boolean slim, HoldHandsSkeletalPose.HandSide side) {
        if (state == null || matrices == null || vertexConsumers == null || texture == null || side == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }

        Entity self = client.world.getEntityById(state.id);
        if (self == null) {
            return false;
        }

        long renderKey = (((long) state.id) << 1) ^ side.ordinal();
        if (!HoldHandsClientState.isHoldingHands(state.id)) {
            LAST_ROTATIONS.remove(renderKey);
            LAST_VALID_TARGETS.remove(renderKey);
            return false;
        }

        float renderBodyYaw = HoldHandsClientState.isFollower(state.id)
                ? HoldHandsClientState.getHoldBodyYaw(state.id, state.bodyYaw)
                : state.bodyYaw;
        Vec3d renderFeet = new Vec3d(state.x, state.y, state.z);
        if (!finiteVec(renderFeet)) {
            renderFeet = self.getPos();
        }
        Vec3d targetWorld = HoldHandsClientState.getSharedHandPoint(state.id);
        if (targetWorld == null) {
            return false;
        }
        Vec3d startLocal = HoldHandsLinkGeometry.shoulderSocket(side);
        Vec3d endLocal = HoldHandsLinkGeometry.worldVectorToBodyLocal(targetWorld.subtract(renderFeet), renderBodyYaw);
        Vec3d modelStart = toVanillaModelSpace(startLocal);
        Vec3d modelEnd = toVanillaModelSpace(endLocal);
        Vec3d modelSegment = modelEnd.subtract(modelStart);
        double segmentLength = modelSegment.length();
        if (!Double.isFinite(segmentLength) || segmentLength <= MIN_SEGMENT_LENGTH) {
            return false;
        }

        if (segmentLength > MAX_RENDER_ARM_LENGTH) {
            Vec3d previousTarget = LAST_VALID_TARGETS.get(renderKey);
            if (finiteVec(previousTarget)) {
                Vec3d previousLocal = HoldHandsLinkGeometry.worldVectorToBodyLocal(
                        previousTarget.subtract(renderFeet), renderBodyYaw);
                Vec3d previousModel = toVanillaModelSpace(previousLocal);
                if (previousModel.subtract(modelStart).length() <= HARD_RENDER_ARM_LENGTH) {
                    modelEnd = previousModel;
                    modelSegment = modelEnd.subtract(modelStart);
                    segmentLength = modelSegment.length();
                }
            }
            if (segmentLength > MAX_RENDER_ARM_LENGTH) {
                if (segmentLength > HARD_RENDER_ARM_LENGTH) {
                    modelEnd = modelStart.add(modelSegment.multiply(MAX_RENDER_ARM_LENGTH / segmentLength));
                    modelSegment = modelEnd.subtract(modelStart);
                }
                segmentLength = modelSegment.length();
            }
        }
        if (!Double.isFinite(segmentLength) || segmentLength <= MIN_SEGMENT_LENGTH
                || segmentLength > HARD_RENDER_ARM_LENGTH) {
            return false;
        }
        Vec3d safeTargetWorld = renderFeet.add(HoldHandsLinkGeometry.bodyLocalToWorldVector(
                fromVanillaModelSpace(modelEnd), renderBodyYaw));
        if (finiteVec(safeTargetWorld)) {
            LAST_VALID_TARGETS.put(renderKey, safeTargetWorld);
        }
        if (LAST_ROTATIONS.size() > 4096 || LAST_VALID_TARGETS.size() > 4096) {
            LAST_ROTATIONS.clear();
            LAST_VALID_TARGETS.clear();
            if (finiteVec(safeTargetWorld)) {
                LAST_VALID_TARGETS.put(renderKey, safeTargetWorld);
            }
        }

        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        Quaternionf rotation = smoothRotation(renderKey, stableArmRotation(modelSegment.multiply(1.0D / segmentLength), side));
        renderArmModel(matrices, vertices, modelStart, modelEnd,
                bakedArm(side, slim), side, light, rotation);
        return true;
    }

    private static Vec3d toVanillaModelSpace(Vec3d bodyLocal) {
        return new Vec3d(-bodyLocal.x, 1.501D - bodyLocal.y, -bodyLocal.z);
    }

    private static Vec3d fromVanillaModelSpace(Vec3d model) {
        return new Vec3d(-model.x, 1.501D - model.y, -model.z);
    }

    private static void renderArmModel(MatrixStack matrices, VertexConsumer vertices, Vec3d start, Vec3d end,
                                       BakedArm arm, HoldHandsSkeletalPose.HandSide side, int light,
                                       Quaternionf rotation) {
        Vec3d segment = end.subtract(start);
        double length = segment.length();
        if (length <= MIN_SEGMENT_LENGTH) {
            return;
        }

        Vec3d axis = segment.multiply(1.0D / length);
        matrices.push();
        try {
            matrices.translate((float) start.x, (float) start.y, (float) start.z);
            matrices.multiply(rotation);
            matrices.scale(1.0F, VANILLA_ARM_SCALE, 1.0F);
            CarryPoseModelApplier.beginSuppressPartPose();
            try {
                arm.root().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
            } finally {
                CarryPoseModelApplier.endSuppressPartPose();
            }
        } finally {
            matrices.pop();
        }
    }

    private static Quaternionf smoothRotation(long key, Quaternionf target) {
        Quaternionf previous = LAST_ROTATIONS.get(key);
        if (previous == null) {
            Quaternionf stored = new Quaternionf(target);
            LAST_ROTATIONS.put(key, stored);
            return target;
        }
        previous.slerp(target, 0.35F);
        return new Quaternionf(previous);
    }

    private static Quaternionf stableArmRotation(Vec3d armAxis, HoldHandsSkeletalPose.HandSide side) {
        Vec3d direction = safeNormalize(armAxis, new Vec3d(0.0D, 1.0D, 0.0D));
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
    }

    private static Vec3d perpendicularComponent(Vec3d vector, Vec3d normal) {
        return vector.subtract(normal.multiply(vector.dotProduct(normal)));
    }

    private static Vec3d safeNormalize(Vec3d vector, Vec3d fallback) {
        return vector.lengthSquared() > 0.000001D ? vector.normalize() : fallback;
    }

    private static boolean finiteVec(Vec3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static BakedArm bakedArm(HoldHandsSkeletalPose.HandSide side, boolean slim) {
        if (side == HoldHandsSkeletalPose.HandSide.LEFT) {
            return slim ? SLIM_LEFT_ARM : DEFAULT_LEFT_ARM;
        }
        return slim ? SLIM_RIGHT_ARM : DEFAULT_RIGHT_ARM;
    }

    private static BakedArm bakeArm(HoldHandsSkeletalPose.HandSide side, boolean slim) {
        int width = slim ? 3 : 4;
        int skinU = side == HoldHandsSkeletalPose.HandSide.LEFT ? 32 : 40;
        int skinV = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 16;
        int sleeveU = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 40;
        int sleeveV = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 32;
        float minX = -width * 0.5F;

        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        ModelPartData arm = root.addChild("arm",
                ModelPartBuilder.create().uv(skinU, skinV).cuboid(minX, 0.0F, -2.0F,
                        width, ARM_PIXEL_LENGTH, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        arm.addChild("sleeve",
                ModelPartBuilder.create().uv(sleeveU, sleeveV).cuboid(minX, 0.0F, -2.0F,
                        width, ARM_PIXEL_LENGTH, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        return new BakedArm(TexturedModelData.of(modelData, 64, 64).createModel());
    }

    private record BakedArm(ModelPart root) {
    }
}
