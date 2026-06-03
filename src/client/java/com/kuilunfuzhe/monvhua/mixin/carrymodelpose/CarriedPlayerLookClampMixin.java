package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseTuning;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CarriedPlayerLookClampMixin {
	@Inject(method = "changeLookDirection", at = @At("RETURN"))
	private void monvhua$clampCarriedPlayerLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!monvhua$isLocalCarriedPlayer(self)) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Entity carrier = monvhua$getCarrier(client, self);
		if (carrier == null) {
			return;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(true);
		float baseHeadYaw = CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW;
		float baseHeadPitch = CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH;
		float baseHeadRoll = CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL;
		CarryAttachedRenderMath.CarriedHeadWorldRotation baseRotation = CarryAttachedRenderMath.getCarriedHeadWorldRotation(
				carrier,
				tickProgress,
				baseHeadYaw,
				baseHeadPitch,
				baseHeadRoll
		);

		float centerYawDegrees = baseRotation.yawDegrees() + CarryPoseTuning.CARRIED_VIEW_CENTER_YAW_OFFSET_DEGREES;
		float centerPitchDegrees = baseRotation.pitchDegrees() + CarryPoseTuning.CARRIED_VIEW_CENTER_PITCH_OFFSET_DEGREES;
		float yawOffset = MathHelper.wrapDegrees(self.getYaw() - centerYawDegrees);
		float pitchOffset = self.getPitch() - centerPitchDegrees;
		float clampedYawOffset = MathHelper.clamp(
				yawOffset,
				-CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_YAW_LIMIT_DEGREES
		);
		float clampedPitchOffset = MathHelper.clamp(
				pitchOffset,
				-CarryPoseTuning.CARRIED_VIEW_PITCH_UP_LIMIT_DEGREES,
				CarryPoseTuning.CARRIED_VIEW_PITCH_DOWN_LIMIT_DEGREES
		);

		self.setYaw(centerYawDegrees + clampedYawOffset);
		self.setPitch(centerPitchDegrees + clampedPitchOffset);
	}

	@Unique
	private static boolean monvhua$isLocalCarriedPlayer(Entity self) {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null && client.player != null && self == client.player && CarryPoseClientState.isCarried(self.getId());
	}

	@Unique
	private static Entity monvhua$getCarrier(MinecraftClient client, Entity self) {
		if (client.world == null) {
			return null;
		}

		int carrierId = CarryPoseClientState.getPartnerId(self.getId());
		return carrierId >= 0 ? client.world.getEntityById(carrierId) : null;
	}
}
