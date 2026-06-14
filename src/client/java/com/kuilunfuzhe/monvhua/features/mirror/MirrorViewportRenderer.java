package com.kuilunfuzhe.monvhua.features.mirror;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.kuilunfuzhe.monvhua.compat.DhCompat;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.concurrent.atomic.AtomicBoolean;

public class MirrorViewportRenderer {
	private static final AtomicBoolean renderingMirror = new AtomicBoolean(false);
	private static SimpleFramebuffer fullMirrorFbo;

	/**
	 * 渲染镜子视角到全屏 FBO（由 WorldRenderer HEAD mixin 调用）
	 */
	public static void renderFullScreenMirror(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		if (!MirrorClientManager.isActive()) return;
		if (renderingMirror.getAndSet(true)) return;

		try {
			int fbw = client.getFramebuffer().textureWidth;
			int fbh = client.getFramebuffer().textureHeight;
			if (fbw <= 0 || fbh <= 0) return;

			fullMirrorFbo = resizeFbo(fullMirrorFbo, "mirror_full", fbw, fbh);

			float yaw = mainCamera.getYaw();
			float pitch = mainCamera.getPitch();
			Vec3d pos = MirrorClientManager.getActiveSlotCameraPos(mainCamera.getPos());
			if (pos == null) return;

			RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				fullMirrorFbo.getColorAttachment(),
				0xFF000000,
				fullMirrorFbo.getDepthAttachment(),
				1.0
			);

			GpuTextureView previousColorOutput = RenderSystem.outputColorTextureOverride;
			GpuTextureView previousDepthOutput = RenderSystem.outputDepthTextureOverride;
			GpuBufferSlice previousFog = RenderSystem.getShaderFog();
			RenderSystem.backupProjectionMatrix();
			DhCompat.suspend();
			IrisMirrorCompat.beginMirrorRender();
			FramebufferOverride.setOverride(fullMirrorFbo);
			RenderSystem.outputColorTextureOverride = fullMirrorFbo.getColorAttachmentView();
			RenderSystem.outputDepthTextureOverride = fullMirrorFbo.getDepthAttachmentView();
			try {
				Camera cam = new Camera();
				cam.update(client.world, client.player, false, false, tickCounter.getTickProgress(false));
				CameraAccessor acc = (CameraAccessor) cam;
				acc.invokeSetPos(pos.x, pos.y, pos.z);
				acc.invokeSetRotation(yaw, pitch);

				Matrix4f view = new Matrix4f(positionMatrix);
				Matrix4f projection = new Matrix4f(projectionMatrix);

				client.worldRenderer.setupFrustum(pos, view, projection);
				client.worldRenderer.render(
					ObjectAllocator.TRIVIAL, tickCounter, false,
					cam, view, projection, fog, fogColor, true
				);
			} finally {
				client.worldRenderer.setupFrustum(mainCamera.getPos(), new Matrix4f(positionMatrix), new Matrix4f(projectionMatrix));
				RenderSystem.restoreProjectionMatrix();
				RenderSystem.setShaderFog(previousFog);
				RenderSystem.outputColorTextureOverride = previousColorOutput;
				RenderSystem.outputDepthTextureOverride = previousDepthOutput;
				FramebufferOverride.clearOverride();
				IrisMirrorCompat.endMirrorRender();
				DhCompat.resume();
			}
		} finally {
			renderingMirror.set(false);
		}
	}

	/**
	 * 对角线合成：将镜子 FBO 的内容拷贝到主 framebuffer 的左下三角区域。
	 * 对角线从屏幕左下到右上，左边 = 镜子视角，右边 = 主视角。
	 * 由 HUD overlay 在 main world render 完成后调用。
	 */
	public static void renderDiagonalSplit(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (fullMirrorFbo == null) return;
		if (!MirrorClientManager.isActive()) return;

		var mainFb = client.getFramebuffer();
		int fbw = mainFb.textureWidth;
		int fbh = mainFb.textureHeight;

		if (fullMirrorFbo.textureWidth != fbw || fullMirrorFbo.textureHeight != fbh) return;

		MirrorFramebufferCompositor.renderDiagonalSplit(fullMirrorFbo, mainFb);
	}

	private static SimpleFramebuffer resizeFbo(SimpleFramebuffer fbo, String name, int w, int h) {
		if (fbo == null || fbo.textureWidth != w || fbo.textureHeight != h) {
			if (fbo != null) fbo.delete();
			return new SimpleFramebuffer(name, w, h, true);
		}
		return fbo;
	}

	public static void cleanup() {
		if (fullMirrorFbo != null) {
			fullMirrorFbo.delete();
			fullMirrorFbo = null;
		}
		MirrorFramebufferCompositor.cleanup();
	}
}
