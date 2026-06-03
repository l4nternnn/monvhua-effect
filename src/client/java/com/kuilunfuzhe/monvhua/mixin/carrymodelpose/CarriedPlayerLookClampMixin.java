package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarriedPlayerViewState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
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
	@Unique
	private static boolean monvhua$capturedCarriedLook;
	@Unique
	private static float monvhua$capturedYaw;
	@Unique
	private static float monvhua$capturedPitch;

	@Inject(method = "changeLookDirection", at = @At("HEAD"))
	private void monvhua$captureCarriedPlayerLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		monvhua$capturedCarriedLook = monvhua$isLocalCarriedPlayer(self);
		if (!monvhua$capturedCarriedLook) {
			return;
		}

		monvhua$capturedYaw = self.getYaw();
		monvhua$capturedPitch = self.getPitch();
	}

	@Inject(method = "changeLookDirection", at = @At("RETURN"))
	private void monvhua$applyCarriedPlayerLocalLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!monvhua$capturedCarriedLook || !monvhua$isLocalCarriedPlayer(self)) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Entity carrier = monvhua$getCarrier(client, self);
		if (carrier == null) {
			return;
		}

		float yawDelta = MathHelper.wrapDegrees(self.getYaw() - monvhua$capturedYaw);
		float pitchDelta = self.getPitch() - monvhua$capturedPitch;
		float tickProgress = client.getRenderTickCounter().getTickProgress(true);
		CarriedPlayerViewState.changeLocalViewRotation(self, carrier, tickProgress, yawDelta, pitchDelta);
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
