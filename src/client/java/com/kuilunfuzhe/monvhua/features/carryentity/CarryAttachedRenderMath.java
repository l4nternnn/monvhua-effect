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
	// 注意：这是普通 1 倍玩家尺寸下的基础手调偏移，实际渲染会再按抱人/被抱双方高度 scale 动态补偿。
	public static final float ATTACHED_CARRIED_X = 0.5F;
	// 被抱者整体挂载点的高度；增大 = 被抱者整体更高，减小 = 更低。
	// 注意：这是普通 1 倍玩家尺寸下的基础手调偏移，实际渲染会再按抱人/被抱双方高度 scale 动态补偿。
	public static final float ATTACHED_CARRIED_Y = -0.05F;
	// 计算 scale 的基准玩家高度；当前玩家标准站立碰撞箱高度约为 1.8 方块。
	public static final float REFERENCE_PLAYER_HEIGHT = 1.8F;
	// 抱人者变高/变大时，左右挂载点随体型外扩的补偿比例；1.0 = 左右偏移完全跟随抱人者 scale。
	public static final float CARRIER_SCALE_SIDE_COMPENSATION = 1.0F;
	// 被抱者变高/变大时，为避免大体型被抱者贴进抱人者身体，额外向当前左右偏移方向外推的补偿比例。
	public static final float CARRIED_SCALE_SIDE_COMPENSATION = 0.35F;
	// 抱人者变高时，怀抱锚点随身高上移的补偿系数；增大 = 大体型抱人者怀里模型更高。
	public static final float CARRIER_SCALE_HEIGHT_COMPENSATION = 1.0F;
	// 被抱者变高时，为保持身体锚点落在怀抱位置，需要把被抱者脚底渲染原点下移的补偿系数；增大 = 大体型被抱者整体更低。
	public static final float CARRIED_SCALE_HEIGHT_COMPENSATION = 0.9F;
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
	public static final float DRAG_CARRIED_MODEL_ROTATION_X_DEGREES = -102.5001F;
	public static final float DRAG_CARRIED_MODEL_ROTATION_Y_DEGREES = 0.0002F;
	public static final float DRAG_CARRIED_MODEL_ROTATION_Z_DEGREES = -179.9999F;
	public static final float DRAG_CARRIED_MODEL_FACE_AXIS_FIX_X_DEGREES = 180.0F;

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

	public static float getCarriedBaseHeadYaw(Entity carried) {
		if (isDragCarried(carried)) {
			return CarryPoseTuning.DRAG_HEAD_YAW;
		}
		return CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
	}

	public static float getCarriedBaseHeadPitch(Entity carried) {
		if (isDragCarried(carried)) {
			return CarryPoseTuning.DRAG_HEAD_PITCH;
		}
		return CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
	}

	public static float getCarriedBaseHeadRoll(Entity carried) {
		if (isDragCarried(carried)) {
			return CarryPoseTuning.DRAG_HEAD_ROLL;
		}
		return CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
	}

	public static void applyAttachedTransform(MatrixStack matrices) {
		applyAttachedTransform(matrices, null, null);
	}

	public static void applyAttachedTransform(MatrixStack matrices, Entity carrier, Entity carried) {
		matrices.translate(getAttachedCarriedX(carrier, carried), getAttachedCarriedY(carrier, carried), ATTACHED_CARRIED_Z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ATTACHED_CARRIED_YAW_DEGREES));
		applyCarriedPoseAdjustment(matrices, isDragCarried(carried));
	}

	public static float getAttachedCarriedX(Entity carrier, Entity carried) {
		if (carrier == null || carried == null) {
			return ATTACHED_CARRIED_X;
		}

		float carrierScale = getEntityHeightScale(carrier);
		float carriedScale = getEntityHeightScale(carried);
		return ATTACHED_CARRIED_X
				+ ATTACHED_CARRIED_X * CARRIER_SCALE_SIDE_COMPENSATION * (carrierScale - 1.0F)
				+ ATTACHED_CARRIED_X * CARRIED_SCALE_SIDE_COMPENSATION * (carriedScale - 1.0F);
	}

	public static float getAttachedCarriedY(Entity carrier, Entity carried) {
		if (carrier == null || carried == null) {
			return ATTACHED_CARRIED_Y;
		}

		float carrierScale = getEntityHeightScale(carrier);
		float carriedScale = getEntityHeightScale(carried);
		return ATTACHED_CARRIED_Y
				+ CARRIER_SCALE_HEIGHT_COMPENSATION * (carrierScale - 1.0F)
				- CARRIED_SCALE_HEIGHT_COMPENSATION * (carriedScale - 1.0F);
	}

	private static float getEntityHeightScale(Entity entity) {
		float height = entity.getHeight();
		if (height <= 0.0F) {
			return 1.0F;
		}
		return height / REFERENCE_PLAYER_HEIGHT;
	}

	public static Vec3d getAttachedBaseWorldPos(Entity carrier, float tickProgress) {
		return getAttachedBaseWorldPos(carrier, null, tickProgress);
	}

	public static Vec3d getAttachedBaseWorldPos(Entity carrier, Entity carried, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices, carrier, carried);
		return transformLocalPoint(matrices, 0.0F, 0.0F, 0.0F);
	}

	public static Vec3d getCarriedCameraHeadWorldPos(Entity carrier, float tickProgress) {
		return getCarriedCameraHeadWorldPos(carrier, null, tickProgress);
	}

	public static Vec3d getCarriedCameraHeadWorldPos(Entity carrier, Entity carried, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices, carrier, carried);
		applyCarriedModelHorizontalTransform(matrices, 1.0F, isDragCarried(carried));
		boolean dragPose = isDragCarried(carried);
		return transformLocalPoint(matrices, getCameraLocalX(dragPose), getCameraLocalY(dragPose), getCameraLocalZ(dragPose));
	}

	public static WorldViewRotation getCarriedLocalViewWorldRotation(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return getCarriedLocalViewWorldRotation(carrier, null, tickProgress, localYawDegrees, localPitchDegrees);
	}

	public static WorldViewRotation getCarriedLocalViewWorldRotation(Entity carrier, Entity carried, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		CarriedCameraOrientation orientation = getCarriedLocalViewCameraOrientation(carrier, carried, tickProgress, localYawDegrees, localPitchDegrees);
		return new WorldViewRotation(orientation.yawDegrees(), orientation.pitchDegrees());
	}

	public static CarriedCameraOrientation getCarriedLocalViewCameraOrientation(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return getCarriedLocalViewCameraOrientation(carrier, null, tickProgress, localYawDegrees, localPitchDegrees);
	}

	public static CarriedCameraOrientation getCarriedLocalViewCameraOrientation(Entity carrier, Entity carried, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		MatrixStack matrices = createConfiguredCarriedViewMatrix(carrier, carried, tickProgress, localYawDegrees, localPitchDegrees);
		Vector3f modelForward = transformLocalDirection(matrices, 0.0F, 0.0F, -1.0F);

		Quaternionf rotation = new Matrix4f(matrices.peek().getPositionMatrix())
				.getUnnormalizedRotation(new Quaternionf())
				.normalize();

		float yaw = (float) Math.toDegrees(Math.atan2(-modelForward.x, modelForward.z));
		float horizontalLength = MathHelper.sqrt(modelForward.x * modelForward.x + modelForward.z * modelForward.z);
		float pitch = (float) Math.toDegrees(Math.atan2(-modelForward.y, horizontalLength));
		Vector3f horizontalPlane = new Vector3f(CAMERA_LOCAL_FORWARD).rotate(rotation).normalize();
		Vector3f verticalPlane = new Vector3f(CAMERA_LOCAL_UP).rotate(rotation).normalize();
		Vector3f diagonalPlane = new Vector3f(CAMERA_LOCAL_RIGHT).rotate(rotation).normalize();
		return new CarriedCameraOrientation(yaw, pitch, rotation, horizontalPlane, verticalPlane, diagonalPlane);
	}

	public static DebugTransform getCarriedBaseHeadDebugTransform(Entity carrier, float tickProgress) {
		return getCarriedBaseHeadDebugTransform(carrier, null, tickProgress);
	}

	public static DebugTransform getCarriedBaseHeadDebugTransform(Entity carrier, Entity carried, float tickProgress) {
		float baseHeadYaw = getCarriedBaseHeadYaw(carried);
		float baseHeadPitch = getCarriedBaseHeadPitch(carried);
		float baseHeadRoll = getCarriedBaseHeadRoll(carried);
		return createDebugTransform(createCarriedBaseHeadMatrix(
				carrier,
				carried,
				tickProgress,
				baseHeadYaw,
				baseHeadPitch,
				baseHeadRoll
		));
	}

	public static DebugTransform getCarriedLocalViewDebugTransform(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return getCarriedLocalViewDebugTransform(carrier, null, tickProgress, localYawDegrees, localPitchDegrees);
	}

	public static DebugTransform getCarriedLocalViewDebugTransform(Entity carrier, Entity carried, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return createDebugTransform(createConfiguredCarriedViewMatrix(
				carrier,
				carried,
				tickProgress,
				localYawDegrees,
				localPitchDegrees
		));
	}

	private static MatrixStack createConfiguredCarriedViewMatrix(Entity carrier, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		return createConfiguredCarriedViewMatrix(carrier, null, tickProgress, localYawDegrees, localPitchDegrees);
	}

	private static MatrixStack createConfiguredCarriedViewMatrix(Entity carrier, Entity carried, float tickProgress, float localYawDegrees, float localPitchDegrees) {
		float baseHeadYaw = getCarriedBaseHeadYaw(carried);
		float baseHeadPitch = getCarriedBaseHeadPitch(carried);
		float baseHeadRoll = getCarriedBaseHeadRoll(carried);
		return createCarriedViewMatrix(
				carrier,
				carried,
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
		return getCarriedHeadWorldRotation(carrier, null, tickProgress, headYawRadians, headPitchRadians, headRollRadians);
	}

	public static CarriedHeadWorldRotation getCarriedHeadWorldRotation(Entity carrier, Entity carried, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		MatrixStack matrices = createCarriedHeadParentMatrix(carrier, carried, tickProgress);
		applyModelPartRotation(matrices, headPitchRadians, headYawRadians, headRollRadians);
		Vector3f forward = transformLocalDirection(matrices, 0.0F, 0.0F, -1.0F);
		float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
		float horizontalLength = MathHelper.sqrt(forward.x * forward.x + forward.z * forward.z);
		float pitch = (float) Math.toDegrees(Math.atan2(-forward.y, horizontalLength));
		return new CarriedHeadWorldRotation(yaw, pitch);
	}

	public static HeadViewOffset getCarriedHeadViewOffsetForWorldView(Entity carrier, float tickProgress, float viewYawDegrees, float viewPitchDegrees, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians) {
		return getCarriedHeadViewOffsetForWorldView(carrier, null, tickProgress, viewYawDegrees, viewPitchDegrees, baseHeadYawRadians, baseHeadPitchRadians, baseHeadRollRadians);
	}

	public static HeadViewOffset getCarriedHeadViewOffsetForWorldView(Entity carrier, Entity carried, float tickProgress, float viewYawDegrees, float viewPitchDegrees, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians) {
		Vector4f worldForward = getWorldForwardVector(viewYawDegrees, viewPitchDegrees);
		Matrix4f inverseBaseHeadMatrix = new Matrix4f(createCarriedDefaultViewBaseMatrix(
				carrier,
				carried,
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
		return clampCarriedWorldView(carrier, null, tickProgress, viewYawDegrees, viewPitchDegrees, baseHeadYawRadians, baseHeadPitchRadians, baseHeadRollRadians);
	}

	public static WorldViewRotation clampCarriedWorldView(Entity carrier, Entity carried, float tickProgress, float viewYawDegrees, float viewPitchDegrees, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians) {
		HeadViewOffset offset = getCarriedHeadViewOffsetForWorldView(
				carrier,
				carried,
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
		Vector4f worldForward = createCarriedDefaultViewBaseMatrix(
				carrier,
				carried,
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
		return getCarriedHeadModelRotationForWorldView(carrier, null, tickProgress, viewYawDegrees, viewPitchDegrees);
	}

	public static HeadModelRotation getCarriedHeadModelRotationForWorldView(Entity carrier, Entity carried, float tickProgress, float viewYawDegrees, float viewPitchDegrees) {
		Vector4f worldForward = getWorldForwardVector(viewYawDegrees, viewPitchDegrees);

		Matrix4f inverseHeadParentMatrix = new Matrix4f(createCarriedRenderedHeadParentMatrix(carrier, carried, tickProgress).peek().getPositionMatrix()).invert();
		Vector4f localForward4 = inverseHeadParentMatrix.transform(worldForward);
		Vector3f localForward = new Vector3f(localForward4.x, localForward4.y, localForward4.z).normalize();
		localForward.rotateZ(-getCarriedBaseHeadRoll(carried));
		float headYawRadians = (float) Math.atan2(-localForward.x, -localForward.z);
		float localHorizontalLength = MathHelper.sqrt(localForward.x * localForward.x + localForward.z * localForward.z);
		float headPitchRadians = (float) Math.atan2(localForward.y, localHorizontalLength);
		return new HeadModelRotation(headYawRadians, headPitchRadians);
	}

	private static MatrixStack createCarriedViewMatrix(Entity carrier, Entity carried, float tickProgress, float baseHeadYawRadians, float baseHeadPitchRadians, float baseHeadRollRadians, float centerYawDegrees, float centerPitchDegrees, float localYawDegrees, float localPitchDegrees) {
		MatrixStack matrices = createCarriedDefaultViewBaseMatrix(
				carrier,
				carried,
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

	private static MatrixStack createCarriedDefaultViewBaseMatrix(Entity carrier, Entity carried, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		MatrixStack matrices = createCarriedBaseHeadMatrix(carrier, carried, tickProgress, headYawRadians, headPitchRadians, headRollRadians);
		applyDefaultViewRotation(matrices, isDragCarried(carried));
		return matrices;
	}

	private static MatrixStack createCarriedBaseHeadMatrix(Entity carrier, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		return createCarriedBaseHeadMatrix(carrier, null, tickProgress, headYawRadians, headPitchRadians, headRollRadians);
	}

	private static MatrixStack createCarriedBaseHeadMatrix(Entity carrier, Entity carried, float tickProgress, float headYawRadians, float headPitchRadians, float headRollRadians) {
		MatrixStack matrices = createCarriedHeadParentMatrix(carrier, carried, tickProgress);
		applyModelPartRotation(matrices, headPitchRadians, headYawRadians, headRollRadians);
		return matrices;
	}

	private static void applyModelPartRotation(MatrixStack matrices, float pitchRadians, float yawRadians, float rollRadians) {
		if (pitchRadians == 0.0F && yawRadians == 0.0F && rollRadians == 0.0F) {
			return;
		}
		matrices.multiply(new Quaternionf().rotationZYX(rollRadians, yawRadians, pitchRadians));
	}

	private static MatrixStack createCarriedHeadParentMatrix(Entity carrier, float tickProgress) {
		return createCarriedHeadParentMatrix(carrier, null, tickProgress);
	}

	private static MatrixStack createCarriedHeadParentMatrix(Entity carrier, Entity carried, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices, carrier, carried);
		applyCarriedModelHorizontalTransform(matrices, 1.0F, isDragCarried(carried));
		return matrices;
	}

	private static MatrixStack createCarriedRenderedHeadParentMatrix(Entity carrier, Entity carried, float tickProgress) {
		MatrixStack matrices = createCarrierWorldMatrix(carrier, tickProgress);
		applyAttachedTransform(matrices, carrier, carried);
		float baseScale = getEntityRenderBaseScale(carried);
		applyCarriedPlayerRenderModelTransform(matrices, baseScale);
		applyCarriedModelHorizontalTransform(matrices, baseScale, isDragCarried(carried));
		return matrices;
	}

	public static void applyCarriedPlayerRenderModelTransform(MatrixStack matrices, float baseScale) {
		matrices.scale(baseScale, baseScale, baseScale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.translate(0.0F, -1.501F, 0.0F);
	}

	private static float getEntityRenderBaseScale(Entity entity) {
		if (entity instanceof LivingEntity living) {
			return living.getScale();
		}
		return 1.0F;
	}

	public static MatrixStack createAttachedViewMatrix(Entity carrier, float tickProgress, Vec3d cameraPos, Matrix4f positionMatrix) {
		return createAttachedViewMatrix(carrier, null, tickProgress, cameraPos, positionMatrix);
	}

	public static MatrixStack createAttachedViewMatrix(Entity carrier, Entity carried, float tickProgress, Vec3d cameraPos, Matrix4f positionMatrix) {
		Vec3d carrierPos = carrier.getLerpedPos(tickProgress);
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(positionMatrix);
		matrices.translate(carrierPos.x - cameraPos.x, carrierPos.y - cameraPos.y, carrierPos.z - cameraPos.z);
		applyCarrierYaw(matrices, carrier, tickProgress);
		applyAttachedTransform(matrices, carrier, carried);
		return matrices;
	}

	public static void applyCarriedModelHorizontalTransform(MatrixStack matrices, float baseScale) {
		applyCarriedModelHorizontalTransform(matrices, baseScale, false);
	}

	public static void applyCarriedModelHorizontalTransform(MatrixStack matrices, float baseScale, boolean dragPose) {
		float pivotY = CARRIED_MODEL_PIVOT_Y / baseScale;
		matrices.translate(0.0F, pivotY, 0.0F);
		if (dragPose) {
			applyDragCarriedModelRotation(matrices);
		} else {
			applyCarriedModelRotation(matrices);
		}
		matrices.translate(0.0F, -pivotY, 0.0F);
	}

	private static void applyCarriedPoseAdjustment(MatrixStack matrices, boolean dragPose) {
		float offsetX = CarryPoseTuning.getCarriedPoseOffsetX(dragPose);
		float offsetY = CarryPoseTuning.getCarriedPoseOffsetY(dragPose);
		float offsetZ = CarryPoseTuning.getCarriedPoseOffsetZ(dragPose);
		float yawDegrees = CarryPoseTuning.getCarriedPoseYawDegrees(dragPose);
		float pitchDegrees = CarryPoseTuning.getCarriedPosePitchDegrees(dragPose);
		float rollDegrees = CarryPoseTuning.getCarriedPoseRollDegrees(dragPose);
		if (offsetX != 0.0F || offsetY != 0.0F || offsetZ != 0.0F) {
			matrices.translate(offsetX, offsetY, offsetZ);
		}
		if (yawDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
		}
		if (pitchDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDegrees));
		}
		if (rollDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDegrees));
		}
	}

	private static void applyDefaultViewRotation(MatrixStack matrices, boolean dragPose) {
		if (!CarryPoseTuning.isDefaultViewTransformEnabled(dragPose)) {
			return;
		}
		float yawDegrees = CarryPoseTuning.getDefaultViewYawDegrees(dragPose);
		float pitchDegrees = CarryPoseTuning.getDefaultViewPitchDegrees(dragPose);
		float rollDegrees = CarryPoseTuning.getDefaultViewRollDegrees(dragPose);
		if (yawDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
		}
		if (pitchDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDegrees));
		}
		if (rollDegrees != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDegrees));
		}
	}

	private static float getCameraLocalX(boolean dragPose) {
		return CARRIED_CAMERA_HEAD_LOCAL_X + (CarryPoseTuning.isDefaultViewTransformEnabled(dragPose) ? CarryPoseTuning.getDefaultViewOffsetX(dragPose) : 0.0F);
	}

	private static float getCameraLocalY(boolean dragPose) {
		return CARRIED_CAMERA_HEAD_LOCAL_Y + (CarryPoseTuning.isDefaultViewTransformEnabled(dragPose) ? CarryPoseTuning.getDefaultViewOffsetY(dragPose) : 0.0F);
	}

	private static float getCameraLocalZ(boolean dragPose) {
		return CARRIED_CAMERA_HEAD_LOCAL_Z + (CarryPoseTuning.isDefaultViewTransformEnabled(dragPose) ? CarryPoseTuning.getDefaultViewOffsetZ(dragPose) : 0.0F);
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

	public static void applyDragCarriedModelRotation(MatrixStack matrices) {
		if (DRAG_CARRIED_MODEL_ROTATION_Y_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(DRAG_CARRIED_MODEL_ROTATION_Y_DEGREES));
		}
		if (DRAG_CARRIED_MODEL_ROTATION_X_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(DRAG_CARRIED_MODEL_ROTATION_X_DEGREES));
		}
		if (DRAG_CARRIED_MODEL_ROTATION_Z_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(DRAG_CARRIED_MODEL_ROTATION_Z_DEGREES));
		}
		if (DRAG_CARRIED_MODEL_FACE_AXIS_FIX_X_DEGREES != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(DRAG_CARRIED_MODEL_FACE_AXIS_FIX_X_DEGREES));
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
				-MathHelper.sin(yawRadians) * horizontalLength,
				-MathHelper.sin(pitchRadians),
				-MathHelper.cos(yawRadians) * horizontalLength,
				0.0F
		);
	}

	private static Vector3f transformLocalDirection(MatrixStack matrices, float localX, float localY, float localZ) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Vector4f direction = matrix.transform(new Vector4f(localX, localY, localZ, 0.0F));
		return new Vector3f(direction.x, direction.y, direction.z).normalize();
	}

	private static boolean isDragCarried(Entity carried) {
		return carried != null && CarryPoseClientState.isDragCarried(carried.getId());
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
