package com.kuilunfuzhe.monvhua.mixin.compat.emf;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning;
import com.kuilunfuzhe.monvhua.features.gravity.GravityExtractPoseClientState;
import com.kuilunfuzhe.monvhua.features.hold_hands.HoldHandsClientState;
import com.kuilunfuzhe.monvhua.features.hold_hands.HoldHandsSkeletalPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Locale;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartVanilla", remap = false)
public abstract class EmfModelPartVanillaCarryArmMotionMixin {
	private static Field monvhua$currentRenderStateField;

	@Unique
	private boolean monvhua$scaledArmMotion;
	@Unique
	private float monvhua$originalPitch;
	@Unique
	private float monvhua$originalYaw;
	@Unique
	private float monvhua$originalRoll;
	@Unique
	private boolean monvhua$hiddenForHoldHands;
	@Unique
	private boolean monvhua$originalVisible;

	@Shadow(remap = false)
	private String name;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), require = 0)
	private void monvhua$scaleCarrierArmMotion(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$beginScaleCarrierArmMotion();
	}

	@Inject(method = "method_22699(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V", at = @At("HEAD"), require = 0)
	private void monvhua$scaleCarrierArmMotionIntermediary(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$beginScaleCarrierArmMotion();
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("RETURN"), require = 0)
	private void monvhua$restoreCarrierArmMotion(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$restoreCarrierArmMotion();
	}

	@Inject(method = "method_22699(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V", at = @At("RETURN"), require = 0)
	private void monvhua$restoreCarrierArmMotionIntermediary(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$restoreCarrierArmMotion();
	}

	@Unique
	private void monvhua$beginScaleCarrierArmMotion() {
		if (monvhua$scaledArmMotion || name == null) {
			return;
		}
		String partName = name.toLowerCase(Locale.ROOT);
		if (monvhua$beginHideHeldHandArm(partName)) {
			return;
		}
		if (monvhua$isRenderingGravityExtractPose()) {
			if (monvhua$isRightArm(partName)) {
				monvhua$setCurrentArmPose((float) Math.toRadians(-165.0D), (float) Math.toRadians(8.0D), (float) Math.toRadians(8.0D));
			}
			return;
		}
		if (monvhua$isRenderingCarrierPose()) {
			if (monvhua$isRightArm(partName)) {
				monvhua$scaleCurrentArmMotion(CarryPoseTuning.CARRIER_RIGHT_ARM_PITCH, CarryPoseTuning.CARRIER_RIGHT_ARM_YAW, CarryPoseTuning.CARRIER_RIGHT_ARM_ROLL);
				return;
			}
			if (monvhua$isLeftArm(partName)) {
				monvhua$scaleCurrentArmMotion(CarryPoseTuning.CARRIER_LEFT_ARM_PITCH, CarryPoseTuning.CARRIER_LEFT_ARM_YAW, CarryPoseTuning.CARRIER_LEFT_ARM_ROLL);
			}
		}
	}

	@Unique
	private void monvhua$restoreCarrierArmMotion() {
		if (monvhua$hiddenForHoldHands) {
			ModelPart part = (ModelPart) (Object) this;
			part.visible = monvhua$originalVisible;
			monvhua$hiddenForHoldHands = false;
		}
		if (!monvhua$scaledArmMotion) {
			return;
		}
		ModelPart part = (ModelPart) (Object) this;
		part.pitch = monvhua$originalPitch;
		part.yaw = monvhua$originalYaw;
		part.roll = monvhua$originalRoll;
		monvhua$scaledArmMotion = false;
	}

	@Unique
	private void monvhua$scaleCurrentArmMotion(float basePitch, float baseYaw, float baseRoll) {
		ModelPart part = (ModelPart) (Object) this;
		monvhua$scaledArmMotion = true;
		monvhua$originalPitch = part.pitch;
		monvhua$originalYaw = part.yaw;
		monvhua$originalRoll = part.roll;
		float scale = CarryPoseTuning.CARRIER_ARM_MOTION_SCALE;
		part.pitch = basePitch + (part.pitch - basePitch) * scale;
		part.yaw = baseYaw + (part.yaw - baseYaw) * scale;
		part.roll = baseRoll + (part.roll - baseRoll) * scale;
	}

	@Unique
	private void monvhua$setCurrentArmPose(float pitch, float yaw, float roll) {
		ModelPart part = (ModelPart) (Object) this;
		monvhua$scaledArmMotion = true;
		monvhua$originalPitch = part.pitch;
		monvhua$originalYaw = part.yaw;
		monvhua$originalRoll = part.roll;
		part.pitch = pitch;
		part.yaw = yaw;
		part.roll = roll;
	}

	@Unique
	private boolean monvhua$beginHideHeldHandArm(String partName) {
		HoldHandsSkeletalPose.HandSide heldSide = monvhua$currentHeldHandSide();
		if (heldSide == null) {
			return false;
		}
		if ((heldSide == HoldHandsSkeletalPose.HandSide.RIGHT && !monvhua$isRightArm(partName))
				|| (heldSide == HoldHandsSkeletalPose.HandSide.LEFT && !monvhua$isLeftArm(partName))) {
			return false;
		}
		ModelPart part = (ModelPart) (Object) this;
		monvhua$hiddenForHoldHands = true;
		monvhua$originalVisible = part.visible;
		part.visible = false;
		return true;
	}

	@Unique
	private static boolean monvhua$isRightArm(String partName) {
		return (partName.contains("right") && (partName.contains("arm") || partName.contains("sleeve"))) || partName.equals("right_arm") || partName.equals("rightarm") || partName.equals("right_sleeve") || partName.equals("rightsleeve");
	}

	@Unique
	private static boolean monvhua$isLeftArm(String partName) {
		return (partName.contains("left") && (partName.contains("arm") || partName.contains("sleeve"))) || partName.equals("left_arm") || partName.equals("leftarm") || partName.equals("left_sleeve") || partName.equals("leftsleeve");
	}

	private static boolean monvhua$isRenderingCarrierPose() {
		try {
			Field field = monvhua$currentRenderStateField;
			if (field == null) {
				field = CarryPoseModelApplier.class.getDeclaredField("currentRenderState");
				field.setAccessible(true);
				monvhua$currentRenderStateField = field;
			}
			Object state = field.get(null);
			return state instanceof PlayerEntityRenderState playerState && CarryPoseClientState.isAnyCarrier(playerState.id);
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	@Unique
	private static HoldHandsSkeletalPose.HandSide monvhua$currentHeldHandSide() {
		try {
			Field field = monvhua$currentRenderStateField;
			if (field == null) {
				field = CarryPoseModelApplier.class.getDeclaredField("currentRenderState");
				field.setAccessible(true);
				monvhua$currentRenderStateField = field;
			}
			Object state = field.get(null);
			if (state instanceof PlayerEntityRenderState playerState && HoldHandsClientState.isHoldingHands(playerState.id)) {
				return HoldHandsClientState.getHandSide(playerState.id);
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
	}

	private static boolean monvhua$isRenderingGravityExtractPose() {
		try {
			Field field = monvhua$currentRenderStateField;
			if (field == null) {
				field = CarryPoseModelApplier.class.getDeclaredField("currentRenderState");
				field.setAccessible(true);
				monvhua$currentRenderStateField = field;
			}
			Object state = field.get(null);
			return state instanceof PlayerEntityRenderState playerState && GravityExtractPoseClientState.isActive(playerState.id);
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}
}
