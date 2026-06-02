package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererCarryPoseMixin {
	@Unique
	private static final float CARRIED_PIVOT_Y = 0.9F;
	@Unique
	private static final float CARRIED_ROTATION_X_DEGREES = -90.0F;
	@Unique
	private static final float CARRIED_ROTATION_Y_DEGREES = 0.0F;
	@Unique
	private static final float CARRIED_ROTATION_Z_DEGREES = 0.0F;

	@Unique
	private static boolean lockedAttachedCarriedRotation;
	@Unique
	private static float lockedAttachedCarriedBodyYaw;
	@Unique
	private static float lockedAttachedCarriedRelativeHeadYaw;
	@Unique
	private static float lockedAttachedCarriedPitch;

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
	private void monvhua$lockAttachedCarriedEntityRotation(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!CarryAttachmentRenderState.isRenderingAttachedCarriedEntity()) {
			return;
		}

		lockedAttachedCarriedRotation = true;
		lockedAttachedCarriedBodyYaw = state.bodyYaw;
		lockedAttachedCarriedRelativeHeadYaw = state.relativeHeadYaw;
		lockedAttachedCarriedPitch = state.pitch;

		state.bodyYaw = 0.0F;
		state.relativeHeadYaw = 0.0F;
		state.pitch = 0.0F;
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
	private void monvhua$restoreAttachedCarriedEntityRotation(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!lockedAttachedCarriedRotation) {
			return;
		}

		state.bodyYaw = lockedAttachedCarriedBodyYaw;
		state.relativeHeadYaw = lockedAttachedCarriedRelativeHeadYaw;
		state.pitch = lockedAttachedCarriedPitch;
		lockedAttachedCarriedRotation = false;
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 1, shift = At.Shift.AFTER))
	private void monvhua$applyCarriedPlayerTransform(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!monvhua$shouldUseCarriedTransform(state)) {
			return;
		}

		float pivotY = CARRIED_PIVOT_Y / state.baseScale;

		matrices.push();
		matrices.translate(0.0F, pivotY, 0.0F);
		monvhua$rotateCarriedPlayer(matrices);
		matrices.translate(0.0F, -pivotY, 0.0F);
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
	private void monvhua$restoreCarriedPlayerTransform(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (monvhua$shouldUseCarriedTransform(state)) {
			matrices.pop();
		}
	}

	@Unique
	private static boolean monvhua$shouldUseCarriedTransform(LivingEntityRenderState state) {
		if (CarryAttachmentRenderState.isRenderingAttachedCarriedEntity()) {
			return true;
		}
		return state instanceof PlayerEntityRenderState playerState && CarryPoseClientState.isCarried(playerState.id);
	}

	@Unique
	private static void monvhua$rotateCarriedPlayer(MatrixStack matrices) {
		if (CARRIED_ROTATION_Y_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(CARRIED_ROTATION_Y_DEGREES));
		}
		if (CARRIED_ROTATION_X_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(CARRIED_ROTATION_X_DEGREES));
		}
		if (CARRIED_ROTATION_Z_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(CARRIED_ROTATION_Z_DEGREES));
		}
	}
}
