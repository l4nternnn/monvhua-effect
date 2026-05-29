package com.kuilunfuzhe.monvhua.renderer.body.special;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.Set;

public class CombinedBodySpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final PlayerEntityModel model;
    private final PlayerEntityModel slimModel;

    public CombinedBodySpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new PlayerEntityModel(entityModels.getModelPart(ModModelLayers.COMBINED_BODY), false);
        this.slimModel = new PlayerEntityModel(entityModels.getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
    }

    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);
        boolean slim = "slim".equals(data.armModel());
        PlayerEntityModel activeModel = slim ? slimModel : model;

        boolean hatVisible = activeModel.hat.visible;
        boolean jacketVisible = activeModel.jacket.visible;
        boolean leftSleeveVisible = activeModel.leftSleeve.visible;
        boolean rightSleeveVisible = activeModel.rightSleeve.visible;
        boolean leftPantsVisible = activeModel.leftPants.visible;
        boolean rightPantsVisible = activeModel.rightPants.visible;

        activeModel.hat.visible = false;
        activeModel.jacket.visible = false;
        activeModel.leftSleeve.visible = false;
        activeModel.rightSleeve.visible = false;
        activeModel.leftPants.visible = false;
        activeModel.rightPants.visible = false;
        activeModel.render(matrices, vertexConsumer, light, overlay);

        activeModel.hat.visible = hatVisible;
        activeModel.jacket.visible = jacketVisible;
        activeModel.leftSleeve.visible = leftSleeveVisible;
        activeModel.rightSleeve.visible = rightSleeveVisible;
        activeModel.leftPants.visible = leftPantsVisible;
        activeModel.rightPants.visible = rightPantsVisible;

        matrices.push();
        activeModel.getRootPart().applyTransform(matrices);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.head, activeModel.hat,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderHeadHat(m, v, data.texture(), light, overlay));
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.body, activeModel.jacket,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerJacket(m, v, data.texture(), light, overlay));
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.leftArm, activeModel.leftSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLeftSleeve(m, v, data.texture(), light, overlay, slim));
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.rightArm, activeModel.rightSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerRightSleeve(m, v, data.texture(), light, overlay, slim));
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.leftLeg, activeModel.leftPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLeftPants(m, v, data.texture(), light, overlay));
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.rightLeg, activeModel.rightPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerRightPants(m, v, data.texture(), light, overlay));
        matrices.pop();
    }

    private void renderVoxelLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, RenderLayer renderLayer,
                                  int light, int overlay, ModelPart parent, ModelPart outer,
                                  VoxelRenderCall voxelRenderCall) {
        if (!outer.visible) {
            return;
        }

        matrices.push();
        parent.applyTransform(matrices);
        matrices.push();
        outer.applyTransform(matrices);
        boolean renderedVoxelLayer = voxelRenderCall.render(matrices, vertexConsumers.getBuffer(renderLayer));
        matrices.pop();
        if (!renderedVoxelLayer) {
            outer.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
        }
        matrices.pop();
    }

    private interface VoxelRenderCall {
        boolean render(MatrixStack matrices, VertexConsumer vertexConsumer);
    }

    @Override
    public void collectVertices(Set<Vector3f> vertices) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.translate(0.5F, 1.0F, 0.5F);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.getRootPart().collectVertices(matrixStack, vertices);
    }

    public static class Unbaked extends BodyPartSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public MapCodec<Unbaked> getCodec() {
            return CODEC;
        }

        @Override
        public BodyPartSpecialModelRenderer bake(LoadedEntityModels entityModels) {
            return new CombinedBodySpecialModelRenderer(entityModels);
        }
    }
}
