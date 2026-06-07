package com.kuilunfuzhe.monvhua.renderer.body.special;

import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.TorsoBendFollower;
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
        ModelPart waist = getChild(activeModel.body, CombinedBodyModelData.WAIST);
        ModelPart waistJacket = getChild(waist, CombinedBodyModelData.WAIST_JACKET);
        ModelPart leftForearm = getChild(activeModel.leftArm, CombinedBodyModelData.LEFT_FOREARM);
        ModelPart leftForearmSleeve = getChild(leftForearm, CombinedBodyModelData.LEFT_FOREARM_SLEEVE);
        ModelPart rightForearm = getChild(activeModel.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
        ModelPart rightForearmSleeve = getChild(rightForearm, CombinedBodyModelData.RIGHT_FOREARM_SLEEVE);
        ModelPart leftLowerLeg = getChild(activeModel.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
        ModelPart leftLowerPants = getChild(leftLowerLeg, CombinedBodyModelData.LEFT_LOWER_PANTS);
        ModelPart rightLowerLeg = getChild(activeModel.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
        ModelPart rightLowerPants = getChild(rightLowerLeg, CombinedBodyModelData.RIGHT_LOWER_PANTS);

        OuterVisibility outerVisibility = new OuterVisibility(activeModel.hat, activeModel.jacket,
                activeModel.leftSleeve, activeModel.rightSleeve, activeModel.leftPants, activeModel.rightPants,
                waistJacket, leftForearmSleeve, rightForearmSleeve, leftLowerPants, rightLowerPants);

        // 隐藏所有外层，绘制不含外层的身体本体
        outerVisibility.hide();
        activeModel.render(matrices, vertexConsumer, light, overlay);

        // 恢复所有外层可见性
        outerVisibility.restore();

        matrices.push();
        activeModel.getRootPart().applyTransform(matrices);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.hat,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderHeadHat(m, v, data.texture(), light, overlay),
                activeModel.head);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.jacket,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerUpperJacket(m, v, data.texture(), light, overlay),
                activeModel.body);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, waistJacket,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLowerJacket(m, v, data.texture(), light, overlay),
                activeModel.body, waist);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.leftSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerUpperLeftSleeve(m, v, data.texture(), light, overlay, slim),
                activeModel.leftArm);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, leftForearmSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLowerLeftSleeve(m, v, data.texture(), light, overlay, slim),
                activeModel.leftArm, leftForearm);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.rightSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerUpperRightSleeve(m, v, data.texture(), light, overlay, slim),
                activeModel.rightArm);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, rightForearmSleeve,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLowerRightSleeve(m, v, data.texture(), light, overlay, slim),
                activeModel.rightArm, rightForearm);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.leftPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerUpperLeftPants(m, v, data.texture(), light, overlay),
                activeModel.leftLeg);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, leftLowerPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLowerLeftPants(m, v, data.texture(), light, overlay),
                activeModel.leftLeg, leftLowerLeg);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, activeModel.rightPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerUpperRightPants(m, v, data.texture(), light, overlay),
                activeModel.rightLeg);
        renderVoxelLayer(matrices, vertexConsumers, renderLayer, light, overlay, rightLowerPants,
                (m, v) -> SkinOuterLayerVoxelRenderer.renderPlayerLowerRightPants(m, v, data.texture(), light, overlay),
                activeModel.rightLeg, rightLowerLeg);
        matrices.pop();

        matrices.pop();
    }

    /**
     * 渲染单个部位的外层：应用父部件变换→应用外层变换→尝试体素渲染，
     * 若体素渲染失败（返回false），则回退到标准的outer层渲染。
     */
    private void renderVoxelLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, RenderLayer renderLayer,
                                  int light, int overlay, ModelPart outer, VoxelRenderCall voxelRenderCall,
                                  ModelPart... ancestors) {
        if (outer == null || !outer.visible) {
            return;
        }

        matrices.push();
        for (ModelPart ancestor : ancestors) {
            if (ancestor == null) {
                matrices.pop();
                return;
            }
            ancestor.applyTransform(matrices);
        }
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

    private static ModelPart getChild(ModelPart part, String childName) {
        return part != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private record OuterVisibility(ModelPart[] parts, boolean[] visible) {
        private OuterVisibility(ModelPart... parts) {
            this(parts, new boolean[parts.length]);
            for (int i = 0; i < parts.length; i++) {
                this.visible[i] = parts[i] != null && parts[i].visible;
            }
        }

        private void hide() {
            for (ModelPart part : this.parts) {
                if (part != null) {
                    part.visible = false;
                }
            }
        }

        private void restore() {
            for (int i = 0; i < this.parts.length; i++) {
                if (this.parts[i] != null) {
                    this.parts[i].visible = this.visible[i];
                }
            }
        }
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
        applyTorsoPose(model, data);
        applyPartPose(model.head, data, "head");
        applyPartPose(model.leftArm, data, "left_arm");
        applyPartPose(model.rightArm, data, "right_arm");
        applyPartPose(model.leftLeg, data, "left_leg");
        applyPartPose(model.rightLeg, data, "right_leg");
    }

    private static void applyTorsoPose(PlayerEntityModel model, NbtCompound data) {
        ModelPart blendPart = getBlendPart(model.body, "torso");
        if (blendPart != null) {
            blendPart.visible = false;
        }
        TorsoBendFollower.applyPose(model,
                data.getFloat("pose_torso_pitch", 0.0F),
                data.getFloat("pose_torso_yaw", 0.0F),
                data.getFloat("pose_torso_roll", 0.0F),
                Math.max(0.1F, data.getFloat("pose_torso_scale", 1.0F)));
        if (hasBendPose(data, "torso")) {
            if (blendPart != null) {
                blendPart.visible = true;
            }
            TorsoBendFollower.apply(model,
                    data.getFloat("bend_torso_pitch", 0.0F),
                    data.getFloat("bend_torso_yaw", 0.0F),
                    data.getFloat("bend_torso_roll", 0.0F));
        }
    }

    private static void applyPartPose(ModelPart part, NbtCompound data, String partName) {
        applyPose(part, data, partName);
        ModelPart blendPart = getBlendPart(part, partName);
        if (blendPart != null) {
            blendPart.visible = false;
        }
        ModelPart bendPart = getBendPart(part, partName);
        if (bendPart != null) {
            applyBendPose(bendPart, data, partName);
            if (blendPart != null && hasBendPose(data, partName)) {
                blendPart.visible = true;
                applyHalfBendPose(blendPart, data, partName);
            }
        }
    }

    private static ModelPart getBendPart(ModelPart part, String partName) {
        String childName = switch (partName) {
            case "torso" -> CombinedBodyModelData.WAIST;
            case "left_arm" -> CombinedBodyModelData.LEFT_FOREARM;
            case "right_arm" -> CombinedBodyModelData.RIGHT_FOREARM;
            case "left_leg" -> CombinedBodyModelData.LEFT_LOWER_LEG;
            case "right_leg" -> CombinedBodyModelData.RIGHT_LOWER_LEG;
            default -> null;
        };
        return childName != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private static ModelPart getBlendPart(ModelPart part, String partName) {
        String childName = switch (partName) {
            case "torso" -> CombinedBodyModelData.WAIST_BLEND;
            case "left_arm" -> CombinedBodyModelData.LEFT_ELBOW_BLEND;
            case "right_arm" -> CombinedBodyModelData.RIGHT_ELBOW_BLEND;
            case "left_leg" -> CombinedBodyModelData.LEFT_KNEE_BLEND;
            case "right_leg" -> CombinedBodyModelData.RIGHT_KNEE_BLEND;
            default -> null;
        };
        return childName != null && part.hasChild(childName) ? part.getChild(childName) : null;
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

    private static void applyBendPose(ModelPart part, NbtCompound data, String partName) {
        float degreesToRadians = (float) (Math.PI / 180.0);
        part.pitch += data.getFloat("bend_" + partName + "_pitch", 0.0F) * degreesToRadians;
        part.yaw += data.getFloat("bend_" + partName + "_yaw", 0.0F) * degreesToRadians;
        part.roll += data.getFloat("bend_" + partName + "_roll", 0.0F) * degreesToRadians;
    }

    private static void applyHalfBendPose(ModelPart part, NbtCompound data, String partName) {
        float degreesToRadians = (float) (Math.PI / 180.0);
        part.pitch += data.getFloat("bend_" + partName + "_pitch", 0.0F) * 0.5F * degreesToRadians;
        part.yaw += data.getFloat("bend_" + partName + "_yaw", 0.0F) * 0.5F * degreesToRadians;
        part.roll += data.getFloat("bend_" + partName + "_roll", 0.0F) * 0.5F * degreesToRadians;
    }

    private static boolean hasBendPose(NbtCompound data, String partName) {
        return data.getFloat("bend_" + partName + "_pitch", 0.0F) != 0.0F
                || data.getFloat("bend_" + partName + "_yaw", 0.0F) != 0.0F
                || data.getFloat("bend_" + partName + "_roll", 0.0F) != 0.0F;
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
