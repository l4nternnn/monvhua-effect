package com.kuilunfuzhe.monvhua.renderer.body.head;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.head.HeadModel;
import com.kuilunfuzhe.monvhua.features.block.body.head.HeadBlock;
import com.kuilunfuzhe.monvhua.features.block.body.head.HeadBlockEntity;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
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
 * 头部方块实体渲染器。
 * 负责在方块世界中渲染玩家头部模型，根据方块朝向自动旋转，
 * 从玩家Profile或UUID获取皮肤纹理，并优先使用体素渲染器渲染帽层。
 */
public class HeadBlockEntityRenderer implements BlockEntityRenderer<HeadBlockEntity> {
    /** 头部模型 */
    private final HeadModel model;

    /**
     * 从模型层注册表中获取头部模型部件并初始化渲染器。
     */
    public HeadBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new HeadModel(ctx.getLayerModelPart(ModModelLayers.HEAD));
    }

    /**
     * 渲染头部方块实体。
     * 渲染流程：放置变换矩阵 → 获取皮肤纹理 → 先渲染内层（头部主体）→ 再渲染外层（帽子/体素）
     *
     * @param entity 头部方块实体
     * @param tickDelta 帧插值时间
     * @param matrices 矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light 光照值
     * @param overlay 覆盖纹理UV
     * @param cameraPos 相机位置
     */
    @Override
    public void render(HeadBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(HeadBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        // 模型原点移至方块中心
        matrices.translate(0.5F, 0.0F, 0.5F);
        // 根据方块朝向旋转模型
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        // X轴翻转：头部需要左右镜像；Y轴翻转：上下倒置修正坐标系
        matrices.scale(-1.0F, -1.0F, 1.0F);

        // 获取皮肤纹理：本地皮肤优先，否则三级回退
        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        model.setHeadRotation(0, yaw, 0);

        // 获取头部模型部件及其帽层
        ModelPart head = model.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart hat = head.getChild(EntityModelPartNames.HAT);
        // 先隐藏帽层，仅渲染头部内层主体
        hat.visible = false;

        RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(texture);
        model.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);

        // 显示帽层，优先使用体素渲染器，回退到标准模型渲染
        hat.visible = true;
        matrices.push();
        model.getRootPart().applyTransform(matrices);
        head.applyTransform(matrices);
        matrices.push();
        hat.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderHeadHat(matrices, vertexConsumers.getBuffer(renderLayer),
                texture, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        if (!renderedVoxelLayer) {
            hat.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();

        matrices.pop();
    }

    /**
     * 获取皮肤纹理的三级回退策略：
     * 1. 优先从ProfileComponent获取完整皮肤纹理
     * 2. 回退到通过UUID构建临时GameProfile查询皮肤
     * 3. 最终回退到默认纹理
     */
    private Identifier getSkinTexture(@Nullable ProfileComponent owner, @Nullable UUID playerUuid) {
        // 第一级：完整Profile纹理获取
        if (owner != null && owner.gameProfile() != null) {
            GameProfile profile = owner.gameProfile();
            if (!profile.getProperties().get("textures").isEmpty()) {
                return MinecraftClient.getInstance()
                        .getSkinProvider()
                        .getSkinTextures(profile)
                        .texture();
            }
        }
        // 第二级：UUID临时Profile查询
        if (playerUuid != null) {
            GameProfile fallbackProfile = new GameProfile(playerUuid, "");
            return MinecraftClient.getInstance()
                    .getSkinProvider()
                    .getSkinTextures(fallbackProfile)
                    .texture();
        }
        // 第三级：默认纹理
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
