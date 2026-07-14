package com.kuilunfuzhe.monvhua.renderer.bodypose;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 身体姿势编辑器的世界空间3D预览渲染器。
 * 将预览模型、地面网格、坐标轴、移动轴和旋转环直接渲染在游戏世界中。
 * 支持预览模式（跟随玩家）和放置模式（固定位置）。
 */
public class BodyPoseWorldPreviewRenderer {

	private static final Identifier WHITE_TEXTURE = Identifier.ofVanilla("textures/block/white_concrete.png");
	private static final float AXIS_LENGTH = 4.0F / 3.0F;
	private static final int GROUND_GRID_SIZE = 21;
	private static final float GROUND_GRID_HALF_SIZE = GROUND_GRID_SIZE * 0.5F;
	private static final float GROUND_GRID_CELL = 1.0F;
	private static final float GROUND_Y = 1.05F;
	private static final float ARROW_RADIUS = 0.05F;
	private static final float ARROW_LENGTH = 0.35F;
	private static final int ARROW_SEGMENTS = 16;
	private static final float AXIS_SHAFT_RADIUS = 0.012F;
	private static final float GIZMO_CENTER_RADIUS = 0.035F;
	private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;
	private static final float ROTATION_RING_TUBE_RADIUS = 0.0085F;
	private static final int ROTATION_RING_SEGMENTS = 72;
	private static final int ROTATION_RING_TUBE_SEGMENTS = 12;
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		if (!BodyPoseEditorFragment.isWorldPreviewActive()) return;

		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		Vec3d worldPos = getWorldPreviewPosition(client);

		BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
		if (bufferBuilders == null) return;
		VertexConsumerProvider.Immediate vertexConsumers = bufferBuilders.getEntityVertexConsumers();
		if (vertexConsumers == null) return;

		boolean showAxes = BodyPoseEditorFragment.isWorldAxesShown();
		boolean placementOverlay = BodyPoseEditorFragment.isWorldPlacementOverlayActive();
		boolean showWorldAxes = showAxes && !placementOverlay;
		boolean axesMovable = BodyPoseEditorFragment.isWorldAxesMovable();
		boolean fixedMode = BodyPoseEditorFragment.getWorldPreviewMode() == BodyPoseEditorFragment.PreviewMode.FIXED;
		boolean trueSkeletalMode = BodyPoseEditorFragment.isTrueSkeletalPoseMode();
		boolean rotationMode = BodyPoseEditorFragment.isStaticRotationGizmoMode();
		boolean editingPlacedTarget = BodyPoseEditorFragment.isEditingPlacedBodyInWorldPlacement();
		String highlightedMove = BodyPoseEditorFragment.getStaticHighlightedMoveAxis();
		String highlightedRot = BodyPoseEditorFragment.getStaticHighlightedRotationAxis();
		float offsetX = BodyPoseEditorFragment.getWorldModelOffsetX();
		float offsetY = BodyPoseEditorFragment.getWorldModelOffsetY();
		float offsetZ = BodyPoseEditorFragment.getWorldModelOffsetZ();
		float modelPitch = BodyPoseEditorFragment.getWorldModelPitch();
		float modelYaw = BodyPoseEditorFragment.getWorldModelYaw();
		float modelRoll = BodyPoseEditorFragment.getWorldModelRoll();
		float bodyScale = BodyPoseEditorFragment.getWorldBodyScale();
		// Combined rotation matching GUI model: Z(roll) → Y(yaw) → X(pitch)
		// GUI: root.roll=+totalRoll, root.yaw=-totalYaw, root.pitch=+totalPitch
		float totalPitch = modelPitch;
		float totalYaw = modelYaw;
		float totalRoll = modelRoll;

		double dx = worldPos.x - cameraPos.x;
		double dy = worldPos.y - cameraPos.y;
		double dz = worldPos.z - cameraPos.z;

