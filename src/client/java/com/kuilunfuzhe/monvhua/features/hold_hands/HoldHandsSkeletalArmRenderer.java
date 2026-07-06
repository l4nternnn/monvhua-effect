package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import com.kuilunfuzhe.monvhua.mixin.PlayerEntityRenderStateAccessor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;

public final class HoldHandsSkeletalArmRenderer {
    private static final Set<String> RIGHT_ARM_ONLY = Set.of(HoldHandsSkeletalPose.RIGHT_ARM_PART);
    private static final Set<String> LEFT_ARM_ONLY = Set.of(HoldHandsSkeletalPose.LEFT_ARM_PART);
    private static final Set<String> BOTH_ARMS = Set.of(HoldHandsSkeletalPose.RIGHT_ARM_PART, HoldHandsSkeletalPose.LEFT_ARM_PART);

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
        return renderArm(matrices, vertexConsumers, skinTextures.texture(), light,
                skinTextures.model() == SkinTextures.Model.SLIM, side);
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
            return BodyPoseSkeletalPreviewRenderer.renderPlayerAttached(matrices, vertexConsumers, texture, light,
                    rotations, Map.of(), Map.of(), visibleParts, slim,
                    RenderLayer.getEntityCutoutNoCull(texture), Set.of(), customData);
        } finally {
            matrices.pop();
        }
    }
}
