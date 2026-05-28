package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class MirrorViewportRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MirrorViewportRenderer.class);
	private static final AtomicBoolean renderingMirror = new AtomicBoolean(false);
	private static final long MIRROR_UPDATE_INTERVAL_TICKS = 1L;
	private static volatile boolean disabledDueToRenderError = false;
	private static long lastMirrorRenderTick = Long.MIN_VALUE;
	private static SimpleFramebuffer fullMirrorFbo;

	public static void renderFullScreenMirror(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		if (!MirrorClientManager.isActive()) return;
		if (disabledDueToRenderError) return;
		if (renderingMirror.getAndSet(true)) return;

		try {
			MirrorClientManager.CameraData slot0 = MirrorClientManager.getSlot(0);
			if (!slot0.active()) return;

			int fbw = client.getFramebuffer().textureWidth;
			int fbh = client.getFramebuffer().textureHeight;
			if (fbw <= 0 || fbh <= 0) return;

			long worldTick = client.world.getTime();
			boolean needsRefresh = fullMirrorFbo == null
				|| fullMirrorFbo.textureWidth != fbw
				|| fullMirrorFbo.textureHeight != fbh
				|| worldTick - lastMirrorRenderTick >= MIRROR_UPDATE_INTERVAL_TICKS;
			if (!needsRefresh) return;

			fullMirrorFbo = resizeFbo(fullMirrorFbo, "mirror_full", fbw, fbh);

			float yaw = mainCamera.getYaw();
			float pitch = mainCamera.getPitch();
			Vec3d playerPos = client.player.getPos();
			Vec3d pos = MirrorClientManager.getSlotWorldPos(0, playerPos);
			if (pos == null) return;

			FramebufferOverride.setOverride(fullMirrorFbo);
			try {
				Camera cam = new Camera();
				CameraAccessor acc = (CameraAccessor) cam;
				acc.invokeSetPos(pos.x, pos.y, pos.z);
				acc.invokeSetRotation(yaw, pitch);

				float fov = client.options.getFov().getValue().floatValue();
				float aspect = (float) fbw / fbh;
				Matrix4f proj = new Matrix4f().perspective(
					fov * (float) (Math.PI / 180.0),
					aspect,
					0.05f,
					client.gameRenderer.getFarPlaneDistance()
				);

				Vec3d forward = new Vec3d(
					-Math.sin(yaw * (float) (Math.PI / 180.0)) * Math.cos(pitch * (float) (Math.PI / 180.0)),
					-Math.sin(pitch * (float) (Math.PI / 180.0)),
					Math.cos(yaw * (float) (Math.PI / 180.0)) * Math.cos(pitch * (float) (Math.PI / 180.0))
				);
				Matrix4f view = new Matrix4f().lookAt(0, 0, 0, (float) forward.x, (float) forward.y, (float) forward.z, 0, 1, 0);

				client.worldRenderer.render(
					ObjectAllocator.TRIVIAL,
					tickCounter,
					false,
					cam,
					view,
					proj,
					fog,
					fogColor,
					true
				);
				lastMirrorRenderTick = worldTick;
			} finally {
				FramebufferOverride.clearOverride();
			}
		} catch (RuntimeException e) {
			disabledDueToRenderError = true;
			cleanup();
			LOGGER.warn("Disabled mirror viewport rendering after renderer compatibility failure.", e);
		} finally {
			renderingMirror.set(false);
		}
	}

	public static void renderDiagonalSplit(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (fullMirrorFbo == null) return;
		if (!MirrorClientManager.isActive()) return;

		var mainFb = client.getFramebuffer();
		int fbw = mainFb.textureWidth;
		int fbh = mainFb.textureHeight;

		if (fullMirrorFbo.textureWidth != fbw || fullMirrorFbo.textureHeight != fbh) return;

		var encoder = RenderSystem.getDevice().createCommandEncoder();
		for (int row = 0; row < fbh; row++) {
			int width = fbw * (fbh - row) / fbh;
			if (width <= 0) continue;
			if (width > fbw) width = fbw;

			encoder.copyTextureToTexture(
				fullMirrorFbo.getColorAttachment(),
				mainFb.getColorAttachment(),
				0,
				0, row,
				0, row,
				width, 1
			);
		}
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
		lastMirrorRenderTick = Long.MIN_VALUE;
	}
}
