package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.model.ChestBlockModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ChestWorldDisplay implements WorldDisplay {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    // 1.21.8 model data: the base is 14x10x14 at y=0, and the lid is
    // 14x5x14 at y=9. At a fully open pitch (-PI/2), its top reaches y=23.
    private static final float MODEL_WIDTH = 14.0F * MODEL_UNIT;
    private static final float MODEL_DEPTH = 14.0F * MODEL_UNIT;
    private static final float MODEL_CENTER_Y = 11.5F * MODEL_UNIT;
    private static final float YAW_DEGREES = -45.0F;
    private static ChestBlockModel model;
    private static SpriteIdentifier sprite;

    @Override
    public void render(WorldDisplayContext context) {
        if (model == null) {
            ModelPart root = context.client().getLoadedEntityModels().getModelPart(EntityModelLayers.CHEST);
            model = new ChestBlockModel(root);
            sprite = TexturedRenderLayers.CHEST;
        }
        // ChestBlockModel already converts vanilla lid progress to its negative X rotation.
        model.setLockAndLidPitch(context.openProgress());

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(
                context.origin().x - context.cameraPos().x,
                context.origin().y - context.cameraPos().y,
                context.origin().z - context.cameraPos().z
        );
        matrices.multiply(context.billboardRotation());
        // The billboard supplies the camera-facing basis. Keep the chest pose
        // level in that basis; only the fixed yaw gives the requested 3/4 view.
        matrices.translate(0.0F, context.contentCenterY(), 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(YAW_DEGREES));

        float yaw = (float) Math.toRadians(Math.abs(YAW_DEGREES));
        float projectedWidth = MODEL_WIDTH * MathHelper.cos(yaw) + MODEL_DEPTH * MathHelper.sin(yaw);
        float widthScale = context.contentWidth() * 0.88F / projectedWidth;
        // The normal bubble is intentionally shallow. Size from its horizontal
        // content span so the fixed three-quarter view remains readable; the
        // open lid may extend beyond the shallow slot, as in the target view.
        float scale = widthScale;
        matrices.scale(scale, scale, scale);
        // ModelPart writes cuboid coordinates in sixteenths. Center its real
        // normalized full-open bounds (x/z=1..15, y=0..23), rather than pixels.
        matrices.translate(-0.5F, -MODEL_CENTER_Y, -0.5F);
        VertexConsumer vertices = sprite.getVertexConsumer(
                context.consumers(), WorldDisplayRenderLayers::chest);
        model.render(matrices, vertices, context.light(), OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
