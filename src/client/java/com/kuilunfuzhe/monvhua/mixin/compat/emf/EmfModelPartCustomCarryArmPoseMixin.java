package com.kuilunfuzhe.monvhua.mixin.compat.emf;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
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
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom", remap = false)
public abstract class EmfModelPartCustomCarryArmPoseMixin {
	private static Field monvhua$currentRenderStateField;

	@Unique
	private boolean monvhua$scaledArmMotion;
	@Unique
	private float monvhua$originalPitch;
	@Unique
	private float monvhua$originalYaw;
	@Unique
	private float monvhua$originalRoll;

	@Shadow(remap = false)
	public String partToBeAttached;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), require = 0)
	private void monvhua$applyCarrierArmPoseToEmfCustomPart(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$beginApplyCarrierArmPose(matrices);
	}

	@Inject(method = "method_22699(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V", at = @At("HEAD"), require = 0)
	private void monvhua$applyCarrierArmPoseToEmfCustomPartIntermediary(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		monvhua$beginApplyCarrierArmPose(matrices);
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
	private void monvhua$beginApplyCarrierArmPose(MatrixStack matrices) {
		if (monvhua$scaledArmMotion || !monvhua$isRenderingCarrierPose() || partToBeAttached == null || matrices == null) {
			return;
		}
		String attachedPart = partToBeAttached.toLowerCase(Locale.ROOT);
		if (attachedPart.contains("right") && (attachedPart.contains("arm") || attachedPart.contains("sleeve"))) {
			monvhua$scaleCurrentArmMotion();
			monvhua$applyArmPoseToMatrix(matrices, CarryPoseTuning.CARRIER_RIGHT_ARM_PITCH, CarryPoseTuning.CARRIER_RIGHT_ARM_YAW, CarryPoseTuning.CARRIER_RIGHT_ARM_ROLL);
			return;
		}
		if (attachedPart.contains("left") && (attachedPart.contains("arm") || attachedPart.contains("sleeve"))) {
			monvhua$scaleCurrentArmMotion();
			monvhua$applyArmPoseToMatrix(matrices, CarryPoseTuning.CARRIER_LEFT_ARM_PITCH, CarryPoseTuning.CARRIER_LEFT_ARM_YAW, CarryPoseTuning.CARRIER_LEFT_ARM_ROLL);
		}
	}

	@Unique
	private void monvhua$restoreCarrierArmMotion() {
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
	private void monvhua$scaleCurrentArmMotion() {
		ModelPart part = (ModelPart) (Object) this;
		monvhua$scaledArmMotion = true;
		monvhua$originalPitch = part.pitch;
		monvhua$originalYaw = part.yaw;
		monvhua$originalRoll = part.roll;
		float scale = CarryPoseTuning.CARRIER_ARM_MOTION_SCALE;
		part.pitch *= scale;
		part.yaw *= scale;
		part.roll *= scale;
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

	private static void monvhua$applyArmPoseToMatrix(MatrixStack matrices, float pitch, float yaw, float roll) {
		matrices.multiply(new Quaternionf()
				.rotateZ(roll)
				.rotateY(yaw)
				.rotateX(pitch));
	}
}
