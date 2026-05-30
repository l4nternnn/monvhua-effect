package com.kuilunfuzhe.monvhua.renderer.body.arm;

import com.mojang.serialization.MapCodec;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.arm.RightArmModel;
import com.kuilunfuzhe.monvhua.model.arm.RightArmSlimModel;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.special.BodyPartSpecialModelRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.Set;

/**
 * 右手特殊模型渲染器，用于在物品展示/掉落物上下文中渲染自定义右手模型。
 * 支持标准（default）和纤细（slim）两种手臂模型，通过Data中的armModel字段切换。
 * 渲染时先关闭sleeve层绘制身体本体，再尝试体素渲染外层面，失败时回退到标准外层渲染。
 */
public class RightArmSpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final RightArmModel model;
    private final RightArmSlimModel slimModel;

    public RightArmSpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new RightArmModel(entityModels.getModelPart(ModModelLayers.RIGHT_ARM));
        this.slimModel = new RightArmSlimModel(entityModels.getModelPart(ModModelLayers.RIGHT_ARM_SLIM));
    }

    /**
     * 渲染右手模型：先隐藏sleeve层绘制手臂本体，再尝试通过体素渲染器绘制外层面，
     * 若体素渲染失败则回退到标准的sleeve层渲染。
     */
    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        boolean slim = "slim".equals(data.armModel());
        SkullBlockEntityModel activeModel = slim ? slimModel : model;
        ModelPart arm = activeModel.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart sleeve = arm.getChild("right_sleeve");
        // 先隐藏sleeve，绘制不含外层的身体本体
        sleeve.visible = false;
        activeModel.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
        // 恢复sleeve可见性，后续用于体素渲染或标准外层渲染
        sleeve.visible = true;

        matrices.push();
        activeModel.getRootPart().applyTransform(matrices);
        arm.applyTransform(matrices);
        matrices.push();
        sleeve.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderRightSleeve(matrices, vertexConsumers.getBuffer(renderLayer),
                data.texture(), light, overlay, slim);
        matrices.pop();
        if (!renderedVoxelLayer) {
            sleeve.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
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
            return new RightArmSpecialModelRenderer(entityModels);
        }
    }
}
