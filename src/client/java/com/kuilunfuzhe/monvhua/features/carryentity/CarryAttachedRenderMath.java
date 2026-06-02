package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CarryAttachedRenderMath {
	public static final float ATTACHED_CARRIED_X = 0.0F;
	public static final float ATTACHED_CARRIED_Y = 1.45F;
	public static final float ATTACHED_CARRIED_Z = -0.65F;
	public static final float ATTACHED_CARRIED_YAW_DEGREES = 180.0F;

	private static final float CARRIED_CAMERA_HEAD_LOCAL_X = 0.0F;
	private static final float CARRIED_CAMERA_HEAD_LOCAL_Y = 0.15F;
	private static final float CARRIED_CAMERA_HEAD_LOCAL_Z = -0.3F;

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
