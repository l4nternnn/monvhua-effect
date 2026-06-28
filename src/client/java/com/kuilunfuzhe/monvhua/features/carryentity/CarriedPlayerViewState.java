package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public final class CarriedPlayerViewState {
	private static int localCarriedEntityId = -1;
	private static int localCarrierEntityId = -1;
	private static boolean localInitialized;
	private static float localViewYawDegrees;
	private static float localViewPitchDegrees;

	private static int headCarriedEntityId = -1;
	private static boolean headInitialized;
	private static float headYawRadians;
	private static float headPitchRadians;

	private CarriedPlayerViewState() {
	}

	/**
	 * Updates the carried player's local view offsets and writes the equivalent world yaw/pitch back to the entity.
	 *
	 * <p>The camera starts from the carried model's full base head orientation, including roll, then applies the
	 * configured view center and finally these local yaw/pitch offsets. The stored local offsets are kept in the same
	 * domain for both the model head and the carried camera path, so the visual head and actual ray stay synchronized.</p>
	 */
	public static void updateLocalViewRotation(Entity carried, Entity carrier, float tickProgress) {
		ensureLocalInitialized(carried, carrier);
		if (CarryPoseTuning.CARRIED_FIXED_VIEW_ENABLED) {
			localViewYawDegrees = CarryPoseTuning.CARRIED_FIXED_VIEW_LOCAL_YAW_DEGREES;
			localViewPitchDegrees = CarryPoseTuning.CARRIED_FIXED_VIEW_LOCAL_PITCH_DEGREES;
			applyLocalViewRotation(carried, carrier, tickProgress);
			return;
		}
		localViewYawDegrees = MathHelper.clamp(
				localViewYawDegrees,
				-CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES
		);
		localViewPitchDegrees = MathHelper.clamp(
				localViewPitchDegrees,
				-CarryPoseTuning.CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES
		);
		applyLocalViewRotation(carried, carrier, tickProgress);
	}

	public static void changeLocalViewRotation(Entity carried, Entity carrier, float tickProgress, float yawDeltaDegrees, float pitchDeltaDegrees) {
		ensureLocalInitialized(carried, carrier);
		if (CarryPoseTuning.CARRIED_FIXED_VIEW_ENABLED) {
			localViewYawDegrees = CarryPoseTuning.CARRIED_FIXED_VIEW_LOCAL_YAW_DEGREES;
			localViewPitchDegrees = CarryPoseTuning.CARRIED_FIXED_VIEW_LOCAL_PITCH_DEGREES;
			applyLocalViewRotation(carried, carrier, tickProgress);
			return;
		}
		localViewYawDegrees = MathHelper.clamp(
				localViewYawDegrees - yawDeltaDegrees,
				-CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES
		);
		localViewPitchDegrees = MathHelper.clamp(
				localViewPitchDegrees - pitchDeltaDegrees,
				-CarryPoseTuning.CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES
		);
		applyLocalViewRotation(carried, carrier, tickProgress);
	}

	public static void updateHeadViewRotationFromWorldView(Entity carried, Entity carrier, float tickProgress) {
		if (CarryPoseTuning.CARRIED_FIXED_VIEW_ENABLED) {
			clearHeadViewRotation(carried.getId());
			return;
		}
		if (localInitialized && localCarriedEntityId == carried.getId() && localCarrierEntityId == carrier.getId()) {
			updateHeadViewRotation(carried.getId(), localViewYawDegrees, localViewPitchDegrees);
			return;
		}
		updateHeadViewRotationFromWorldView(carried.getId(), carrier, carried, tickProgress, carried.getYaw(), carried.getPitch());
	}

	private static void ensureLocalInitialized(Entity carried, Entity carrier) {
		if (localInitialized && localCarriedEntityId == carried.getId() && localCarrierEntityId == carrier.getId()) {
			return;
		}

		localInitialized = true;
		localCarriedEntityId = carried.getId();
		localCarrierEntityId = carrier.getId();
		localViewYawDegrees = 0.0F;
		localViewPitchDegrees = 0.0F;
	}

	private static void applyLocalViewRotation(Entity carried, Entity carrier, float tickProgress) {
		CarryAttachedRenderMath.WorldViewRotation worldRotation = CarryAttachedRenderMath.getCarriedLocalViewWorldRotation(
				carrier,
				carried,
				tickProgress,
				localViewYawDegrees,
				localViewPitchDegrees
		);
		setEntityViewRotation(carried, worldRotation.yawDegrees(), worldRotation.pitchDegrees());
		if (CarryPoseTuning.CARRIED_FIXED_VIEW_ENABLED) {
			clearHeadViewRotation(carried.getId());
			return;
		}
		updateHeadViewRotation(carried.getId(), localViewYawDegrees, localViewPitchDegrees);
	}

	private static void clearHeadViewRotation(int entityId) {
		if (headInitialized && headCarriedEntityId == entityId) {
			headInitialized = false;
			headCarriedEntityId = -1;
			headYawRadians = 0.0F;
			headPitchRadians = 0.0F;
		}
	}

	private static void setEntityViewRotation(Entity carried, float yawDegrees, float pitchDegrees) {
		carried.setYaw(yawDegrees);
		carried.setPitch(pitchDegrees);
		carried.lastYaw = yawDegrees;
		carried.lastPitch = pitchDegrees;
	}

	private static void updateHeadViewRotationFromWorldView(int entityId, Entity carrier, Entity carried, float tickProgress, float yawDegrees, float pitchDegrees) {
		CarryAttachedRenderMath.HeadModelRotation headRotation = CarryAttachedRenderMath.getCarriedHeadModelRotationForWorldView(
				carrier,
				carried,
				tickProgress,
				yawDegrees,
				pitchDegrees
		);
		headInitialized = true;
		headCarriedEntityId = entityId;
		headYawRadians = headRotation.yawRadians();
		headPitchRadians = headRotation.pitchRadians();
	}

	private static void updateHeadViewRotation(int entityId, float headLocalYawDegrees, float headLocalPitchDegrees) {
		float baseHeadYaw = CarryPoseClientState.isDragCarried(entityId)
				? CarryPoseTuning.DRAG_HEAD_YAW
				: CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		float baseHeadPitch = CarryPoseClientState.isDragCarried(entityId)
				? CarryPoseTuning.DRAG_HEAD_PITCH
				: CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;

		headInitialized = true;
		headCarriedEntityId = entityId;
		headYawRadians = baseHeadYaw + (float) Math.toRadians(headLocalYawDegrees * CarryPoseTuning.CARRIED_HEAD_VIEW_YAW_SCALE * CarryPoseTuning.CARRIED_HEAD_VIEW_YAW_DIRECTION);
		headPitchRadians = baseHeadPitch + (float) Math.toRadians(headLocalPitchDegrees * CarryPoseTuning.CARRIED_HEAD_VIEW_PITCH_SCALE * CarryPoseTuning.CARRIED_HEAD_VIEW_PITCH_DIRECTION);
	}

	public static boolean shouldApplyHeadViewRotation(int entityId) {
		return headInitialized && headCarriedEntityId == entityId;
	}

	public static float getHeadYawRadians() {
		return headYawRadians;
	}

	public static float getHeadPitchRadians() {
		return headPitchRadians;
	}

	public static float getLocalViewYawDegrees() {
		return localViewYawDegrees;
	}

	public static float getLocalViewPitchDegrees() {
		return localViewPitchDegrees;
	}

	public static void resetIfCarriedEntity(int entityId) {
		if (localInitialized && localCarriedEntityId == entityId) {
			localInitialized = false;
			localCarriedEntityId = -1;
			localCarrierEntityId = -1;
			localViewYawDegrees = 0.0F;
			localViewPitchDegrees = 0.0F;
		}
		if (headInitialized && headCarriedEntityId == entityId) {
			headInitialized = false;
			headCarriedEntityId = -1;
			headYawRadians = 0.0F;
			headPitchRadians = 0.0F;
		}
	}

	public static void reset() {
		localInitialized = false;
		localCarriedEntityId = -1;
		localCarrierEntityId = -1;
		localViewYawDegrees = 0.0F;
		localViewPitchDegrees = 0.0F;
		headInitialized = false;
		headCarriedEntityId = -1;
		headYawRadians = 0.0F;
		headPitchRadians = 0.0F;
	}
}
