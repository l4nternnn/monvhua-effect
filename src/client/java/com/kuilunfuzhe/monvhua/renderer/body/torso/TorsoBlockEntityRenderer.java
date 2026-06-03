package com.kuilunfuzhe.monvhua.renderer.body.torso;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.torso.TorsoModel;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlock;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlockEntity;
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
 * 躯干方块实体渲染器。
 * 负责在方块世界中渲染玩家躯干模型，根据方块朝向自动旋转，
 * 从玩家Profile或UUID获取皮肤纹理，并优先使用体素渲染器渲染夹克层。
 */
public class TorsoBlockEntityRenderer implements BlockEntityRenderer<TorsoBlockEntity> {
    /** 躯干模型 */
    private final TorsoModel model;

    /**
     * 从模型层注册表中获取躯干模型部件并初始化渲染器。
     */
    public TorsoBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new TorsoModel(ctx.getLayerModelPart(ModModelLayers.TORSO));
    }

    /**
     * 渲染躯干方块实体。
     * 渲染流程：放置变换矩阵 → 获取皮肤纹理 → 先渲染内层（躯干主体）→ 再渲染外层（夹克/体素）
     *
     * @param entity 躯干方块实体
     * @param tickDelta 帧插值时间
     * @param matrices 矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light 光照值
     * @param overlay 覆盖纹理UV
     * @param cameraPos 相机位置
     */
    @Override
    public void render(TorsoBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(TorsoBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        // 模型原点移至方块中心
        matrices.translate(0.5F, 0.0F, 0.5F);
        // 根据方块朝向旋转模型
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        // 仅翻转Y轴：躯干不需要X轴镜像，只需上下倒置修正坐标系
        matrices.scale(1.0F, -1.0F, 1.0F);
        // Y轴向下偏移0.2：使躯干模型在方块内垂直居中（躯干模型高度为12像素，需向下微调对齐）
        matrices.translate(-0.0F, -0.2F, -0.0F);

        // 获取皮肤纹理：本地皮肤优先，否则三级回退
        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        model.setHeadRotation(0, yaw, 0);

        // 模型结构：根节点下的HEAD子节点承载实际的躯干几何体
        ModelPart torso = model.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart jacket = torso.getChild(EntityModelPartNames.JACKET);
        // 先隐藏夹克层，仅渲染躯干内层主体
        jacket.visible = false;

        RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(texture);
        model.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);

        // 显示夹克层，优先使用体素渲染器，回退到标准模型渲染
        jacket.visible = true;
        matrices.push();
        model.getRootPart().applyTransform(matrices);
        torso.applyTransform(matrices);
        matrices.push();
        jacket.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderJacket(matrices, vertexConsumers.getBuffer(renderLayer),
                texture, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        if (!renderedVoxelLayer) {
            jacket.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);
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
