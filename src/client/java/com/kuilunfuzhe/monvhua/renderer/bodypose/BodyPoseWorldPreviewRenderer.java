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

/**
 * 身体姿势编辑器的世界空间3D预览渲染器。
 * 将预览模型、地面网格、坐标轴、移动轴和旋转环直接渲染在游戏世界中。
 * 支持预览模式（跟随玩家）和放置模式（固定位置）。
 */
public class BodyPoseWorldPreviewRenderer {

	private static final float AXIS_LENGTH = 4.0F / 3.0F;
	private static final int GROUND_GRID_SIZE = 21;
	private static final float GROUND_GRID_HALF_SIZE = GROUND_GRID_SIZE * 0.5F;
	private static final float GROUND_GRID_CELL = 1.0F;
	private static final float GROUND_Y = 1.05F;
	private static final float ARROW_RADIUS = 0.15F;
	private static final float ARROW_LENGTH = 0.35F;
	private static final int ARROW_SEGMENTS = 6;
	private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;
	private static final int ROTATION_RING_SEGMENTS = 48;

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
		boolean axesMovable = BodyPoseEditorFragment.isWorldAxesMovable();
		boolean fixedMode = BodyPoseEditorFragment.getWorldPreviewMode() == BodyPoseEditorFragment.PreviewMode.FIXED;
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

		// 1. Ground grid (world orientation, at world base position)
		if (showAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			renderGroundGrid(matrices.peek().getPositionMatrix(), lineVc);
			matrices.pop();
		}

		// 2. Coordinate axes (world orientation, may follow model offset)
		if (showAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			if (axesMovable) {
				matrices.translate(offsetX, offsetY, offsetZ);
			}
			renderAxes(matrices.peek().getPositionMatrix(), lineVc);
			matrices.pop();
		}

		// 3. Move axes (world orientation, at model offset — NO rotation applied)
		if (showAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			matrices.translate(offsetX, offsetY, offsetZ);
			renderMoveAxes(matrices.peek().getPositionMatrix(), lineVc, highlightedMove);
			matrices.pop();
		}

		// 4. Rotation rings (model orientation, with combined preview+model rotation)
		if (showAxes) {
			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			matrices.translate(offsetX, offsetY, offsetZ);
			// Same rotation order as GUI ModelPart.applyTransform: Z→Y→X
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(totalPitch));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-totalYaw));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(totalRoll));
			renderRotationRings(matrices.peek().getPositionMatrix(), lineVc, highlightedRot);
			matrices.pop();
		}

		// 5. Player model (with combined preview+model rotation)
		PlayerEntityModel model = getWorldModelInstance(client);
		if (model != null) {
			Identifier skinTexture = BodyPoseEditorFragment.getWorldSkinTexture();
			BlockPos lightPos = BlockPos.ofFloored(worldPos.x, worldPos.y + 1, worldPos.z);
			int light = WorldRenderer.getLightmapCoordinates(client.world, lightPos);

			matrices.push();
			matrices.translate(dx, dy, dz);
			applyFixedModeFlip(matrices, fixedMode);
			matrices.translate(offsetX, offsetY, offsetZ);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(totalPitch));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-totalYaw));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(totalRoll));
			matrices.scale(bodyScale, bodyScale, bodyScale);
			if (!BodyPoseEditorFragment.isTrueSkeletalPoseMode()
					|| !BodyPoseSkeletalPreviewRenderer.render(matrices, vertexConsumers, skinTexture, light)) {
				RenderLayer modelLayer = RenderLayer.getEntityTranslucent(skinTexture);
				VertexConsumer modelVc = vertexConsumers.getBuffer(modelLayer);
				renderPlayerModelParts(model, matrices, modelVc, light);
			}
			matrices.pop();
		}

		// 6. Editor item displays (same display context as placed ItemDisplayEntity)
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

		vertexConsumers.draw();
	}

	private static Vec3d getWorldPreviewPosition(MinecraftClient client) {
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
		if (fixedMode) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
		}
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

	private static void renderMoveAxes(Matrix4f posMatrix, VertexConsumer vc, String highlightedAxis) {
		renderMoveAxis(posMatrix, vc, 1, 0, 0, "x".equals(highlightedAxis), 255, 50, 50);
		renderMoveAxis(posMatrix, vc, 0, 1, 0, "y".equals(highlightedAxis), 50, 220, 50);
		renderMoveAxis(posMatrix, vc, 0, 0, 1, "z".equals(highlightedAxis), 60, 80, 255);
	}

	private static void renderMoveAxis(Matrix4f posMatrix, VertexConsumer vc,
			float dirX, float dirY, float dirZ, boolean highlighted, int r, int g, int b) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 245;
		addLine(vc, posMatrix, 0, 0, 0,
				AXIS_LENGTH * dirX, AXIS_LENGTH * dirY, AXIS_LENGTH * dirZ,
				lineR, lineG, lineB, alpha);
		renderCone(posMatrix, vc,
				AXIS_LENGTH * dirX, AXIS_LENGTH * dirY, AXIS_LENGTH * dirZ,
				dirX, dirY, dirZ, lineR, lineG, lineB, alpha);
	}

	private static void renderRotationRings(Matrix4f posMatrix, VertexConsumer vc, String highlightedAxis) {
		renderRotationRing(posMatrix, vc, "pitch".equals(highlightedAxis), 255, 70, 70, 0);
		renderRotationRing(posMatrix, vc, "yaw".equals(highlightedAxis), 70, 230, 70, 1);
		renderRotationRing(posMatrix, vc, "roll".equals(highlightedAxis), 90, 110, 255, 2);
	}

	private static void renderRotationRing(Matrix4f posMatrix, VertexConsumer vc,
			boolean highlighted, int r, int g, int b, int plane) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 210;
		float prevX = 0, prevY = 0, prevZ = 0;
		for (int i = 0; i <= ROTATION_RING_SEGMENTS; i++) {
			float angle = (float) (Math.PI * 2.0 * i / ROTATION_RING_SEGMENTS);
			float cos = (float) Math.cos(angle) * ROTATION_RING_RADIUS;
			float sin = (float) Math.sin(angle) * ROTATION_RING_RADIUS;
			float x = plane == 0 ? 0 : cos;
			float y = plane == 1 ? 0 : (plane == 0 ? cos : sin);
			float z = plane == 2 ? 0 : sin;
			if (i > 0) {
				addLine(vc, posMatrix, prevX, prevY, prevZ, x, y, z, lineR, lineG, lineB, alpha);
			}
			prevX = x; prevY = y; prevZ = z;
		}
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
}
