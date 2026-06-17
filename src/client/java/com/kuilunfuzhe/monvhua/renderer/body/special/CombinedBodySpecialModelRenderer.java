package com.kuilunfuzhe.monvhua.renderer.body.special;

import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.TorsoBendFollower;
import com.kuilunfuzhe.monvhua.features.paint.ModelPaintData;
import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import com.kuilunfuzhe.monvhua.features.paint.SkinTexturePixels;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
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
    private static final float PAINT_SURFACE_OFFSET = 0.002F;
    private static final float OUTER_LAYER_PAINT_OFFSET = 0.5F / 16.0F + PAINT_SURFACE_OFFSET;
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
        if (renderTrueSkeletalModel(matrices, vertexConsumers, light, data)) {
            return;
        }

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

        renderModelPaint(matrices, vertexConsumers, activeModel, slim, data.texture(), data.customData(), light);

        matrices.pop();
    }

    private boolean renderTrueSkeletalModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Data data) {
        NbtCompound customData = data.customData();
        if (customData == null || !"true_skeletal".equals(customData.getString("body_pose_mode", ""))) {
            return false;
        }

        Map<String, float[]> rotations = new HashMap<>();
        Map<String, Float> scales = new HashMap<>();
        NbtList bones = customData.getListOrEmpty("true_skeletal_bones");
        for (int i = 0; i < bones.size(); i++) {
            NbtCompound bone = bones.getCompound(i).orElse(null);
            if (bone == null) {
                continue;
            }
            String name = bone.getString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            float pitch = bone.getFloat("pitch", 0.0F);
            float yaw = bone.getFloat("yaw", 0.0F);
            float roll = bone.getFloat("roll", 0.0F);
            float scale = Math.max(0.1F, bone.getFloat("scale", 1.0F));
            if (pitch != 0.0F || yaw != 0.0F || roll != 0.0F) {
                rotations.put(name, new float[] { pitch, yaw, roll });
            }
            if (scale != 1.0F) {
                scales.put(name, scale);
            }
        }

        matrices.push();
        matrices.scale(1.0F, -1.0F, 1.0F);
        applyTrueSkeletalPlacementTransform(matrices, customData);
        boolean rendered = BodyPoseSkeletalPreviewRenderer.render(matrices, vertexConsumers, data.texture(), light,
                rotations, scales, Set.of());
        matrices.pop();
        return rendered;
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

    private void renderModelPaint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, PlayerEntityModel model,
                                  boolean slim, net.minecraft.util.Identifier texture, NbtCompound customData, int light) {
        if (customData == null || !customData.contains(ModelPaintData.MODEL_PAINT_KEY)) {
            return;
        }
        VertexConsumer vertices = vertexConsumers.getBuffer(PaintRenderLayers.paintOverlay());
        SkinTexturePixels skinPixels = SkinTexturePixels.get(texture);
        for (ModelPaintData.ModelFace face : ModelPaintData.readFaces(customData, slim)) {
            OuterPaintTarget outerTarget = outerPaintTarget(model, slim, face.surface());
            if (outerTarget != null && skinPixels != null) {
                matrices.push();
                model.getRootPart().applyTransform(matrices);
                for (ModelPart part : outerTarget.path()) {
                    if (part == null) {
                        matrices.pop();
                        return;
                    }
                    part.applyTransform(matrices);
                }
                renderOuterEdgePaint(vertices, matrices.peek().getPositionMatrix(), outerTarget, skinPixels,
                        face.face(), face.width(), face.height(), face.pixels(), light);
                matrices.pop();
                continue;
            }
            SurfaceTarget target = surfaceTarget(model, slim, face.surface());
            if (target == null) {
                continue;
            }
            matrices.push();
            model.getRootPart().applyTransform(matrices);
            for (ModelPart part : target.path()) {
                if (part == null) {
                    matrices.pop();
                    return;
                }
                part.applyTransform(matrices);
            }
            renderPaintFace(vertices, matrices.peek().getPositionMatrix(), target.cuboid(), target.paintOffset(),
                    face.face(), face.width(), face.height(), face.pixels(), light);
            matrices.pop();
        }
    }

    private OuterPaintTarget outerPaintTarget(PlayerEntityModel model, boolean slim, String surface) {
        OuterSurfaceKey key = OuterSurfaceKey.parse(surface);
        if (key == null) {
            return null;
        }
        ModelPart waist = getChild(model.body, CombinedBodyModelData.WAIST);
        ModelPart waistJacket = getChild(waist, CombinedBodyModelData.WAIST_JACKET);
        ModelPart leftForearm = getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM);
        ModelPart leftForearmSleeve = getChild(leftForearm, CombinedBodyModelData.LEFT_FOREARM_SLEEVE);
        ModelPart rightForearm = getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
        ModelPart rightForearmSleeve = getChild(rightForearm, CombinedBodyModelData.RIGHT_FOREARM_SLEEVE);
        ModelPart leftLowerLeg = getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
        ModelPart leftLowerPants = getChild(leftLowerLeg, CombinedBodyModelData.LEFT_LOWER_PANTS);
        ModelPart rightLowerLeg = getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
        ModelPart rightLowerPants = getChild(rightLowerLeg, CombinedBodyModelData.RIGHT_LOWER_PANTS);
        int armWidth = slim ? 3 : 4;
        int leftArmX = -1;
        int rightArmX = slim ? -2 : -3;
        return switch (key.baseSurface()) {
            case "head" -> new OuterPaintTarget(new ModelPart[]{model.head, model.hat}, key.sourceFace(),
                    new PixelBox(-4, -8, -4, 8, 8, 8), 32, 0, 0, true, true);
            case "body_upper" -> new OuterPaintTarget(new ModelPart[]{model.body, model.jacket}, key.sourceFace(),
                    new PixelBox(-4, 0, -2, 8, 6, 4), 16, 32, 0, true, false);
            case "body_lower" -> new OuterPaintTarget(new ModelPart[]{model.body, waist, waistJacket}, key.sourceFace(),
                    new PixelBox(-4, 0, -2, 8, 6, 4), 16, 32, 6, false, true);
            case "left_arm_upper" -> new OuterPaintTarget(new ModelPart[]{model.leftArm, model.leftSleeve}, key.sourceFace(),
                    new PixelBox(leftArmX, -2, -2, armWidth, 6, 4), 48, 48, 0, true, false);
            case "left_arm_lower" -> new OuterPaintTarget(new ModelPart[]{model.leftArm, leftForearm, leftForearmSleeve}, key.sourceFace(),
                    new PixelBox(leftArmX, 0, -2, armWidth, 6, 4), 48, 48, 6, false, true);
            case "right_arm_upper" -> new OuterPaintTarget(new ModelPart[]{model.rightArm, model.rightSleeve}, key.sourceFace(),
                    new PixelBox(rightArmX, -2, -2, armWidth, 6, 4), 40, 32, 0, true, false);
            case "right_arm_lower" -> new OuterPaintTarget(new ModelPart[]{model.rightArm, rightForearm, rightForearmSleeve}, key.sourceFace(),
                    new PixelBox(rightArmX, 0, -2, armWidth, 6, 4), 40, 32, 6, false, true);
            case "left_leg_upper" -> new OuterPaintTarget(new ModelPart[]{model.leftLeg, model.leftPants}, key.sourceFace(),
                    new PixelBox(-2, 0, -2, 4, 6, 4), 0, 48, 0, true, false);
            case "left_leg_lower" -> new OuterPaintTarget(new ModelPart[]{model.leftLeg, leftLowerLeg, leftLowerPants}, key.sourceFace(),
                    new PixelBox(-2, 0, -2, 4, 6, 4), 0, 48, 6, false, true);
            case "right_leg_upper" -> new OuterPaintTarget(new ModelPart[]{model.rightLeg, model.rightPants}, key.sourceFace(),
                    new PixelBox(-2, 0, -2, 4, 6, 4), 0, 32, 0, true, false);
            case "right_leg_lower" -> new OuterPaintTarget(new ModelPart[]{model.rightLeg, rightLowerLeg, rightLowerPants}, key.sourceFace(),
                    new PixelBox(-2, 0, -2, 4, 6, 4), 0, 32, 6, false, true);
            default -> null;
        };
    }

    private SurfaceTarget surfaceTarget(PlayerEntityModel model, boolean slim, String surface) {
        ModelPart waist = getChild(model.body, CombinedBodyModelData.WAIST);
        ModelPart waistJacket = getChild(waist, CombinedBodyModelData.WAIST_JACKET);
        ModelPart leftForearm = getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM);
        ModelPart leftForearmSleeve = getChild(leftForearm, CombinedBodyModelData.LEFT_FOREARM_SLEEVE);
        ModelPart rightForearm = getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
        ModelPart rightForearmSleeve = getChild(rightForearm, CombinedBodyModelData.RIGHT_FOREARM_SLEEVE);
        ModelPart leftLowerLeg = getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
        ModelPart leftLowerPants = getChild(leftLowerLeg, CombinedBodyModelData.LEFT_LOWER_PANTS);
        ModelPart rightLowerLeg = getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
        ModelPart rightLowerPants = getChild(rightLowerLeg, CombinedBodyModelData.RIGHT_LOWER_PANTS);
        float armWidth = slim ? 3.0F : 4.0F;
        float leftArmX = slim ? -1.0F : -1.0F;
        float rightArmX = slim ? -2.0F : -3.0F;
        return switch (surface) {
            case "head_outer" -> new SurfaceTarget(new ModelPart[]{model.head, model.hat}, new Cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), OUTER_LAYER_PAINT_OFFSET);
            case "body_upper_outer" -> new SurfaceTarget(new ModelPart[]{model.body, model.jacket}, new Cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "body_lower_outer" -> new SurfaceTarget(new ModelPart[]{model.body, waist, waistJacket}, new Cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "left_arm_upper_outer" -> new SurfaceTarget(new ModelPart[]{model.leftArm, model.leftSleeve}, new Cuboid(leftArmX, -2.0F, -2.0F, armWidth, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "left_arm_lower_outer" -> new SurfaceTarget(new ModelPart[]{model.leftArm, leftForearm, leftForearmSleeve}, new Cuboid(leftArmX, 0.0F, -2.0F, armWidth, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "right_arm_upper_outer" -> new SurfaceTarget(new ModelPart[]{model.rightArm, model.rightSleeve}, new Cuboid(rightArmX, -2.0F, -2.0F, armWidth, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "right_arm_lower_outer" -> new SurfaceTarget(new ModelPart[]{model.rightArm, rightForearm, rightForearmSleeve}, new Cuboid(rightArmX, 0.0F, -2.0F, armWidth, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "left_leg_upper_outer" -> new SurfaceTarget(new ModelPart[]{model.leftLeg, model.leftPants}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "left_leg_lower_outer" -> new SurfaceTarget(new ModelPart[]{model.leftLeg, leftLowerLeg, leftLowerPants}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "right_leg_upper_outer" -> new SurfaceTarget(new ModelPart[]{model.rightLeg, model.rightPants}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "right_leg_lower_outer" -> new SurfaceTarget(new ModelPart[]{model.rightLeg, rightLowerLeg, rightLowerPants}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), OUTER_LAYER_PAINT_OFFSET);
            case "head" -> new SurfaceTarget(new ModelPart[]{model.head}, new Cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F));
            case "body_upper" -> new SurfaceTarget(new ModelPart[]{model.body}, new Cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F));
            case "body_lower" -> new SurfaceTarget(new ModelPart[]{model.body, waist}, new Cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F));
            case "left_arm_upper" -> new SurfaceTarget(new ModelPart[]{model.leftArm}, new Cuboid(leftArmX, -2.0F, -2.0F, armWidth, 6.0F, 4.0F));
            case "left_arm_lower" -> new SurfaceTarget(new ModelPart[]{model.leftArm, leftForearm}, new Cuboid(leftArmX, 0.0F, -2.0F, armWidth, 6.0F, 4.0F));
            case "right_arm_upper" -> new SurfaceTarget(new ModelPart[]{model.rightArm}, new Cuboid(rightArmX, -2.0F, -2.0F, armWidth, 6.0F, 4.0F));
            case "right_arm_lower" -> new SurfaceTarget(new ModelPart[]{model.rightArm, rightForearm}, new Cuboid(rightArmX, 0.0F, -2.0F, armWidth, 6.0F, 4.0F));
            case "left_leg_upper" -> new SurfaceTarget(new ModelPart[]{model.leftLeg}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F));
            case "left_leg_lower" -> new SurfaceTarget(new ModelPart[]{model.leftLeg, leftLowerLeg}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F));
            case "right_leg_upper" -> new SurfaceTarget(new ModelPart[]{model.rightLeg}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F));
            case "right_leg_lower" -> new SurfaceTarget(new ModelPart[]{model.rightLeg, rightLowerLeg}, new Cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F));
            default -> null;
        };
    }

    private static void renderOuterEdgePaint(VertexConsumer vertices, Matrix4f matrix, OuterPaintTarget target,
                                             SkinTexturePixels skinPixels, Direction paintFace,
                                             int width, int height, int[] pixels, int light) {
        OuterPaintFace sourceFace = outerPaintFace(target, target.sourceFace());
        if (sourceFace == null) {
            return;
        }
        int renderWidth = Math.min(width, sourceFace.width());
        int renderHeight = Math.min(height, sourceFace.height());
        for (int y = 0; y < renderHeight; y++) {
            for (int x = 0; x < renderWidth; x++) {
                int color = pixels[y * width + x];
                if (color == 0 || !skinPixels.isOpaque(sourceFace.textureX() + x, sourceFace.textureY() + y)) {
                    continue;
                }
                renderOuterPixelPaint(vertices, matrix, sourceFace, skinPixels, paintFace, x, y, color, light);
            }
        }
    }

    private static void renderOuterPixelPaint(VertexConsumer vertices, Matrix4f matrix, OuterPaintFace sourceFace,
                                              SkinTexturePixels skinPixels, Direction paintFace,
                                              int x, int y, int color, int light) {
        PaintPoint p00 = sourceFace.origin()
                .add(sourceFace.uStep().multiply(x))
                .add(sourceFace.vStep().multiply(y));
        PaintPoint p10 = p00.add(sourceFace.uStep());
        PaintPoint p11 = p10.add(sourceFace.vStep());
        PaintPoint p01 = p00.add(sourceFace.vStep());
        PaintPoint offset = sourceFace.normal().multiply(0.5F + 0.002F * 16.0F);
        PaintPoint o00 = p00.add(offset);
        PaintPoint o10 = p10.add(offset);
        PaintPoint o11 = p11.add(offset);
        PaintPoint o01 = p01.add(offset);

        if (paintFace == sourceFace.face()) {
            emitPaintPointQuad(vertices, matrix, o00, o10, o11, o01, sourceFace.normal().direction(), color, light);
            return;
        }

        emitOuterEdgeIfVisible(vertices, matrix, sourceFace, skinPixels, paintFace, x, y,
                Edge.UP, o00, o10, p10, p00, sourceFace.vStep().negate(), color, light);
        emitOuterEdgeIfVisible(vertices, matrix, sourceFace, skinPixels, paintFace, x, y,
                Edge.RIGHT, o10, o11, p11, p10, sourceFace.uStep(), color, light);
        emitOuterEdgeIfVisible(vertices, matrix, sourceFace, skinPixels, paintFace, x, y,
                Edge.DOWN, o11, o01, p01, p11, sourceFace.vStep(), color, light);
        emitOuterEdgeIfVisible(vertices, matrix, sourceFace, skinPixels, paintFace, x, y,
                Edge.LEFT, o01, o00, p00, p01, sourceFace.uStep().negate(), color, light);
    }

    private static void emitOuterEdgeIfVisible(VertexConsumer vertices, Matrix4f matrix, OuterPaintFace sourceFace,
                                               SkinTexturePixels skinPixels, Direction paintFace,
                                               int x, int y, Edge edge,
                                               PaintPoint p0, PaintPoint p1, PaintPoint p2, PaintPoint p3,
                                               PaintPoint normal, int color, int light) {
        if (directionFromVector(normal) != paintFace || !isOuterEdge(sourceFace, skinPixels, x, y, edge)) {
            return;
        }
        PaintPoint sideNormal = normal.direction();
        PaintPoint paintOffset = sideNormal.multiply(PAINT_SURFACE_OFFSET * 16.0F);
        emitPaintPointQuad(vertices, matrix,
                p0.add(paintOffset), p1.add(paintOffset), p2.add(paintOffset), p3.add(paintOffset),
                sideNormal, color, light);
    }

    private static boolean isOuterEdge(OuterPaintFace sourceFace, SkinTexturePixels skinPixels, int x, int y, Edge edge) {
        return switch (edge) {
            case UP -> !isOuterFacePixelOpaque(sourceFace, skinPixels, x, y - 1);
            case RIGHT -> !isOuterFacePixelOpaque(sourceFace, skinPixels, x + 1, y);
            case DOWN -> !isOuterFacePixelOpaque(sourceFace, skinPixels, x, y + 1);
            case LEFT -> !isOuterFacePixelOpaque(sourceFace, skinPixels, x - 1, y);
        };
    }

    private static boolean isOuterFacePixelOpaque(OuterPaintFace sourceFace, SkinTexturePixels skinPixels, int x, int y) {
        if (x < 0 || y < 0 || x >= sourceFace.width() || y >= sourceFace.height()) {
            return false;
        }
        return skinPixels.isOpaque(sourceFace.textureX() + x, sourceFace.textureY() + y);
    }

    private static OuterPaintFace outerPaintFace(OuterPaintTarget target, Direction sourceFace) {
        for (OuterPaintFace face : outerPaintFaces(target)) {
            if (face.face() == sourceFace) {
                return face;
            }
        }
        return null;
    }

    private static OuterPaintFace[] outerPaintFaces(OuterPaintTarget target) {
        PixelBox box = target.box();
        int textureX = target.textureX();
        int sideTextureY = target.textureY() + box.depth() + target.textureYSegment();
        OuterPaintFace[] faces = new OuterPaintFace[6];
        int index = 0;
        if (target.renderStartCap()) {
            faces[index++] = new OuterPaintFace(Direction.UP, textureX + box.depth(), target.textureY(),
                    box.width(), box.depth(),
                    new PaintPoint(box.x(), box.y(), box.z() + box.depth()),
                    new PaintPoint(1.0F, 0.0F, 0.0F),
                    new PaintPoint(0.0F, 0.0F, -1.0F),
                    new PaintPoint(0.0F, -1.0F, 0.0F));
        }
        if (target.renderEndCap()) {
            faces[index++] = new OuterPaintFace(Direction.DOWN, textureX + box.depth() + box.width(), target.textureY(),
                    box.width(), box.depth(),
                    new PaintPoint(box.x(), box.y() + box.height(), box.z() + box.depth()),
                    new PaintPoint(1.0F, 0.0F, 0.0F),
                    new PaintPoint(0.0F, 0.0F, -1.0F),
                    new PaintPoint(0.0F, 1.0F, 0.0F));
        }
        faces[index++] = new OuterPaintFace(Direction.WEST, textureX, sideTextureY,
                box.depth(), box.height(),
                new PaintPoint(box.x(), box.y(), box.z() + box.depth()),
                new PaintPoint(0.0F, 0.0F, -1.0F),
                new PaintPoint(0.0F, 1.0F, 0.0F),
                new PaintPoint(-1.0F, 0.0F, 0.0F));
        faces[index++] = new OuterPaintFace(Direction.NORTH, textureX + box.depth(), sideTextureY,
                box.width(), box.height(),
                new PaintPoint(box.x(), box.y(), box.z()),
                new PaintPoint(1.0F, 0.0F, 0.0F),
                new PaintPoint(0.0F, 1.0F, 0.0F),
                new PaintPoint(0.0F, 0.0F, -1.0F));
        faces[index++] = new OuterPaintFace(Direction.EAST, textureX + box.depth() + box.width(), sideTextureY,
                box.depth(), box.height(),
                new PaintPoint(box.x() + box.width(), box.y(), box.z()),
                new PaintPoint(0.0F, 0.0F, 1.0F),
                new PaintPoint(0.0F, 1.0F, 0.0F),
                new PaintPoint(1.0F, 0.0F, 0.0F));
        faces[index++] = new OuterPaintFace(Direction.SOUTH, textureX + box.depth() + box.width() + box.depth(), sideTextureY,
                box.width(), box.height(),
                new PaintPoint(box.x() + box.width(), box.y(), box.z() + box.depth()),
                new PaintPoint(-1.0F, 0.0F, 0.0F),
                new PaintPoint(0.0F, 1.0F, 0.0F),
                new PaintPoint(0.0F, 0.0F, 1.0F));
        if (index == faces.length) {
            return faces;
        }
        OuterPaintFace[] compact = new OuterPaintFace[index];
        System.arraycopy(faces, 0, compact, 0, index);
        return compact;
    }

    private static Direction directionFromVector(PaintPoint vector) {
        float ax = Math.abs(vector.x());
        float ay = Math.abs(vector.y());
        float az = Math.abs(vector.z());
        if (ax >= ay && ax >= az) {
            return vector.x() < 0.0F ? Direction.WEST : Direction.EAST;
        }
        if (ay >= az) {
            return vector.y() < 0.0F ? Direction.UP : Direction.DOWN;
        }
        return vector.z() < 0.0F ? Direction.NORTH : Direction.SOUTH;
    }

    private static void emitPaintPointQuad(VertexConsumer vertices, Matrix4f matrix,
                                           PaintPoint p0, PaintPoint p1, PaintPoint p2, PaintPoint p3,
                                           PaintPoint normal, int color, int light) {
        vertex(vertices, matrix, p0.x() / 16.0F, p0.y() / 16.0F, p0.z() / 16.0F, color, normal.x(), normal.y(), normal.z(), light);
        vertex(vertices, matrix, p1.x() / 16.0F, p1.y() / 16.0F, p1.z() / 16.0F, color, normal.x(), normal.y(), normal.z(), light);
        vertex(vertices, matrix, p2.x() / 16.0F, p2.y() / 16.0F, p2.z() / 16.0F, color, normal.x(), normal.y(), normal.z(), light);
        vertex(vertices, matrix, p3.x() / 16.0F, p3.y() / 16.0F, p3.z() / 16.0F, color, normal.x(), normal.y(), normal.z(), light);
    }

    private static void renderPaintFace(VertexConsumer vertices, Matrix4f matrix, Cuboid cuboid, float offset,
                                        Direction face, int width, int height, int[] pixels, int light) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = pixels[y * width + x];
                if (color == 0) {
                    continue;
                }
                appendPaintPixel(vertices, matrix, cuboid, face, offset, x, y, width, height, color, light);
            }
        }
    }

    private static void appendPaintPixel(VertexConsumer vertices, Matrix4f matrix, Cuboid cuboid, Direction face,
                                         float offset, int x, int y, int width, int height, int color, int light) {
        float u0 = x / (float) width;
        float u1 = (x + 1) / (float) width;
        float v0 = y / (float) height;
        float v1 = (y + 1) / (float) height;
        float minX = cuboid.x() / 16.0F;
        float minY = cuboid.y() / 16.0F;
        float minZ = cuboid.z() / 16.0F;
        float maxX = (cuboid.x() + cuboid.width()) / 16.0F;
        float maxY = (cuboid.y() + cuboid.height()) / 16.0F;
        float maxZ = (cuboid.z() + cuboid.depth()) / 16.0F;
        float px0;
        float px1;
        float py0;
        float py1;
        float pz0;
        float pz1;
        switch (face) {
            case UP -> {
                px0 = lerp(minX, maxX, u0); px1 = lerp(minX, maxX, u1);
                pz0 = lerp(minZ, maxZ, v0); pz1 = lerp(minZ, maxZ, v1);
                py0 = py1 = minY - offset;
                vertex(vertices, matrix, px0, py0, pz0, color, 0, -1, 0, light);
                vertex(vertices, matrix, px1, py0, pz0, color, 0, -1, 0, light);
                vertex(vertices, matrix, px1, py0, pz1, color, 0, -1, 0, light);
                vertex(vertices, matrix, px0, py0, pz1, color, 0, -1, 0, light);
            }
            case DOWN -> {
                px0 = lerp(minX, maxX, u0); px1 = lerp(minX, maxX, u1);
                pz0 = lerp(minZ, maxZ, v0); pz1 = lerp(minZ, maxZ, v1);
                py0 = py1 = maxY + offset;
                vertex(vertices, matrix, px0, py0, pz1, color, 0, 1, 0, light);
                vertex(vertices, matrix, px1, py0, pz1, color, 0, 1, 0, light);
                vertex(vertices, matrix, px1, py0, pz0, color, 0, 1, 0, light);
                vertex(vertices, matrix, px0, py0, pz0, color, 0, 1, 0, light);
            }
            case NORTH -> {
                px0 = lerp(minX, maxX, u0); px1 = lerp(minX, maxX, u1);
                py0 = lerp(minY, maxY, v0); py1 = lerp(minY, maxY, v1);
                pz0 = pz1 = minZ - offset;
                vertex(vertices, matrix, px0, py0, pz0, color, 0, 0, -1, light);
                vertex(vertices, matrix, px1, py0, pz0, color, 0, 0, -1, light);
                vertex(vertices, matrix, px1, py1, pz0, color, 0, 0, -1, light);
                vertex(vertices, matrix, px0, py1, pz0, color, 0, 0, -1, light);
            }
            case SOUTH -> {
                px0 = lerp(minX, maxX, u0); px1 = lerp(minX, maxX, u1);
                py0 = lerp(minY, maxY, v0); py1 = lerp(minY, maxY, v1);
                pz0 = pz1 = maxZ + offset;
                vertex(vertices, matrix, px1, py0, pz0, color, 0, 0, 1, light);
                vertex(vertices, matrix, px0, py0, pz0, color, 0, 0, 1, light);
                vertex(vertices, matrix, px0, py1, pz0, color, 0, 0, 1, light);
                vertex(vertices, matrix, px1, py1, pz0, color, 0, 0, 1, light);
            }
            case WEST -> {
                pz0 = lerp(minZ, maxZ, u0); pz1 = lerp(minZ, maxZ, u1);
                py0 = lerp(minY, maxY, v0); py1 = lerp(minY, maxY, v1);
                px0 = px1 = minX - offset;
                vertex(vertices, matrix, px0, py0, pz1, color, -1, 0, 0, light);
                vertex(vertices, matrix, px0, py0, pz0, color, -1, 0, 0, light);
                vertex(vertices, matrix, px0, py1, pz0, color, -1, 0, 0, light);
                vertex(vertices, matrix, px0, py1, pz1, color, -1, 0, 0, light);
            }
            case EAST -> {
                pz0 = lerp(minZ, maxZ, u0); pz1 = lerp(minZ, maxZ, u1);
                py0 = lerp(minY, maxY, v0); py1 = lerp(minY, maxY, v1);
                px0 = px1 = maxX + offset;
                vertex(vertices, matrix, px0, py0, pz0, color, 1, 0, 0, light);
                vertex(vertices, matrix, px0, py0, pz1, color, 1, 0, 0, light);
                vertex(vertices, matrix, px0, py1, pz1, color, 1, 0, 0, light);
                vertex(vertices, matrix, px0, py1, pz0, color, 1, 0, 0, light);
            }
        }
    }

    private static float lerp(float min, float max, float value) {
        return min + (max - min) * value;
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, int color,
                               float normalX, float normalY, float normalZ, int light) {
        int alpha = (color >>> 24) & 0xFF;
        vertices.vertex(matrix, x, y, z)
                .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, alpha == 0 ? 0 : Math.max(alpha, 240))
                .texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light == 0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light)
                .normal(normalX, normalY, normalZ);
    }

    private record SurfaceTarget(ModelPart[] path, Cuboid cuboid, float paintOffset) {
        private SurfaceTarget(ModelPart[] path, Cuboid cuboid) {
            this(path, cuboid, PAINT_SURFACE_OFFSET);
        }
    }

    private record Cuboid(float x, float y, float z, float width, float height, float depth) {
    }

    private record OuterPaintTarget(ModelPart[] path, Direction sourceFace, PixelBox box,
                                    int textureX, int textureY, int textureYSegment,
                                    boolean renderStartCap, boolean renderEndCap) {
    }

    private record OuterSurfaceKey(String baseSurface, Direction sourceFace) {
        private static OuterSurfaceKey parse(String surface) {
            if (surface == null) {
                return null;
            }
            int marker = surface.indexOf("_outer_");
            if (marker < 0) {
                return null;
            }
            Direction sourceFace = parseDirection(surface.substring(marker + "_outer_".length()));
            if (sourceFace == null) {
                return null;
            }
            return new OuterSurfaceKey(surface.substring(0, marker), sourceFace);
        }

        private static Direction parseDirection(String name) {
            return switch (name) {
                case "down" -> Direction.DOWN;
                case "up" -> Direction.UP;
                case "north" -> Direction.NORTH;
                case "south" -> Direction.SOUTH;
                case "west" -> Direction.WEST;
                case "east" -> Direction.EAST;
                default -> null;
            };
        }
    }

    private record PixelBox(int x, int y, int z, int width, int height, int depth) {
    }

    private record OuterPaintFace(Direction face, int textureX, int textureY, int width, int height,
                                  PaintPoint origin, PaintPoint uStep, PaintPoint vStep, PaintPoint normal) {
    }

    private record PaintPoint(float x, float y, float z) {
        private PaintPoint add(PaintPoint other) {
            return new PaintPoint(x + other.x, y + other.y, z + other.z);
        }

        private PaintPoint multiply(float scalar) {
            return new PaintPoint(x * scalar, y * scalar, z * scalar);
        }

        private PaintPoint negate() {
            return new PaintPoint(-x, -y, -z);
        }

        private PaintPoint direction() {
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length <= 0.000001F) {
                return new PaintPoint(0.0F, 1.0F, 0.0F);
            }
            return new PaintPoint(x / length, y / length, z / length);
        }
    }

    private enum Edge {
        UP,
        RIGHT,
        DOWN,
        LEFT
    }

    public static void resetModel(PlayerEntityModel model) {
        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
    }

    public static void applyPoseData(PlayerEntityModel model, NbtCompound data) {
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

    private static void applyTrueSkeletalPlacementTransform(MatrixStack matrices, NbtCompound data) {
        if (data == null) {
            return;
        }
        matrices.translate(
                data.getFloat("pose_model_offset_x", 0.0F),
                data.getFloat("pose_model_offset_y", 0.0F),
                data.getFloat("pose_model_offset_z", 0.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-data.getFloat("pose_model_pitch", 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(data.getFloat("pose_model_yaw", 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-data.getFloat("pose_model_roll", 0.0F)));
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
