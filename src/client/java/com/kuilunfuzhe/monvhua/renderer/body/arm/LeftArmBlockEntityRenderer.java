package com.kuilunfuzhe.monvhua.renderer.body.arm;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.arm.LeftArmModel;
import com.kuilunfuzhe.monvhua.model.arm.LeftArmSlimModel;
import com.kuilunfuzhe.monvhua.features.block.body.arm.LeftArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body.arm.LeftArmBlockEntity;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 左臂方块实体渲染器。
 * 负责在方块世界中渲染玩家左臂模型，支持普通和细手臂（Slim）两种皮肤模型，
 * 根据方块的朝向自动旋转模型，并从玩家Profile或UUID获取皮肤纹理。
 */
public class LeftArmBlockEntityRenderer implements BlockEntityRenderer<LeftArmBlockEntity> {
    /** 普通左臂模型 */
    private final LeftArmModel model;
    /** 细手臂（Slim）皮肤左臂模型 */
    private final LeftArmSlimModel slimModel;

    /**
     * 从模型层注册表中获取左臂模型部件并初始化渲染器。
     */
    public LeftArmBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new LeftArmModel(ctx.getLayerModelPart(ModModelLayers.LEFT_ARM));
        this.slimModel = new LeftArmSlimModel(ctx.getLayerModelPart(ModModelLayers.LEFT_ARM_SLIM));
    }

    /**
     * 渲染左臂方块实体。
     * 渲染流程：放置变换矩阵 → 获取皮肤纹理 → 选择模型 → 先渲染内层（手臂主体）→ 再渲染外层（袖子/体素）
     *
     * @param entity 左臂方块实体，包含皮肤类型、所有者信息等
     * @param tickDelta 帧插值时间，用于平滑动画
     * @param matrices 矩阵栈，用于累积变换
     * @param vertexConsumers 顶点消费者提供器，用于获取渲染缓冲
     * @param light 光照值（天空光+方块光打包）
     * @param overlay 覆盖纹理UV坐标
     * @param cameraPos 相机位置，用于计算相对偏移
     */
    @Override
    public void render(LeftArmBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(LeftArmBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        // 将模型原点偏移至方块左前侧（X=0.4偏左，Z=0.6偏前），使左臂居中显示
        matrices.translate(0.4F, 0.0F, 0.6F);
        // 根据方块朝向旋转模型，使正面始终朝向预设方向
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        // Y轴和Z轴翻转：修正Minecraft坐标系差异，使模型正面朝外（-Y倒置，-Z镜像）
        matrices.scale(1.00F, -1.0F, -1.0F);
        // 微调归零（预留调整空间）
        matrices.translate(0.0F, 0.0F, 0.0F);

        // 获取皮肤纹理：优先使用本地皮肤，否则通过三级回退策略获取
        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        // 根据皮肤类型选择对应模型：slim用细手臂模型，否则用普通模型
        boolean slim = "slim".equals(entity.getSkinType());
        SkullBlockEntityModel activeModel = slim ? slimModel : model;
        activeModel.setHeadRotation(0, yaw, 0);

        // 获取左臂模型部件及其外层（袖子）
        ModelPart arm = activeModel.getRootPart().getChild(EntityModelPartNames.LEFT_ARM);
        ModelPart sleeve = arm.getChild("left_sleeve");
        // 先隐藏袖子，仅渲染手臂内层主体
        sleeve.visible = false;

        RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(texture);
        activeModel.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);

        // 显示袖子，渲染外层：优先使用体素渲染器，回退到标准模型渲染
        sleeve.visible = true;
        matrices.push();
        activeModel.getRootPart().applyTransform(matrices);
        arm.applyTransform(matrices);
        matrices.push();
        sleeve.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderLeftSleeve(matrices, vertexConsumers.getBuffer(renderLayer),
                texture, light, OverlayTexture.DEFAULT_UV, slim);
        matrices.pop();
        if (!renderedVoxelLayer) {
            // 体素渲染器未处理时，使用标准模型渲染袖子
            sleeve.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();

        matrices.pop();
    }

    /**
     * 获取皮肤纹理的三级回退策略：
     * 1. 优先从方块实体的ProfileComponent获取皮肤纹理（正版/离线玩家的完整Profile）
     * 2. 回退到通过UUID构建临时GameProfile查询Mojang API获取离线皮肤
     * 3. 最终回退到默认纹理 "textures/block/torso.png"
     */
    private Identifier getSkinTexture(@Nullable ProfileComponent owner, @Nullable UUID playerUuid) {
        // 第一级：尝试从完整Profile获取纹理（含正版皮肤和披风数据）
        if (owner != null && owner.gameProfile() != null) {
            GameProfile profile = owner.gameProfile();
            if (!profile.getProperties().get("textures").isEmpty()) {
                return MinecraftClient.getInstance()
                        .getSkinProvider()
                        .getSkinTextures(profile)
                        .texture();
            }
        }
        // 第二级：以UUID构建临时Profile，尝试从SkinProvider缓存/Api查询皮肤
        if (playerUuid != null) {
            GameProfile fallbackProfile = new GameProfile(playerUuid, "");
            return MinecraftClient.getInstance()
                    .getSkinProvider()
                    .getSkinTextures(fallbackProfile)
                    .texture();
        }
        // 第三级：返回mod自带的默认纹理
        return Identifier.of("monvhua", "textures/block/torso.png");
    }

    /**
     * 将方块朝向（FACING）转换为Y轴旋转角度（yaw）。
     * 映射关系：NORTH=180°, EAST=-90°, SOUTH=0°, WEST=90°
     */
    private float getYawFromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
