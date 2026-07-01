package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerEntityModel.class, priority = 500)
public abstract class PlayerEntityModelCarryPoseMixin {
	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
	private void monvhua$applyCarryPose(PlayerEntityRenderState state, CallbackInfo ci) {
		PlayerEntityModel model = (PlayerEntityModel) (Object) this;
		CarryPoseModelApplier.apply(model, state);
	}
}
