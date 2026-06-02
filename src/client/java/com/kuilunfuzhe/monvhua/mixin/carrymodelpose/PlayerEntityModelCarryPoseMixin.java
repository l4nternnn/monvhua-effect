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
	private ModelPart monvhua$head;

	// ========== 被抱者（侧躺/公主抱）模型部件姿态参数，单位：弧度 ==========
	// 说明：pitch = 上下摆动，yaw = 左右转动，roll = 侧向翻滚/扭转。
	// 说明：被抱者“整体横躺”的大旋转在 CarryAttachedRenderMath / PlayerEntityRendererCarryPoseMixin 中处理；这里主要调四肢和头部姿势。

	// 被抱者身体上下俯仰；正值/负值会让上半身前倾或后仰，方向以游戏内效果为准。
	@Unique private static final float BODY_PITCH = 0.0F;
	// 被抱者身体左右扭转；用于让身体略微朝抱人者内侧或外侧。
	@Unique private static final float BODY_YAW = 0.0F;
	// 被抱者身体侧向翻滚；一般保持 0，因为整体横躺已由矩阵处理。
	@Unique private static final float BODY_ROLL = 0.0F;

	// 被抱者右臂上下摆动；更负通常表示手臂更向前/向上伸，适合做环抱动作。
	@Unique private static final float RIGHT_ARM_PITCH = -1.2F;
	// 被抱者右臂左右张合；用于让右臂向身体内侧或外侧靠拢。
	@Unique private static final float RIGHT_ARM_YAW = 0.5F;
	// 被抱者右臂侧向扭转；用于让手臂姿势更贴合侧躺/环抱。
	@Unique private static final float RIGHT_ARM_ROLL = 0.3F;

	// 被抱者左臂上下摆动；更负通常表示手臂更向前/向上伸，适合做环抱动作。
	@Unique private static final float LEFT_ARM_PITCH = -1.2F;
	// 被抱者左臂左右张合；通常和右臂 yaw 取相反方向，保持双臂对称。
	@Unique private static final float LEFT_ARM_YAW = -0.5F;
	// 被抱者左臂侧向扭转；通常和右臂 roll 取相反方向，保持双臂对称。
	@Unique private static final float LEFT_ARM_ROLL = -0.3F;

	// 被抱者右腿上下摆动；更负通常表示腿向前弯/抬，适合搭在抱人者手臂附近。
	@Unique private static final float RIGHT_LEG_PITCH = -1.0F;
	// 被抱者右腿左右张合；用于让腿自然分开或并拢。
	@Unique private static final float RIGHT_LEG_YAW = 0.2F;
	// 被抱者右腿侧向扭转；用于让腿部在侧躺时更自然。
	@Unique private static final float RIGHT_LEG_ROLL = 0.1F;

	// 被抱者左腿上下摆动；通常和右腿 pitch 接近，保持双腿弯曲一致。
	@Unique private static final float LEFT_LEG_PITCH = -1.0F;
	// 被抱者左腿左右张合；通常和右腿 yaw 取相反方向，保持双腿对称。
	@Unique private static final float LEFT_LEG_YAW = -0.2F;
	// 被抱者左腿侧向扭转；通常和右腿 roll 取相反方向，保持双腿对称。
	@Unique private static final float LEFT_LEG_ROLL = -0.1F;

	// 被抱者头部上下俯仰；用于让头稍微低头或后仰。
	@Unique private static final float HEAD_PITCH = -0.3F;
	// 被抱者头部左右转动；用于让头看向抱人者或外侧。
	@Unique private static final float HEAD_YAW = 0.0F;
	// 被抱者头部侧向歪头；用于让头贴合怀抱姿势。
	@Unique private static final float HEAD_ROLL = 0.0F;

	// ========== 被抱者姿态附加偏移，单位：弧度 ==========
	// 说明：CUSTOM_* 会叠加到上面的基础姿态上，适合临时微调，不想破坏基础值时改这里。
	// 被抱者身体 pitch 附加偏移。
	@Unique private static final float CUSTOM_BODY_PITCH = 0.0F;
	// 被抱者身体 yaw 附加偏移。
	@Unique private static final float CUSTOM_BODY_YAW = 0.0F;
	// 被抱者身体 roll 附加偏移。
	@Unique private static final float CUSTOM_BODY_ROLL = 0.0F;
	// 被抱者右臂 pitch 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_ARM_PITCH = 0.0F;
	// 被抱者右臂 yaw 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_ARM_YAW = 0.0F;
	// 被抱者右臂 roll 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_ARM_ROLL = 0.0F;
	// 被抱者左臂 pitch 附加偏移。
	@Unique private static final float CUSTOM_LEFT_ARM_PITCH = 0.0F;
	// 被抱者左臂 yaw 附加偏移。
	@Unique private static final float CUSTOM_LEFT_ARM_YAW = 0.0F;
	// 被抱者左臂 roll 附加偏移。
	@Unique private static final float CUSTOM_LEFT_ARM_ROLL = 0.0F;
	// 被抱者右腿 pitch 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_LEG_PITCH = 0.0F;
	// 被抱者右腿 yaw 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_LEG_YAW = 0.0F;
	// 被抱者右腿 roll 附加偏移。
	@Unique private static final float CUSTOM_RIGHT_LEG_ROLL = 0.0F;
	// 被抱者左腿 pitch 附加偏移。
	@Unique private static final float CUSTOM_LEFT_LEG_PITCH = 0.0F;
	// 被抱者左腿 yaw 附加偏移。
	@Unique private static final float CUSTOM_LEFT_LEG_YAW = 0.0F;
	// 被抱者左腿 roll 附加偏移。
	@Unique private static final float CUSTOM_LEFT_LEG_ROLL = 0.0F;
	// 被抱者头部 pitch 附加偏移。
	@Unique private static final float CUSTOM_HEAD_PITCH = 0.0F;
	// 被抱者头部 yaw 附加偏移。
	@Unique private static final float CUSTOM_HEAD_YAW = 0.0F;
	// 被抱者头部 roll 附加偏移。
	@Unique private static final float CUSTOM_HEAD_ROLL = 0.0F;

	// ========== 抱人者模型部件姿态参数，单位：弧度 ==========
	// 抱人者身体上下俯仰；略微正值可表现为身体前倾用力抱人。
	@Unique private static final float CARRIER_BODY_PITCH = 0.08F;
	// 抱人者右臂上下摆动；更负通常表示手臂向前抬起托住被抱者。
	@Unique private static final float CARRIER_RIGHT_ARM_PITCH = -1.15F;
	// 抱人者右臂左右张合；用于调整右手离身体远近。
	@Unique private static final float CARRIER_RIGHT_ARM_YAW = -0.45F;
	// 抱人者右臂侧向扭转；用于让托举姿势更自然。
	@Unique private static final float CARRIER_RIGHT_ARM_ROLL = 0.32F;
	// 抱人者左臂上下摆动；更负通常表示手臂向前抬起托住被抱者。
	@Unique private static final float CARRIER_LEFT_ARM_PITCH = -1.15F;
	// 抱人者左臂左右张合；通常和右臂 yaw 取相反方向。
	@Unique private static final float CARRIER_LEFT_ARM_YAW = 0.45F;
	// 抱人者左臂侧向扭转；通常和右臂 roll 取相反方向。
	@Unique private static final float CARRIER_LEFT_ARM_ROLL = -0.32F;

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
