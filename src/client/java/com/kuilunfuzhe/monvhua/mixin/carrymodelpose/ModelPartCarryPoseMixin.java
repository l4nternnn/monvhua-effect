package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 2000)
public abstract class ModelPartCarryPoseMixin {
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), require = 0)
	private void monvhua$reapplyCarryPoseBeforePartRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		CarryPoseModelApplier.applyCurrentRenderContextBeforePartRender((ModelPart) (Object) this);
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V", at = @At("HEAD"), require = 0)
	private void monvhua$reapplyCarryPoseBeforePartRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, CallbackInfo ci) {
		CarryPoseModelApplier.applyCurrentRenderContextBeforePartRender((ModelPart) (Object) this);
	}
}
