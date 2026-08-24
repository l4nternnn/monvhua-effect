package com.kuilunfuzhe.monvhua.renderer.activity;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityClient;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleSize;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleStyle;
import com.kuilunfuzhe.monvhua.features.activity.EmotionCatalog;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityBasis;
import com.kuilunfuzhe.monvhua.features.gravity.SurfaceGravityClientEngine;
import com.kuilunfuzhe.monvhua.features.activity.emotion.EmotionTextureManager;
import com.kuilunfuzhe.monvhua.features.activity.emotion.FoodAnimation;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarCatalog;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarLayout;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import net.minecraft.util.Identifier;
import com.kuilunfuzhe.monvhua.renderer.worlddisplay.WorldDisplayTextureRenderer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class UiActivityBubbleRenderer {
    private static final double MIN_RENDER_DISTANCE_BLOCKS = 64.0D;
    private static final double MAX_RENDER_DISTANCE_BLOCKS = 128.0D;
    private static final double NAME_LABEL_EXTRA_HEIGHT = 0.50D;
    private static final double NAME_LABEL_CLEARANCE = 0.12D;
    private static final double TAIL_TO_CENTER = 0.38D;
    private static final float PIXEL_ASPECT = 28.0F / 18.0F;
    private static final float WIDTH = 0.92F * 4.0F / 3.0F;
    private static final float HEIGHT = 0.62F * 4.0F / 3.0F;
    private static final int DOT_CYCLE_TICKS = 24;
    private static volatile float sizeMultiplier = UiActivityBubbleSize.DEFAULT_MULTIPLIER;
    private static volatile UiActivityBubbleStyle style = UiActivityBubbleStyle.DEFAULT;
    private static final List<PendingBubble> PENDING = new ArrayList<>();
    private static final List<PendingAvatar> PENDING_AVATARS = new ArrayList<>();
    private static final Set<Identifier> PREPARED_AVATAR_TEXTURES = new HashSet<>();

    private UiActivityBubbleRenderer() {
    }

    public static void setSizeMultiplier(float multiplier) {
        sizeMultiplier = UiActivityBubbleSize.sanitize(multiplier);
    }

    public static float sizeMultiplier() {
        return sizeMultiplier;
    }

    public static void setStyle(UiActivityBubbleStyle nextStyle) {
        style = nextStyle == null ? UiActivityBubbleStyle.DEFAULT : nextStyle;
    }

    public static UiActivityBubbleStyle style() {
        return style;
    }

    /** Loads resource-backed avatar textures outside the final composite pass. */
    public static void prepareAvatarTexture(MinecraftClient client, Identifier textureId) {
        if (client == null || textureId == null || PREPARED_AVATAR_TEXTURES.contains(textureId)) {
            return;
        }
        client.getTextureManager().getTexture(textureId);
        PREPARED_AVATAR_TEXTURES.add(textureId);
    }

    public static void render(WorldRenderContext context) {
        renderInternal(context);
    }

    private static void renderInternal(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null
                || context.positionMatrix() == null || context.projectionMatrix() == null) {
            return;
        }

        PENDING.clear();
        PENDING_AVATARS.clear();

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        double animationTime = client.world.getTime() + tickProgress;
        long animationMillis = Util.getMeasuringTimeMs();
        EmotionTextureManager.trimInactive(animationMillis);
        Vec3d cameraPos = context.camera().getPos();
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
            boolean sleepClouds = emotion != null && emotion.id() == 11;
            boolean blockDisplay = emotion != null && emotion.type() == EmotionCatalog.Type.BLOCK_DISPLAY;
            boolean foodAnimation = FoodAnimation.isEatFood(state.contentId());
            Vec3d bubblePos = bubblePosition(player, tickProgress, sleepClouds);
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
                    ? effectPhase(state, emotion, animationMillis)
                    : dotPhase;
            Identifier bubbleTexture = UiActivityBubbleTextureRenderer.render(
                    player.getUuid(),
                    client,
                    emotionTexture,
                    reveal,
                    effectPhase,
                    effectiveContentId,
                    style.vertexAlpha()
            );
            if (bubbleTexture != null) {
                PendingBubble pending = projectBubble(
                        context, bubbleTexture, bubblePos, style == UiActivityBubbleStyle.PIXEL
                );
                if (pending != null) {
                    PENDING.add(pending);
                    int avatarId = UiActivityClient.avatarFor(player.getUuid());
                    Identifier avatarTexture = UiActivityBubbleAvatarCatalog.textureId(avatarId);
                    if (avatarTexture != null) {
                        UiActivityBubbleAvatarLayout layout = UiActivityClient.avatarLayout(avatarId);
                        boolean pixelAvatar = UiActivityBubbleAvatarCatalog.isPixel(avatarId);
                        float bubbleWidth = pendingBubbleWidth(style == UiActivityBubbleStyle.PIXEL);
                        float avatarHeight = HEIGHT * sizeMultiplier * layout.scale();
                        float avatarWidth = avatarHeight * avatarAspect(avatarId);
                        float localX = (layout.centerX() - 0.5F) * bubbleWidth * sizeMultiplier;
                        // Layout Y is measured from the bubble's top edge, while the
                        // billboard's positive local Y points upward.
                        float localY = (0.5F - layout.centerY()) * HEIGHT * sizeMultiplier;
                        PendingAvatar avatar = projectQuad(context, avatarTexture, bubblePos,
                                localX, localY, avatarWidth, avatarHeight, pixelAvatar);
                        if (avatar != null) PENDING_AVATARS.add(avatar);
                    }
                }
            }
        }
        UiActivityBubbleTextureRenderer.trimInactive(client, animationMillis);
    }

    /**
     * Draws cached bubbles after Iris' final pass. The main depth attachment is
     * shared with Iris' gbuffer, so this pass keeps real block occlusion without
     * sending the bubble through a shader-pack entity pass.
     */
    public static void renderPostProcess() {
        if (PENDING.isEmpty() && PENDING_AVATARS.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null
                || main.getDepthAttachmentView() == null) {
            PENDING.clear();
            return;
        }

        try {
            for (PendingBubble bubble : PENDING) {
                GpuBuffer vertices = createCompositeBuffer(bubble);
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Monvhua activity bubble composite",
                        main.getColorAttachmentView(),
                        OptionalInt.empty(),
                        main.getDepthAttachmentView(),
                        OptionalDouble.empty())) {
                    pass.setPipeline(UiActivityBubblePipelines.COMPOSITE);
                    pass.setVertexBuffer(0, vertices);
                    GpuTextureView texture = client.getTextureManager()
                            .getTexture(bubble.textureId()).getGlTextureView();
                    if (texture == null) {
                        continue;
                    }
                    pass.bindSampler("InSampler", texture);
                    pass.draw(0, 6);
                } finally {
                    vertices.close();
                }
            }
            for (PendingAvatar avatar : PENDING_AVATARS) {
                if (!PREPARED_AVATAR_TEXTURES.contains(avatar.textureId())) {
                    continue;
                }
                GpuBuffer vertices = createCompositeBuffer(avatar);
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Monvhua activity avatar composite",
                        main.getColorAttachmentView(), OptionalInt.empty(),
                        main.getDepthAttachmentView(), OptionalDouble.empty())) {
                    pass.setPipeline(UiActivityBubblePipelines.AVATAR_COMPOSITE);
                    pass.setVertexBuffer(0, vertices);
                    GpuTextureView texture = client.getTextureManager()
                            .getTexture(avatar.textureId()).getGlTextureView();
                    if (texture == null) continue;
                    pass.bindSampler("InSampler", texture);
                    pass.draw(0, 6);
                } finally {
                    vertices.close();
                }
            }
        } finally {
            PENDING.clear();
            PENDING_AVATARS.clear();
        }
    }

    public static void clearPending() {
        PENDING.clear();
        PENDING_AVATARS.clear();
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

    /**
     * Procedural artwork is cosmetic and must run against the local monotonic
     * clock. Server world time is only used to synchronize the triggering state.
     */
    private static float effectPhase(UiActivityClient.VisualState state, EmotionCatalog.Entry emotion,
                                     long animationMillis) {
        long cycleMillis = EmotionCatalog.animationCycleMillis(emotion);
        long elapsed = Math.max(0L, animationMillis - state.effectStartedAtMillis());
        return (elapsed % cycleMillis) / (float) cycleMillis;
    }

    private static boolean shouldRender(MinecraftClient client, PlayerEntity player, Vec3d cameraPos) {
        if (!player.isAlive() || player.isSpectator() || player.isInvisible()) {
            return false;
        }
        if (player == client.player && client.options.getPerspective().isFirstPerson()) {
            return false;
        }
        double renderDistance = Math.min(
                MAX_RENDER_DISTANCE_BLOCKS,
                Math.max(
                        MIN_RENDER_DISTANCE_BLOCKS,
                        client.options.getViewDistance().getValue() * 16.0D
                )
        );
        return player.squaredDistanceTo(cameraPos) <= renderDistance * renderDistance;
    }

    private static Vec3d bubblePosition(PlayerEntity player, float tickProgress, boolean sleepClouds) {
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        double centerCorrection = (HEIGHT * (sizeMultiplier - 1.0F)) * 0.5D;
        double tailClearance = sleepClouds ? 0.0D : TAIL_TO_CENTER;
        if (SurfaceGravityClientEngine.isRenderActive(player)) {
            Direction down = GravityMagic.getSurfaceGravityDirection(player);
            Vec3d up = SurfaceGravityBasis.of(down).up();
            Vec3d eye = SurfaceGravityClientEngine.eyePos(player, tickProgress);
            Vec3d bodyTop = eye.add(up.multiply(dimensions.height() - dimensions.eyeHeight()));
            return bodyTop.add(up.multiply(NAME_LABEL_EXTRA_HEIGHT + NAME_LABEL_CLEARANCE + tailClearance
                    + centerCorrection));
        }
        return player.getLerpedPos(tickProgress).add(
                0.0D,
                dimensions.height() + NAME_LABEL_EXTRA_HEIGHT + NAME_LABEL_CLEARANCE + tailClearance
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

    private static PendingBubble projectBubble(WorldRenderContext context, Identifier textureId,
                                               Vec3d bubblePos, boolean pixelStyle) {
        float bubbleWidth = pendingBubbleWidth(pixelStyle);
        PendingAvatar projected = projectQuad(context, textureId, bubblePos, 0.0F, 0.0F,
                bubbleWidth * sizeMultiplier, HEIGHT * sizeMultiplier, pixelStyle);
        return projected == null ? null
                : new PendingBubble(projected.textureId(), projected.projected(), projected.pixelStyle());
    }

    private static float pendingBubbleWidth(boolean pixelStyle) {
        return pixelStyle ? HEIGHT * PIXEL_ASPECT : WIDTH;
    }

    private static float avatarAspect(int avatarId) {
        return switch (avatarId) {
            case UiActivityBubbleAvatarCatalog.HIRO -> 712.0F / 787.0F;
            case UiActivityBubbleAvatarCatalog.WEIJIE -> 915.0F / 918.0F;
            case UiActivityBubbleAvatarCatalog.NOA -> 32.0F / 33.0F;
            default -> 1.0F;
        };
    }

    private static PendingAvatar projectQuad(WorldRenderContext context, Identifier textureId,
                                             Vec3d quadPos, float centerX, float centerY,
                                             float width, float height, boolean pixelStyle) {
        Vec3d cameraPos = context.camera().getPos();
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;

        Matrix4f model = new Matrix4f(context.positionMatrix())
                .translate((float) (quadPos.x - cameraPos.x),
                        (float) (quadPos.y - cameraPos.y),
                        (float) (quadPos.z - cameraPos.z))
                .rotate(context.camera().getRotation());
        Matrix4f clip = new Matrix4f(context.projectionMatrix()).mul(model);
        float[][] corners = {
                {centerX - halfWidth, centerY - halfHeight, 0.0F, 0.0F, 0.0F},
                {centerX + halfWidth, centerY - halfHeight, 0.0F, 1.0F, 0.0F},
                {centerX + halfWidth, centerY + halfHeight, 0.0F, 1.0F, 1.0F},
                {centerX - halfWidth, centerY + halfHeight, 0.0F, 0.0F, 1.0F}
        };
        float[] projected = new float[20];
        for (int i = 0; i < corners.length; i++) {
            float[] corner = corners[i];
            org.joml.Vector4f position = clip.transform(new org.joml.Vector4f(
                    corner[0], corner[1], corner[2], 1.0F));
            if (position.w <= 0.0001F) {
                return null;
            }
            float inverseW = 1.0F / position.w;
            int offset = i * 5;
            projected[offset] = position.x * inverseW;
            projected[offset + 1] = position.y * inverseW;
            projected[offset + 2] = position.z * inverseW;
            projected[offset + 3] = corner[3];
            projected[offset + 4] = corner[4];
        }
        return new PendingAvatar(textureId, projected, pixelStyle);
    }

    private static GpuBuffer createCompositeBuffer(PendingBubble bubble) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(6 * 24).order(ByteOrder.nativeOrder());
        putCompositeVertex(buffer, bubble, 0);
        putCompositeVertex(buffer, bubble, 1);
        putCompositeVertex(buffer, bubble, 2);
        putCompositeVertex(buffer, bubble, 0);
        putCompositeVertex(buffer, bubble, 2);
        putCompositeVertex(buffer, bubble, 3);
        buffer.flip();
        return RenderSystem.getDevice().createBuffer(
                () -> "Monvhua activity bubble composite vertices", 40, buffer);
    }

    private static GpuBuffer createCompositeBuffer(PendingAvatar avatar) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(6 * 24).order(ByteOrder.nativeOrder());
        putCompositeVertex(buffer, avatar, 0);
        putCompositeVertex(buffer, avatar, 1);
        putCompositeVertex(buffer, avatar, 2);
        putCompositeVertex(buffer, avatar, 0);
        putCompositeVertex(buffer, avatar, 2);
        putCompositeVertex(buffer, avatar, 3);
        buffer.flip();
        return RenderSystem.getDevice().createBuffer(
                () -> "Monvhua activity avatar composite vertices", 40, buffer);
    }

    private static void putCompositeVertex(ByteBuffer buffer, PendingBubble bubble, int index) {
        int offset = index * 5;
        float[] vertices = bubble.projected();
        buffer.putFloat(vertices[offset]);
        buffer.putFloat(vertices[offset + 1]);
        buffer.putFloat(vertices[offset + 2]);
        buffer.putFloat(vertices[offset + 3]);
        buffer.putFloat(vertices[offset + 4]);
        // Alpha is a style flag for the composite shader; the shader intentionally
        // keeps the bubble texture's own color and alpha unchanged.
        buffer.putInt(bubble.pixelStyle() ? 0xFEFFFFFF : 0xFFFFFFFF);
    }

    private static void putCompositeVertex(ByteBuffer buffer, PendingAvatar avatar, int index) {
        int offset = index * 5;
        float[] vertices = avatar.projected();
        buffer.putFloat(vertices[offset]);
        buffer.putFloat(vertices[offset + 1]);
        buffer.putFloat(vertices[offset + 2]);
        buffer.putFloat(vertices[offset + 3]);
        buffer.putFloat(vertices[offset + 4]);
        buffer.putInt(avatar.pixelStyle() ? 0xFEFFFFFF : 0xFFFFFFFF);
    }

    private record PendingBubble(Identifier textureId, float[] projected, boolean pixelStyle) {
    }

    private record PendingAvatar(Identifier textureId, float[] projected, boolean pixelStyle) {
    }
}
