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

/**
 * 躯干特殊模型渲染器，用于在物品展示/掉落物上下文中渲染自定义躯干模型。
 * 渲染时先关闭jacket层绘制躯干本体，再尝试体素渲染jacket层，失败时回退到标准jacket渲染。
 */
public class TorsoSpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final TorsoModel model;

    public TorsoSpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new TorsoModel(entityModels.getModelPart(ModModelLayers.TORSO));
    }

    /**
     * 渲染躯干模型：先隐藏jacket层绘制躯干本体，再尝试通过体素渲染器绘制jacket层，
     * 若体素渲染失败则回退到标准的jacket层渲染。
     */
    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        ModelPart torso = model.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart jacket = torso.getChild(EntityModelPartNames.JACKET);
        // 先隐藏jacket，绘制不含外层的躯干本体
        jacket.visible = false;
        model.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        // 恢复jacket可见性，后续用于体素渲染或标准外层渲染
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
