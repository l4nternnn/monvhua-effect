package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

import java.util.Set;

public final class CarryDragSkeletalRenderer {
	private static final Set<String> FIRST_PERSON_VISIBLE_PARTS = Set.of("torso", "left_arm", "right_arm", "left_leg", "right_leg");

	private CarryDragSkeletalRenderer() {
	}

	public static boolean renderIfDragCarried(Entity carried, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, boolean hideHead) {
		if (!(carried instanceof AbstractClientPlayerEntity player) || !CarryPoseClientState.isDragCarried(carried.getId())) {
			return false;
		}

		SkinTextures skinTextures = player.getSkinTextures();
		boolean slim = skinTextures.model() == SkinTextures.Model.SLIM;
		matrices.push();
		try {
			CarryAttachedRenderMath.applyCarriedModelHorizontalTransform(matrices, 1.0F, true);
			if (hideHead) {
				return BodyPoseSkeletalPreviewRenderer.renderDragPose(matrices, vertexConsumers, skinTextures.texture(), light, slim, FIRST_PERSON_VISIBLE_PARTS);
			}
			return BodyPoseSkeletalPreviewRenderer.renderDragPose(matrices, vertexConsumers, skinTextures.texture(), light, slim);
		} finally {
			matrices.pop();
		}
	}
}
