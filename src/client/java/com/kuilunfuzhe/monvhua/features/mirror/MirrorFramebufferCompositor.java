package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

public final class MirrorFramebufferCompositor {
	private static final RenderPipeline MIRROR_BLIT_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder()
			.withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/mirror_framebuffer_split"))
			.withVertexShader("core/blit_screen")
			.withFragmentShader("core/blit_screen")
			.withSampler("InSampler")
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
			.build()
	);
	private static final RenderPipeline VIEWPORT_BLIT_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder()
			.withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/framebuffer_viewport"))
			.withVertexShader(Identifier.of(MonvhuaMod.MOD_ID, "core/framebuffer_viewport"))
			.withFragmentShader(Identifier.of(MonvhuaMod.MOD_ID, "core/framebuffer_viewport"))
			.withSampler("InSampler")
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
			.build()
	);
	private static final RenderPipeline VIEWPORT_GUI_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
			.withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/framebuffer_viewport_gui"))
			.withoutBlend()
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
			.build()
	);

	private static GpuBuffer splitTriangleBuffer;
	private static GpuBuffer viewportRectBuffer;
	private static int viewportRectKey;

	private MirrorFramebufferCompositor() {
	}

	public static void renderDiagonalSplit(SimpleFramebuffer mirrorFramebuffer, Framebuffer mainFramebuffer) {
		if (mirrorFramebuffer == null || mainFramebuffer == null) return;
		if (mirrorFramebuffer.getColorAttachmentView() == null || mainFramebuffer.getColorAttachmentView() == null) return;

		GpuBuffer vertexBuffer = getSplitTriangleBuffer();
		try (RenderPass pass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(() -> "Monvhua mirror framebuffer split", mainFramebuffer.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(MIRROR_BLIT_PIPELINE);
			pass.setVertexBuffer(0, vertexBuffer);
			pass.bindSampler("InSampler", mirrorFramebuffer.getColorAttachmentView());
			pass.draw(0, 3);
		}
	}

	public static void renderGuiRect(
		SimpleFramebuffer source,
		Framebuffer target,
		int scaledX,
		int scaledY,
		int scaledWidth,
		int scaledHeight,
		int scaledScreenWidth,
		int scaledScreenHeight
	) {
		if (source == null || target == null) return;
		if (source.getColorAttachmentView() == null || target.getColorAttachmentView() == null) return;
		if (scaledWidth <= 0 || scaledHeight <= 0 || scaledScreenWidth <= 0 || scaledScreenHeight <= 0) return;

		float left = clamp01((float) scaledX / scaledScreenWidth);
		float right = clamp01((float) (scaledX + scaledWidth) / scaledScreenWidth);
		float top = clamp01(1.0F - (float) scaledY / scaledScreenHeight);
		float bottom = clamp01(1.0F - (float) (scaledY + scaledHeight) / scaledScreenHeight);
		if (right <= left || top <= bottom) return;

		GpuBuffer vertexBuffer = getViewportRectBuffer(
			scaledX, scaledY, scaledWidth, scaledHeight, scaledScreenWidth, scaledScreenHeight,
			left, right, top, bottom
		);
		try (RenderPass pass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(() -> "Monvhua framebuffer viewport", target.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(VIEWPORT_BLIT_PIPELINE);
			pass.setVertexBuffer(0, vertexBuffer);
			pass.bindSampler("InSampler", source.getColorAttachmentView());
			pass.draw(0, 6);
		}
	}

	public static void renderGuiRect(DrawContext context, SimpleFramebuffer source, int x, int y, int width, int height) {
		if (context == null || source == null || source.getColorAttachmentView() == null) return;
		if (width <= 0 || height <= 0) return;
		context.state.addSimpleElement(new ViewportRectRenderState(
			VIEWPORT_GUI_PIPELINE,
			TextureSetup.of(source.getColorAttachmentView()),
			new Matrix3x2f(context.getMatrices()),
			x,
			y,
			width,
			height,
			0xFFFFFFFF,
			new ScreenRect(x, y, width, height)
		));
	}

	private record ViewportRectRenderState(
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		Matrix3x2f pose,
		int x,
		int y,
		int width,
		int height,
		int color,
		ScreenRect bounds
	) implements SimpleGuiElementRenderState {
		@Override
		public void setupVertices(VertexConsumer vertices, float depth) {
			vertex(vertices, x, y, 0.0F, 1.0F, depth);
			vertex(vertices, x, y + height, 0.0F, 0.0F, depth);
			vertex(vertices, x + width, y + height, 1.0F, 0.0F, depth);
			vertex(vertices, x + width, y, 1.0F, 1.0F, depth);
		}

		@Override
		public ScreenRect scissorArea() {
			return null;
		}

		private void vertex(VertexConsumer vertices, float x, float y, float u, float v, float depth) {
			vertices.vertex(pose, x, y, depth).texture(u, v).color(color);
		}
	}

	private static GpuBuffer getViewportRectBuffer(
		int scaledX,
		int scaledY,
		int scaledWidth,
		int scaledHeight,
		int scaledScreenWidth,
		int scaledScreenHeight,
		float left,
		float right,
		float top,
		float bottom
	) {
		int key = scaledX;
		key = 31 * key + scaledY;
		key = 31 * key + scaledWidth;
		key = 31 * key + scaledHeight;
		key = 31 * key + scaledScreenWidth;
		key = 31 * key + scaledScreenHeight;
		if (viewportRectBuffer != null && viewportRectKey == key) {
			return viewportRectBuffer;
		}
		if (viewportRectBuffer != null) {
			viewportRectBuffer.close();
			viewportRectBuffer = null;
		}

		ByteBuffer vertices = ByteBuffer.allocateDirect(6 * 5 * Float.BYTES).order(ByteOrder.nativeOrder());
		putTexturedPosition(vertices, left, bottom, 0.0F, 0.0F);
		putTexturedPosition(vertices, right, bottom, 1.0F, 0.0F);
		putTexturedPosition(vertices, right, top, 1.0F, 1.0F);
		putTexturedPosition(vertices, left, bottom, 0.0F, 0.0F);
		putTexturedPosition(vertices, right, top, 1.0F, 1.0F);
		putTexturedPosition(vertices, left, top, 0.0F, 1.0F);
		vertices.flip();
		viewportRectKey = key;
		viewportRectBuffer = RenderSystem.getDevice().createBuffer(() -> "Monvhua framebuffer viewport rect", 40, vertices);
		return viewportRectBuffer;
	}

	private static GpuBuffer getSplitTriangleBuffer() {
		if (splitTriangleBuffer == null) {
			ByteBuffer vertices = ByteBuffer.allocateDirect(3 * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
			putPosition(vertices, 0.0F, 0.0F, 0.0F);
			putPosition(vertices, 1.0F, 1.0F, 0.0F);
			putPosition(vertices, 0.0F, 1.0F, 0.0F);
			vertices.flip();
			splitTriangleBuffer = RenderSystem.getDevice().createBuffer(() -> "Monvhua mirror split triangle", 40, vertices);
		}
		return splitTriangleBuffer;
	}

	private static void putPosition(ByteBuffer buffer, float x, float y, float z) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(z);
	}

	private static void putTexturedPosition(ByteBuffer buffer, float x, float y, float u, float v) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(0.0F);
		buffer.putFloat(u);
		buffer.putFloat(v);
	}

	private static float clamp01(float value) {
		if (value < 0.0F) return 0.0F;
		if (value > 1.0F) return 1.0F;
		return value;
	}

	public static void cleanup() {
		if (splitTriangleBuffer != null) {
			splitTriangleBuffer.close();
			splitTriangleBuffer = null;
		}
		if (viewportRectBuffer != null) {
			viewportRectBuffer.close();
			viewportRectBuffer = null;
		}
	}
}
