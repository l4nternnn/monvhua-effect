package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import com.kuilunfuzhe.monvhua.features.forensics.FrameResourcePool;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/** Each deferred preview owns a separate color/depth target until GUI composition finishes. */
public final class EvidenceViewportRenderer extends SpecialGuiElementRenderer<EvidenceViewportRenderState> {
    private final FrameResourcePool<Target> targets;
    private boolean rendered;

    public EvidenceViewportRenderer(VertexConsumerProvider.Immediate consumers) { super(consumers);targets=new FrameResourcePool<>(()->new Target(consumers),Target::close); }
    @Override public Class<EvidenceViewportRenderState> getElementClass() { return EvidenceViewportRenderState.class; }
    @Override protected String getName() { return "evidence viewport"; }

    @Override public void render(EvidenceViewportRenderState state, GuiRenderState gui, int scale) {
        rendered=true;targets.acquire().render(state,gui,scale);
    }

    public void beginFrame(){targets.beginFrame();rendered=false;}
    public boolean hasRendered(){return rendered;}
    @Override protected void render(EvidenceViewportRenderState state,MatrixStack ignored){throw new IllegalStateException("Viewport draws must use an independent target");}
    @Override public void close(){try{targets.close();}finally{super.close();}}

    private static final class Target extends SpecialGuiElementRenderer<EvidenceViewportRenderState> {
        private RawProjectionMatrix cameraProjection;
        Target(VertexConsumerProvider.Immediate consumers){super(consumers);}
        @Override public Class<EvidenceViewportRenderState> getElementClass(){return EvidenceViewportRenderState.class;}
        @Override protected String getName(){return "evidence viewport target";}
        @Override public void render(EvidenceViewportRenderState state,GuiRenderState gui,int scale){
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

        @Override protected void render(EvidenceViewportRenderState state, MatrixStack ignored) {
            if (cameraProjection == null) cameraProjection = new RawProjectionMatrix("evidence viewport");
            RenderSystem.setProjectionMatrix(cameraProjection.set(state.frame().projection()),
                    ProjectionType.PERSPECTIVE);
            MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.PLAYER_SKIN);
            MatrixStack matrices = new MatrixStack();
            matrices.multiplyPositionMatrix(state.frame().view());
            matrices.peek().getNormalMatrix().set(state.frame().view()).invert().transpose();
            state.scene().render(matrices, vertexConsumers);
            vertexConsumers.draw();
        }

        @Override public void close() {
            try { super.close(); } finally {
                if (cameraProjection != null) cameraProjection.close();
                cameraProjection = null;
            }
        }
    }
}
