package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.compat.DhCompat;
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

/**
 * 全屏镜像视口渲染器。
 * 负责在镜像FBO中渲染独立视角的世界画面，支持对角线分割HUD叠加、
 * DH（Distant Horizons）兼容性处理、以及渲染错误熔断机制。
 * 通过ThreadLocal帧缓冲覆盖将世界渲染重定向到镜像FBO。
 */
public class MirrorViewportRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MirrorViewportRenderer.class);
	/** 防止重入渲染的标志 */
	private static final AtomicBoolean renderingMirror = new AtomicBoolean(false);
	/** 镜像刷新间隔（1 tick = 每帧更新） */
	private static final long MIRROR_UPDATE_INTERVAL_TICKS = 1L;
	/** 渲染出错后熔断，禁用后续渲染 */
	private static volatile boolean disabledDueToRenderError = false;
	/** 上次镜像渲染的世界tick，用于控制刷新频率 */
	private static long lastMirrorRenderTick = Long.MIN_VALUE;
	/** 全屏镜像帧缓冲对象 */
	private static SimpleFramebuffer fullMirrorFbo;

	/**
	 * 渲染全屏镜像到独立的FBO中。
	 * 在镜像发生点位置创建虚拟摄像机，以玩家朝向渲染世界场景，
	 * 输出到fullMirrorFbo供后续HUD叠加使用。
	 *
	 * @param tickCounter 渲染tick计数器
	 * @param fog         雾效缓冲区
	 * @param fogColor    雾颜色
	 * @param mainCamera  主摄像机（用于获取朝向）
	 */
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

			clearMirrorFramebuffer(fullMirrorFbo);
			FramebufferOverride.setOverride(fullMirrorFbo);
			DhCompat.suspend();
			try {
				Camera cam = new Camera();
				CameraAccessor acc = (CameraAccessor) cam;
				acc.invokeSetPos(pos.x, pos.y, pos.z);
				acc.invokeSetRotation(yaw, pitch);

				float fov = client.options.getFov().getValue().floatValue();
				float aspect = (float) fbw / fbh;
				Matrix4f proj = new Matrix4f().perspective(
					fov * (float) (Math.PI / 180.0), // 视场角转弧度
					aspect,
					0.05f, // 近裁剪面距离，极小值以渲染近处方块
					client.gameRenderer.getFarPlaneDistance()
				);

				Vec3d forward = new Vec3d(
					-Math.sin(yaw * (float) (Math.PI / 180.0)) * Math.cos(pitch * (float) (Math.PI / 180.0)),
					-Math.sin(pitch * (float) (Math.PI / 180.0)),
					Math.cos(yaw * (float) (Math.PI / 180.0)) * Math.cos(pitch * (float) (Math.PI / 180.0))
				);
				Matrix4f view = new Matrix4f().lookAt(
					(float) pos.x, (float) pos.y, (float) pos.z,
					(float) (pos.x + forward.x), (float) (pos.y + forward.y), (float) (pos.z + forward.z),
					0, 1, 0
				);

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
				DhCompat.resume();
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

	/**
	 * 将对角线分割的镜像画面复制到主帧缓冲。
	 * 每行复制的宽度从顶部全宽到底部0宽线性递减，形成对角线渐变效果。
	 *
	 * @param context HUD绘制上下文
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

	private static void clearMirrorFramebuffer(SimpleFramebuffer fbo) {
		if (fbo == null || fbo.getColorAttachment() == null || fbo.getDepthAttachment() == null) return;
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
			fbo.getColorAttachment(),
			0x00000000,
			fbo.getDepthAttachment(),
			1.0
		);
	}

	/** 清理镜像FBO并重置渲染状态 */
	public static void cleanup() {
		if (fullMirrorFbo != null) {
			fullMirrorFbo.delete();
			fullMirrorFbo = null;
		}
		lastMirrorRenderTick = Long.MIN_VALUE;
	}
}
