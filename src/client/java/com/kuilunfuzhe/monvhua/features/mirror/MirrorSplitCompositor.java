package com.kuilunfuzhe.monvhua.features.mirror;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class MirrorSplitCompositor {
	private static final int FULL_WHITE = 0xFFFFFFFF;
	private static final RenderPipeline MIRROR_TRIANGLE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
			.withLocation(Identifier.of(MonvhuaMod.MOD_ID, "pipeline/mirror_diagonal_split"))
			.withoutBlend()
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
			.build()
	);

	private MirrorSplitCompositor() {
	}

	public static void renderDiagonalSplit(DrawContext context, GpuTextureView mirrorTexture) {
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		if (width <= 0 || height <= 0) return;

		context.state.addSimpleElement(new MirrorTriangleRenderState(
			MIRROR_TRIANGLE_PIPELINE,
			TextureSetup.of(mirrorTexture),
			new Matrix3x2f(context.getMatrices()),
			width,
			height,
			FULL_WHITE,
			new ScreenRect(0, 0, width, height)
		));
	}

	private record MirrorTriangleRenderState(
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		Matrix3x2f pose,
		int width,
		int height,
		int color,
		ScreenRect bounds
	) implements SimpleGuiElementRenderState {
		@Override
		public void setupVertices(VertexConsumer vertices, float depth) {
			vertex(vertices, 0.0F, 0.0F, 0.0F, 1.0F, depth);
			vertex(vertices, width, 0.0F, 1.0F, 1.0F, depth);
			vertex(vertices, 0.0F, height, 0.0F, 0.0F, depth);
		}

		@Override
		public ScreenRect scissorArea() {
			return null;
		}

		private void vertex(VertexConsumer vertices, float x, float y, float u, float v, float depth) {
			vertices.vertex(pose, x, y, depth).texture(u, v).color(color);
		}
	}
}
