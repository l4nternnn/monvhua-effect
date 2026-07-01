package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityExtractPoseClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/**
 * Applies Monvhua carry poses to the current player model instance.
 * <p>
 * Carrier pose intentionally only overrides upper-body parts so leg movement can still be supplied by vanilla/EMF.
 * Carried pose remains a full-body forced pose.
 * </p>
 */
public final class CarryPoseModelApplier {
	private static PlayerEntityModel currentRenderModel;
	private static PlayerEntityRenderState currentRenderState;
	private static int currentRenderDepth;

	private CarryPoseModelApplier() {
	}

	public static void beginRenderContext(PlayerEntityModel model, PlayerEntityRenderState state) {
		currentRenderModel = model;
		currentRenderState = state;
		currentRenderDepth++;
	}

	public static void endRenderContext(PlayerEntityModel model, PlayerEntityRenderState state) {
		if (currentRenderDepth > 0) {
			currentRenderDepth--;
		}
		if (currentRenderDepth == 0 || currentRenderModel == model || currentRenderState == state) {
			currentRenderModel = null;
			currentRenderState = null;
			currentRenderDepth = 0;
		}
	}

	public static void applyCurrentRenderContextBeforePartRender(ModelPart part) {
		PlayerEntityModel model = currentRenderModel;
		PlayerEntityRenderState state = currentRenderState;
		if (model == null || state == null) {
			return;
		}
		if (CarryPoseClientState.isAnyCarrier(state.id)) {
			applyCarrierPartPose(model, part);
		} else if (CarryPoseClientState.isCarried(state.id)) {
			applyCarriedPose(model, state.id);
		}
		if (GravityExtractPoseClientState.isActive(state.id)) {
			applyGravityExtractPartPose(model, part);
		}
	}

	public static boolean isRenderingCarrierPose() {
		return currentRenderState != null && CarryPoseClientState.isAnyCarrier(currentRenderState.id);
	}

	public static boolean isCurrentRightArmPart(ModelPart part) {
		return currentRenderModel != null && part == currentRenderModel.rightArm;
	}

	public static boolean isCurrentLeftArmPart(ModelPart part) {
		return currentRenderModel != null && part == currentRenderModel.leftArm;
	}

	public static void applyCarrierRightArmPose(ModelPart part) {
		if (part == null) {
			return;
		}
		part.pitch = CarryPoseTuning.CARRIER_RIGHT_ARM_PITCH;
		part.yaw = CarryPoseTuning.CARRIER_RIGHT_ARM_YAW;
		part.roll = CarryPoseTuning.CARRIER_RIGHT_ARM_ROLL;
	}

	public static void applyCarrierLeftArmPose(ModelPart part) {
		if (part == null) {
			return;
		}
		part.pitch = CarryPoseTuning.CARRIER_LEFT_ARM_PITCH;
		part.yaw = CarryPoseTuning.CARRIER_LEFT_ARM_YAW;
		part.roll = CarryPoseTuning.CARRIER_LEFT_ARM_ROLL;
	}

	public static void applyCarrierRightArmPoseToMatrix(MatrixStack matrices) {
		if (matrices != null) {
			matrices.multiply(new org.joml.Quaternionf()
					.rotateZ(CarryPoseTuning.CARRIER_RIGHT_ARM_ROLL)
					.rotateY(CarryPoseTuning.CARRIER_RIGHT_ARM_YAW)
					.rotateX(CarryPoseTuning.CARRIER_RIGHT_ARM_PITCH));
		}
	}

	public static void applyCarrierLeftArmPoseToMatrix(MatrixStack matrices) {
		if (matrices != null) {
			matrices.multiply(new org.joml.Quaternionf()
					.rotateZ(CarryPoseTuning.CARRIER_LEFT_ARM_ROLL)
					.rotateY(CarryPoseTuning.CARRIER_LEFT_ARM_YAW)
					.rotateX(CarryPoseTuning.CARRIER_LEFT_ARM_PITCH));
		}
	}

