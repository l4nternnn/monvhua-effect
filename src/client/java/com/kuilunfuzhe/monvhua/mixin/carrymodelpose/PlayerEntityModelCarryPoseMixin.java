package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarriedPlayerViewState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelCarryPoseMixin {
	@Unique
	private ModelPart monvhua$body;
	@Unique
	private ModelPart monvhua$rightArm;
	@Unique
	private ModelPart monvhua$leftArm;
	@Unique
	private ModelPart monvhua$rightLeg;
	@Unique
	private ModelPart monvhua$leftLeg;
	@Unique
	private ModelPart monvhua$head;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void monvhua$captureParts(CallbackInfo ci) {
		PlayerEntityModel self = (PlayerEntityModel) (Object) this;
		this.monvhua$body = self.body;
		this.monvhua$rightArm = self.rightArm;
		this.monvhua$leftArm = self.leftArm;
		this.monvhua$rightLeg = self.rightLeg;
		this.monvhua$leftLeg = self.leftLeg;
		this.monvhua$head = self.head;
	}

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
	private void monvhua$applyCarryPose(PlayerEntityRenderState state, CallbackInfo ci) {
		if (CarryPoseClientState.isCarrier(state.id)) {
			applyCarrierPose();
			return;
		}
		if (CarryPoseClientState.isCarried(state.id)) {
			applyCarriedPose(state.id);
		}
	}

	@Unique
	private void applyCarrierPose() {
		monvhua$body.pitch = CarryPoseTuning.CARRIER_BODY_PITCH;
		monvhua$rightArm.pitch = CarryPoseTuning.CARRIER_RIGHT_ARM_PITCH;
		monvhua$rightArm.yaw = CarryPoseTuning.CARRIER_RIGHT_ARM_YAW;
		monvhua$rightArm.roll = CarryPoseTuning.CARRIER_RIGHT_ARM_ROLL;
		monvhua$leftArm.pitch = CarryPoseTuning.CARRIER_LEFT_ARM_PITCH;
		monvhua$leftArm.yaw = CarryPoseTuning.CARRIER_LEFT_ARM_YAW;
		monvhua$leftArm.roll = CarryPoseTuning.CARRIER_LEFT_ARM_ROLL;
	}

	@Unique
	private void applyCarriedPose(int entityId) {
		// 身体
		monvhua$body.pitch = CarryPoseTuning.BODY_PITCH + CarryPoseTuning.CUSTOM_BODY_PITCH;
		monvhua$body.yaw = CarryPoseTuning.BODY_YAW + CarryPoseTuning.CUSTOM_BODY_YAW;
		monvhua$body.roll = CarryPoseTuning.BODY_ROLL + CarryPoseTuning.CUSTOM_BODY_ROLL;

		// 手臂（环颈）
		monvhua$rightArm.pitch = CarryPoseTuning.RIGHT_ARM_PITCH + CarryPoseTuning.CUSTOM_RIGHT_ARM_PITCH;
		monvhua$rightArm.yaw = CarryPoseTuning.RIGHT_ARM_YAW + CarryPoseTuning.CUSTOM_RIGHT_ARM_YAW;
		monvhua$rightArm.roll = CarryPoseTuning.RIGHT_ARM_ROLL + CarryPoseTuning.CUSTOM_RIGHT_ARM_ROLL;
		monvhua$leftArm.pitch = CarryPoseTuning.LEFT_ARM_PITCH + CarryPoseTuning.CUSTOM_LEFT_ARM_PITCH;
		monvhua$leftArm.yaw = CarryPoseTuning.LEFT_ARM_YAW + CarryPoseTuning.CUSTOM_LEFT_ARM_YAW;
		monvhua$leftArm.roll = CarryPoseTuning.LEFT_ARM_ROLL + CarryPoseTuning.CUSTOM_LEFT_ARM_ROLL;

		// 双腿（弯曲自然下垂）
		monvhua$rightLeg.pitch = CarryPoseTuning.RIGHT_LEG_PITCH + CarryPoseTuning.CUSTOM_RIGHT_LEG_PITCH;
		monvhua$rightLeg.yaw = CarryPoseTuning.RIGHT_LEG_YAW + CarryPoseTuning.CUSTOM_RIGHT_LEG_YAW;
		monvhua$rightLeg.roll = CarryPoseTuning.RIGHT_LEG_ROLL + CarryPoseTuning.CUSTOM_RIGHT_LEG_ROLL;
		monvhua$leftLeg.pitch = CarryPoseTuning.LEFT_LEG_PITCH + CarryPoseTuning.CUSTOM_LEFT_LEG_PITCH;
		monvhua$leftLeg.yaw = CarryPoseTuning.LEFT_LEG_YAW + CarryPoseTuning.CUSTOM_LEFT_LEG_YAW;
		monvhua$leftLeg.roll = CarryPoseTuning.LEFT_LEG_ROLL + CarryPoseTuning.CUSTOM_LEFT_LEG_ROLL;

		// 头部（后仰，可配合颈部环抱效果）
		monvhua$head.pitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		monvhua$head.yaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		monvhua$head.roll = CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
		updateCarriedHeadViewRotation(entityId);
		if (CarriedPlayerViewState.shouldApplyHeadViewRotation(entityId)) {
			monvhua$head.pitch = CarriedPlayerViewState.getHeadPitchRadians();
			monvhua$head.yaw = CarriedPlayerViewState.getHeadYawRadians();
		}
	}

	@Unique
	private void updateCarriedHeadViewRotation(int entityId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		Entity carried = client.world.getEntityById(entityId);
		if (carried == null) {
			return;
		}

		int carrierId = CarryPoseClientState.getPartnerId(entityId);
		if (carrierId < 0) {
			return;
		}

		Entity carrier = client.world.getEntityById(carrierId);
		if (carrier == null) {
			return;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(true);
		if (CarryAttachmentRenderState.isRenderingAttachedCarriedEntity()) {
			CarriedPlayerViewState.updateHeadViewRotationFromWorldView(carried, carrier, tickProgress);
			return;
		}
		CarriedPlayerViewState.updateLocalViewRotation(carried, carrier, tickProgress);
	}
}
