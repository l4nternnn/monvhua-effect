package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityClient;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleSize;
import com.kuilunfuzhe.monvhua.features.activity.EmotionCatalog;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionTextureManager;
import com.kuilunfuzhe.monvhua.features.activity.emotion.FoodAnimation;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import net.minecraft.util.Identifier;
import com.kuilunfuzhe.monvhua.renderer.worlddisplay.WorldDisplayTextureRenderer;

public final class UiActivityBubbleRenderer {
    private static final double MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final double NAME_LABEL_EXTRA_HEIGHT = 0.50D;
    private static final double NAME_LABEL_CLEARANCE = 0.12D;
    private static final double TAIL_TO_CENTER = 0.38D;
    private static final float WIDTH = 0.92F * 4.0F / 3.0F;
    private static final float HEIGHT = 0.62F * 4.0F / 3.0F;
    private static final int DOT_CYCLE_TICKS = 24;
    private static volatile float sizeMultiplier = UiActivityBubbleSize.DEFAULT_MULTIPLIER;

    private UiActivityBubbleRenderer() {
    }

    public static void setSizeMultiplier(float multiplier) {
        sizeMultiplier = UiActivityBubbleSize.sanitize(multiplier);
    }

    public static float sizeMultiplier() {
        return sizeMultiplier;
    }

    public static void render(WorldRenderContext context) {
        renderInternal(context);
    }

    private static void renderInternal(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        double animationTime = client.world.getTime() + tickProgress;
        long animationMillis = Util.getMeasuringTimeMs();
        EmotionTextureManager.trimInactive(animationMillis);
        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        boolean blockTexturePrepared = false;
        for (PlayerEntity player : client.world.getPlayers()) {
            UiActivityClient.VisualState state = UiActivityClient.stateFor(player.getUuid());
            if (state == null || !shouldRender(client, player, cameraPos)) {
                continue;
            }

            float reveal = revealProgress(state, animationTime);
            if (reveal <= 0.001F) {
                continue;
            }

            EmotionCatalog.Entry emotion = EmotionCatalog.byId(state.contentId());
            boolean procedural = EmotionCatalog.isProcedural(emotion);
            boolean blockDisplay = emotion != null && emotion.type() == EmotionCatalog.Type.BLOCK_DISPLAY;
            boolean foodAnimation = FoodAnimation.isEatFood(state.contentId());
            Vec3d bubblePos = bubblePosition(player, tickProgress);
            if (bubblePos.squaredDistanceTo(cameraPos) > MAX_DISTANCE_SQUARED) {
                continue;
            }

            float dotPhase = reveal >= 0.999F
                    ? (float) (animationTime % DOT_CYCLE_TICKS) / DOT_CYCLE_TICKS
                    : 0.0F;
            if (blockDisplay && !blockTexturePrepared) {
                WorldDisplayTextureRenderer.render(
                        state.contentId(), client, blockOpenProgress(state, animationTime)
                );
                blockTexturePrepared = true;
            }

            Identifier emotionTexture = foodAnimation
                    ? FoodAnimation.textureFor(player.getUuid(), state.effectStartedAtGameTime())
                    : procedural && emotion != null ? emotion.resourceId()
                    : blockDisplay ? WorldDisplayTextureRenderer.textureId()
                    : EmotionTextureManager.textureFor(state.contentId(), true, animationMillis);
            int effectiveContentId = procedural || blockDisplay || emotionTexture != null
                    ? state.contentId() : 0;
            if (foodAnimation) {
                effectiveContentId = FoodAnimation.renderContentId(
                        player.getUuid(), state.effectStartedAtGameTime()
                );
            }
            float effectPhase = procedural
                    ? (animationMillis % EmotionCatalog.animationCycleMillis(emotion))
                        / (float) EmotionCatalog.animationCycleMillis(emotion)
                    : dotPhase;
            Identifier bubbleTexture = UiActivityBubbleTextureRenderer.render(
                    player.getUuid(),
                    client,
                    emotionTexture,
                    reveal,
                    effectPhase,
                    effectiveContentId
            );
            if (bubbleTexture != null) {
                VertexConsumer vertices = context.consumers().getBuffer(
                        RenderLayer.getEntityTranslucentEmissive(bubbleTexture)
                );
                drawWorldBubble(vertices, matrices, context, bubblePos);
            }
        }
        UiActivityBubbleTextureRenderer.trimInactive(client, animationMillis);
    }

