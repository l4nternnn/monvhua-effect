package com.kuilunfuzhe.monvhua.features.hold_hands;

import com.kuilunfuzhe.monvhua.mixin.PlayerEntityRenderStateAccessor;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;

public final class HoldHandsSkeletalArmRenderer {
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
        return HoldHandsRigidArmSegmentRenderer.render(state, matrices, vertexConsumers, skinTextures.texture(), light,
                skinTextures.model() == SkinTextures.Model.SLIM, side);
    }

}
