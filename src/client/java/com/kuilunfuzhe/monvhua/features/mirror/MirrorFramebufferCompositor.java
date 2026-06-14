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
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

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

	private static GpuBuffer splitTriangleBuffer;

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

	public static void cleanup() {
		if (splitTriangleBuffer != null) {
			splitTriangleBuffer.close();
			splitTriangleBuffer = null;
		}
	}
}
