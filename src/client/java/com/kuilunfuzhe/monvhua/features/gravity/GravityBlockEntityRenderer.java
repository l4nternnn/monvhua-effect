package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class GravityBlockEntityRenderer extends EntityRenderer<GravityBlockEntity, GravityBlockEntityRenderer.GravityBlockRenderState> {
    public GravityBlockEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public GravityBlockRenderState createRenderState() {
        return new GravityBlockRenderState();
    }

    @Override
    public void updateRenderState(GravityBlockEntity entity, GravityBlockRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.blockState = entity.getBlockState();
        state.pitch = lerp(entity.prevRenderPitch, entity.renderPitch, tickDelta);
        state.roll = lerp(entity.prevRenderRoll, entity.renderRoll, tickDelta);
    }

    @Override
    public void render(GravityBlockRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.translate(-0.5D, 0.0D, -0.5D);
        matrices.translate(0.5D, 0.5D, 0.5D);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));
        matrices.translate(-0.5D, -0.5D, -0.5D);
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(state.blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(state, matrices, vertexConsumers, light);
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    public static class GravityBlockRenderState extends EntityRenderState {
        public BlockState blockState = Blocks.STONE.getDefaultState();
        public float pitch;
        public float roll;
    }
}
