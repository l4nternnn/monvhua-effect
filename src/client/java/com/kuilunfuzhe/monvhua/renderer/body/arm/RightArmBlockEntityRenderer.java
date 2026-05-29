package com.kuilunfuzhe.monvhua.renderer.body.arm;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.arm.RightArmModel;
import com.kuilunfuzhe.monvhua.model.arm.RightArmSlimModel;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlock;
import com.kuilunfuzhe.monvhua.features.block.body.arm.RightArmBlockEntity;
import com.kuilunfuzhe.monvhua.renderer.body.SkinOuterLayerVoxelRenderer;
import com.kuilunfuzhe.monvhua.util.SkinColorSampler;
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

public class RightArmBlockEntityRenderer implements BlockEntityRenderer<RightArmBlockEntity> {
    private final RightArmModel model;
    private final RightArmSlimModel slimModel;

    public RightArmBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new RightArmModel(ctx.getLayerModelPart(ModModelLayers.RIGHT_ARM));
        this.slimModel = new RightArmSlimModel(ctx.getLayerModelPart(ModModelLayers.RIGHT_ARM_SLIM));
    }

    @Override
    public void render(RightArmBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(RightArmBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        matrices.translate(0.5F, 0.0F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.scale(1.0F, -1.0F, -1.0F);
        matrices.translate(0.0F, 0.0F, 0.0F);

        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        boolean slim = "slim".equals(entity.getSkinType());
        SkullBlockEntityModel activeModel = slim ? slimModel : model;
        activeModel.setHeadRotation(0, yaw, 0);

        SkinColorSampler.OuterLayerColors colors = SkinColorSampler.getOrSample(texture);
        if (colors != null) {
            ModelPart arm = activeModel.getRootPart().getChild(EntityModelPartNames.HEAD);
            ModelPart sleeve = arm.getChild("right_sleeve");
            sleeve.visible = false;

            activeModel.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, OverlayTexture.DEFAULT_UV);

            sleeve.visible = true;
            matrices.push();
            activeModel.getRootPart().applyTransform(matrices);
            arm.applyTransform(matrices);
            sleeve.applyTransform(matrices);
            SkinOuterLayerVoxelRenderer.renderRightSleeve(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)),
                    texture, light, OverlayTexture.DEFAULT_UV, slim);
            matrices.pop();
        } else {
            activeModel.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, OverlayTexture.DEFAULT_UV);
        }

        matrices.pop();
    }

    private Identifier getSkinTexture(@Nullable ProfileComponent owner, @Nullable UUID playerUuid) {
        if (owner != null && owner.gameProfile() != null) {
            GameProfile profile = owner.gameProfile();
            if (!profile.getProperties().get("textures").isEmpty()) {
                return MinecraftClient.getInstance()
                        .getSkinProvider()
                        .getSkinTextures(profile)
                        .texture();
            }
        }
        if (playerUuid != null) {
            GameProfile fallbackProfile = new GameProfile(playerUuid, "");
            return MinecraftClient.getInstance()
                    .getSkinProvider()
                    .getSkinTextures(fallbackProfile)
                    .texture();
        }
        return Identifier.of("monvhua", "textures/block/torso.png");
    }

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
