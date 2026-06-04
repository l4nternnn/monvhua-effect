package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.compat.EmfCompat;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererCarryPoseMixin {
	@Shadow
	public abstract EntityModel<?> getModel();

	@Unique
	private static boolean lockedAttachedCarriedRotation;
	@Unique
	private static float lockedAttachedCarriedBodyYaw;
	@Unique
	private static float lockedAttachedCarriedRelativeHeadYaw;
	@Unique
	private static float lockedAttachedCarriedPitch;
	@Unique
	private static boolean hiddenFirstPersonCarriedHead;
	@Unique
	private static boolean hiddenFirstPersonCarriedHeadVisible;
	@Unique
	private static boolean hiddenFirstPersonCarriedHatVisible;

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
	private void monvhua$lockAttachedCarriedEntityRotation(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!CarryAttachmentRenderState.isRenderingAttachedCarriedEntity()) {
			return;
		}

		monvhua$hideFirstPersonCarriedPlayerHead(state);

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
		monvhua$endCarryPoseRenderContext(state);
		monvhua$restoreFirstPersonCarriedPlayerHead();

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

		matrices.push();
		CarryAttachedRenderMath.applyCarriedModelHorizontalTransform(matrices, state.baseScale);
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
	private void monvhua$restoreCarriedPlayerTransform(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (monvhua$shouldUseCarriedTransform(state)) {
			matrices.pop();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", shift = At.Shift.BEFORE), require = 1)
	private void monvhua$reapplyCarryPoseBeforeModelRender(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!(state instanceof PlayerEntityRenderState playerState) || !(getModel() instanceof PlayerEntityModel playerModel)) {
			return;
		}

		monvhua$syncEmfCarrierUpperBodyAnimationState(playerState, playerModel);
		CarryPoseModelApplier.beginRenderContext(playerModel, playerState);
		CarryPoseModelApplier.apply(playerModel, playerState);
	}

	@Unique
	private void monvhua$syncEmfCarrierUpperBodyAnimationState(PlayerEntityRenderState playerState, PlayerEntityModel playerModel) {
		PlayerEntity player = monvhua$getRenderedPlayer(playerState.id);
		if (player == null) {
			return;
		}

		if (CarryPoseClientState.isCarrier(playerState.id)) {
			EmfCompat.pauseCarrierUpperBodyAnimations(new EmfCompat.PlayerEntityModelParts(player, playerModel.body, playerModel.rightArm, playerModel.leftArm), playerState.id);
			return;
		}

		EmfCompat.resumeCarrierAnimations(player);
	}

	@Unique
	private static PlayerEntity monvhua$getRenderedPlayer(int entityId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}

		Entity entity = client.world.getEntityById(entityId);
		return entity instanceof PlayerEntity player ? player : null;
	}

	@Unique
	private void monvhua$endCarryPoseRenderContext(LivingEntityRenderState state) {
		if (state instanceof PlayerEntityRenderState playerState && getModel() instanceof PlayerEntityModel playerModel) {
			CarryPoseModelApplier.endRenderContext(playerModel, playerState);
		}
	}

	@Unique
	private void monvhua$hideFirstPersonCarriedPlayerHead(LivingEntityRenderState state) {
		if (hiddenFirstPersonCarriedHead || !CarryAttachmentRenderState.isRenderingFirstPersonSelfCarriedEntity() || !(state instanceof PlayerEntityRenderState) || !(getModel() instanceof PlayerEntityModel playerModel)) {
			return;
		}

		hiddenFirstPersonCarriedHead = true;
		hiddenFirstPersonCarriedHeadVisible = playerModel.head.visible;
		hiddenFirstPersonCarriedHatVisible = playerModel.hat.visible;
		playerModel.head.visible = false;
		playerModel.hat.visible = false;
	}

	@Unique
	private void monvhua$restoreFirstPersonCarriedPlayerHead() {
		if (!hiddenFirstPersonCarriedHead || !(getModel() instanceof PlayerEntityModel playerModel)) {
			return;
		}

		playerModel.head.visible = hiddenFirstPersonCarriedHeadVisible;
		playerModel.hat.visible = hiddenFirstPersonCarriedHatVisible;
		hiddenFirstPersonCarriedHead = false;
	}

	@Unique
	private static boolean monvhua$shouldUseCarriedTransform(LivingEntityRenderState state) {
		if (CarryAttachmentRenderState.isRenderingAttachedCarriedEntity()) {
			return true;
		}
		return state instanceof PlayerEntityRenderState playerState && CarryPoseClientState.isCarried(playerState.id);
	}
}
