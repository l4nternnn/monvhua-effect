package com.kuilunfuzhe.monvhua.features.carryentity;

/**
 * 被抱/抱人模型姿态调试参数。
 *
 * <p>这些字段故意不使用 final，方便在调试器 Evaluate Expression 里运行时修改，例如：</p>
 * <pre>
 * com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning.CARRIER_RIGHT_ARM_YAW = -0.7F
 * </pre>
 *
 * <p>单位：弧度。若按角度思考，可用 {@code (float) Math.toRadians(-45.0)}。</p>
 */
public final class CarryPoseTuning {
	private CarryPoseTuning() {
	}

	public static void applyTransformConfig(CarryTransformConfig config) {
		if (config == null) {
			config = CarryTransformConfig.createDefault();
		}
		applyPrincessTransform(config.transformFor(com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets.POSE_PRINCESS));
		applyDragTransform(config.transformFor(com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets.POSE_DRAG));
		CarriedPlayerViewState.reset();
	}

	public static void resetTransformConfig() {
		applyTransformConfig(CarryTransformConfig.createDefault());
	}

	private static void applyPrincessTransform(CarryTransformConfig.TransformSet transform) {
		CARRIED_POSE_OFFSET_X = transform.pose.offsetX;
		CARRIED_POSE_OFFSET_Y = transform.pose.offsetY;
		CARRIED_POSE_OFFSET_Z = transform.pose.offsetZ;
		CARRIED_POSE_PITCH_DEGREES = transform.pose.pitchDegrees;
		CARRIED_POSE_YAW_DEGREES = transform.pose.yawDegrees;
		CARRIED_POSE_ROLL_DEGREES = transform.pose.rollDegrees;
		CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED = transform.view.enabled;
		CARRIED_DEFAULT_VIEW_OFFSET_X = transform.view.offsetX;
		CARRIED_DEFAULT_VIEW_OFFSET_Y = transform.view.offsetY;
		CARRIED_DEFAULT_VIEW_OFFSET_Z = transform.view.offsetZ;
		CARRIED_DEFAULT_VIEW_PITCH_DEGREES = transform.view.pitchDegrees;
		CARRIED_DEFAULT_VIEW_YAW_DEGREES = transform.view.yawDegrees;
		CARRIED_DEFAULT_VIEW_ROLL_DEGREES = transform.view.rollDegrees;
	}

	private static void applyDragTransform(CarryTransformConfig.TransformSet transform) {
		DRAG_CARRIED_POSE_OFFSET_X = transform.pose.offsetX;
		DRAG_CARRIED_POSE_OFFSET_Y = transform.pose.offsetY;
		DRAG_CARRIED_POSE_OFFSET_Z = transform.pose.offsetZ;
		DRAG_CARRIED_POSE_PITCH_DEGREES = transform.pose.pitchDegrees;
		DRAG_CARRIED_POSE_YAW_DEGREES = transform.pose.yawDegrees;
		DRAG_CARRIED_POSE_ROLL_DEGREES = transform.pose.rollDegrees;
		DRAG_CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED = transform.view.enabled;
		DRAG_CARRIED_DEFAULT_VIEW_OFFSET_X = transform.view.offsetX;
		DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Y = transform.view.offsetY;
		DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Z = transform.view.offsetZ;
		DRAG_CARRIED_DEFAULT_VIEW_PITCH_DEGREES = transform.view.pitchDegrees;
		DRAG_CARRIED_DEFAULT_VIEW_YAW_DEGREES = transform.view.yawDegrees;
		DRAG_CARRIED_DEFAULT_VIEW_ROLL_DEGREES = transform.view.rollDegrees;
	}

	public static float getCarriedPoseOffsetX(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_OFFSET_X : CARRIED_POSE_OFFSET_X;
	}

	public static float getCarriedPoseOffsetY(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_OFFSET_Y : CARRIED_POSE_OFFSET_Y;
	}

