package com.kuilunfuzhe.monvhua.mixin.multiple_perspectives;

import com.kuilunfuzhe.monvhua.features.evil_eyes.ClairvoyanceViewportRenderer;
import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorViewportRenderer;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererHookMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void monvhua$preparePortalPerspective(
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
		if (FramebufferOverride.getOverride() != null || PortalFramebufferOverride.get() != null) {
			return;
		}
		PortalFramebufferRenderer.renderNearestPortal(
			tickCounter,
			camera,
			projectionMatrix
		);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void monvhua$renderMirrorPerspective(
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
		if (FramebufferOverride.getOverride() != null || PortalFramebufferOverride.get() != null) {
			return;
		}
		MirrorViewportRenderer.renderFullScreenMirror(tickCounter, fog, fogColor, camera, positionMatrix, projectionMatrix);
		if (ClairvoyanceViewportRenderer.shouldRenderPreviewWorld()) {
			ClairvoyanceViewportRenderer.renderPreviewWorld(tickCounter, fog, fogColor, camera, positionMatrix, projectionMatrix);
		}
	}
}
