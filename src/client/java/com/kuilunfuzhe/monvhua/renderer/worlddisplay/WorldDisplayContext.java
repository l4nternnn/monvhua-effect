package com.kuilunfuzhe.monvhua.renderer.worlddisplay;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public record WorldDisplayContext(
        MinecraftClient client,
        WorldRenderContext worldRenderContext,
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        Vec3d cameraPos,
        Vec3d origin,
        float tickDelta,
        double animationTime,
        float openProgress,
        int light,
        float contentWidth,
        float contentHeight,
        float contentCenterY,
        Quaternionf billboardRotation
) {
    public static WorldDisplayContext from(MinecraftClient client, WorldRenderContext worldRenderContext,
                                           Vec3d cameraPos, Vec3d origin, float tickDelta,
                                           double animationTime, float openProgress,
                                           float contentWidth, float contentHeight, float contentCenterY) {
        int worldLight = WorldRenderer.getLightmapCoordinates(
                client.world, BlockPos.ofFloored(origin.x, origin.y + 0.5D, origin.z));
        // Thought-bubble displays are UI-like world overlays; retain a small amount of
        // world context while preventing underground displays from becoming invisible.
        int light = LightmapTextureManager.applyEmission(worldLight, 6);
        return new WorldDisplayContext(
                client,
                worldRenderContext,
                worldRenderContext.matrixStack(),
                worldRenderContext.consumers(),
                cameraPos,
                origin,
                tickDelta,
                animationTime,
                openProgress,
                light,
                contentWidth,
                contentHeight,
                contentCenterY,
                new Quaternionf(worldRenderContext.camera().getRotation())
        );
    }
}