	public static float getCarriedPoseOffsetZ(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_OFFSET_Z : CARRIED_POSE_OFFSET_Z;
	}

	public static float getCarriedPosePitchDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_PITCH_DEGREES : CARRIED_POSE_PITCH_DEGREES;
	}

	public static float getCarriedPoseYawDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_YAW_DEGREES : CARRIED_POSE_YAW_DEGREES;
	}

	public static float getCarriedPoseRollDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_POSE_ROLL_DEGREES : CARRIED_POSE_ROLL_DEGREES;
	}

	public static boolean isDefaultViewTransformEnabled(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED : CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED;
	}

	public static float getDefaultViewOffsetX(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_OFFSET_X : CARRIED_DEFAULT_VIEW_OFFSET_X;
	}

	public static float getDefaultViewOffsetY(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Y : CARRIED_DEFAULT_VIEW_OFFSET_Y;
	}

	public static float getDefaultViewOffsetZ(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Z : CARRIED_DEFAULT_VIEW_OFFSET_Z;
	}

	public static float getDefaultViewPitchDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_PITCH_DEGREES : CARRIED_DEFAULT_VIEW_PITCH_DEGREES;
	}

	public static float getDefaultViewYawDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_YAW_DEGREES : CARRIED_DEFAULT_VIEW_YAW_DEGREES;
	}

	public static float getDefaultViewRollDegrees(boolean dragPose) {
		return dragPose ? DRAG_CARRIED_DEFAULT_VIEW_ROLL_DEGREES : CARRIED_DEFAULT_VIEW_ROLL_DEGREES;
	}

	// ========== 被抱者（侧躺/公主抱）模型部件姿态参数，单位：弧度 ==========
	// 说明：pitch = 上下摆动，yaw = 左右转动，roll = 侧向翻滚/扭转。
	// 说明：被抱者“整体横躺”的大旋转在 CarryAttachedRenderMath / PlayerEntityRendererCarryPoseMixin 中处理；这里主要调四肢和头部姿势。

	// 被抱者身体上下俯仰；正值/负值会让上半身前倾或后仰，方向以游戏内效果为准。
	public static float BODY_PITCH = 0.0F;
	// 被抱者身体左右扭转；用于让身体略微朝抱人者内侧或外侧。
	public static float BODY_YAW = 0.1F;
	// 被抱者身体侧向翻滚；一般保持 0，因为整体横躺已由矩阵处理。
	public static float BODY_ROLL = 0.0F;

	// 被抱者右臂上下摆动；更负通常表示手臂更向前/向上伸，适合做环抱动作。
	public static float RIGHT_ARM_PITCH = 0.9F;
	// 被抱者右臂左右张合；用于让右臂向身体内侧或外侧靠拢。
	public static float RIGHT_ARM_YAW = 0.1F;
	// 被抱者右臂侧向扭转；用于让手臂姿势更贴合侧躺/环抱。
	public static float RIGHT_ARM_ROLL = 0.5F;

	// 被抱者左臂上下摆动；更负通常表示手臂更向前/向上伸，适合做环抱动作。
	public static float LEFT_ARM_PITCH = -0.2F;
	// 被抱者左臂左右张合；通常和右臂 yaw 取相反方向，保持双臂对称。
	public static float LEFT_ARM_YAW = -0.5F;
	// 被抱者左臂侧向扭转；通常和右臂 roll 取相反方向，保持双臂对称。
	public static float LEFT_ARM_ROLL = -0.3F;

	// 被抱者右腿上下摆动；更负通常表示腿向前弯/抬，适合搭在抱人者手臂附近。
	public static float RIGHT_LEG_PITCH = -1.0F;
	// 被抱者右腿左右张合；用于让腿自然分开或并拢。
	public static float RIGHT_LEG_YAW = 0.2F;
	// 被抱者右腿侧向扭转；用于让腿部在侧躺时更自然。
	public static float RIGHT_LEG_ROLL = 0.1F;

	// 被抱者左腿上下摆动；通常和右腿 pitch 接近，保持双腿弯曲一致。
	public static float LEFT_LEG_PITCH = -1.3F;
	// 被抱者左腿左右张合；通常和右腿 yaw 取相反方向，保持双腿对称。
	public static float LEFT_LEG_YAW = 0.1F;
	// 被抱者左腿侧向扭转；通常和右腿 roll 取相反方向，保持双腿对称。
	public static float LEFT_LEG_ROLL = -0.6F;

	// 被抱者头部上下俯仰；用于让头稍微低头或后仰。
	public static float HEAD_PITCH = -0.3F;
	// 被抱者头部左右转动；用于让头看向抱人者或外侧。
	public static float HEAD_YAW = 0.0F;
	// 被抱者头部侧向歪头；用于让头贴合怀抱姿势。
	public static float HEAD_ROLL = 0.0F;

	// ========== 被抱者姿态附加偏移，单位：弧度 ==========
	// 说明：CUSTOM_* 会叠加到上面的基础姿态上，适合临时微调，不想破坏基础值时改这里。
	// 被抱者身体 pitch 附加偏移。
	public static float CUSTOM_BODY_PITCH = 0.0F;
	// 被抱者身体 yaw 附加偏移。
	public static float CUSTOM_BODY_YAW = 0.0F;
	// 被抱者身体 roll 附加偏移。
	public static float CUSTOM_BODY_ROLL = 0.0F;
	// 被抱者右臂 pitch 附加偏移。
	public static float CUSTOM_RIGHT_ARM_PITCH = 0.0F;
	// 被抱者右臂 yaw 附加偏移。
	public static float CUSTOM_RIGHT_ARM_YAW = 0.0F;
	// 被抱者右臂 roll 附加偏移。
	public static float CUSTOM_RIGHT_ARM_ROLL = 0.0F;
	// 被抱者左臂 pitch 附加偏移。
	public static float CUSTOM_LEFT_ARM_PITCH = 0.0F;
	// 被抱者左臂 yaw 附加偏移。
	public static float CUSTOM_LEFT_ARM_YAW = 0.0F;
	// 被抱者左臂 roll 附加偏移。
	public static float CUSTOM_LEFT_ARM_ROLL = 0.0F;
	// 被抱者右腿 pitch 附加偏移。
	public static float CUSTOM_RIGHT_LEG_PITCH = 0.0F;
	// 被抱者右腿 yaw 附加偏移。
	public static float CUSTOM_RIGHT_LEG_YAW = 0.0F;
	// 被抱者右腿 roll 附加偏移。
	public static float CUSTOM_RIGHT_LEG_ROLL = 0.0F;
	// 被抱者左腿 pitch 附加偏移。
	public static float CUSTOM_LEFT_LEG_PITCH = 0.0F;
	// 被抱者左腿 yaw 附加偏移。
	public static float CUSTOM_LEFT_LEG_YAW = 0.0F;
	// 被抱者左腿 roll 附加偏移。
	public static float CUSTOM_LEFT_LEG_ROLL = 0.0F;
	// 被抱者头部 pitch 附加偏移。
	public static float CUSTOM_HEAD_PITCH = 0.9F;
	// 被抱者头部 yaw 附加偏移。
	public static float CUSTOM_HEAD_YAW = -0.7F;
	// 被抱者头部 roll 附加偏移。
	public static float CUSTOM_HEAD_ROLL = -0.2F;

	// ========== 拖动姿势额外参数，单位：弧度 ==========
	public static float DRAG_BODY_PITCH = degreesToRadians(-25.0F);
	public static float DRAG_BODY_YAW = 0.0F;
	public static float DRAG_BODY_ROLL = 0.0F;
	public static float DRAG_RIGHT_ARM_PITCH = 0.0F;
	public static float DRAG_RIGHT_ARM_YAW = 0.0F;
	public static float DRAG_RIGHT_ARM_ROLL = 0.0F;
	public static float DRAG_LEFT_ARM_PITCH = 0.0F;
	public static float DRAG_LEFT_ARM_YAW = 0.0F;
	public static float DRAG_LEFT_ARM_ROLL = 0.0F;
	public static float DRAG_RIGHT_LEG_PITCH = 0.0F;
	public static float DRAG_RIGHT_LEG_YAW = 0.0F;
	public static float DRAG_RIGHT_LEG_ROLL = 0.0F;
	public static float DRAG_LEFT_LEG_PITCH = 0.0F;
	public static float DRAG_LEFT_LEG_YAW = 0.0F;
	public static float DRAG_LEFT_LEG_ROLL = 0.0F;
	public static float DRAG_HEAD_PITCH = degreesToRadians(-25.0F);
	public static float DRAG_HEAD_YAW = 0.0F;
	public static float DRAG_HEAD_ROLL = 0.0F;

	// Runtime whole-carried-pose adjustment. Offsets are blocks, rotations are degrees.
	public static float CARRIED_POSE_OFFSET_X = 0.0F;
	public static float CARRIED_POSE_OFFSET_Y = 0.0F;
	public static float CARRIED_POSE_OFFSET_Z = 0.0F;
	public static float CARRIED_POSE_PITCH_DEGREES = 0.0F;
	public static float CARRIED_POSE_YAW_DEGREES = 0.0F;
	public static float CARRIED_POSE_ROLL_DEGREES = 0.0F;

	// Optional carried-player default view transform. Disabled means no change to the current default.
	public static boolean CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED = false;
	public static float CARRIED_DEFAULT_VIEW_OFFSET_X = 0.0F;
	public static float CARRIED_DEFAULT_VIEW_OFFSET_Y = 0.0F;
	public static float CARRIED_DEFAULT_VIEW_OFFSET_Z = 0.0F;
	public static float CARRIED_DEFAULT_VIEW_PITCH_DEGREES = 0.0F;
	public static float CARRIED_DEFAULT_VIEW_YAW_DEGREES = 0.0F;
	public static float CARRIED_DEFAULT_VIEW_ROLL_DEGREES = 0.0F;

	// Drag pose uses a separate whole-pose and default-view transform.
	public static float DRAG_CARRIED_POSE_OFFSET_X = 0.0F;
	public static float DRAG_CARRIED_POSE_OFFSET_Y = 0.0F;
	public static float DRAG_CARRIED_POSE_OFFSET_Z = 0.0F;
	public static float DRAG_CARRIED_POSE_PITCH_DEGREES = 0.0F;
	public static float DRAG_CARRIED_POSE_YAW_DEGREES = 0.0F;
	public static float DRAG_CARRIED_POSE_ROLL_DEGREES = 0.0F;
	public static boolean DRAG_CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED = false;
	public static float DRAG_CARRIED_DEFAULT_VIEW_OFFSET_X = 0.0F;
	public static float DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Y = 0.0F;
	public static float DRAG_CARRIED_DEFAULT_VIEW_OFFSET_Z = 0.0F;
	public static float DRAG_CARRIED_DEFAULT_VIEW_PITCH_DEGREES = 0.0F;
	public static float DRAG_CARRIED_DEFAULT_VIEW_YAW_DEGREES = 0.0F;
	public static float DRAG_CARRIED_DEFAULT_VIEW_ROLL_DEGREES = 0.0F;

	// ========== 被抱者第一人称视角限制/头部同步参数 ==========
	// 开启后，被抱者第一人称视角固定到下面的 local yaw/pitch，不再由鼠标控制；模型头部也不再跟随视角，改由 HEAD_* / CUSTOM_HEAD_* 手调。
	public static boolean CARRIED_FIXED_VIEW_ENABLED = false;
	// 固定视角的本地 yaw，单位：度；取自调试输出 localViewYawDegrees。
	public static float CARRIED_FIXED_VIEW_LOCAL_YAW_DEGREES = 210.0F;
	// 固定视角的本地 pitch，单位：度；取自调试输出 localViewPitchDegrees。
	public static float CARRIED_FIXED_VIEW_LOCAL_PITCH_DEGREES = -10F;
	// 被抱者第一人称视角限制中心相对基础头部世界朝向的左右偏移，单位：度；只移动限制/追踪零点，不直接改变基础头部姿势。
	public static float CARRIED_VIEW_CENTER_YAW_OFFSET_DEGREES = 0F;//-135.0F;
	// 被抱者第一人称视角限制中心相对基础头部世界朝向的上下偏移，单位：度；正负方向按游戏内效果微调。
	public static float CARRIED_VIEW_CENTER_PITCH_OFFSET_DEGREES = 0F;// 45.0F;
	// 被抱者第一人称视角相对“被抱模型头部基础朝向”的左右可转范围，单位：度。
	public static float CARRIED_VIEW_YAW_LIMIT_DEGREES = 180.0F;
	// 被抱者第一人称视角相对“被抱模型头部基础朝向”的向上可转范围，单位：度。
	public static float CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES = 90.0F;
	// 被抱者第一人称视角相对“被抱模型头部基础朝向”的向下可转范围，单位：度。
	public static float CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES = 90.0F;
	// 被抱者视角左右转动同步到额外模型头部 yaw 的比例；1.0 = 完全同步，0.5 = 只同步一半。
	public static float CARRIED_HEAD_VIEW_YAW_SCALE = 1.0F;
	// 被抱者视角左右转动同步到额外模型头部 yaw 的方向；如果左右相反，运行时改成 -1.0F。
	public static float CARRIED_HEAD_VIEW_YAW_DIRECTION = -1.0F;
	// 被抱者视角上下转动同步到额外模型头部 pitch 的比例；1.0 = 完全同步，0.5 = 只同步一半。
	public static float CARRIED_HEAD_VIEW_PITCH_SCALE = 1.0F;
	// 被抱者视角上下转动同步到额外模型头部 pitch 的方向；如果上下相反，运行时改成 -1.0F。
	public static float CARRIED_HEAD_VIEW_PITCH_DIRECTION = -1.0F;

	// ========== 抱人者模型部件姿态参数，单位：弧度 ==========
	// 抱人者抱持时保留的手臂动态摆动比例；1.0 = 原幅度，0.0 = 完全锁死，建议 0.25~0.5。
	public static float CARRIER_ARM_MOTION_SCALE = 0.35F;
	// 抱人者身体上下俯仰；略微正值可表现为身体前倾用力抱人。
	public static float CARRIER_BODY_PITCH = 0.08F;
	// 抱人者右臂上下摆动；更负通常表示手臂向前抬起托住被抱者。
	public static float CARRIER_RIGHT_ARM_PITCH = -1F;
	// 抱人者右臂左右张合；用于调整右手离身体远近。
	public static float CARRIER_RIGHT_ARM_YAW = -0.2F;
	// 抱人者右臂侧向扭转；用于让托举姿势更自然。
	public static float CARRIER_RIGHT_ARM_ROLL = 0.45F;
	// 抱人者左臂上下摆动；更负通常表示手臂向前抬起托住被抱者。
	public static float CARRIER_LEFT_ARM_PITCH = -0.8F;
	// 抱人者左臂左右张合；通常和右臂 yaw 取相反方向。
	public static float CARRIER_LEFT_ARM_YAW = 0.45F;
	// 抱人者左臂侧向扭转；通常和右臂 roll 取相反方向。
	public static float CARRIER_LEFT_ARM_ROLL = -0.32F;

	private static float degreesToRadians(float degrees) {
		return degrees * ((float) Math.PI / 180.0F);
	}
}
