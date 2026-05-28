package com.kuilunfuzhe.monvhua.features.mirror;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import net.fabricmc.loader.api.FabricLoader;
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
	private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
	private static SimpleFramebuffer fullMirrorFbo;

	/**
	 * 渲染镜子视角到全屏 FBO（由 WorldRenderer HEAD mixin 调用）
	 */
	public static void renderFullScreenMirror(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		if (!MirrorClientManager.isActive()) return;
		if (IRIS_LOADED) return;
		if (renderingMirror.getAndSet(true)) return;

		MirrorClientManager.CameraData slot0 = MirrorClientManager.getSlot(0);
		if (!slot0.active()) {
			renderingMirror.set(false);
			return;
		}

		try {
			int fbw = client.getFramebuffer().textureWidth;
			int fbh = client.getFramebuffer().textureHeight;
			if (fbw <= 0 || fbh <= 0) return;

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
					fov * (float) (Math.PI / 180.0), aspect, 0.05f,
					client.gameRenderer.getFarPlaneDistance()
				);

				// 用 lookAt 确保偏航/俯仰正确，然后清零平移（平移由 cam.getPos() 提供一次）
				Vec3d forward = new Vec3d(
					-Math.sin(yaw * (float)(Math.PI / 180.0)) * Math.cos(pitch * (float)(Math.PI / 180.0)),
					-Math.sin(pitch * (float)(Math.PI / 180.0)),
					Math.cos(yaw * (float)(Math.PI / 180.0)) * Math.cos(pitch * (float)(Math.PI / 180.0))
				);
				Matrix4f view = new Matrix4f().lookAt(0, 0, 0, (float)forward.x, (float)forward.y, (float)forward.z, 0, 1, 0);

				client.worldRenderer.render(
					ObjectAllocator.TRIVIAL, tickCounter, false,
					cam, view, proj, fog, fogColor, true
				);
			} finally {
				FramebufferOverride.clearOverride();
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

		var encoder = RenderSystem.getDevice().createCommandEncoder();
		for (int row = 0; row < fbh; row++) {
			int bx = fbw * (fbh - row) / fbh;
			if (bx <= 0) continue;
			if (bx > fbw) bx = fbw;

			encoder.copyTextureToTexture(
				fullMirrorFbo.getColorAttachment(),
				mainFb.getColorAttachment(),
				0,
				0, row,
				0, row,
				bx, 1
			);
		}

		// 绘制对角线
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
//		for (int sy = 0; sy < sh; sy++) {
//			int sx = sw * (sh - sy) / sh;
//			if (sx >= 0 && sx < sw) {
//				context.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
//			}
//		}
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
	}
}
