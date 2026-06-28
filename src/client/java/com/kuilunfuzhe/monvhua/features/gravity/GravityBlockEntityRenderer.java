package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class GravityBlockEntityRenderer extends EntityRenderer<GravityBlockEntity, GravityBlockEntityRenderer.GravityBlockRenderState> {
    private static final double FAR_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final double BACKFACE_OCCLUSION_MIN_RADIUS = 2.0D;
    private static final double BACKFACE_OCCLUSION_DOT_BIAS = 0.18D;

    public GravityBlockEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public GravityBlockRenderState createRenderState() {
        return new GravityBlockRenderState();
    }

    @Override
    public void updateRenderState(GravityBlockEntity entity, GravityBlockRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.blockState = entity.getBlockState();
        state.pitch = lerp(entity.prevRenderPitch, entity.renderPitch, tickDelta);
        state.yaw = lerp(entity.prevRenderYaw, entity.renderYaw, tickDelta);
        state.roll = lerp(entity.prevRenderRoll, entity.renderRoll, tickDelta);
        state.grouped = entity.hasRenderGroup();
        state.groupCenter = entity.getRenderGroupCenter();
        state.groupRadius = entity.getRenderGroupRadius();
        state.groupOwnerId = entity.getRenderGroupOwnerId();
    }

    @Override
    public void render(GravityBlockRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (shouldSkipBlockGroupRender(state)) {
            return;
        }
        matrices.push();
        matrices.translate(-0.5D, 0.0D, -0.5D);
        matrices.translate(0.5D, 0.5D, 0.5D);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));
        matrices.translate(-0.5D, -0.5D, -0.5D);
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(state.blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(state, matrices, vertexConsumers, light);
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private static boolean shouldSkipBlockGroupRender(GravityBlockRenderState state) {
        if (!state.grouped) {
            return false;
        }
        if (state.squaredDistanceToCamera > FAR_RENDER_DISTANCE_SQUARED) {
            return true;
        }
        if (state.groupRadius < BACKFACE_OCCLUSION_MIN_RADIUS) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return false;
        }
        Vec3d groupCenter = currentGroupCenter(client, state);
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d centerToCamera = camera.subtract(groupCenter);
        double centerDistance = centerToCamera.length();
        if (centerDistance <= state.groupRadius * 1.15D) {
            return false;
        }

        Vec3d centerToBlock = new Vec3d(state.x, state.y, state.z).subtract(groupCenter);
        double blockDistance = centerToBlock.length();
        if (blockDistance < 1.0E-4D) {
            return false;
        }
        double facingDot = centerToBlock.dotProduct(centerToCamera) / (blockDistance * centerDistance);
        return facingDot < -BACKFACE_OCCLUSION_DOT_BIAS;
    }

    private static Vec3d currentGroupCenter(MinecraftClient client, GravityBlockRenderState state) {
        if (state.groupOwnerId >= 0 && client.world != null) {
            Entity owner = client.world.getEntityById(state.groupOwnerId);
            if (owner != null) {
                return new Vec3d(owner.getX(), owner.getY() + owner.getHeight() + 3.0D + Math.max(0.0D, state.groupRadius - 0.75D), owner.getZ());
            }
        }
        return state.groupCenter;
    }

    public static class GravityBlockRenderState extends EntityRenderState {
        public BlockState blockState = Blocks.STONE.getDefaultState();
        public float pitch;
        public float yaw;
        public float roll;
        public boolean grouped;
        public Vec3d groupCenter = Vec3d.ZERO;
        public double groupRadius;
        public int groupOwnerId = -1;
    }
}
