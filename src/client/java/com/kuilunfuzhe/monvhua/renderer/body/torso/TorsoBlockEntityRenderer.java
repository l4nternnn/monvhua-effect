package com.kuilunfuzhe.monvhua.renderer.body.torso;

import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.torso.TorsoModel;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlock;
import com.kuilunfuzhe.monvhua.features.block.body.torso.TorsoBlockEntity;
import com.kuilunfuzhe.monvhua.util.SkinColorSampler;
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

public class TorsoBlockEntityRenderer implements BlockEntityRenderer<TorsoBlockEntity> {
    private final TorsoModel model;

    public TorsoBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new TorsoModel(ctx.getLayerModelPart(ModModelLayers.TORSO));
    }

    @Override
    public void render(TorsoBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        Direction direction = entity.getCachedState().get(TorsoBlock.FACING);
        float yaw = getYawFromDirection(direction);

        matrices.push();
        matrices.translate(0.5F, 0.0F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.scale(1.0F, -1.0F, 1.0F);
        matrices.translate(-0.0F, -0.2F, -0.0F);


        String localSkin = entity.getLocalSkin();
        Identifier texture;
        if (localSkin != null) {
            texture = Identifier.of("monvhua", "textures/local_skin/" + localSkin + ".png");
        } else {
            texture = getSkinTexture(entity.getOwner(), entity.getPlayerUuid());
        }

        SkinColorSampler.OuterLayerColors colors = SkinColorSampler.getOrSample(texture);
        model.setHeadRotation(0, yaw, 0);

        if (colors != null) {
            ModelPart torso = model.getRootPart().getChild(EntityModelPartNames.HEAD);
            ModelPart jacket = torso.getChild(EntityModelPartNames.JACKET);
            jacket.visible = false;

            model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, OverlayTexture.DEFAULT_UV);

            jacket.visible = true;
            jacket.hidden = true;
            matrices.push();
            model.getRootPart().applyTransform(matrices);
            torso.applyTransform(matrices);

            double dist = cameraPos.distanceTo(Vec3d.ofCenter(entity.getPos()));
            if (dist < 12.0) {
                matrices.translate(0.0F, -5.0F, 0.0F);
                matrices.scale(1.188F, 1.125F, 1.375F);
                matrices.translate(0.0F, 5.0F, 0.0F);
            }

            jacket.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
            jacket.hidden = false;
        } else {
            model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, OverlayTexture.DEFAULT_UV);
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
