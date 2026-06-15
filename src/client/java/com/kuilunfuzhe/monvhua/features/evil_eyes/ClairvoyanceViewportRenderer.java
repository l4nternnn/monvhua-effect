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

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClairvoyanceViewportRenderer {
	private static final AtomicBoolean renderingPreview = new AtomicBoolean(false);
	private static final double CAMERA_DISTANCE = 4.0;
	private static final int MAX_TARGETS = 4;

	private static final UUID[] selectedTargets = new UUID[MAX_TARGETS];
	private static final SimpleFramebuffer[] previewFramebuffers = new SimpleFramebuffer[MAX_TARGETS];
	private static final Float[] smoothYaws = new Float[MAX_TARGETS];
	private static final Double[] smoothDistances = new Double[MAX_TARGETS];
	private static final Vec3d[] smoothCameraPositions = new Vec3d[MAX_TARGETS];

	private ClairvoyanceViewportRenderer() {
	}

	public static void setSelectedTarget(UUID target) {
		if (target == null) {
			clearSlot(0);
			return;
		}
		if (!target.equals(selectedTargets[0])) {
			removeTarget(target);
			shiftRightFrom(0);
			selectedTargets[0] = target;
			resetSmoothing(0);
		}
	}

	public static UUID getSelectedTarget() {
		return selectedTargets[0];
	}

	public static void clearSelectedTarget(UUID target) {
		if (target == null) {
			clearAllTargets();
			return;
		}
		for (int i = 0; i < MAX_TARGETS; i++) {
			if (target.equals(selectedTargets[i])) {
				clearSlot(i);
			}
		}
		compactTargets();
	}

	public static void syncPreviewTargets(Collection<UUID> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			clearAllTargets();
			return;
		}
		for (int i = 0; i < MAX_TARGETS; i++) {
			UUID target = selectedTargets[i];
			if (target != null && !candidates.contains(target)) {
				clearSlot(i);
			}
		}
		compactTargets();
		for (UUID candidate : candidates) {
			if (candidate == null || containsTarget(candidate)) {
				continue;
			}
			int slot = firstEmptySlot();
			if (slot < 0) {
				break;
			}
			selectedTargets[slot] = candidate;
			resetSmoothing(slot);
		}
	}

	public static boolean hasPreviewTarget() {
		for (UUID target : selectedTargets) {
			if (target != null) return true;
		}
		return false;
	}

	public static int previewTargetCount() {
		int count = 0;
		for (UUID target : selectedTargets) {
			if (target != null) count++;
		}
		return count;
	}

	public static boolean shouldRenderPreviewWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		return Evil_EyesClient.isViewportMode()
			&& client.currentScreen instanceof com.kuilunfuzhe.monvhua.gui.evil_eyes.Evil_eyesScreen
			&& hasPreviewTarget();
	}

	public static void renderPreviewWorld(RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || !hasPreviewTarget()) return;
		if (renderingPreview.getAndSet(true)) return;

		try {
			for (int slot = 0; slot < MAX_TARGETS; slot++) {
				UUID targetId = selectedTargets[slot];
				if (targetId == null) continue;
				Entity target = client.world.getEntity(targetId);
				if (target == null || !target.isAlive()) {
					clearSlot(slot);
					continue;
				}
				renderPreviewSlot(slot, client, target, tickCounter, fog, fogColor, mainCamera, positionMatrix, projectionMatrix);
			}
			compactTargets();
		} finally {
			renderingPreview.set(false);
		}
	}

	private static void renderPreviewSlot(int slot, MinecraftClient client, Entity target, RenderTickCounter tickCounter, GpuBufferSlice fog, Vector4f fogColor, Camera mainCamera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
		int fbw = client.getFramebuffer().textureWidth;
		int fbh = client.getFramebuffer().textureHeight;
		if (fbw <= 0 || fbh <= 0) return;
		previewFramebuffers[slot] = resizeFbo(previewFramebuffers[slot], "clairvoyance_preview_" + slot, fbw, fbh);
		SimpleFramebuffer previewFramebuffer = previewFramebuffers[slot];

		ViewPose pose = computeViewPose(slot, client, target);
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
	}

	public static void renderPreviewRect(DrawContext context, int x, int y, int width, int height) {
		renderPreviewRect(context, 0, x, y, width, height);
	}

	public static void renderPreviewRect(DrawContext context, int slot, int x, int y, int width, int height) {
		if (slot < 0 || slot >= MAX_TARGETS) return;
		SimpleFramebuffer previewFramebuffer = previewFramebuffers[slot];
		if (previewFramebuffer == null || selectedTargets[slot] == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		var mainFramebuffer = client.getFramebuffer();
		if (previewFramebuffer.textureWidth != mainFramebuffer.textureWidth || previewFramebuffer.textureHeight != mainFramebuffer.textureHeight) {
			return;
		}
		MirrorFramebufferCompositor.renderGuiRect(context, previewFramebuffer, x, y, width, height);
	}

	public static void cleanup() {
		for (int i = 0; i < MAX_TARGETS; i++) {
			if (previewFramebuffers[i] != null) {
				previewFramebuffers[i].delete();
				previewFramebuffers[i] = null;
			}
			selectedTargets[i] = null;
			resetSmoothing(i);
		}
	}

	private static ViewPose computeViewPose(int slot, MinecraftClient client, Entity target) {
		float targetYaw = target.getYaw();
		Float smoothYaw = smoothYaws[slot];
		float yaw = smoothYaw == null ? targetYaw : smoothYaw + MathHelper.wrapDegrees(targetYaw - smoothYaw) * 0.5F;
		smoothYaws[slot] = yaw;

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
		Double smoothDistance = smoothDistances[slot];
		if (smoothDistance != null) {
			rawDistance = smoothDistance + (rawDistance - smoothDistance) * 0.15;
		}
		double distance = Math.max(0.5, rawDistance);
		smoothDistances[slot] = distance;
		Vec3d rawCameraPos = targetPos.add(backX * distance, 1.5, backZ * distance);
		Vec3d smoothCameraPos = smoothCameraPositions[slot];
		Vec3d cameraPos = smoothCameraPos == null ? rawCameraPos : smoothCameraPos.lerp(rawCameraPos, 0.35);
		smoothCameraPositions[slot] = cameraPos;

		double dx = targetEyePos.x - cameraPos.x;
		double dy = targetEyePos.y - cameraPos.y;
		double dz = targetEyePos.z - cameraPos.z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		float cameraYaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F));
		float cameraPitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance))), -90.0F, 90.0F);
		return new ViewPose(cameraPos, cameraYaw, cameraPitch);
	}

	private static void resetSmoothing(int slot) {
		smoothYaws[slot] = null;
		smoothDistances[slot] = null;
		smoothCameraPositions[slot] = null;
	}

	private static void clearAllTargets() {
		for (int i = 0; i < MAX_TARGETS; i++) {
			clearSlot(i);
		}
	}

	private static void clearSlot(int slot) {
		selectedTargets[slot] = null;
		resetSmoothing(slot);
	}

	private static void removeTarget(UUID target) {
		for (int i = 0; i < MAX_TARGETS; i++) {
			if (target.equals(selectedTargets[i])) {
				clearSlot(i);
			}
		}
		compactTargets();
	}

	private static boolean containsTarget(UUID target) {
		for (UUID selectedTarget : selectedTargets) {
			if (target.equals(selectedTarget)) return true;
		}
		return false;
	}

	private static int firstEmptySlot() {
		for (int i = 0; i < MAX_TARGETS; i++) {
			if (selectedTargets[i] == null) return i;
		}
		return -1;
	}

	private static void shiftRightFrom(int index) {
		for (int i = MAX_TARGETS - 1; i > index; i--) {
			selectedTargets[i] = selectedTargets[i - 1];
			smoothYaws[i] = smoothYaws[i - 1];
			smoothDistances[i] = smoothDistances[i - 1];
			smoothCameraPositions[i] = smoothCameraPositions[i - 1];
		}
		clearSlot(index);
	}

	private static void compactTargets() {
		int write = 0;
		for (int read = 0; read < MAX_TARGETS; read++) {
			if (selectedTargets[read] == null) continue;
			if (write != read) {
				selectedTargets[write] = selectedTargets[read];
				smoothYaws[write] = smoothYaws[read];
				smoothDistances[write] = smoothDistances[read];
				smoothCameraPositions[write] = smoothCameraPositions[read];
				selectedTargets[read] = null;
				resetSmoothing(read);
			}
			write++;
		}
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
