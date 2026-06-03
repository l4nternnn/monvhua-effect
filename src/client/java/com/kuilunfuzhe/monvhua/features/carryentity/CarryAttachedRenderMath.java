package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class CarryAttachedRenderMath {
	// 被抱者整体挂载点的左右偏移；正值通常偏向抱人者的左/右侧之一，负值相反，需要按游戏内效果微调。
	public static final float ATTACHED_CARRIED_X = 0.5F;
	// 被抱者整体挂载点的高度；增大 = 被抱者整体更高，减小 = 更低。
	public static final float ATTACHED_CARRIED_Y = -0.05F;
	// 被抱者整体挂载点的前后偏移；增大/减小会让被抱者更靠近或远离抱人者胸前，方向以游戏内效果为准。
	public static final float ATTACHED_CARRIED_Z = -0.3F;
	// 被抱者整体绕竖直 Y 轴的朝向角度，单位：度；常用 0、90、180、-90 来修正面朝方向/头脚方向。
	public static final float ATTACHED_CARRIED_YAW_DEGREES = 180.0F;
	// 被抱者模型横躺旋转的枢轴高度；影响身体绕哪里躺倒，调错会导致头/脚绕奇怪位置甩动。
	public static final float CARRIED_MODEL_PIVOT_Y = 0.0F;
	// 被抱者模型绕 X 轴的横躺角度，单位：度；主要控制躺下/仰起/趴下，-90 通常表示水平横躺。
	public static final float CARRIED_MODEL_ROTATION_X_DEGREES = -60.0F;
	// 被抱者模型绕 Y 轴的补偿旋转，单位：度；用于微调身体在抱人者怀里的左右朝向。
	public static final float CARRIED_MODEL_ROTATION_Y_DEGREES = -75.0F;
	// 被抱者模型绕 Z 轴的翻滚角度，单位：度；用于让身体向左/右侧倾斜。
	public static final float CARRIED_MODEL_ROTATION_Z_DEGREES = -10.0F;

	// 沿被抱者“头 -> 脚 / 脚 -> 头”身体长轴的摄像机偏移，单位：方块；用于把第一人称原点推到头部附近。
	// 注意：这里不直接放到局部 Y，因为当前渲染矩阵里局部 Y 的移动轨迹会垂直于身体长轴。
	public static final float PLAYER_EYE_HEIGHT = 1.6F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的左右偏移；正负方向按游戏内效果微调。
	public static final float CARRIED_CAMERA_HEAD_LOCAL_X = -0.15F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的身体厚度/贴脸高度偏移；不是头脚方向，通常只做小幅微调。
	public static final float CARRIED_CAMERA_HEAD_LOCAL_Y = 0.8F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的头脚方向偏移；调 PLAYER_EYE_HEIGHT 时主要沿身体长轴移动。
	public static final float CARRIED_CAMERA_HEAD_LOCAL_Z = PLAYER_EYE_HEIGHT;

	// 当前公主抱姿势中，被抱者头部/身体正面按局部 +Z 处理；Minecraft Camera 自身默认看向 -Z，应用到相机时需要 180° Y 轴补偿。
	private static final Vector3f CAMERA_LOCAL_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);
	private static final Vector3f CAMERA_LOCAL_UP = new Vector3f(0.0F, 1.0F, 0.0F);
	private static final Vector3f CAMERA_LOCAL_RIGHT = new Vector3f(-1.0F, 0.0F, 0.0F);

	private CarryAttachedRenderMath() {
	}

	public static void applyAttachedTransform(MatrixStack matrices) {
		matrices.translate(ATTACHED_CARRIED_X, ATTACHED_CARRIED_Y, ATTACHED_CARRIED_Z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ATTACHED_CARRIED_YAW_DEGREES));
	}

	public static Vec3d getAttachedBaseWorldPos(Entity carrier, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices);
		return transformLocalPoint(matrices, 0.0F, 0.0F, 0.0F);
	}

	public static Vec3d getCarriedCameraHeadWorldPos(Entity carrier, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices);
		applyCarriedModelHorizontalTransform(matrices, 1.0F);
		return transformLocalPoint(matrices, CARRIED_CAMERA_HEAD_LOCAL_X, CARRIED_CAMERA_HEAD_LOCAL_Y, CARRIED_CAMERA_HEAD_LOCAL_Z);
	}

	public static WorldViewRotation getCarriedLocalViewWorldRotation(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		CarriedCameraOrientation orientation = getCarriedLocalViewCameraOrientation(carrier, tickProgress, localYawDegrees, localPitchDegrees);
		return new WorldViewRotation(orientation.yawDegrees(), orientation.pitchDegrees());
	}

	public static CarriedCameraOrientation getCarriedLocalViewCameraOrientation(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		MatrixStack matrices = createConfiguredCarriedViewMatrix(carrier, tickProgress, localYawDegrees, localPitchDegrees);
		Vector3f modelForward = transformLocalDirection(matrices, 0.0F, 0.0F, 1.0F);

		Quaternionf rotation = new Matrix4f(matrices.peek().getPositionMatrix())
				.getUnnormalizedRotation(new Quaternionf())
				.normalize();
		rotation.rotateY((float) Math.PI);

		float yaw = (float) Math.toDegrees(Math.atan2(-modelForward.x, modelForward.z));
		float horizontalLength = MathHelper.sqrt(modelForward.x * modelForward.x + modelForward.z * modelForward.z);
		float pitch = (float) Math.toDegrees(Math.atan2(-modelForward.y, horizontalLength));
		Vector3f horizontalPlane = new Vector3f(CAMERA_LOCAL_FORWARD).rotate(rotation).normalize();
		Vector3f verticalPlane = new Vector3f(CAMERA_LOCAL_UP).rotate(rotation).normalize();
		Vector3f diagonalPlane = new Vector3f(CAMERA_LOCAL_RIGHT).rotate(rotation).normalize();
		return new CarriedCameraOrientation(yaw, pitch, rotation, horizontalPlane, verticalPlane, diagonalPlane);
	}

	public static DebugTransform getCarriedBaseHeadDebugTransform(Entity carrier, float tickProgress) {
		float baseHeadYaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		float baseHeadPitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		float baseHeadRoll = CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
		return createDebugTransform(createCarriedBaseHeadMatrix(
				carrier,
				tickProgress,
				baseHeadYaw,
				baseHeadPitch,
				baseHeadRoll
		));
	}

	public static DebugTransform getCarriedLocalViewDebugTransform(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return createDebugTransform(createConfiguredCarriedViewMatrix(
				carrier,
				tickProgress,
				localYawDegrees,
				localPitchDegrees
		));
	}

	private static MatrixStack createConfiguredCarriedViewMatrix(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		float baseHeadYaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		float baseHeadPitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		float baseHeadRoll = CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
		return createCarriedViewMatrix(
				carrier,
				tickProgress,
				baseHeadYaw,
				baseHeadPitch,
				baseHeadRoll,
				CarryPoseTuning.CARRIED_VIEW_CENTER_YAW_OFFSET_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_CENTER_PITCH_OFFSET_DEGREES,
				localYawDegrees,
				localPitchDegrees
		);
	}

	public static CarriedHeadWorldRotation getCarriedHeadWorldRotation(Entity carrier, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		MatrixStack matrices = createCarriedHeadParentMatrix(carrier, tickProgress);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(headYawRadians));
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(headPitchRadians));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotation(headRollRadians));
		Vector3f forward = transformLocalDirection(matrices, 0.0F, 0.0F, 1.0F);
		float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
		float horizontalLength = MathHelper.sqrt(forward.x * forward.x + forward.z * forward.z);
		float pitch = (float) Math.toDegrees(Math.atan2(-forward.y, horizontalLength));
		return new CarriedHeadWorldRotation(yaw, pitch);
	}

	public static HeadViewOffset getCarriedHeadViewOffsetForWorldView(Entity carrier, float tickProgress, float viewYawDegrees, float viewPitchDegrees, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians) {
		Vector4f worldForward = getWorldForwardVector(viewYawDegrees, viewPitchDegrees);
		Matrix4f inverseBaseHeadMatrix = new Matrix4f(createCarriedBaseHeadMatrix(
				carrier,
				tickProgress,
				baseHeadYawRadians,
				baseHeadPitchRadians,
				baseHeadRollRadians
		).peek().getPositionMatrix()).invert();
		Vector4f localForward4 = inverseBaseHeadMatrix.transform(worldForward);
		Vector3f localForward = new Vector3f(localForward4.x, localForward4.y, localForward4.z).normalize();
		float yawDegrees = (float) Math.toDegrees(Math.atan2(localForward.x, localForward.z));
		float localHorizontalLength = MathHelper.sqrt(localForward.x * localForward.x + localForward.z * localForward.z);
		float pitchDegrees = (float) Math.toDegrees(Math.atan2(localForward.y, localHorizontalLength));
		return new HeadViewOffset(yawDegrees, pitchDegrees);
	}

	public static WorldViewRotation clampCarriedWorldView(Entity carrier, float tickProgress, float viewYawDegrees, float viewPitchDegrees, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians) {
		HeadViewOffset offset = getCarriedHeadViewOffsetForWorldView(
				carrier,
				tickProgress,
				viewYawDegrees,
				viewPitchDegrees,
				baseHeadYawRadians,
				baseHeadPitchRadians,
				baseHeadRollRadians
		);
		float clampedYawDegrees = MathHelper.clamp(
				offset.yawDegrees(),
				-CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES
		);
		float clampedPitchDegrees = MathHelper.clamp(
				offset.pitchDegrees(),
				-CarryPoseTuning.CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES
		);
		Vector4f localForward = getLocalForwardVector(clampedYawDegrees, clampedPitchDegrees);
		Vector4f worldForward = createCarriedBaseHeadMatrix(
				carrier,
				tickProgress,
				baseHeadYawRadians,
				baseHeadPitchRadians,
				baseHeadRollRadians
		).peek().getPositionMatrix().transform(localForward);
		Vector3f normalizedForward = new Vector3f(worldForward.x, worldForward.y, worldForward.z).normalize();
		float yaw = (float) Math.toDegrees(Math.atan2(-normalizedForward.x, normalizedForward.z));
		float horizontalLength = MathHelper.sqrt(normalizedForward.x * normalizedForward.x + normalizedForward.z * normalizedForward.z);
		float pitch = (float) Math.toDegrees(Math.atan2(-normalizedForward.y, horizontalLength));
		return new WorldViewRotation(yaw, pitch);
	}

	public static HeadModelRotation getCarriedHeadModelRotationForWorldView(Entity carrier, float tickProgress, float viewYawDegrees, float viewPitchDegrees) {
		Vector4f worldForward = getWorldForwardVector(viewYawDegrees, viewPitchDegrees);

		Matrix4f inverseHeadParentMatrix = new Matrix4f(createCarriedHeadParentMatrix(carrier, tickProgress).peek().getPositionMatrix()).invert();
		Vector4f localForward4 = inverseHeadParentMatrix.transform(worldForward);
		Vector3f localForward = new Vector3f(localForward4.x, localForward4.y, localForward4.z).normalize();
		float headYawRadians = (float) Math.atan2(localForward.x, localForward.z);
		float localHorizontalLength = MathHelper.sqrt(localForward.x * localForward.x + localForward.z * localForward.z);
		float headPitchRadians = (float) Math.atan2(-localForward.y, localHorizontalLength);
		return new HeadModelRotation(headYawRadians, headPitchRadians);
	}

	private static MatrixStack createCarriedViewMatrix(Entity carrier, float tickProgress, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians, float centerYawDegrees, float centerPitchDegrees, float localYawDegrees, float localPitchDegrees) {
		MatrixStack matrices = createCarriedBaseHeadMatrix(
				carrier,
				tickProgress,
				baseHeadYawRadians,
				baseHeadPitchRadians,
				baseHeadRollRadians
		);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(centerYawDegrees));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(centerPitchDegrees));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(localYawDegrees));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(localPitchDegrees));
		return matrices;
	}

	private static MatrixStack createCarriedBaseHeadMatrix(Entity carrier, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		MatrixStack matrices = createCarriedHeadParentMatrix(carrier, tickProgress);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(headYawRadians));
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(headPitchRadians));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotation(headRollRadians));
		return matrices;
	}

	private static MatrixStack createCarriedHeadParentMatrix(Entity carrier, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices);
		applyCarriedModelHorizontalTransform(matrices, 1.0F);
		return matrices;
	}

	public static MatrixStack createAttachedViewMatrix(Entity carrier, float tickProgress, Vec3d cameraPos, Matrix4f positionMatrix) {
		Vec3d carrierPos = carrier.getLerpedPos(tickProgress);
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(positionMatrix);
		matrices.translate(carrierPos.x - cameraPos.x, carrierPos.y - cameraPos.y, carrierPos.z - cameraPos.z);
		applyCarrierYaw(matrices, carrier, tickProgress);
		applyAttachedTransform(matrices);
		return matrices;
	}

	public static void applyCarriedModelHorizontalTransform(MatrixStack matrices, float baseScale) {
		float pivotY = CARRIED_MODEL_PIVOT_Y / baseScale;
		matrices.translate(0.0F, pivotY, 0.0F);
		applyCarriedModelRotation(matrices);
		matrices.translate(0.0F, -pivotY, 0.0F);
	}

	public static void applyCarriedModelRotation(MatrixStack matrices) {
		if (CARRIED_MODEL_ROTATION_Y_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(CARRIED_MODEL_ROTATION_Y_DEGREES));
		}
		if (CARRIED_MODEL_ROTATION_X_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(CARRIED_MODEL_ROTATION_X_DEGREES));
		}
		if (CARRIED_MODEL_ROTATION_Z_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(CARRIED_MODEL_ROTATION_Z_DEGREES));
		}
	}

	public static MatrixStack createCarrierWorldMatrix(Entity carrier, float tickProgress) {
		MatrixStack matrices = new MatrixStack();
		Vec3d carrierPos = carrier.getLerpedPos(tickProgress);
		matrices.translate(carrierPos.x, carrierPos.y, carrierPos.z);
		applyCarrierYaw(matrices, carrier, tickProgress);
		return matrices;
	}

	// 把抱人者矩阵旋转到“躯干朝向”，不要使用头部/视角 yaw；否则抱人者转头时被抱者模型会跟着甩动。
	public static void applyCarrierYaw(MatrixStack matrices, Entity carrier, float tickProgress) {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - getCarrierBodyYaw(carrier, tickProgress)));
	}

	private static float getCarrierBodyYaw(Entity carrier, float tickProgress) {
		if (carrier instanceof LivingEntity livingCarrier) {
			return MathHelper.lerpAngleDegrees(tickProgress, livingCarrier.lastBodyYaw, livingCarrier.bodyYaw);
		}
		return carrier.getLerpedYaw(tickProgress);
	}

	public static Vec3d transformLocalPoint(MatrixStack matrices, float localX, float localY, float localZ) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Vector3f point = matrix.transformPosition(localX, localY, localZ, new Vector3f());
		return new Vec3d(point.x, point.y, point.z);
	}

	private static Vector4f getWorldForwardVector(float viewYawDegrees, float viewPitchDegrees) {
		float viewYawRadians = (float) Math.toRadians(viewYawDegrees);
		float viewPitchRadians = (float) Math.toRadians(viewPitchDegrees);
		float horizontalLength = MathHelper.cos(viewPitchRadians);
		return new Vector4f(
				-MathHelper.sin(viewYawRadians) * horizontalLength,
				-MathHelper.sin(viewPitchRadians),
				MathHelper.cos(viewYawRadians) * horizontalLength,
				0.0F
		);
	}

	private static Vector4f getLocalForwardVector(float yawDegrees, float pitchDegrees) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		float pitchRadians = (float) Math.toRadians(pitchDegrees);
		float horizontalLength = MathHelper.cos(pitchRadians);
		return new Vector4f(
				MathHelper.sin(yawRadians) * horizontalLength,
				MathHelper.sin(pitchRadians),
				MathHelper.cos(yawRadians) * horizontalLength,
				0.0F
		);
	}

	private static Vector3f transformLocalDirection(MatrixStack matrices, float localX, float localY, float localZ) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Vector4f direction = matrix.transform(new Vector4f(localX, localY, localZ, 0.0F));
		return new Vector3f(direction.x, direction.y, direction.z).normalize();
	}

	private static DebugTransform createDebugTransform(MatrixStack matrices) {
		Matrix4f matrix = new Matrix4f(matrices.peek().getPositionMatrix());
		Quaternionf rotation = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
		return new DebugTransform(
				transformLocalPoint(matrices, 0.0F, 0.0F, 0.0F),
				transformLocalDirection(matrices, 1.0F, 0.0F, 0.0F),
				transformLocalDirection(matrices, 0.0F, 1.0F, 0.0F),
				transformLocalDirection(matrices, 0.0F, 0.0F, 1.0F),
				rotation,
				matrix
		);
	}

	public record CarriedHeadWorldRotation(float yawDegrees, float pitchDegrees) {
	}

	public record HeadViewOffset(float yawDegrees, float pitchDegrees) {
	}

	public record WorldViewRotation(float yawDegrees, float pitchDegrees) {
	}

	public record CarriedCameraOrientation(float yawDegrees, float pitchDegrees, Quaternionf rotation, Vector3f horizontalPlane, Vector3f verticalPlane, Vector3f diagonalPlane) {
	}

	public record DebugTransform(Vec3d position, Vector3f right, Vector3f up, Vector3f forward, Quaternionf rotation, Matrix4f matrix) {
	}

	public record HeadModelRotation(float yawRadians, float pitchRadians) {
	}
}
