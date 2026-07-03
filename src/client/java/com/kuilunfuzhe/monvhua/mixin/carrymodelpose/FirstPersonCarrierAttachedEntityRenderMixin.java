package com.kuilunfuzhe.monvhua.mixin.carrymodelpose;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachedRenderMath;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryAttachmentRenderState;
import com.kuilunfuzhe.monvhua.features.carryentity.CarriedPlayerPoseRenderer;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryDragSkeletalRenderer;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class FirstPersonCarrierAttachedEntityRenderMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void monvhua$renderAttachedCarriedEntities(
			ObjectAllocator allocator,
			RenderTickCounter tickCounter,
			boolean renderBlockOutline,
			Camera camera,
			Matrix4f positionMatrix,
			Matrix4f projectionMatrix,
			GpuBufferSlice fog,
			Vector4f fogColor,
			boolean shouldRenderSky,
			CallbackInfo ci
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		float tickProgress = tickCounter.getTickProgress(true);
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

		for (Entity carrier : client.world.getEntities()) {
			monvhua$renderAttachedCarriedEntity(client, dispatcher, vertexConsumers, carrier, tickProgress, camera, positionMatrix);
		}

		vertexConsumers.draw();
	}

	private static void monvhua$renderAttachedCarriedEntity(MinecraftClient client, EntityRenderDispatcher dispatcher, VertexConsumerProvider.Immediate vertexConsumers, Entity carrier, float tickProgress, Camera camera, Matrix4f positionMatrix) {
		if (!monvhua$shouldRenderInWorldTail(client, carrier)) {
			return;
		}
		if (!CarryPoseClientState.isCarrier(carrier.getId())) {
			return;
		}

		int carriedId = CarryPoseClientState.getPartnerId(carrier.getId());
		if (carriedId < 0) {
			return;
		}

		Entity carried = client.world.getEntityById(carriedId);
		if (carried == null) {
			return;
		}

		boolean firstPersonSelfCarried = client.player == carried && client.options.getPerspective().isFirstPerson();

		MatrixStack matrices = CarryAttachedRenderMath.createAttachedViewMatrix(carrier, carried, tickProgress, camera.getPos(), positionMatrix);
		int light = WorldRenderer.getLightmapCoordinates(client.world, carried.getBlockPos());

		if (firstPersonSelfCarried) {
			CarryAttachmentRenderState.beginFirstPersonSelfCarriedEntityRender(carried.getId());
		} else {
			CarryAttachmentRenderState.beginAttachedCarriedEntityRender(carried.getId());
		}
		try {
			if (!CarryDragSkeletalRenderer.renderIfDragCarried(carried, matrices, vertexConsumers, light, firstPersonSelfCarried)
					&& !CarriedPlayerPoseRenderer.render(carried, matrices, vertexConsumers, light, firstPersonSelfCarried)) {
				dispatcher.render(carried, 0.0D, 0.0D, 0.0D, tickProgress, matrices, vertexConsumers, light);
			}
		} finally {
			if (firstPersonSelfCarried) {
				CarryAttachmentRenderState.endFirstPersonSelfCarriedEntityRender();
			} else {
				CarryAttachmentRenderState.endAttachedCarriedEntityRender();
			}
		}
	}

	private static boolean monvhua$shouldRenderInWorldTail(MinecraftClient client, Entity carrier) {
		if (client.player == null) {
			return false;
		}
		return CarryPoseClientState.isCarrier(carrier.getId());
	}
}
