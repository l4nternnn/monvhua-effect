package com.kuilunfuzhe.monvhua.renderer.body.arm;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.arm.RightArmModel;
import com.kuilunfuzhe.monvhua.model.arm.RightArmSlimModel;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlockEntity;
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
 * 右臂方块实体渲染器。
 * 负责在方块世界中渲染玩家右臂模型，支持普通和细手臂（Slim）两种皮肤模型，
 * 根据方块的朝向自动旋转模型，并从玩家Profile或UUID获取皮肤纹理。
 */
public class RightArmBlockEntityRenderer implements BlockEntityRenderer<RightArmBlockEntity> {
    /** 普通右臂模型 */
    private final RightArmModel model;
    /** 细手臂（Slim）皮肤右臂模型 */
    private final RightArmSlimModel slimModel;

    /**
     * 从模型层注册表中获取右臂模型部件并初始化渲染器。
     */
    public RightArmBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new RightArmModel(ctx.getLayerModelPart(ModModelLayers.RIGHT_ARM));
        this.slimModel = new RightArmSlimModel(ctx.getLayerModelPart(ModModelLayers.RIGHT_ARM_SLIM));
    }

    /**
     * 渲染右臂方块实体。
     * 渲染流程：放置变换矩阵 → 获取皮肤纹理 → 选择模型 → 先渲染内层（手臂主体）→ 再渲染外层（袖子/体素）
     *
     * @param entity 右臂方块实体
     * @param tickDelta 帧插值时间
     * @param matrices 矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light 光照值
     * @param overlay 覆盖纹理UV
     * @param cameraPos 相机位置
     */
    @Override
    public void render(RightArmBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(RightArmBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        // 将模型原点移至方块中心（0.5, 0.5），使右臂居中显示
        matrices.translate(0.5F, 0.0F, 0.5F);
        // 根据方块朝向旋转模型
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        // Y轴和Z轴翻转：修正坐标系差异
        matrices.scale(1.0F, -1.0F, -1.0F);
        // 微调归零
        matrices.translate(0.0F, 0.0F, 0.0F);

        // 获取皮肤纹理：本地皮肤优先，否则三级回退
        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        // 根据皮肤类型选择对应模型
        boolean slim = "slim".equals(entity.getSkinType());
        SkullBlockEntityModel activeModel = slim ? slimModel : model;
        activeModel.setHeadRotation(0, yaw, 0);

        // 模型结构：根节点下的HEAD子节点承载实际的右臂几何体
        ModelPart arm = activeModel.getRootPart().getChild(EntityModelPartNames.HEAD);
        ModelPart sleeve = arm.getChild("right_sleeve");
        // 先隐藏袖子，仅渲染手臂内层主体
        sleeve.visible = false;

        RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(texture);
        activeModel.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);

        // 显示袖子，优先使用体素渲染器，回退到标准模型渲染
        sleeve.visible = true;
        matrices.push();
        activeModel.getRootPart().applyTransform(matrices);
        arm.applyTransform(matrices);
        matrices.push();
        sleeve.applyTransform(matrices);
        boolean renderedVoxelLayer = SkinOuterLayerVoxelRenderer.renderRightSleeve(matrices, vertexConsumers.getBuffer(renderLayer),
                texture, light, OverlayTexture.DEFAULT_UV, slim);
        matrices.pop();
        if (!renderedVoxelLayer) {
            sleeve.render(matrices, vertexConsumers.getBuffer(renderLayer), light, OverlayTexture.DEFAULT_UV);
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
