package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/** Reuses Minecraft's GUI color/depth targets, resize and premultiplied-alpha composition. */
public final class PoseViewportRenderer extends SpecialGuiElementRenderer<PoseViewportRenderState> {
    private RawProjectionMatrix cameraProjection;
    private boolean rendered;

    public PoseViewportRenderer(VertexConsumerProvider.Immediate consumers) { super(consumers); }
    @Override public Class<PoseViewportRenderState> getElementClass() { return PoseViewportRenderState.class; }
    @Override protected String getName() { return "body pose viewport"; }

    @Override public void render(PoseViewportRenderState state, GuiRenderState gui, int scale) {
        rendered = true;
        var color = RenderSystem.outputColorTextureOverride;
        var depth = RenderSystem.outputDepthTextureOverride;
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var type = RenderSystem.getProjectionType();
        var lights = RenderSystem.getShaderLights();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            super.render(state, gui, scale);
        } finally {
            modelView.popMatrix();
            RenderSystem.outputColorTextureOverride = color;
            RenderSystem.outputDepthTextureOverride = depth;
            RenderSystem.setProjectionMatrix(projection, type);
            RenderSystem.setShaderLights(lights);
        }
    }

    @Override protected void render(PoseViewportRenderState state, MatrixStack ignored) {
        if (cameraProjection == null) cameraProjection = new RawProjectionMatrix("body pose viewport");
        RenderSystem.setProjectionMatrix(cameraProjection.set(state.frame().projection()),
                state.frame().orthographic() ? ProjectionType.ORTHOGRAPHIC : ProjectionType.PERSPECTIVE);
        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.PLAYER_SKIN);
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(state.frame().view());
        matrices.peek().getNormalMatrix().set(state.frame().view()).invert().transpose();
        state.scene().render(matrices, vertexConsumers);
        vertexConsumers.draw();
    }

    public boolean hasRendered() { return rendered; }

    @Override public void close() {
        try { super.close(); } finally {
            if (cameraProjection != null) cameraProjection.close();
            cameraProjection = null;
        }
    }
}
