package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class CarrierAttachedEntityRenderMixin {

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
	private void monvhua$renderAttachedCarriedEntity(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!(state instanceof PlayerEntityRenderState playerState) || CarryAttachmentRenderState.isRenderingAttachedCarriedEntity() || !CarryPoseClientState.isCarrier(playerState.id)) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		int carriedId = CarryPoseClientState.getCarrierId(playerState.id);
		if (carriedId < 0) {
			return;
		}

		Entity carried = client.world.getEntityById(carriedId);
		if (carried == null) {
			return;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(true);
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

		matrices.push();
		CarryAttachedRenderMath.applyAttachedTransform(matrices, state.baseScale, carried);

		CarryAttachmentRenderState.beginAttachedCarriedEntityRender();
		try {
			dispatcher.render(carried, 0.0D, 0.0D, 0.0D, tickProgress, matrices, vertexConsumers, light);
		} finally {
			CarryAttachmentRenderState.endAttachedCarriedEntityRender();
			matrices.pop();
		}
	}
}
