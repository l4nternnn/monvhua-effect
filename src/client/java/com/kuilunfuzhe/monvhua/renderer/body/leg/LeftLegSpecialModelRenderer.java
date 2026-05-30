package com.kuilunfuzhe.monvhua.renderer.body.leg;

import com.mojang.serialization.MapCodec;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.leg.LeftLegModel;
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
 * 左腿特殊模型渲染器，用于在物品展示/掉落物上下文中渲染自定义左腿模型。
 * 渲染时先关闭pants层绘制腿部本体，再尝试体素渲染pants层，失败时回退到标准pants渲染。
 */
public class LeftLegSpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final LeftLegModel model;

    public LeftLegSpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new LeftLegModel(entityModels.getModelPart(ModModelLayers.LEFT_LEG));
    }

    /**
     * 渲染左腿模型：先隐藏pants层绘制腿部本体，再尝试通过体素渲染器绘制pants层，
     * 若体素渲染失败则回退到标准的pants层渲染。
     */
    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        ModelPart leg = model.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart pants = leg.getChild("left_pants");
        // 先隐藏pants，绘制不含外层的腿部本体
        pants.visible = false;
        model.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        // 恢复pants可见性，后续用于体素渲染或标准外层渲染
        pants.visible = true;

        matrices.push();
        model.getRootPart().applyTransform(matrices);
        leg.applyTransform(matrices);
        matrices.push();
        pants.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderLeftPants(matrices, vertexConsumers.getBuffer(renderLayer),
                data.texture(), light, overlay);
        matrices.pop();
        if (!renderedVoxelLayer) {
            pants.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
        }
        matrices.pop();
    }

    /**
     * 收集模型顶点，用于物品展示上下文中计算包围盒。
     */
    @Override
    public void collectVertices(Set<Vector3f> vertices) {
        MatrixStack matrixStack = new MatrixStack();
        // 将模型原点平移到方块中心，使渲染位置与物品展示一致
        matrixStack.translate(0.5F, 1.0F, 0.5F);
        // 镜像翻转以匹配物品展示时的坐标系
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
            return new LeftLegSpecialModelRenderer(entityModels);
        }
    }
}