	public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
		if (CarryPoseClientState.isAnyCarrier(state.id)) {
			applyCarrierPose(model);
		} else if (CarryPoseClientState.isCarried(state.id)) {
			applyCarriedPose(model, state.id);
		}
		if (GravityExtractPoseClientState.isActive(state.id)) {
			applyGravityExtractPose(model);
		}
	}

	public static void applyGravityExtractPose(PlayerEntityModel model) {
		applyGravityExtractRightArmPose(model.rightArm);
	}

	public static void applyGravityExtractRightArmPose(ModelPart part) {
		if (part == null) {
			return;
		}
		part.pitch = (float) Math.toRadians(-165.0D);
		part.yaw = (float) Math.toRadians(8.0D);
		part.roll = (float) Math.toRadians(8.0D);
	}

	private static void applyGravityExtractPartPose(PlayerEntityModel model, ModelPart part) {
		if (part == model.rightArm) {
			applyGravityExtractRightArmPose(part);
			return;
		}
	}

	private static void applyCarrierPose(PlayerEntityModel model) {
		model.body.pitch = CarryPoseTuning.CARRIER_BODY_PITCH;
		applyCarrierRightArmPose(model.rightArm);
		applyCarrierLeftArmPose(model.leftArm);
	}

	private static void applyCarrierPartPose(PlayerEntityModel model, ModelPart part) {
		if (part == model.body) {
			model.body.pitch = CarryPoseTuning.CARRIER_BODY_PITCH;
			return;
		}
		if (part == model.rightArm) {
			applyCarrierRightArmPose(part);
			return;
		}
		if (part == model.leftArm) {
			applyCarrierLeftArmPose(part);
		}
	}

	private static void applyCarriedPose(PlayerEntityModel model, int entityId) {
		// 身体
		if (CarryPoseClientState.isDragCarried(entityId)) {
			applyCarriedDragPose(model, entityId);
			return;
		}
		model.body.pitch = CarryPoseTuning.BODY_PITCH + CarryPoseTuning.CUSTOM_BODY_PITCH;
		model.body.yaw = CarryPoseTuning.BODY_YAW + CarryPoseTuning.CUSTOM_BODY_YAW;
		model.body.roll = CarryPoseTuning.BODY_ROLL + CarryPoseTuning.CUSTOM_BODY_ROLL;

		// 手臂（环颈）
		model.rightArm.pitch = CarryPoseTuning.RIGHT_ARM_PITCH + CarryPoseTuning.CUSTOM_RIGHT_ARM_PITCH;
		model.rightArm.yaw = CarryPoseTuning.RIGHT_ARM_YAW + CarryPoseTuning.CUSTOM_RIGHT_ARM_YAW;
		model.rightArm.roll = CarryPoseTuning.RIGHT_ARM_ROLL + CarryPoseTuning.CUSTOM_RIGHT_ARM_ROLL;
		model.leftArm.pitch = CarryPoseTuning.LEFT_ARM_PITCH + CarryPoseTuning.CUSTOM_LEFT_ARM_PITCH;
		model.leftArm.yaw = CarryPoseTuning.LEFT_ARM_YAW + CarryPoseTuning.CUSTOM_LEFT_ARM_YAW;
		model.leftArm.roll = CarryPoseTuning.LEFT_ARM_ROLL + CarryPoseTuning.CUSTOM_LEFT_ARM_ROLL;

		// 双腿（弯曲自然下垂）
		model.rightLeg.pitch = CarryPoseTuning.RIGHT_LEG_PITCH + CarryPoseTuning.CUSTOM_RIGHT_LEG_PITCH;
		model.rightLeg.yaw = CarryPoseTuning.RIGHT_LEG_YAW + CarryPoseTuning.CUSTOM_RIGHT_LEG_YAW;
		model.rightLeg.roll = CarryPoseTuning.RIGHT_LEG_ROLL + CarryPoseTuning.CUSTOM_RIGHT_LEG_ROLL;
		model.leftLeg.pitch = CarryPoseTuning.LEFT_LEG_PITCH + CarryPoseTuning.CUSTOM_LEFT_LEG_PITCH;
		model.leftLeg.yaw = CarryPoseTuning.LEFT_LEG_YAW + CarryPoseTuning.CUSTOM_LEFT_LEG_YAW;
		model.leftLeg.roll = CarryPoseTuning.LEFT_LEG_ROLL + CarryPoseTuning.CUSTOM_LEFT_LEG_ROLL;

		// 头部（后仰，可配合颈部环抱效果）
		model.head.pitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		model.head.yaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		model.head.roll = CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
		updateCarriedHeadViewRotation(entityId);
		if (CarriedPlayerViewState.shouldApplyHeadViewRotation(entityId)) {
			model.head.pitch = CarriedPlayerViewState.getHeadPitchRadians();
			model.head.yaw = CarriedPlayerViewState.getHeadYawRadians();
		}
	}

	private static void applyCarriedDragPose(PlayerEntityModel model, int entityId) {
		model.body.pitch = CarryPoseTuning.DRAG_BODY_PITCH;
		model.body.yaw = CarryPoseTuning.DRAG_BODY_YAW;
		model.body.roll = CarryPoseTuning.DRAG_BODY_ROLL;

		model.rightArm.pitch = CarryPoseTuning.DRAG_RIGHT_ARM_PITCH;
		model.rightArm.yaw = CarryPoseTuning.DRAG_RIGHT_ARM_YAW;
		model.rightArm.roll = CarryPoseTuning.DRAG_RIGHT_ARM_ROLL;
		model.leftArm.pitch = CarryPoseTuning.DRAG_LEFT_ARM_PITCH;
		model.leftArm.yaw = CarryPoseTuning.DRAG_LEFT_ARM_YAW;
		model.leftArm.roll = CarryPoseTuning.DRAG_LEFT_ARM_ROLL;

		model.rightLeg.pitch = CarryPoseTuning.DRAG_RIGHT_LEG_PITCH;
		model.rightLeg.yaw = CarryPoseTuning.DRAG_RIGHT_LEG_YAW;
		model.rightLeg.roll = CarryPoseTuning.DRAG_RIGHT_LEG_ROLL;
		model.leftLeg.pitch = CarryPoseTuning.DRAG_LEFT_LEG_PITCH;
		model.leftLeg.yaw = CarryPoseTuning.DRAG_LEFT_LEG_YAW;
		model.leftLeg.roll = CarryPoseTuning.DRAG_LEFT_LEG_ROLL;

		model.head.pitch = CarryPoseTuning.DRAG_HEAD_PITCH;
		model.head.yaw = CarryPoseTuning.DRAG_HEAD_YAW;
		model.head.roll = CarryPoseTuning.DRAG_HEAD_ROLL;
		updateCarriedHeadViewRotation(entityId);
		if (CarriedPlayerViewState.shouldApplyHeadViewRotation(entityId)) {
			model.head.pitch = CarriedPlayerViewState.getHeadPitchRadians();
			model.head.yaw = CarriedPlayerViewState.getHeadYawRadians();
		}
	}

	private static void updateCarriedHeadViewRotation(int entityId) {
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
