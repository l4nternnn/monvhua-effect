package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorScreen;
import net.minecraft.client.MinecraftClient;
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
	private static final int AXIS_LENGTH = 2;

	@Unique
	private static final float GRID_RANGE = 5.0F;

	@Unique
	private static final float GRID_CELL = 0.5F;

	@Unique
	private static final float ARROW_RADIUS = 0.15F;

	@Unique
	private static final float ARROW_LENGTH = 0.35F;

	@Unique
	private static final int ARROW_SEGMENTS = 6;

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

		renderAxes(posMatrix, vc);
		renderGrid(posMatrix, vc);
		renderOrigin(posMatrix, vc);

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
	private void renderGrid(Matrix4f posMatrix, VertexConsumer vc) {
		int gridAlpha = 80;
		int majorAlpha = 140;

		int steps = (int) (GRID_RANGE / GRID_CELL);
		for (int i = -steps; i <= steps; i++) {
			float coord = i * GRID_CELL;
			boolean isMajor = Math.abs(i % 2) == 0; // every 1.0 is major
			int alpha = isMajor ? majorAlpha : gridAlpha;
			int gray = isMajor ? 190 : 160;

			// Lines parallel to X axis (at this Z position)
			if (Math.abs(coord) <= GRID_RANGE) {
				addLine(vc, posMatrix,
					-GRID_RANGE, 0, coord,
					GRID_RANGE, 0, coord,
					gray, gray, gray, alpha);
			}

			// Lines parallel to Z axis (at this X position)
			if (Math.abs(coord) <= GRID_RANGE) {
				addLine(vc, posMatrix,
					coord, 0, -GRID_RANGE,
					coord, 0, GRID_RANGE,
					gray, gray, gray, alpha);
			}
		}
	}

	@Unique
	private void renderOrigin(Matrix4f posMatrix, VertexConsumer vc) {
		float s = 0.15F;
		int white = 255;

		// Small cross lines at origin
		addLine(vc, posMatrix, -s, 0, 0, s, 0, 0, white, white, white, 255);
		addLine(vc, posMatrix, 0, -s, 0, 0, s, 0, white, white, white, 255);
		addLine(vc, posMatrix, 0, 0, -s, 0, 0, s, white, white, white, 255);

		// Small diagonal lines for visibility
		float d = s * 0.7F;
		addLine(vc, posMatrix, -d, -d, 0, d, d, 0, white, white, white, 200);
		addLine(vc, posMatrix, d, -d, 0, -d, d, 0, white, white, white, 200);
		addLine(vc, posMatrix, -d, 0, -d, d, 0, d, white, white, white, 200);
		addLine(vc, posMatrix, d, 0, -d, -d, 0, d, white, white, white, 200);
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
