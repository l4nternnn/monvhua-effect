package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityClient;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class UiActivityBubbleRenderer {
    private static final double MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final double HEAD_OFFSET = 0.48D;
    private static final float WIDTH = 0.92F;
    private static final float HEIGHT = 0.62F;
    private static final int DOT_CYCLE_TICKS = 24;

    private UiActivityBubbleRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        double animationTime = client.world.getTime() + tickProgress;
        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer vertices = context.consumers().getBuffer(UiActivityBubbleRenderLayers.bubble());

        for (PlayerEntity player : client.world.getPlayers()) {
            UiActivityClient.VisualState state = UiActivityClient.stateFor(player.getUuid());
            if (state == null || !shouldRender(client, player, cameraPos)) {
                continue;
            }

            float reveal = revealProgress(state, animationTime);
            if (reveal <= 0.001F) {
                continue;
            }

            Vec3d bubblePos = bubblePosition(player, tickProgress);
            if (bubblePos.squaredDistanceTo(cameraPos) > MAX_DISTANCE_SQUARED) {
                continue;
            }

            float dotPhase = reveal >= 0.999F
                    ? (float) (animationTime % DOT_CYCLE_TICKS) / DOT_CYCLE_TICKS
                    : 0.0F;
            drawBubble(vertices, matrices, context, bubblePos, reveal, dotPhase, state.contentId());
        }
    }

    private static boolean shouldRender(MinecraftClient client, PlayerEntity player, Vec3d cameraPos) {
        if (!player.isAlive() || player.isSpectator() || player.isInvisible()) {
            return false;
        }
        if (player == client.player && client.options.getPerspective().isFirstPerson()) {
            return false;
        }
        return player.squaredDistanceTo(cameraPos) <= MAX_DISTANCE_SQUARED;
    }

    private static Vec3d bubblePosition(PlayerEntity player, float tickProgress) {
        if (SurfaceGravityClientEngine.isRenderActive(player)) {
            Direction down = GravityMagic.getSurfaceGravityDirection(player);
            Vec3d up = SurfaceGravityBasis.of(down).up();
            return SurfaceGravityClientEngine.eyePos(player, tickProgress).add(up.multiply(HEAD_OFFSET));
        }
        return player.getLerpedPos(tickProgress).add(0.0D, player.getStandingEyeHeight() + HEAD_OFFSET, 0.0D);
    }

    private static float revealProgress(UiActivityClient.VisualState state, double animationTime) {
        if (state.isHiding()) {
            float hidden = MathHelper.clamp(
                    (float) ((animationTime - state.hidingAtGameTime()) / UiActivityClient.HIDE_DURATION_TICKS),
                    0.0F,
                    1.0F
            );
            return 1.0F - hidden;
        }
        return MathHelper.clamp(
                (float) ((animationTime - state.shownAtGameTime()) / UiActivityClient.REVEAL_DURATION_TICKS),
                0.0F,
                1.0F
        );
    }

    private static void drawBubble(VertexConsumer vertices, MatrixStack matrices, WorldRenderContext context,
                                   Vec3d bubblePos, float reveal, float dotPhase, int contentId) {
        Vec3d cameraPos = context.camera().getPos();
        int revealByte = Math.round(reveal * 255.0F);
        int dotPhaseByte = Math.round(dotPhase * 255.0F);
        int contentByte = Math.clamp(contentId, 0, 255);
        float halfWidth = WIDTH * 0.5F;
        float halfHeight = HEIGHT * 0.5F;

        matrices.push();
        matrices.translate(
                bubblePos.x - cameraPos.x,
                bubblePos.y - cameraPos.y,
                bubblePos.z - cameraPos.z
        );
        matrices.multiply(context.camera().getRotation());
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        emitVertex(vertices, positionMatrix, -halfWidth, -halfHeight, 0.0F, 0.0F,
                revealByte, dotPhaseByte, contentByte);
        emitVertex(vertices, positionMatrix, halfWidth, -halfHeight, 1.0F, 0.0F,
                revealByte, dotPhaseByte, contentByte);
        emitVertex(vertices, positionMatrix, halfWidth, halfHeight, 1.0F, 1.0F,
                revealByte, dotPhaseByte, contentByte);
        emitVertex(vertices, positionMatrix, -halfWidth, halfHeight, 0.0F, 1.0F,
                revealByte, dotPhaseByte, contentByte);
        matrices.pop();
    }

    private static void emitVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v,
                                   int reveal, int dotPhase, int contentId) {
        vertices.vertex(matrix, x, y, 0.0F)
                .texture(u, v)
                .color(reveal, dotPhase, contentId, 255);
    }
}
