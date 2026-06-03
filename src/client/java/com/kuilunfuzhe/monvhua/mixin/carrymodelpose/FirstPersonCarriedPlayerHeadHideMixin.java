package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public abstract class FirstPersonCarriedPlayerHeadHideMixin {
	@Unique
	private boolean monvhua$hiddenFirstPersonCarriedHead;
	@Unique
	private boolean monvhua$previousHeadVisible;
	@Unique
	private boolean monvhua$previousHatVisible;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"))
	private void monvhua$hideFirstPersonCarriedHead(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (!CarryAttachmentRenderState.isRenderingFirstPersonSelfCarriedEntity() || !((Object) this instanceof PlayerEntityModel playerModel)) {
			return;
		}

		monvhua$hiddenFirstPersonCarriedHead = true;
		monvhua$previousHeadVisible = playerModel.head.visible;
		monvhua$previousHatVisible = playerModel.hat.visible;
		playerModel.head.visible = false;
		playerModel.hat.visible = false;
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("RETURN"))
	private void monvhua$restoreFirstPersonCarriedHead(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (!monvhua$hiddenFirstPersonCarriedHead || !((Object) this instanceof PlayerEntityModel playerModel)) {
			return;
		}

		playerModel.head.visible = monvhua$previousHeadVisible;
		playerModel.hat.visible = monvhua$previousHatVisible;
		monvhua$hiddenFirstPersonCarriedHead = false;
	}
}
