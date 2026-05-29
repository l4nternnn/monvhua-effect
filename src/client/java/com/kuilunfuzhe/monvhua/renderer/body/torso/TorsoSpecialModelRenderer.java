package com.kuilunfuzhe.monvhua.renderer.body.torso;

import com.mojang.serialization.MapCodec;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.torso.TorsoModel;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.special.BodyPartSpecialModelRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import java.util.Set;

public class TorsoSpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final TorsoModel model;

    public TorsoSpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new TorsoModel(entityModels.getModelPart(ModModelLayers.TORSO));
    }

    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        ModelPart torso = model.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart jacket = torso.getChild(EntityModelPartNames.JACKET);
        jacket.visible = false;
        model.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        jacket.visible = true;

        matrices.push();
        model.getRootPart().applyTransform(matrices);
        torso.applyTransform(matrices);
        matrices.push();
        jacket.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderJacket(matrices, vertexConsumers.getBuffer(renderLayer),
                data.texture(), light, overlay);
        matrices.pop();
        if (!renderedVoxelLayer) {
            jacket.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
        }
        matrices.pop();
    }

    // 实现 collectVertices 方法
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
            return new TorsoSpecialModelRenderer(entityModels);
        }
    }
}
