package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CarryAttachedRenderMath {
	// 被抱者整体挂载点的左右偏移；正值通常偏向抱人者的左/右侧之一，负值相反，需要按游戏内效果微调。
	public static final float ATTACHED_CARRIED_X = 0.5F;
	// 被抱者整体挂载点的高度；增大 = 被抱者整体更高，减小 = 更低。
	public static final float ATTACHED_CARRIED_Y = -0.1F;
	// 被抱者整体挂载点的前后偏移；增大/减小会让被抱者更靠近或远离抱人者胸前，方向以游戏内效果为准。
	public static final float ATTACHED_CARRIED_Z = -0.4F;
	// 被抱者整体绕竖直 Y 轴的朝向角度，单位：度；常用 0、90、180、-90 来修正面朝方向/头脚方向。
	public static final float ATTACHED_CARRIED_YAW_DEGREES = 180.0F;
	// 被抱者模型横躺旋转的枢轴高度；影响身体绕哪里躺倒，调错会导致头/脚绕奇怪位置甩动。
	public static final float CARRIED_MODEL_PIVOT_Y = 0.0F;
	// 被抱者模型绕 X 轴的横躺角度，单位：度；主要控制躺下/仰起/趴下，-90 通常表示水平横躺。
	public static final float CARRIED_MODEL_ROTATION_X_DEGREES = -45.0F;
	// 被抱者模型绕 Y 轴的补偿旋转，单位：度；用于微调身体在抱人者怀里的左右朝向。
	public static final float CARRIED_MODEL_ROTATION_Y_DEGREES = -90.0F;
	// 被抱者模型绕 Z 轴的翻滚角度，单位：度；用于让身体向左/右侧倾斜。
	public static final float CARRIED_MODEL_ROTATION_Z_DEGREES = 0.0F;

	// 沿被抱者“头 -> 脚 / 脚 -> 头”身体长轴的摄像机偏移，单位：方块；用于把第一人称原点推到头部附近。
	// 注意：这里不直接放到局部 Y，因为当前渲染矩阵里局部 Y 的移动轨迹会垂直于身体长轴。
	private static final float PLAYER_EYE_HEIGHT = 1.25F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的左右偏移；正负方向按游戏内效果微调。
	private static final float CARRIED_CAMERA_HEAD_LOCAL_X = 0.0F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的身体厚度/贴脸高度偏移；不是头脚方向，通常只做小幅微调。
	private static final float CARRIED_CAMERA_HEAD_LOCAL_Y = 1.25F;
	// 被抱者第一人称摄像机在“被抱者模型自身局部坐标”里的头脚方向偏移；调 PLAYER_EYE_HEIGHT 时主要沿身体长轴移动。
	private static final float CARRIED_CAMERA_HEAD_LOCAL_Z = PLAYER_EYE_HEIGHT;

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

	public static void applyCarrierYaw(MatrixStack matrices, Entity carrier, float tickProgress) {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - carrier.getLerpedYaw(tickProgress)));
	}

	public static Vec3d transformLocalPoint(MatrixStack matrices, float localX, float localY, float localZ) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Vector3f point = matrix.transformPosition(localX, localY, localZ, new Vector3f());
		return new Vec3d(point.x, point.y, point.z);
	}
}
