package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaintBucketCarryClientState {
    private static final Map<Integer, CarriedBucket> CARRIED_BUCKETS = new ConcurrentHashMap<>();

    private PaintBucketCarryClientState() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaintBucketCarryS2C.ID, (packet, context) ->
                context.client().execute(() -> apply(packet)));
    }

    public static void apply(PaintOverlayPackets.PaintBucketCarryS2C packet) {
        if (!packet.active()) {
            CARRIED_BUCKETS.remove(packet.entityId());
            return;
        }
        CARRIED_BUCKETS.put(packet.entityId(), new CarriedBucket(packet.filled(), packet.color()));
    }

    public static void render(WorldRenderContext context) {
        if (CARRIED_BUCKETS.isEmpty() || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        float tickProgress = context.tickCounter().getTickProgress(true);
        Vec3d camera = context.camera().getPos();
        for (Map.Entry<Integer, CarriedBucket> entry : CARRIED_BUCKETS.entrySet()) {
            Entity carrier = client.world.getEntityById(entry.getKey());
            if (carrier != null) {
                renderBucket(client, context, carrier, entry.getValue(), tickProgress, camera);
            }
        }
    }

    private static void renderBucket(MinecraftClient client, WorldRenderContext context, Entity carrier, CarriedBucket carried, float tickProgress, Vec3d camera) {
        MatrixStack matrices = context.matrixStack();
        Vec3d pos = carrier.getLerpedPos(tickProgress);
        float yaw = carrier.getLerpedYaw(tickProgress);
        int light = WorldRenderer.getLightmapCoordinates(client.world, carrier.getBlockPos());

        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.translate(-0.5D, 0.72D, -0.62D);
        matrices.scale(0.82F, 0.82F, 0.82F);
        client.getBlockRenderManager().renderBlockAsEntity(
                PaintItems.PAINT_BUCKET_BLOCK.getDefaultState(),
                matrices,
                context.consumers(),
                light,
                OverlayTexture.DEFAULT_UV
        );
        if (carried.filled()) {
            renderPaintSurface(context, carried.color());
        }
        matrices.pop();
    }

    private static void renderPaintSurface(WorldRenderContext context, int color) {
        var vertices = context.consumers().getBuffer(PaintRenderLayers.paintOverlay());
        var matrix = context.matrixStack().peek().getPositionMatrix();
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        PaintBucketBlockEntityRenderer.vertex(vertices, matrix, 0.25F, 0.55F, 0.25F, r, g, b, 255);
        PaintBucketBlockEntityRenderer.vertex(vertices, matrix, 0.75F, 0.55F, 0.25F, r, g, b, 255);
        PaintBucketBlockEntityRenderer.vertex(vertices, matrix, 0.75F, 0.55F, 0.75F, r, g, b, 255);
        PaintBucketBlockEntityRenderer.vertex(vertices, matrix, 0.25F, 0.55F, 0.75F, r, g, b, 255);
    }

    private record CarriedBucket(boolean filled, int color) {
    }
}
