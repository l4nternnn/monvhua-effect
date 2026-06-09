package com.kuilunfuzhe.monvhua.renderer.body.special;

import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.TorsoBendFollower;
import com.kuilunfuzhe.monvhua.features.paint.ModelPaintData;
import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
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

        renderModelPaint(matrices, vertexConsumers, activeModel, slim, data.customData(), light);

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

    private void renderModelPaint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, PlayerEntityModel model,
                                  boolean slim, NbtCompound customData, int light) {
        if (customData == null || !customData.contains(ModelPaintData.MODEL_PAINT_KEY)) {
            return;
        }
        VertexConsumer vertices = vertexConsumers.getBuffer(PaintRenderLayers.paintOverlay());
        for (ModelPaintData.ModelFace face : ModelPaintData.readFaces(customData)) {
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
            renderPaintFace(vertices, matrices.peek().getPositionMatrix(), target.cuboid(), face.face(), face.pixels(), light);
            matrices.pop();
        }
    }

    private SurfaceTarget surfaceTarget(PlayerEntityModel model, boolean slim, String surface) {
        ModelPart waist = getChild(model.body, CombinedBodyModelData.WAIST);
        ModelPart leftForearm = getChild(model.leftArm, CombinedBodyModelData.LEFT_FOREARM);
        ModelPart rightForearm = getChild(model.rightArm, CombinedBodyModelData.RIGHT_FOREARM);
        ModelPart leftLowerLeg = getChild(model.leftLeg, CombinedBodyModelData.LEFT_LOWER_LEG);
        ModelPart rightLowerLeg = getChild(model.rightLeg, CombinedBodyModelData.RIGHT_LOWER_LEG);
        float armWidth = slim ? 3.0F : 4.0F;
        float leftArmX = slim ? -1.0F : -1.0F;
        float rightArmX = slim ? -2.0F : -3.0F;
        return switch (surface) {
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

    private static void renderPaintFace(VertexConsumer vertices, Matrix4f matrix, Cuboid cuboid, Direction face, int[] pixels, int light) {
        for (int y = 0; y < ModelPaintData.SIZE; y++) {
            for (int x = 0; x < ModelPaintData.SIZE; x++) {
                int color = pixels[y * ModelPaintData.SIZE + x];
                if (color == 0) {
                    continue;
                }
                appendPaintPixel(vertices, matrix, cuboid, face, x, y, color, light);
            }
        }
    }

    private static void appendPaintPixel(VertexConsumer vertices, Matrix4f matrix, Cuboid cuboid, Direction face, int x, int y, int color, int light) {
        float u0 = x / (float) ModelPaintData.SIZE;
        float u1 = (x + 1) / (float) ModelPaintData.SIZE;
        float v0 = y / (float) ModelPaintData.SIZE;
        float v1 = (y + 1) / (float) ModelPaintData.SIZE;
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
        float offset = 0.002F;
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

    private record SurfaceTarget(ModelPart[] path, Cuboid cuboid) {
    }

    private record Cuboid(float x, float y, float z, float width, float height, float depth) {
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
