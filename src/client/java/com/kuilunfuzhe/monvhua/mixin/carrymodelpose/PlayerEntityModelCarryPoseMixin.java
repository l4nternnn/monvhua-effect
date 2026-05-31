package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
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
	private ModelPart monvhua$head;   // 新增头部，用于环颈时转头

	// ========== 被抱者（侧躺公主抱）姿态参数（弧度） ==========
// 身体侧躺（绕 Z 轴旋转 90 度，右侧躺）
	// BODY_ROLL=0! 整体旋转由 PlayerEntityRendererCarryPoseMixin
	// 的矩阵变换处理（绕 X 轴 90°），这里不再做侧躺旋转
	@Unique private static final float BODY_PITCH = 0.0F;
	@Unique private static final float BODY_YAW = 0.0F;
	@Unique private static final float BODY_ROLL = 0.0F;

	// 手臂环抱（适应侧躺）
	@Unique private static final float RIGHT_ARM_PITCH = -1.2F;   // 手臂向前
	@Unique private static final float RIGHT_ARM_YAW = 0.5F;      // 略向内
	@Unique private static final float RIGHT_ARM_ROLL = 0.3F;     // 外旋适应侧躺

	@Unique private static final float LEFT_ARM_PITCH = -1.2F;
	@Unique private static final float LEFT_ARM_YAW = -0.5F;
	@Unique private static final float LEFT_ARM_ROLL = -0.3F;

	// 双腿弯曲自然下垂（侧躺状态）
	@Unique private static final float RIGHT_LEG_PITCH = -1.0F;   // 向前搭在抱者手臂上
	@Unique private static final float RIGHT_LEG_YAW = 0.2F;
	@Unique private static final float RIGHT_LEG_ROLL = 0.1F;

	@Unique private static final float LEFT_LEG_PITCH = -1.0F;   // 向前搭在抱者手臂上
	@Unique private static final float LEFT_LEG_YAW = -0.2F;
	@Unique private static final float LEFT_LEG_ROLL = -0.1F;

	// 头部轻微侧转，贴合怀抱姿势
	@Unique private static final float HEAD_PITCH = -0.3F;
	@Unique private static final float HEAD_YAW = 0.0F;
	@Unique private static final float HEAD_ROLL = 0.0F;

	// Additive carried-player part rotation offsets, in radians.
	@Unique private static final float CUSTOM_BODY_PITCH = 0.0F;
	@Unique private static final float CUSTOM_BODY_YAW = 0.0F;
	@Unique private static final float CUSTOM_BODY_ROLL = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_ARM_PITCH = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_ARM_YAW = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_ARM_ROLL = 0.0F;
	@Unique private static final float CUSTOM_LEFT_ARM_PITCH = 0.0F;
	@Unique private static final float CUSTOM_LEFT_ARM_YAW = 0.0F;
	@Unique private static final float CUSTOM_LEFT_ARM_ROLL = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_LEG_PITCH = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_LEG_YAW = 0.0F;
	@Unique private static final float CUSTOM_RIGHT_LEG_ROLL = 0.0F;
	@Unique private static final float CUSTOM_LEFT_LEG_PITCH = 0.0F;
	@Unique private static final float CUSTOM_LEFT_LEG_YAW = 0.0F;
	@Unique private static final float CUSTOM_LEFT_LEG_ROLL = 0.0F;
	@Unique private static final float CUSTOM_HEAD_PITCH = 0.0F;
	@Unique private static final float CUSTOM_HEAD_YAW = 0.0F;
	@Unique private static final float CUSTOM_HEAD_ROLL = 0.0F;


	// ========== 抱起者姿态（可选） ==========
	@Unique private static final float CARRIER_BODY_PITCH = 0.08F;
	@Unique private static final float CARRIER_RIGHT_ARM_PITCH = -1.15F;
	@Unique private static final float CARRIER_RIGHT_ARM_YAW = -0.45F;
	@Unique private static final float CARRIER_RIGHT_ARM_ROLL = 0.32F;
	@Unique private static final float CARRIER_LEFT_ARM_PITCH = -1.15F;
	@Unique private static final float CARRIER_LEFT_ARM_YAW = 0.45F;
	@Unique private static final float CARRIER_LEFT_ARM_ROLL = -0.32F;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void monvhua$captureParts(CallbackInfo ci) {
		PlayerEntityModel self = (PlayerEntityModel) (Object) this;
		this.monvhua$body = self.body;
		this.monvhua$rightArm = self.rightArm;
		this.monvhua$leftArm = self.leftArm;
		this.monvhua$rightLeg = self.rightLeg;
		this.monvhua$leftLeg = self.leftLeg;
		this.monvhua$head = self.head;   // 捕获头部
	}

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
	private void monvhua$applyCarryPose(PlayerEntityRenderState state, CallbackInfo ci) {
		if (CarryPoseClientState.isCarrier(state.id)) {
			applyCarrierPose();
			return;
		}
		if (CarryPoseClientState.isCarried(state.id)) {
			applyCarriedPose();
		}
	}

	@Unique
	private void applyCarrierPose() {
		monvhua$body.pitch = CARRIER_BODY_PITCH;
		monvhua$rightArm.pitch = CARRIER_RIGHT_ARM_PITCH;
		monvhua$rightArm.yaw = CARRIER_RIGHT_ARM_YAW;
		monvhua$rightArm.roll = CARRIER_RIGHT_ARM_ROLL;
		monvhua$leftArm.pitch = CARRIER_LEFT_ARM_PITCH;
		monvhua$leftArm.yaw = CARRIER_LEFT_ARM_YAW;
		monvhua$leftArm.roll = CARRIER_LEFT_ARM_ROLL;
	}

	@Unique
	private void applyCarriedPose() {
		// 身体
		monvhua$body.pitch = BODY_PITCH + CUSTOM_BODY_PITCH;
		monvhua$body.yaw = BODY_YAW + CUSTOM_BODY_YAW;
		monvhua$body.roll = BODY_ROLL + CUSTOM_BODY_ROLL;

		// 手臂（环颈）
		monvhua$rightArm.pitch = RIGHT_ARM_PITCH + CUSTOM_RIGHT_ARM_PITCH;
		monvhua$rightArm.yaw = RIGHT_ARM_YAW + CUSTOM_RIGHT_ARM_YAW;
		monvhua$rightArm.roll = RIGHT_ARM_ROLL + CUSTOM_RIGHT_ARM_ROLL;
		monvhua$leftArm.pitch = LEFT_ARM_PITCH + CUSTOM_LEFT_ARM_PITCH;
		monvhua$leftArm.yaw = LEFT_ARM_YAW + CUSTOM_LEFT_ARM_YAW;
		monvhua$leftArm.roll = LEFT_ARM_ROLL + CUSTOM_LEFT_ARM_ROLL;

		// 双腿（弯曲自然下垂）
		monvhua$rightLeg.pitch = RIGHT_LEG_PITCH + CUSTOM_RIGHT_LEG_PITCH;
		monvhua$rightLeg.yaw = RIGHT_LEG_YAW + CUSTOM_RIGHT_LEG_YAW;
		monvhua$rightLeg.roll = RIGHT_LEG_ROLL + CUSTOM_RIGHT_LEG_ROLL;
		monvhua$leftLeg.pitch = LEFT_LEG_PITCH + CUSTOM_LEFT_LEG_PITCH;
		monvhua$leftLeg.yaw = LEFT_LEG_YAW + CUSTOM_LEFT_LEG_YAW;
		monvhua$leftLeg.roll = LEFT_LEG_ROLL + CUSTOM_LEFT_LEG_ROLL;

		// 头部（后仰，可配合颈部环抱效果）
		monvhua$head.pitch = HEAD_PITCH + CUSTOM_HEAD_PITCH;
		monvhua$head.yaw = HEAD_YAW + CUSTOM_HEAD_YAW;
		monvhua$head.roll = HEAD_ROLL + CUSTOM_HEAD_ROLL;
	}
}