		VertexConsumer lineVc = vertexConsumers.getBuffer(RenderLayer.getLines());
		VertexConsumer solidVc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));

		// 1. Ground grid (world orientation, at world base position)
		if (showWorldAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			renderGroundGrid(matrices.peek().getPositionMatrix(), lineVc);
			matrices.pop();
		}

		// 2. Coordinate axes (world orientation, may follow model offset)
		if (showWorldAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			applyCombinedBodyBaseTransform(matrices);
			if (axesMovable) {
				applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
			}
			renderAxes(matrices.peek().getPositionMatrix(), lineVc);
			matrices.pop();
		}

		// 3. Move axes (world orientation, at model offset — NO rotation applied)
		if (showWorldAxes && !rotationMode) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			applyCombinedBodyBaseTransform(matrices);
			applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
			applyWorldPlacementGizmoLocalTransform(matrices, trueSkeletalMode, totalPitch, totalYaw, totalRoll);
			renderMoveAxes(matrices.peek().getPositionMatrix(), lineVc, solidVc, highlightedMove);
			matrices.pop();
		}

		// 4. Rotation rings (model orientation, with combined preview+model rotation)
		if (showWorldAxes && rotationMode) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			applyCombinedBodyBaseTransform(matrices);
			applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
			// Same rotation order as GUI ModelPart.applyTransform: Z→Y→X
			applyWorldPlacementGizmoLocalTransform(matrices, trueSkeletalMode, totalPitch, totalYaw, totalRoll);
			renderRotationRings(matrices.peek().getPositionMatrix(), lineVc, solidVc, highlightedRot);
			matrices.pop();
		}

		// 5. Player model (with combined preview+model rotation)
		PlayerEntityModel model = getWorldModelInstance(client);
		if (!editingPlacedTarget && model != null) {
			Identifier skinTexture = BodyPoseEditorFragment.getWorldSkinTexture();
			BlockPos lightPos = BlockPos.ofFloored(worldPos.x, worldPos.y + 1, worldPos.z);
			int light = WorldRenderer.getLightmapCoordinates(client.world, lightPos);

			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			applyCombinedBodyBaseTransform(matrices);
			applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
			applyPlacementRotation(matrices, trueSkeletalMode, totalPitch, totalYaw, totalRoll);
			matrices.scale(bodyScale, bodyScale, bodyScale);
			if (!trueSkeletalMode
					|| !BodyPoseSkeletalPreviewRenderer.render(matrices, vertexConsumers, skinTexture, light)) {
				RenderLayer modelLayer = RenderLayer.getEntityCutoutNoCull(skinTexture);
				VertexConsumer modelVc = vertexConsumers.getBuffer(modelLayer);
				renderPlayerModelParts(model, matrices, modelVc, light);
			}
			matrices.pop();
		}

		// 6. Editor item displays (same display context as placed ItemDisplayEntity)
		if (!editingPlacedTarget) {
			for (BodyPoseEditorFragment.EditorItemPreview item : BodyPoseEditorFragment.getWorldEditorItemPreviews()) {
				if (item.stack().isEmpty()) {
					continue;
				}
				BlockPos lightPos = BlockPos.ofFloored(
						worldPos.x + item.offsetX(),
						worldPos.y + item.offsetY(),
						worldPos.z + item.offsetZ());
				int light = WorldRenderer.getLightmapCoordinates(client.world, lightPos);
				matrices.push();
				matrices.translate(dx, dy, dz);
				applyFixedModeFlip(matrices, fixedMode);
				matrices.translate(item.offsetX(), item.offsetY(), item.offsetZ());
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(item.pitch()));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-item.yaw()));
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(item.roll()));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
				client.getItemRenderer().renderItem(item.stack(), item.displayContext(), light, OverlayTexture.DEFAULT_UV,
						matrices, vertexConsumers, client.world, 0);
				matrices.pop();
			}
		}

		if (placementOverlay) {
			vertexConsumers.draw();
			VertexConsumer gizmoVc = vertexConsumers.getBuffer(RenderLayer.getLines());
			VertexConsumer solidGizmoVc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));
			if (rotationMode) {
				matrices.push();
				matrices.translate(dx, dy, dz);
				applyFixedModeFlip(matrices, fixedMode);
				applyCombinedBodyBaseTransform(matrices);
				applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
				applyWorldPlacementGizmoLocalTransform(matrices, trueSkeletalMode, totalPitch, totalYaw, totalRoll);
				renderRotationRings(matrices.peek().getPositionMatrix(), gizmoVc, solidGizmoVc, highlightedRot);
				matrices.pop();
			} else {
				matrices.push();
				matrices.translate(dx, dy, dz);
				applyFixedModeFlip(matrices, fixedMode);
				applyCombinedBodyBaseTransform(matrices);
				applyPlacementOffset(matrices, trueSkeletalMode, offsetX, offsetY, offsetZ);
				applyWorldPlacementGizmoLocalTransform(matrices, trueSkeletalMode, totalPitch, totalYaw, totalRoll);
				renderMoveAxes(matrices.peek().getPositionMatrix(), gizmoVc, solidGizmoVc, highlightedMove);
				matrices.pop();
			}
		}

		vertexConsumers.draw();
	}

	private static Vec3d getWorldPreviewPosition(MinecraftClient client) {
		Vec3d editingPosition = BodyPoseEditorFragment.getEditingPlacedBodyPosition();
		if (editingPosition != null) {
			return editingPosition;
		}
		BodyPoseEditorFragment.PreviewMode mode = BodyPoseEditorFragment.getWorldPreviewMode();
		if (mode == BodyPoseEditorFragment.PreviewMode.FIXED) {
			return new Vec3d(
				BodyPoseEditorFragment.getFixedWorldX(),
				BodyPoseEditorFragment.getFixedWorldY(),
				BodyPoseEditorFragment.getFixedWorldZ()
			);
		}
		return client.player.getPos();
	}

	private static PlayerEntityModel getWorldModelInstance(MinecraftClient client) {
		return BodyPoseEditorFragment.getPreparedWorldPreviewModel();
	}

	private static void applyFixedModeFlip(MatrixStack matrices, boolean fixedMode) {
		// Fixed placement uses the same upright transform as the normal preview.
	}

	private static void applyCombinedBodyBaseTransform(MatrixStack matrices) {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		matrices.translate(-0.5F, -0.5F, -0.5F);
		matrices.translate(0.5F, 0.0F, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		matrices.scale(-1.0F, -1.0F, 1.0F);
	}

	private static void applyPlacementOffset(MatrixStack matrices, boolean trueSkeletalMode, float x, float y, float z) {
		if (trueSkeletalMode) {
			matrices.scale(1.0F, -1.0F, 1.0F);
		}
		matrices.translate(x, y, z);
	}

	private static void applyPlacementRotation(MatrixStack matrices, boolean trueSkeletalMode, float pitch, float yaw, float roll) {
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(trueSkeletalMode ? -pitch : pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(trueSkeletalMode ? yaw : -yaw));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(trueSkeletalMode ? -roll : roll));
	}

	private static void applyWorldPlacementGizmoLocalTransform(MatrixStack matrices,
			boolean trueSkeletalMode, float pitch, float yaw, float roll) {
		applyPlacementRotation(matrices, trueSkeletalMode, pitch, yaw, roll);
		Vector3f center = BodyPoseEditorFragment.getWorldPlacementGizmoCenterLocal();
		matrices.translate(center.x, center.y, center.z);
	}

	private static void renderPlayerModelParts(PlayerEntityModel model, MatrixStack matrices,
			VertexConsumer vertexConsumer, int light) {
		int overlay = OverlayTexture.DEFAULT_UV;
		if (model.head.visible) model.head.render(matrices, vertexConsumer, light, overlay);
		if (model.body.visible) model.body.render(matrices, vertexConsumer, light, overlay);
		if (model.leftArm.visible) model.leftArm.render(matrices, vertexConsumer, light, overlay);
		if (model.rightArm.visible) model.rightArm.render(matrices, vertexConsumer, light, overlay);
		if (model.leftLeg.visible) model.leftLeg.render(matrices, vertexConsumer, light, overlay);
		if (model.rightLeg.visible) model.rightLeg.render(matrices, vertexConsumer, light, overlay);
	}

	// ===== 3D line primitives =====

	private static void renderAxes(Matrix4f posMatrix, VertexConsumer vc) {
		addLine(vc, posMatrix, -AXIS_LENGTH, 0, 0, AXIS_LENGTH, 0, 0, 255, 50, 50, 240);
		addLine(vc, posMatrix, 0, 0, 0, 0, AXIS_LENGTH, 0, 50, 220, 50, 240);
		addLine(vc, posMatrix, 0, 0, -AXIS_LENGTH, 0, 0, AXIS_LENGTH, 60, 80, 255, 240);
		renderCone(posMatrix, vc, AXIS_LENGTH, 0, 0, 1, 0, 0, 255, 50, 50, 240);
		renderCone(posMatrix, vc, 0, AXIS_LENGTH, 0, 0, 1, 0, 50, 220, 50, 240);
		renderCone(posMatrix, vc, 0, 0, AXIS_LENGTH, 0, 0, 1, 60, 80, 255, 240);
	}

	private static void renderMoveAxes(Matrix4f posMatrix, VertexConsumer lineVc,
			VertexConsumer solidVc, String highlightedAxis) {
		renderMoveAxis(posMatrix, lineVc, solidVc, 1, 0, 0, "x".equals(highlightedAxis), 255, 50, 50);
		renderMoveAxis(posMatrix, lineVc, solidVc, 0, 1, 0, "y".equals(highlightedAxis), 50, 220, 50);
		renderMoveAxis(posMatrix, lineVc, solidVc, 0, 0, 1, "z".equals(highlightedAxis), 60, 80, 255);
		renderSolidSphere(posMatrix, solidVc, 0.0F, 0.0F, 0.0F, GIZMO_CENTER_RADIUS, 245, 245, 245, 255);
	}

	private static void renderMoveAxis(Matrix4f posMatrix, VertexConsumer lineVc, VertexConsumer solidVc,
			float dirX, float dirY, float dirZ, boolean highlighted, int r, int g, int b) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 245;
		renderSolidShaft(posMatrix, solidVc, dirX, dirY, dirZ, lineR, lineG, lineB, alpha);
		renderSolidCone(posMatrix, solidVc,
				AXIS_LENGTH * dirX, AXIS_LENGTH * dirY, AXIS_LENGTH * dirZ,
				dirX, dirY, dirZ, lineR, lineG, lineB, alpha);
	}

	private static void renderRotationRings(Matrix4f posMatrix, VertexConsumer lineVc,
			VertexConsumer solidVc, String highlightedAxis) {
		renderRotationRing(posMatrix, lineVc, solidVc, "pitch".equals(highlightedAxis), 255, 70, 70, 0);
		renderRotationRing(posMatrix, lineVc, solidVc, "yaw".equals(highlightedAxis), 70, 230, 70, 1);
		renderRotationRing(posMatrix, lineVc, solidVc, "roll".equals(highlightedAxis), 90, 110, 255, 2);
		renderSolidSphere(posMatrix, solidVc, 0.0F, 0.0F, 0.0F, GIZMO_CENTER_RADIUS, 245, 245, 245, 255);
	}

	private static void renderRotationRing(Matrix4f posMatrix, VertexConsumer lineVc, VertexConsumer solidVc,
			boolean highlighted, int r, int g, int b, int plane) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 210;
		renderSolidRotationRing(posMatrix, solidVc, plane, lineR, lineG, lineB, alpha);
	}

	private static void renderSolidShaft(Matrix4f posMatrix, VertexConsumer vc,
			float dirX, float dirY, float dirZ, int r, int g, int b, int a) {
		PerpBasis basis = basisForDirection(dirX, dirY, dirZ);
		float end = Math.max(0.05F, AXIS_LENGTH - ARROW_LENGTH * 0.45F);
		for (int i = 0; i < ARROW_SEGMENTS; i++) {
			float a0 = (float) (Math.PI * 2.0 * i / ARROW_SEGMENTS);
			float a1 = (float) (Math.PI * 2.0 * (i + 1) / ARROW_SEGMENTS);
			RingPoint p0 = ringAroundAxis(basis, 0.0F, a0, AXIS_SHAFT_RADIUS);
			RingPoint p1 = ringAroundAxis(basis, 0.0F, a1, AXIS_SHAFT_RADIUS);
			RingPoint p2 = ringAroundAxis(basis, end, a1, AXIS_SHAFT_RADIUS);
			RingPoint p3 = ringAroundAxis(basis, end, a0, AXIS_SHAFT_RADIUS);
			float nx = (p0.nx + p1.nx) * 0.5F;
			float ny = (p0.ny + p1.ny) * 0.5F;
			float nz = (p0.nz + p1.nz) * 0.5F;
			emitSolidQuad(vc, posMatrix, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
					p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, r, g, b, a, nx, ny, nz);
		}
	}

	private static void renderSolidCone(Matrix4f posMatrix, VertexConsumer vc,
			float tipX, float tipY, float tipZ,
			float dirX, float dirY, float dirZ, int r, int g, int b, int a) {
		PerpBasis basis = basisForDirection(dirX, dirY, dirZ);
		float baseDistance = AXIS_LENGTH - ARROW_LENGTH;
		for (int i = 0; i < ARROW_SEGMENTS; i++) {
			float a0 = (float) (Math.PI * 2.0 * i / ARROW_SEGMENTS);
			float a1 = (float) (Math.PI * 2.0 * (i + 1) / ARROW_SEGMENTS);
			RingPoint p0 = ringAroundAxis(basis, baseDistance, a0, ARROW_RADIUS);
			RingPoint p1 = ringAroundAxis(basis, baseDistance, a1, ARROW_RADIUS);
			float nx = (p0.nx + p1.nx + dirX * 0.35F) * 0.5F;
			float ny = (p0.ny + p1.ny + dirY * 0.35F) * 0.5F;
			float nz = (p0.nz + p1.nz + dirZ * 0.35F) * 0.5F;
			emitSolidQuad(vc, posMatrix,
					tipX, tipY, tipZ,
					p0.x, p0.y, p0.z,
					p1.x, p1.y, p1.z,
					tipX, tipY, tipZ,
					r, g, b, a, nx, ny, nz);
		}
	}

	private static void renderSolidRotationRing(Matrix4f posMatrix, VertexConsumer vc,
			int plane, int r, int g, int b, int a) {
		for (int i = 0; i < ROTATION_RING_SEGMENTS; i++) {
			float ring0 = (float) (Math.PI * 2.0 * i / ROTATION_RING_SEGMENTS);
			float ring1 = (float) (Math.PI * 2.0 * (i + 1) / ROTATION_RING_SEGMENTS);
			for (int j = 0; j < ROTATION_RING_TUBE_SEGMENTS; j++) {
				float tube0 = (float) (Math.PI * 2.0 * j / ROTATION_RING_TUBE_SEGMENTS);
				float tube1 = (float) (Math.PI * 2.0 * (j + 1) / ROTATION_RING_TUBE_SEGMENTS);
				RingPoint p0 = rotationRingTubePoint(plane, ring0, tube0);
				RingPoint p1 = rotationRingTubePoint(plane, ring1, tube0);
				RingPoint p2 = rotationRingTubePoint(plane, ring1, tube1);
				RingPoint p3 = rotationRingTubePoint(plane, ring0, tube1);
				float nx = (p0.nx + p1.nx + p2.nx + p3.nx) * 0.25F;
				float ny = (p0.ny + p1.ny + p2.ny + p3.ny) * 0.25F;
				float nz = (p0.nz + p1.nz + p2.nz + p3.nz) * 0.25F;
				emitSolidQuad(vc, posMatrix, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
						p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, r, g, b, a, nx, ny, nz);
			}
		}
	}

	private static void renderSolidSphere(Matrix4f posMatrix, VertexConsumer vc,
			float cx, float cy, float cz, float radius, int r, int g, int b, int a) {
		int latSegments = 8;
		int lonSegments = 16;
		for (int lat = 0; lat < latSegments; lat++) {
			float v0 = (float) (-Math.PI * 0.5D + Math.PI * lat / latSegments);
			float v1 = (float) (-Math.PI * 0.5D + Math.PI * (lat + 1) / latSegments);
			for (int lon = 0; lon < lonSegments; lon++) {
				float u0 = (float) (Math.PI * 2.0D * lon / lonSegments);
				float u1 = (float) (Math.PI * 2.0D * (lon + 1) / lonSegments);
				SpherePoint p0 = spherePoint(cx, cy, cz, radius, u0, v0);
				SpherePoint p1 = spherePoint(cx, cy, cz, radius, u1, v0);
				SpherePoint p2 = spherePoint(cx, cy, cz, radius, u1, v1);
				SpherePoint p3 = spherePoint(cx, cy, cz, radius, u0, v1);
				float nx = (p0.nx + p1.nx + p2.nx + p3.nx) * 0.25F;
				float ny = (p0.ny + p1.ny + p2.ny + p3.ny) * 0.25F;
				float nz = (p0.nz + p1.nz + p2.nz + p3.nz) * 0.25F;
				emitSolidQuad(vc, posMatrix, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
						p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, r, g, b, a, nx, ny, nz);
			}
		}
	}

	private static SpherePoint spherePoint(float cx, float cy, float cz, float radius, float yaw, float pitch) {
		float cosPitch = (float) Math.cos(pitch);
		float nx = (float) Math.cos(yaw) * cosPitch;
		float ny = (float) Math.sin(pitch);
		float nz = (float) Math.sin(yaw) * cosPitch;
		return new SpherePoint(cx + nx * radius, cy + ny * radius, cz + nz * radius, nx, ny, nz);
	}

	private static PerpBasis basisForDirection(float dirX, float dirY, float dirZ) {
		float ux;
		float uy;
		float uz;
		float vx;
		float vy;
		float vz;
		if (Math.abs(dirX) < 0.9F) {
			ux = 1.0F; uy = 0.0F; uz = 0.0F;
		} else {
			ux = 0.0F; uy = 1.0F; uz = 0.0F;
		}
		vx = uy * dirZ - uz * dirY;
		vy = uz * dirX - ux * dirZ;
		vz = ux * dirY - uy * dirX;
		float vLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		vx /= vLen; vy /= vLen; vz /= vLen;
		ux = vy * dirZ - vz * dirY;
		uy = vz * dirX - vx * dirZ;
		uz = vx * dirY - vy * dirX;
		return new PerpBasis(dirX, dirY, dirZ, ux, uy, uz, vx, vy, vz);
	}

	private static RingPoint ringAroundAxis(PerpBasis basis, float distance, float angle, float radius) {
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float nx = basis.ux * cos + basis.vx * sin;
		float ny = basis.uy * cos + basis.vy * sin;
		float nz = basis.uz * cos + basis.vz * sin;
		return new RingPoint(
				basis.dirX * distance + nx * radius,
				basis.dirY * distance + ny * radius,
				basis.dirZ * distance + nz * radius,
				nx, ny, nz);
	}

	private static RingPoint rotationRingTubePoint(int plane, float ringAngle, float tubeAngle) {
		float ringCos = (float) Math.cos(ringAngle);
		float ringSin = (float) Math.sin(ringAngle);
		float tubeCos = (float) Math.cos(tubeAngle);
		float tubeSin = (float) Math.sin(tubeAngle);
		float radialX = plane == 0 ? 0.0F : ringCos;
		float radialY = plane == 1 ? 0.0F : (plane == 0 ? ringCos : ringSin);
		float radialZ = plane == 2 ? 0.0F : ringSin;
		float normalX = plane == 0 ? 1.0F : 0.0F;
		float normalY = plane == 1 ? 1.0F : 0.0F;
		float normalZ = plane == 2 ? 1.0F : 0.0F;
		float nx = radialX * tubeCos + normalX * tubeSin;
		float ny = radialY * tubeCos + normalY * tubeSin;
		float nz = radialZ * tubeCos + normalZ * tubeSin;
		return new RingPoint(
				radialX * ROTATION_RING_RADIUS + nx * ROTATION_RING_TUBE_RADIUS,
				radialY * ROTATION_RING_RADIUS + ny * ROTATION_RING_TUBE_RADIUS,
				radialZ * ROTATION_RING_RADIUS + nz * ROTATION_RING_TUBE_RADIUS,
				nx, ny, nz);
	}

	private static void emitSolidQuad(VertexConsumer vc, Matrix4f matrix,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			int r, int g, int b, int a, float nx, float ny, float nz) {
		emitSolidVertex(vc, matrix, x0, y0, z0, r, g, b, a, nx, ny, nz);
		emitSolidVertex(vc, matrix, x1, y1, z1, r, g, b, a, nx, ny, nz);
		emitSolidVertex(vc, matrix, x2, y2, z2, r, g, b, a, nx, ny, nz);
		emitSolidVertex(vc, matrix, x3, y3, z3, r, g, b, a, nx, ny, nz);
	}

	private static void emitSolidVertex(VertexConsumer vc, Matrix4f matrix,
			float x, float y, float z, int r, int g, int b, int a, float nx, float ny, float nz) {
		vc.vertex(matrix, x, y, z)
				.color(r, g, b, a)
				.texture(0.0F, 0.0F)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(nx, ny, nz);
	}

	private static void renderGroundGrid(Matrix4f posMatrix, VertexConsumer vc) {
		for (int i = 0; i <= GROUND_GRID_SIZE; i++) {
			float coord = -GROUND_GRID_HALF_SIZE + i * GROUND_GRID_CELL;
			boolean major = i == 0 || i == GROUND_GRID_SIZE || Math.abs(coord) < 0.001F || i % 5 == 0;
			int alpha = major ? 235 : 155;
			int r = major ? 150 : 105;
			int g = major ? 235 : 185;
			int b = major ? 150 : 110;
			addLine(vc, posMatrix,
					-GROUND_GRID_HALF_SIZE, GROUND_Y, coord,
					GROUND_GRID_HALF_SIZE, GROUND_Y, coord, r, g, b, alpha);
			addLine(vc, posMatrix,
					coord, GROUND_Y, -GROUND_GRID_HALF_SIZE,
					coord, GROUND_Y, GROUND_GRID_HALF_SIZE, r, g, b, alpha);
		}
	}

	private static void renderCone(Matrix4f posMatrix, VertexConsumer vc,
			float tipX, float tipY, float tipZ,
			float dirX, float dirY, float dirZ, int r, int g, int b, int a) {
		float baseX = tipX - dirX * ARROW_LENGTH;
		float baseY = tipY - dirY * ARROW_LENGTH;
		float baseZ = tipZ - dirZ * ARROW_LENGTH;

		float ux, uy, uz, vx, vy, vz;
		if (Math.abs(dirX) < 0.9F) {
			ux = 1; uy = 0; uz = 0;
			vx = uy * dirZ - uz * dirY;
			vy = uz * dirX - ux * dirZ;
			vz = ux * dirY - uy * dirX;
		} else {
			uy = 1; ux = 0; uz = 0;
			vx = uy * dirZ - uz * dirY;
			vy = uz * dirX - ux * dirZ;
			vz = ux * dirY - uy * dirX;
		}
		float vLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		vx /= vLen; vy /= vLen; vz /= vLen;
		ux = vy * dirZ - vz * dirY;
		uy = vz * dirX - vx * dirZ;
		uz = vx * dirY - vy * dirX;

		float prevCx = 0, prevCy = 0, prevCz = 0;
		for (int i = 0; i <= ARROW_SEGMENTS; i++) {
			float angle = (float) (2.0 * Math.PI * i / ARROW_SEGMENTS);
			float cos = (float) Math.cos(angle);
			float sin = (float) Math.sin(angle);
			float cx = baseX + ARROW_RADIUS * (ux * cos + vx * sin);
			float cy = baseY + ARROW_RADIUS * (uy * cos + vy * sin);
			float cz = baseZ + ARROW_RADIUS * (uz * cos + vz * sin);
			if (i > 0) {
				addLine(vc, posMatrix, tipX, tipY, tipZ, cx, cy, cz, r, g, b, a);
				addLine(vc, posMatrix, prevCx, prevCy, prevCz, cx, cy, cz, r, g, b, a);
			}
			prevCx = cx; prevCy = cy; prevCz = cz;
		}
	}

	private static void addLine(VertexConsumer vc, Matrix4f posMatrix,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int r, int g, int b, int a) {
		vc.vertex(posMatrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
		vc.vertex(posMatrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
	}

	private record PerpBasis(float dirX, float dirY, float dirZ,
			float ux, float uy, float uz, float vx, float vy, float vz) {
	}

	private record RingPoint(float x, float y, float z, float nx, float ny, float nz) {
	}

	private record SpherePoint(float x, float y, float z, float nx, float ny, float nz) {
	}
}
