package com.kuilunfuzhe.monvhua.mixin.compat.emf;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseModelApplier;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart", remap = false, priority = 2000)
public abstract class EmfModelPartCarryPoseMixin {
	@Inject(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
			at = @At("HEAD"),
			require = 0,
			remap = false
	)
	private void monvhua$reapplyCarryPoseBeforeEmfPartRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (CarryPoseModelApplier.isRenderingCarriedPose()) {
			CarryPoseModelApplier.applyCurrentRenderContextBeforePartRender((ModelPart) (Object) this);
		}
	}

	@Inject(
			method = "method_22699(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V",
			at = @At("HEAD"),
			require = 0,
			remap = false
	)
	private void monvhua$reapplyCarryPoseBeforeEmfPartRenderIntermediary(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (CarryPoseModelApplier.isRenderingCarriedPose()) {
			CarryPoseModelApplier.applyCurrentRenderContextBeforePartRender((ModelPart) (Object) this);
		}
	}
}
