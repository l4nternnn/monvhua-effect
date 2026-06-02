package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public final class CarriedPlayerViewState {
	private static int carriedEntityId = -1;
	private static int carrierEntityId = -1;
	private static boolean initialized;
	private static float headYawRadians;
	private static float headPitchRadians;

	private CarriedPlayerViewState() {
	}

	/**
	 * Solves the model-part head yaw/pitch that makes the carried model head face the player's real camera direction.
	 *
	 * <p>The camera/world view direction is transformed back into the carried model head parent's local space first.
	 * This is different from directly copying player yaw/pitch, because the carried body is already translated and
	 * rotated into a lying/hug pose before the head part rotation is applied.</p>
	 */
	public static void updateHeadViewRotation(Entity carried, Entity carrier, float tickProgress) {
		CarryAttachedRenderMath.HeadModelRotation solvedRotation = CarryAttachedRenderMath.getCarriedHeadModelRotationForWorldView(
				carrier,
				tickProgress,
				carried.getYaw(),
				carried.getPitch()
		);
		float baseHeadYaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		float baseHeadPitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		float yawOffset = MathHelper.clamp(
				MathHelper.wrapDegrees((float) Math.toDegrees(solvedRotation.yawRadians() - baseHeadYaw)),
				-CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES
		);
		float pitchOffset = MathHelper.clamp(
				(float) Math.toDegrees(solvedRotation.pitchRadians() - baseHeadPitch),
				-CarryPoseTuning.CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES
		);

		initialized = true;
		carriedEntityId = carried.getId();
		carrierEntityId = carrier.getId();
		headYawRadians = baseHeadYaw + (float) Math.toRadians(yawOffset * CarryPoseTuning.CARRIED_HEAD_VIEW_YAW_SCALE);
		headPitchRadians = baseHeadPitch + (float) Math.toRadians(pitchOffset * CarryPoseTuning.CARRIED_HEAD_VIEW_PITCH_SCALE);
	}

	public static boolean shouldApplyHeadViewRotation(int entityId) {
		return initialized && carriedEntityId == entityId;
	}

	public static float getHeadYawRadians() {
		return headYawRadians;
	}

	public static float getHeadPitchRadians() {
		return headPitchRadians;
	}

	public static void resetIfCarriedEntity(int entityId) {
		if (initialized && carriedEntityId == entityId) {
			reset();
		}
	}

	public static void reset() {
		initialized = false;
		carriedEntityId = -1;
		carrierEntityId = -1;
		headYawRadians = 0.0F;
		headPitchRadians = 0.0F;
	}
}
