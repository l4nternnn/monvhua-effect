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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

import java.util.Set;

/**
 * 全身组合特殊模型渲染器，使用PlayerEntityModel一次性渲染完整的玩家身体模型。
 * 支持标准（default）和纤细（slim）两种身材，通过Data中的armModel字段切换。
 *
 * <h3>渲染流程</h3>
 * <ol>
 *   <li>保存6个outer层（hat/jacket/leftSleeve/rightSleeve/leftPants/rightPants）的可见性</li>
 *   <li>全部隐藏后先渲染身体本体</li>
 *   <li>恢复外层可见性</li>
 *   <li>逐个渲染voxel外层（先尝试体素渲染，失败回退标准渲染）</li>
 * </ol>
 */
public class CombinedBodySpecialModelRenderer extends BodyPartSpecialModelRenderer {
    private final PlayerEntityModel model;
    private final PlayerEntityModel slimModel;

    public CombinedBodySpecialModelRenderer(LoadedEntityModels entityModels) {
        super(entityModels);
        this.model = new PlayerEntityModel(entityModels.getModelPart(ModModelLayers.COMBINED_BODY), false);
        this.slimModel = new PlayerEntityModel(entityModels.getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
    }

    /**
     * 渲染全身组合模型：
     * 先保存并隐藏所有6个outer层（hat/jacket/双sleeve/双pants）以绘制身体本体，
     * 然后恢复可见性并逐个渲染各部位的外层（先尝试体素渲染，失败回退标准渲染）。
     */
    @Override
    protected void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               RenderLayer renderLayer, int light, int overlay, Data data) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);
        boolean slim = "slim".equals(data.armModel());
        PlayerEntityModel activeModel = slim ? slimModel : model;
        resetModel(activeModel);
        applyPoseData(activeModel, data.customData());

        matrices.push();
        applyPlacementTransform(matrices, data.customData());

        // 保存6个outer层的当前可见性，用于后续恢复
        boolean hatVisible = activeModel.hat.visible;
        boolean jacketVisible = activeModel.jacket.visible;
        boolean leftSleeveVisible = activeModel.leftSleeve.visible;
        boolean rightSleeveVisible = activeModel.rightSleeve.visible;
        boolean leftPantsVisible = activeModel.leftPants.visible;
        boolean rightPantsVisible = activeModel.rightPants.visible;

        // 隐藏所有外层，绘制不含外层的身体本体
        activeModel.hat.visible = false;
        activeModel.jacket.visible = false;
        activeModel.leftSleeve.visible = false;
        activeModel.rightSleeve.visible = false;
        activeModel.leftPants.visible = false;
        activeModel.rightPants.visible = false;
        activeModel.render(matrices, vertexConsumer, light, overlay);

        // 恢复所有外层可见性
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

        matrices.pop();
    }

    /**
     * 渲染单个部位的外层：应用父部件变换→应用外层变换→尝试体素渲染，
     * 若体素渲染失败（返回false），则回退到标准的outer层渲染。
     */
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
            // 体素渲染失败时回退到标准外层渲染
            outer.render(matrices, vertexConsumers.getBuffer(renderLayer), light, overlay);
        }
        matrices.pop();
    }

    /** 体素渲染回调接口：执行体素渲染并返回是否成功 */
    private interface VoxelRenderCall {
        boolean render(MatrixStack matrices, VertexConsumer vertexConsumer);
    }

    private static void resetModel(PlayerEntityModel model) {
        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
    }

    private static void applyPoseData(PlayerEntityModel model, NbtCompound data) {
        if (data == null) {
            return;
        }
        applyPose(model.head, data, "head");
        applyPose(model.body, data, "torso");
        applyPose(model.leftArm, data, "left_arm");
        applyPose(model.rightArm, data, "right_arm");
        applyPose(model.leftLeg, data, "left_leg");
        applyPose(model.rightLeg, data, "right_leg");
    }

    private static void applyPose(ModelPart part, NbtCompound data, String partName) {
        float degreesToRadians = (float) (Math.PI / 180.0);
        part.pitch += data.getFloat("pose_" + partName + "_pitch", 0.0F) * degreesToRadians;
        part.yaw += data.getFloat("pose_" + partName + "_yaw", 0.0F) * degreesToRadians;
        part.roll += data.getFloat("pose_" + partName + "_roll", 0.0F) * degreesToRadians;
        float scale = Math.max(0.1F, data.getFloat("pose_" + partName + "_scale", 1.0F));
        part.xScale *= scale;
        part.yScale *= scale;
        part.zScale *= scale;
    }

    private static void applyPlacementTransform(MatrixStack matrices, NbtCompound data) {
        if (data == null) {
            return;
        }
        matrices.translate(
                data.getFloat("pose_model_offset_x", 0.0F),
                data.getFloat("pose_model_offset_y", 0.0F),
                data.getFloat("pose_model_offset_z", 0.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(data.getFloat("pose_model_pitch", 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-data.getFloat("pose_model_yaw", 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(data.getFloat("pose_model_roll", 0.0F)));
        float scale = Math.max(0.1F, data.getFloat("pose_model_scale", 1.0F));
        matrices.scale(scale, scale, scale);
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
