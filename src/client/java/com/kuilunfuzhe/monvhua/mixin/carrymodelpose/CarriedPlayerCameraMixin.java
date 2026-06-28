package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarriedPlayerViewState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CarriedPlayerCameraMixin {
	@Shadow
	protected abstract void setPos(Vec3d pos);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At("RETURN"))
	private void monvhua$moveCarriedPlayerCameraToAttachedHead(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (thirdPerson || client.world == null || client.player == null || focusedEntity != client.player || !CarryPoseClientState.isCarried(focusedEntity.getId())) {
			return;
		}

		int carrierId = CarryPoseClientState.getPartnerId(focusedEntity.getId());
		if (carrierId < 0) {
			return;
		}

		Entity carrier = client.world.getEntityById(carrierId);
		if (carrier == null) {
			return;
		}

		CarriedPlayerViewState.updateLocalViewRotation(focusedEntity, carrier, tickProgress);
		setPos(CarryAttachedRenderMath.getCarriedCameraHeadWorldPos(carrier, focusedEntity, tickProgress));
		CarryAttachedRenderMath.CarriedCameraOrientation orientation = CarryAttachedRenderMath.getCarriedLocalViewCameraOrientation(
				carrier,
				focusedEntity,
				tickProgress,
				CarriedPlayerViewState.getLocalViewYawDegrees(),
				CarriedPlayerViewState.getLocalViewPitchDegrees()
		);
		setRotation(orientation.yawDegrees(), orientation.pitchDegrees());
	}
}