    private static float blockOpenProgress(UiActivityClient.VisualState state, double animationTime) {
        float rawProgress;
        if (state.isPendingHide() || state.isHiding()) {
            double closingElapsed = Math.max(0.0D, animationTime - state.hideRequestedAtGameTime());
            rawProgress = 1.0F - (float) closingElapsed / 10.0F;
        } else {
            double openingElapsed = Math.max(0.0D, animationTime - state.effectStartedAtGameTime());
            rawProgress = (float) openingElapsed / 10.0F;
        }
        rawProgress = MathHelper.clamp(rawProgress, 0.0F, 1.0F);
        float inverse = 1.0F - rawProgress;
        return 1.0F - inverse * inverse * inverse;
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
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        double centerCorrection = (HEIGHT * (sizeMultiplier - 1.0F)) * 0.5D;
        if (SurfaceGravityClientEngine.isRenderActive(player)) {
            Direction down = GravityMagic.getSurfaceGravityDirection(player);
            Vec3d up = SurfaceGravityBasis.of(down).up();
            Vec3d eye = SurfaceGravityClientEngine.eyePos(player, tickProgress);
            Vec3d bodyTop = eye.add(up.multiply(dimensions.height() - dimensions.eyeHeight()));
            return bodyTop.add(up.multiply(NAME_LABEL_EXTRA_HEIGHT + NAME_LABEL_CLEARANCE + TAIL_TO_CENTER
                    + centerCorrection));
        }
        return player.getLerpedPos(tickProgress).add(
                0.0D,
                dimensions.height() + NAME_LABEL_EXTRA_HEIGHT + NAME_LABEL_CLEARANCE + TAIL_TO_CENTER
                        + centerCorrection,
                0.0D
        );
    }

    private static float revealProgress(UiActivityClient.VisualState state, double animationTime) {
        if (state.isPendingHide()) {
            return 1.0F;
        }
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

    private static void drawWorldBubble(VertexConsumer vertices, MatrixStack matrices, WorldRenderContext context,
                                        Vec3d bubblePos) {
        Vec3d cameraPos = context.camera().getPos();
        float halfWidth = WIDTH * sizeMultiplier * 0.5F;
        float halfHeight = HEIGHT * sizeMultiplier * 0.5F;

        matrices.push();
        matrices.translate(
                bubblePos.x - cameraPos.x,
                bubblePos.y - cameraPos.y,
                bubblePos.z - cameraPos.z
        );
        matrices.multiply(context.camera().getRotation());
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        // FBO textures use the same bottom-to-top V convention as the original world quad.
        emitWorldVertex(vertices, positionMatrix, -halfWidth, -halfHeight, 0.0F, 0.0F);
        emitWorldVertex(vertices, positionMatrix, halfWidth, -halfHeight, 1.0F, 0.0F);
        emitWorldVertex(vertices, positionMatrix, halfWidth, halfHeight, 1.0F, 1.0F);
        emitWorldVertex(vertices, positionMatrix, -halfWidth, halfHeight, 0.0F, 1.0F);
        matrices.pop();
    }

    private static void emitWorldVertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v) {
        vertices.vertex(matrix, x, y, 0.0F)
                .texture(u, v)
                .color(255, 255, 255, 255)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0x00F000F0)
                .normal(0.0F, 0.0F, 1.0F);
    }
}
