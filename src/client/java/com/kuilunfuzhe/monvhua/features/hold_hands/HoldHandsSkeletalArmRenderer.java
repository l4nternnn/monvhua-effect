package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.mixin.PlayerEntityRenderStateAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class HoldHandsSkeletalArmRenderer {
    private static final Set<String> RIGHT_ARM_ONLY = Set.of(HoldHandsSkeletalPose.RIGHT_ARM_PART);
    private static final Set<String> LEFT_ARM_ONLY = Set.of(HoldHandsSkeletalPose.LEFT_ARM_PART);
    private static final Set<String> BOTH_ARMS = Set.of(HoldHandsSkeletalPose.RIGHT_ARM_PART, HoldHandsSkeletalPose.LEFT_ARM_PART);
    private static final float ARM_MIN_HORIZONTAL_ANGLE = 0.0F;
    private static final float ARM_MAX_HORIZONTAL_ANGLE = 135.0F;
    private static final float ARM_YAW_SCALE = 0.72F;
    private static final float ARM_PITCH_SCALE = 0.62F;
    private static final float ELBOW_BEND_SCALE = 48.0F;
    private static final float MAX_ELBOW_BEND = 42.0F;
    private static final float MIN_ELBOW_BEND = -24.0F;
    private static final float STRETCH_DEADZONE = 0.08F;
    private static final float MAX_STRETCH_EXTRA_DISTANCE = 0.9F;
    private static final float STRETCH_UP_PITCH = 28.0F;
    private static final float SHOULDER_HEIGHT = 1.34F;
    private static final float HAND_TARGET_HEIGHT = 1.18F;
    private static final float SHOULDER_SIDE_OFFSET = 0.34F;
    private static final float PALM_SIDE_OFFSET = 0.46F;
    private static final float TARGET_LIFT_DISTANCE = 0.34F;
    private static final double DEFAULT_FOLLOW_SIDE_OFFSET = 1.008708D;
    private static final double DEFAULT_FOLLOW_FORWARD_OFFSET = 0.089465946D;

    private static boolean hiddenArm;
    private static boolean hiddenArmVisible;
    private static boolean hiddenSleeveVisible;
    private static HoldHandsSkeletalPose.HandSide hiddenSide;

    private HoldHandsSkeletalArmRenderer() {
    }

    public static void hideVanillaArm(PlayerEntityRenderState state, PlayerEntityModel model) {
        if (hiddenArm || state == null || model == null || !HoldHandsClientState.isHoldingHands(state.id)) {
            return;
        }

        hiddenSide = HoldHandsClientState.getHandSide(state.id);
        hiddenArm = true;
        if (hiddenSide == HoldHandsSkeletalPose.HandSide.LEFT) {
            hiddenArmVisible = model.leftArm.visible;
            hiddenSleeveVisible = model.leftSleeve.visible;
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        } else {
            hiddenArmVisible = model.rightArm.visible;
            hiddenSleeveVisible = model.rightSleeve.visible;
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
    }

    public static void renderHeldArmAndRestore(PlayerEntityRenderState state, PlayerEntityModel model,
                                               MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        try {
            renderHeldArm(state, matrices, vertexConsumers, light);
        } finally {
            restoreVanillaArm(model);
        }
    }

    public static void restoreVanillaArm(PlayerEntityModel model) {
        if (!hiddenArm || model == null) {
            return;
        }

        if (hiddenSide == HoldHandsSkeletalPose.HandSide.LEFT) {
            model.leftArm.visible = hiddenArmVisible;
            model.leftSleeve.visible = hiddenSleeveVisible;
        } else {
            model.rightArm.visible = hiddenArmVisible;
            model.rightSleeve.visible = hiddenSleeveVisible;
        }
        hiddenArm = false;
        hiddenSide = null;
    }

    public static boolean renderHeldArm(PlayerEntityRenderState state, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light) {
        if (state == null || !HoldHandsClientState.isHoldingHands(state.id)) {
            return false;
        }

        SkinTextures skinTextures = ((PlayerEntityRenderStateAccessor) state).getSkinTextures();
        if (skinTextures == null) {
            return false;
        }

        HoldHandsSkeletalPose.HandSide side = HoldHandsClientState.getHandSide(state.id);
        Map<String, float[]> rotations = dynamicRotationsFor(state, side);
        return renderArm(matrices, vertexConsumers, skinTextures.texture(), light,
                skinTextures.model() == SkinTextures.Model.SLIM, side, rotations);
    }

    public static boolean renderRightArm(AbstractClientPlayerEntity player, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light) {
        return renderArm(player, matrices, vertexConsumers, light, HoldHandsSkeletalPose.HandSide.RIGHT);
    }

    public static boolean renderLeftArm(AbstractClientPlayerEntity player, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light) {
        return renderArm(player, matrices, vertexConsumers, light, HoldHandsSkeletalPose.HandSide.LEFT);
    }

    public static boolean renderArm(AbstractClientPlayerEntity player, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light,
                                    HoldHandsSkeletalPose.HandSide side) {
        if (player == null || side == null) {
            return false;
        }
        SkinTextures skinTextures = player.getSkinTextures();
        return renderArm(matrices, vertexConsumers, skinTextures.texture(), light,
                skinTextures.model() == SkinTextures.Model.SLIM, side);
    }

    public static boolean renderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    Identifier texture, int light, boolean slim,
                                    HoldHandsSkeletalPose.HandSide side) {
        if (matrices == null || vertexConsumers == null || texture == null || side == null) {
            return false;
        }

        Set<String> visibleParts = side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_ARM_ONLY : RIGHT_ARM_ONLY;
        NbtCompound customData = HoldHandsSkeletalPose.createDefaultArmCustomData("", side, slim);
        return render(matrices, vertexConsumers, texture, light,
                HoldHandsSkeletalPose.rotationsForHand(side), visibleParts, slim, customData);
    }

    private static boolean renderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                     Identifier texture, int light, boolean slim,
                                     HoldHandsSkeletalPose.HandSide side, Map<String, float[]> rotations) {
        if (matrices == null || vertexConsumers == null || texture == null || side == null) {
            return false;
        }

        Set<String> visibleParts = side == HoldHandsSkeletalPose.HandSide.LEFT ? LEFT_ARM_ONLY : RIGHT_ARM_ONLY;
        NbtCompound customData = HoldHandsSkeletalPose.createDefaultArmCustomData("", side, slim);
        return render(matrices, vertexConsumers, texture, light,
                rotations, visibleParts, slim, customData);
    }

    public static boolean renderDefaultPair(AbstractClientPlayerEntity player, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light) {
        if (player == null) {
            return false;
        }
        SkinTextures skinTextures = player.getSkinTextures();
        return renderDefaultPair(matrices, vertexConsumers, skinTextures.texture(), light,
                skinTextures.model() == SkinTextures.Model.SLIM);
    }

    public static boolean renderDefaultPair(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            Identifier texture, int light, boolean slim) {
        if (matrices == null || vertexConsumers == null || texture == null) {
            return false;
        }

        NbtCompound customData = HoldHandsSkeletalPose.createDefaultPairCustomData("", slim);
        return render(matrices, vertexConsumers, texture, light,
                HoldHandsSkeletalPose.defaultPairRotations(), BOTH_ARMS, slim, customData);
    }

    private static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  Identifier texture, int light, Map<String, float[]> rotations,
                                  Set<String> visibleParts, boolean slim, NbtCompound customData) {
        matrices.push();
        try {
            return HoldHandsSkeletalSourceArmRenderer.render(matrices, vertexConsumers, texture, light,
                    rotations, visibleParts, slim, customData);
        } finally {
            matrices.pop();
        }
    }

    private static Map<String, float[]> dynamicRotationsFor(PlayerEntityRenderState state,
                                                            HoldHandsSkeletalPose.HandSide side) {
        Map<String, float[]> rotations = new LinkedHashMap<>(HoldHandsSkeletalPose.rotationsForHand(side));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return rotations;
        }

        Entity self = client.world.getEntityById(state.id);
        Entity partner = client.world.getEntityById(HoldHandsClientState.getPartnerId(state.id));
        if (self == null || partner == null) {
            return rotations;
        }

        Vec3d delta = partner.getPos().subtract(self.getPos());
        double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalLength <= 0.000001D) {
            return rotations;
        }

        float stretchRatio = stretchRatio(delta, state.id);
        Vec3d targetVector = sharedHandTargetVector(self, partner, state.bodyYaw, side, stretchRatio);
        double targetHorizontalLength = Math.sqrt(targetVector.x * targetVector.x + targetVector.z * targetVector.z);
        if (targetHorizontalLength <= 0.000001D) {
            return rotations;
        }

        float localYaw = MathHelper.clamp(relativeArmYawDegrees(state.bodyYaw, targetVector, side),
                ARM_MIN_HORIZONTAL_ANGLE, ARM_MAX_HORIZONTAL_ANGLE);
        float localPitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(targetVector.y, targetHorizontalLength)),
                -90.0F, 90.0F);
        applyEndpointDrivenArmPose(rotations, side, localYaw, localPitch, stretchRatio);
        return rotations;
    }

    private static Vec3d sharedHandTargetVector(Entity self, Entity partner, float bodyYaw,
                                                HoldHandsSkeletalPose.HandSide side, float stretchRatio) {
        HoldHandsSkeletalPose.HandSide partnerSide = HoldHandsClientState.getHandSide(partner.getId());
        Vec3d selfShoulder = armAnchor(self.getPos(), bodyYaw, side, SHOULDER_HEIGHT, SHOULDER_SIDE_OFFSET);
        Vec3d selfPalm = armAnchor(self.getPos(), bodyYaw, side, HAND_TARGET_HEIGHT, PALM_SIDE_OFFSET);
        Vec3d partnerPalm = armAnchor(partner.getPos(), partner.getYaw(), partnerSide, HAND_TARGET_HEIGHT, PALM_SIDE_OFFSET);
        Vec3d sharedTarget = selfPalm.add(partnerPalm).multiply(0.5D)
                .add(0.0D, stretchRatio * TARGET_LIFT_DISTANCE, 0.0D);
        return sharedTarget.subtract(selfShoulder);
    }

    private static Vec3d armAnchor(Vec3d feetPos, float bodyYaw, HoldHandsSkeletalPose.HandSide side,
                                   float height, float sideOffset) {
        double yawRad = Math.toRadians(bodyYaw);
        Vec3d right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        double direction = side == HoldHandsSkeletalPose.HandSide.LEFT ? -1.0D : 1.0D;
        return feetPos.add(right.multiply(direction * sideOffset)).add(0.0D, height, 0.0D);
    }

    private static float relativeArmYawDegrees(float bodyYaw, Vec3d delta, HoldHandsSkeletalPose.HandSide side) {
        float currentYaw = localHorizontalYawDegrees(bodyYaw, delta);
        Vec3d defaultOffset = new Vec3d(DEFAULT_FOLLOW_SIDE_OFFSET, 0.0D, DEFAULT_FOLLOW_FORWARD_OFFSET);
        if (side == HoldHandsSkeletalPose.PASSIVE_ROLE_HAND) {
            defaultOffset = defaultOffset.multiply(-1.0D);
        }
        float defaultYaw = localHorizontalYawDegrees(0.0F, defaultOffset);
        return MathHelper.wrapDegrees(currentYaw - defaultYaw);
    }

    private static float localHorizontalYawDegrees(float bodyYaw, Vec3d delta) {
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double localRight = delta.x * rightX + delta.z * rightZ;
        double localForward = delta.x * forwardX + delta.z * forwardZ;
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(localRight, localForward)));
    }

    private static float stretchRatio(Vec3d delta, int entityId) {
        double defaultDistance = Math.max(0.000001D, HoldHandsClientState.getDefaultDistance(entityId));
        double currentDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double stretchStart = defaultDistance + STRETCH_DEADZONE;
        if (currentDistance <= stretchStart) {
            return 0.0F;
        }

        double stretchLength = Math.max(0.000001D, MAX_STRETCH_EXTRA_DISTANCE - STRETCH_DEADZONE);
        return MathHelper.clamp((float) ((currentDistance - stretchStart) / stretchLength), 0.0F, 1.0F);
    }

    private static void applyEndpointDrivenArmPose(Map<String, float[]> rotations,
                                                   HoldHandsSkeletalPose.HandSide side,
                                                   float localYaw, float localPitch,
                                                   float stretchRatio) {
        String upperBone = side == HoldHandsSkeletalPose.HandSide.LEFT
                ? HoldHandsSkeletalPose.LEFT_ARM_UPPER_BONE
                : HoldHandsSkeletalPose.RIGHT_ARM_UPPER_BONE;
        float[] upperRotation = rotations.get(upperBone);
        if (upperRotation == null || upperRotation.length < 3) {
            return;
        }

        float influence = MathHelper.clamp(stretchRatio, 0.0F, 1.0F);
        float handDirection = side == HoldHandsSkeletalPose.HandSide.LEFT ? 1.0F : -1.0F;
        float upliftDirection = side == HoldHandsSkeletalPose.HandSide.LEFT ? 1.0F : -1.0F;
        float stretchUplift = stretchRatio * STRETCH_UP_PITCH * upliftDirection;

        upperRotation[0] += localPitch * ARM_PITCH_SCALE * upliftDirection * influence + stretchUplift;
        upperRotation[1] += localYaw * ARM_YAW_SCALE * handDirection * influence;
    }
}
