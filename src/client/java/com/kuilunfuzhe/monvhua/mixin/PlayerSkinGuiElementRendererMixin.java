package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.PlayerSkinGuiElementRenderState;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkinGuiElementRenderer.class)
public abstract class PlayerSkinGuiElementRendererMixin {

	@Unique
	private static final float AXIS_LENGTH = 4.0F / 3.0F;

	@Unique
	private static final int GROUND_GRID_SIZE = 21;

	@Unique
	private static final float GROUND_GRID_HALF_SIZE = GROUND_GRID_SIZE * 0.5F;

	@Unique
	private static final float GROUND_GRID_CELL = 1.0F;

	@Unique
	private static final float GROUND_Y = 1.05F;

	@Unique
	private static final float ARROW_RADIUS = 0.15F;

	@Unique
	private static final float ARROW_LENGTH = 0.35F;

	@Unique
	private static final int ARROW_SEGMENTS = 6;

	@Unique
	private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;

	@Unique
	private static final int ROTATION_RING_SEGMENTS = 48;

	@Unique
	private static final int RING_PLANE_YZ = 0;

	@Unique
	private static final int RING_PLANE_XZ = 1;

	@Unique
	private static final int RING_PLANE_XY = 2;

	@Inject(method = "render(Lnet/minecraft/client/gui/render/state/special/PlayerSkinGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V", at = @At("TAIL"))
	private void renderCoordinateAxes3D(PlayerSkinGuiElementRenderState state, MatrixStack matrixStack, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.currentScreen instanceof BodyPoseEditorScreen screen)) {
			return;
		}
		if (!screen.isShowingCoordinateAxes()) {
			return;
		}

		BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
		if (bufferBuilders == null) {
			return;
		}
		VertexConsumerProvider.Immediate vertexConsumers = bufferBuilders.getEntityVertexConsumers();
		if (vertexConsumers == null) {
			return;
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLines());
		Matrix4f posMatrix = matrixStack.peek().getPositionMatrix();

		matrixStack.push();
		ModelPart root = state.playerModel().getRootPart();
		if (screen.isCoordinateAxesMovable()) {
			root.applyTransform(matrixStack);
		}
		posMatrix = matrixStack.peek().getPositionMatrix();
		renderAxes(posMatrix, vc);
		matrixStack.pop();

		matrixStack.push();
		root.applyTransform(matrixStack);
		if (!screen.isEditingPlayerModel()) {
			matrixStack.translate(screen.getModelOffsetX(), screen.getModelOffsetY(), screen.getModelOffsetZ());
		}
		posMatrix = matrixStack.peek().getPositionMatrix();
		renderMoveAxes(posMatrix, vc, screen.getHighlightedMoveAxis());
		renderRotationRings(posMatrix, vc, screen.getHighlightedRotationAxis());
		matrixStack.pop();

		vertexConsumers.draw();
	}

	@Unique
	private void renderAxes(Matrix4f posMatrix, VertexConsumer vc) {
		// X axis: red, -AXIS_LENGTH to +AXIS_LENGTH
		addLine(vc, posMatrix, -AXIS_LENGTH, 0, 0, AXIS_LENGTH, 0, 0, 255, 50, 50, 240);
		// Y axis: green, 0 to AXIS_LENGTH
		addLine(vc, posMatrix, 0, 0, 0, 0, AXIS_LENGTH, 0, 50, 220, 50, 240);
		// Z axis: blue, -AXIS_LENGTH to +AXIS_LENGTH
		addLine(vc, posMatrix, 0, 0, -AXIS_LENGTH, 0, 0, AXIS_LENGTH, 60, 80, 255, 240);

		// Arrow heads at positive ends
		renderCone(posMatrix, vc, AXIS_LENGTH, 0, 0, 1, 0, 0, 255, 50, 50, 240);
		renderCone(posMatrix, vc, 0, AXIS_LENGTH, 0, 0, 1, 0, 50, 220, 50, 240);
		renderCone(posMatrix, vc, 0, 0, AXIS_LENGTH, 0, 0, 1, 60, 80, 255, 240);
	}

	@Unique
	private void renderRotationRings(Matrix4f posMatrix, VertexConsumer vc, String highlightedAxis) {
		renderRotationRing(posMatrix, vc, "pitch".equals(highlightedAxis), 255, 70, 70, RING_PLANE_YZ);
		renderRotationRing(posMatrix, vc, "yaw".equals(highlightedAxis), 70, 230, 70, RING_PLANE_XZ);
		renderRotationRing(posMatrix, vc, "roll".equals(highlightedAxis), 90, 110, 255, RING_PLANE_XY);
	}

	@Unique
	private void renderRotationRing(Matrix4f posMatrix, VertexConsumer vc,
			boolean highlighted, int r, int g, int b, int plane) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 210;
		float prevX = 0.0F;
		float prevY = 0.0F;
		float prevZ = 0.0F;
		for (int i = 0; i <= ROTATION_RING_SEGMENTS; i++) {
			float angle = (float) (Math.PI * 2.0D * i / ROTATION_RING_SEGMENTS);
			float cos = (float) Math.cos(angle) * ROTATION_RING_RADIUS;
			float sin = (float) Math.sin(angle) * ROTATION_RING_RADIUS;
			float x = plane == RING_PLANE_YZ ? 0.0F : cos;
			float y = plane == RING_PLANE_XZ ? 0.0F : (plane == RING_PLANE_YZ ? cos : sin);
			float z = plane == RING_PLANE_XY ? 0.0F : sin;
			if (i > 0) {
				addLine(vc, posMatrix, prevX, prevY, prevZ, x, y, z, lineR, lineG, lineB, alpha);
			}
			prevX = x;
			prevY = y;
			prevZ = z;
		}
	}

	@Unique
	private void renderMoveAxes(Matrix4f posMatrix, VertexConsumer vc, String highlightedAxis) {
		renderMoveAxis(posMatrix, vc, 1, 0, 0, "x".equals(highlightedAxis), 255, 50, 50);
		renderMoveAxis(posMatrix, vc, 0, 1, 0, "y".equals(highlightedAxis), 50, 220, 50);
		renderMoveAxis(posMatrix, vc, 0, 0, 1, "z".equals(highlightedAxis), 60, 80, 255);
	}

	@Unique
	private void renderMoveAxis(Matrix4f posMatrix, VertexConsumer vc,
			float dirX, float dirY, float dirZ, boolean highlighted,
			int r, int g, int b) {
		int lineR = highlighted ? 255 : r;
		int lineG = highlighted ? 235 : g;
		int lineB = highlighted ? 90 : b;
		int alpha = highlighted ? 255 : 245;
		addLine(vc, posMatrix,
				0, 0, 0,
				AXIS_LENGTH * dirX, AXIS_LENGTH * dirY, AXIS_LENGTH * dirZ,
				lineR, lineG, lineB, alpha);
		renderCone(posMatrix, vc,
				AXIS_LENGTH * dirX, AXIS_LENGTH * dirY, AXIS_LENGTH * dirZ,
				dirX, dirY, dirZ,
				lineR, lineG, lineB, alpha);
	}

	@Unique
	private void renderGroundGrid(Matrix4f posMatrix, VertexConsumer vc) {
		int boundaryCount = GROUND_GRID_SIZE;
		for (int i = 0; i <= boundaryCount; i++) {
			float coord = -GROUND_GRID_HALF_SIZE + i * GROUND_GRID_CELL;
			boolean major = i == 0 || i == boundaryCount || Math.abs(coord) < 0.001F || i % 5 == 0;
			int alpha = major ? 235 : 155;
			int r = major ? 150 : 105;
			int g = major ? 235 : 185;
			int b = major ? 150 : 110;

			addLine(vc, posMatrix,
					-GROUND_GRID_HALF_SIZE, GROUND_Y, coord,
					GROUND_GRID_HALF_SIZE, GROUND_Y, coord,
					r, g, b, alpha);
			addLine(vc, posMatrix,
					coord, GROUND_Y, -GROUND_GRID_HALF_SIZE,
					coord, GROUND_Y, GROUND_GRID_HALF_SIZE,
					r, g, b, alpha);
		}
	}

	@Unique
	private void renderCone(Matrix4f posMatrix, VertexConsumer vc,
			float tipX, float tipY, float tipZ,
			float dirX, float dirY, float dirZ,
			int r, int g, int b, int a) {
		// Base center of cone
		float baseX = tipX - dirX * ARROW_LENGTH;
		float baseY = tipY - dirY * ARROW_LENGTH;
		float baseZ = tipZ - dirZ * ARROW_LENGTH;

		// Build perpendicular axes to the direction
		float ux, uy, uz, vx, vy, vz;
		if (Math.abs(dirX) < 0.9F) {
			ux = 1; uy = 0; uz = 0;
			// cross(u, dir) gives a perpendicular
			vx = uy * dirZ - uz * dirY;
			vy = uz * dirX - ux * dirZ;
			vz = ux * dirY - uy * dirX;
		} else {
			uy = 1; ux = 0; uz = 0;
			vx = uy * dirZ - uz * dirY;
			vy = uz * dirX - ux * dirZ;
			vz = ux * dirY - uy * dirX;
		}
		// Normalize v
		float vLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		vx /= vLen; vy /= vLen; vz /= vLen;
		// u = cross(v, dir) — already perpendicular to both
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
				// Edge from tip to circle point
				addLine(vc, posMatrix, tipX, tipY, tipZ, cx, cy, cz, r, g, b, a);
				// Edge along circle
				addLine(vc, posMatrix, prevCx, prevCy, prevCz, cx, cy, cz, r, g, b, a);
			}
			prevCx = cx; prevCy = cy; prevCz = cz;
		}
	}

	@Unique
	private void addLine(VertexConsumer vc, Matrix4f posMatrix,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int r, int g, int b, int a) {
		vc.vertex(posMatrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
		vc.vertex(posMatrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
	}

}
