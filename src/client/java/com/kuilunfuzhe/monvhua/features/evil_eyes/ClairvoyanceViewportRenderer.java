package com.kuilunfuzhe.monvhua.features.evil_eyes;

import com.kuilunfuzhe.monvhua.compat.DhCompat;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorFramebufferCompositor;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClairvoyanceViewportRenderer {
	private static final AtomicBoolean renderingPreview = new AtomicBoolean(false);
	private static final double CAMERA_DISTANCE = 4.0;

	private static UUID selectedTarget;
	private static SimpleFramebuffer previewFramebuffer;
	private static Float smoothYaw;
	private static Double smoothDistance;
	private static Vec3d smoothCameraPos;

	private ClairvoyanceViewportRenderer() {
	}

	public static void setSelectedTarget(UUID target) {
		if (target == null || !target.equals(selectedTarget)) {
			resetSmoothing();
		}
		selectedTarget = target;
	}

	public static UUID getSelectedTarget() {
		return selectedTarget;
	}

	public static void clearSelectedTarget(UUID target) {
		if (target == null || target.equals(selectedTarget)) {
			selectedTarget = null;
		}
	}

	public static boolean hasPreviewTarget() {
		return selectedTarget != null;
	}

	public static boolean shouldRenderPreviewWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		return Evil_EyesClient.isViewportMode()
			&& client.currentScreen instanceof com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen
			&& hasPreviewTarget();
	}

	public static void renderPreviewWorld(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || selectedTarget == null) return;
		if (renderingPreview.getAndSet(true)) return;

		try {
			Entity target = client.world.getEntity(selectedTarget);
			if (target == null) return;

			int fbw = client.getFramebuffer().textureWidth;
			int fbh = client.getFramebuffer().textureHeight;
			if (fbw <= 0 || fbh <= 0) return;
			previewFramebuffer = resizeFbo(previewFramebuffer, "clairvoyance_preview_full", fbw, fbh);

			ViewPose pose = computeViewPose(client, target);
			RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				previewFramebuffer.getColorAttachment(),
				0xFF000000,
				previewFramebuffer.getDepthAttachment(),
				1.0
			);

			GpuTextureView previousColorOutput = RenderSystem.outputColorTextureOverride;
			GpuTextureView previousDepthOutput = RenderSystem.outputDepthTextureOverride;
			GpuBufferSlice previousFog = RenderSystem.getShaderFog();
			RenderSystem.backupProjectionMatrix();
			DhCompat.suspend();
			IrisMirrorCompat.beginMirrorRender();
			FramebufferOverride.setOverride(previewFramebuffer);
			RenderSystem.outputColorTextureOverride = previewFramebuffer.getColorAttachmentView();
			RenderSystem.outputDepthTextureOverride = previewFramebuffer.getDepthAttachmentView();
			try {
				Camera camera = new Camera();
				camera.update(client.world, client.player, false, false, tickCounter.getTickProgress(false));
				CameraAccessor accessor = (CameraAccessor) camera;
				accessor.invokeSetPos(pose.pos.x, pose.pos.y, pose.pos.z);
				accessor.invokeSetRotation(pose.yaw, pose.pitch);

				Quaternionf rotation = camera.getRotation().conjugate(new Quaternionf());
				Matrix4f view = new Matrix4f().rotation(rotation);
				Matrix4f projection = new Matrix4f(projectionMatrix);
				client.worldRenderer.setupFrustum(pose.pos, view, projection);
				client.worldRenderer.render(
					ObjectAllocator.TRIVIAL, tickCounter, false,
					camera, view, projection, fog, fogColor, true
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
			renderingPreview.set(false);
		}
	}

	public static void renderPreviewRect(DrawContext context, int x, int y, int width, int height) {
		if (previewFramebuffer == null || selectedTarget == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		var mainFramebuffer = client.getFramebuffer();
		if (previewFramebuffer.textureWidth != mainFramebuffer.textureWidth || previewFramebuffer.textureHeight != mainFramebuffer.textureHeight) {
			return;
		}
		MirrorFramebufferCompositor.renderGuiRect(context, previewFramebuffer, x, y, width, height);
	}

	public static void cleanup() {
		if (previewFramebuffer != null) {
			previewFramebuffer.delete();
			previewFramebuffer = null;
		}
		selectedTarget = null;
		resetSmoothing();
	}

	private static ViewPose computeViewPose(MinecraftClient client, Entity target) {
		float targetYaw = target.getYaw();
		float yaw = smoothYaw == null ? targetYaw : smoothYaw + MathHelper.wrapDegrees(targetYaw - smoothYaw) * 0.5F;
		smoothYaw = yaw;

		Vec3d targetPos = target.getPos();
		Vec3d targetEyePos = target.getEyePos();
		double radYaw = Math.toRadians(yaw);
		double backX = Math.sin(radYaw);
		double backZ = -Math.cos(radYaw);
		Vec3d idealEnd = targetPos.add(backX * CAMERA_DISTANCE, 1.5, backZ * CAMERA_DISTANCE);

		RaycastContext context = new RaycastContext(
			targetEyePos, idealEnd,
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			target
		);
		BlockHitResult hit = client.world.raycast(context);
		double rawDistance;
		if (hit.getType() == HitResult.Type.BLOCK) {
			rawDistance = Math.max(0.5, hit.getPos().distanceTo(targetEyePos) - 0.2);
		} else {
			rawDistance = CAMERA_DISTANCE;
		}
		if (smoothDistance != null) {
			rawDistance = smoothDistance + (rawDistance - smoothDistance) * 0.15;
		}
		smoothDistance = Math.max(0.5, rawDistance);
		Vec3d rawCameraPos = targetPos.add(backX * smoothDistance, 1.5, backZ * smoothDistance);
		Vec3d cameraPos = smoothCameraPos == null ? rawCameraPos : smoothCameraPos.lerp(rawCameraPos, 0.35);
		smoothCameraPos = cameraPos;

		double dx = targetEyePos.x - cameraPos.x;
		double dy = targetEyePos.y - cameraPos.y;
		double dz = targetEyePos.z - cameraPos.z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		float cameraYaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F));
		float cameraPitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance))), -90.0F, 90.0F);
		return new ViewPose(cameraPos, cameraYaw, cameraPitch);
	}

	private static void resetSmoothing() {
		smoothYaw = null;
		smoothDistance = null;
		smoothCameraPos = null;
	}

	private static SimpleFramebuffer resizeFbo(SimpleFramebuffer fbo, String name, int width, int height) {
		if (fbo == null || fbo.textureWidth != width || fbo.textureHeight != height) {
			if (fbo != null) fbo.delete();
			return new SimpleFramebuffer(name, width, height, true);
		}
		return fbo;
	}

	private record ViewPose(Vec3d pos, float yaw, float pitch) {
	}
}
