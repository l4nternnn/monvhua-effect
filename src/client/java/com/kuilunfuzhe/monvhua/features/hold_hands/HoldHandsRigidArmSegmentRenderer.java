package com.kuilunfuzhe.monvhua.features.hold_hands;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

final class HoldHandsRigidArmSegmentRenderer {
    private static final double MIN_SEGMENT_LENGTH = 0.04D;
    private static final float ARM_PIXEL_LENGTH = 12.0F;
    private static final float VANILLA_ARM_SCALE = 1.0F;
    private static final BakedArm DEFAULT_LEFT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.LEFT, false);
    private static final BakedArm DEFAULT_RIGHT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.RIGHT, false);
    private static final BakedArm SLIM_LEFT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.LEFT, true);
    private static final BakedArm SLIM_RIGHT_ARM = bakeArm(HoldHandsSkeletalPose.HandSide.RIGHT, true);

    private HoldHandsRigidArmSegmentRenderer() {
    }

    static boolean render(PlayerEntityRenderState state, MatrixStack matrices,
                          VertexConsumerProvider vertexConsumers, Identifier texture, int light,
                          boolean slim, HoldHandsSkeletalPose.HandSide side) {
        if (state == null || matrices == null || vertexConsumers == null || texture == null || side == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }

        Entity self = client.world.getEntityById(state.id);
        if (self == null) {
            return false;
        }

        float renderBodyYaw = state.bodyYaw;
        Vec3d targetWorld = HoldHandsClientState.getSharedHandPoint(state.id);
        if (targetWorld == null) {
            return false;
        }
        Vec3d startLocal = HoldHandsLinkGeometry.shoulderSocket(side);
        Vec3d endLocal = HoldHandsLinkGeometry.worldVectorToBodyLocal(targetWorld.subtract(self.getPos()), renderBodyYaw);
        Vec3d segment = endLocal.subtract(startLocal);
        if (segment.lengthSquared() <= MIN_SEGMENT_LENGTH * MIN_SEGMENT_LENGTH) {
            return false;
        }

        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        renderArmModel(matrices, vertices, toVanillaModelSpace(startLocal), toVanillaModelSpace(endLocal),
                bakedArm(side, slim), light);
        return true;
    }

    private static Vec3d toVanillaModelSpace(Vec3d bodyLocal) {
        return new Vec3d(-bodyLocal.x, 1.501D - bodyLocal.y, -bodyLocal.z);
    }

    private static void renderArmModel(MatrixStack matrices, VertexConsumer vertices, Vec3d start, Vec3d end,
                                       BakedArm arm, int light) {
        Vec3d segment = end.subtract(start);
        double length = segment.length();
        if (length <= MIN_SEGMENT_LENGTH) {
            return;
        }

        Vec3d axis = segment.multiply(1.0D / length);
        Quaternionf rotation = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                (float) axis.x, (float) axis.y, (float) axis.z);

        matrices.push();
        matrices.translate((float) start.x, (float) start.y, (float) start.z);
        matrices.multiply(rotation);
        matrices.scale(1.0F, VANILLA_ARM_SCALE, 1.0F);
        arm.root().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    private static BakedArm bakedArm(HoldHandsSkeletalPose.HandSide side, boolean slim) {
        if (side == HoldHandsSkeletalPose.HandSide.LEFT) {
            return slim ? SLIM_LEFT_ARM : DEFAULT_LEFT_ARM;
        }
        return slim ? SLIM_RIGHT_ARM : DEFAULT_RIGHT_ARM;
    }

    private static BakedArm bakeArm(HoldHandsSkeletalPose.HandSide side, boolean slim) {
        int width = slim ? 3 : 4;
        int skinU = side == HoldHandsSkeletalPose.HandSide.LEFT ? 32 : 40;
        int skinV = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 16;
        int sleeveU = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 40;
        int sleeveV = side == HoldHandsSkeletalPose.HandSide.LEFT ? 48 : 32;
        float minX = -width * 0.5F;

        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        ModelPartData arm = root.addChild("arm",
                ModelPartBuilder.create().uv(skinU, skinV).cuboid(minX, 0.0F, -2.0F,
                        width, ARM_PIXEL_LENGTH, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        arm.addChild("sleeve",
                ModelPartBuilder.create().uv(sleeveU, sleeveV).cuboid(minX, 0.0F, -2.0F,
                        width, ARM_PIXEL_LENGTH, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        return new BakedArm(TexturedModelData.of(modelData, 64, 64).createModel());
    }

    private record BakedArm(ModelPart root) {
    }
}
