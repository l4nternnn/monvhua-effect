package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.compat.DhCompat;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.compat.PortalIrisCompat;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalClipPlane;
import com.kuilunfuzhe.monvhua.features.portal.PortalFrame;
import com.kuilunfuzhe.monvhua.features.portal.PortalLinkData;
import com.kuilunfuzhe.monvhua.features.portal.PortalTransform;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewTransform;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.client.render.IndependentPortalRenderer;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import com.kuilunfuzhe.monvhua.mixin.portal.SodiumWorldRendererAccessor;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalFramebufferRenderer {
    private static final AtomicBoolean RENDERING = new AtomicBoolean(false);
    private static final Map<PortalKey, RenderSlot> LIVE_SLOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<PortalKey, RenderSlot> PREVIEW_SLOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<PortalKey, Candidate> VISIBLE_CANDIDATES = new LinkedHashMap<>();
    private static final Map<PortalKey, Integer> PENDING_CAPTURES = new LinkedHashMap<>();

    private static long frameIndex;
    private static int lastFailureLogTick = Integer.MIN_VALUE;
    private static long lastRemoteRendererStateLogFrame = Long.MIN_VALUE / 2L;
    private static long lastLiveContractLogFrame = Long.MIN_VALUE / 2L;
    private static long lastLiveCompositeLogFrame = Long.MIN_VALUE / 2L;
    private static BlockPos lastRequestedSource;
    private static Candidate lastLiveCandidate;
    private static long lastLiveCandidateFrame = Long.MIN_VALUE / 2L;
    private static final Map<BlockPos, RemoteRequestState> REMOTE_REQUESTS = new LinkedHashMap<>();
    private static boolean remoteTerrainDirty;
    private static WorldRenderer remoteWorldRenderer;
    private static BufferBuilderStorage remoteBufferBuilders;
    private static ClientWorld remoteRendererWorld;
    private static FogRenderer remoteFogRenderer;
    private static RawProjectionMatrix remoteProjectionMatrix;
    private static final LongOpenHashSet REMOTE_SODIUM_CHUNKS = new LongOpenHashSet();
    private static final LongOpenHashSet QUEUED_REMOTE_SODIUM_CHUNKS = new LongOpenHashSet();
    private static final LongOpenHashSet DIRTY_REMOTE_SODIUM_CHUNKS = new LongOpenHashSet();
    private static final ArrayDeque<Long> PENDING_REMOTE_SODIUM_CHUNKS = new ArrayDeque<>();
    private static GpuBuffer portalAreaBuffer;
    private static boolean creatingRemoteRenderer;

    private PortalFramebufferRenderer() {
    }

    public static void requestPreviewCapture(BlockPos portalPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || portalPos == null) {
            return;
        }
        PENDING_CAPTURES.put(new PortalKey(client.world.getRegistryKey().getValue(), portalPos.toImmutable()), 0);
    }

    public static void offerVisiblePortal(PortalBlockEntity portal, Vec3d cameraPos) {
        if (RENDERING.get() || portal == null || !portal.isController()
                || !portal.isActive() || portal.getLinkData() == null || portal.getWorld() == null) {
            return;
        }
        PortalKey key = keyFor(portal);
        double distance = Math.max(1.0D, portal.getPortalCenter().squaredDistanceTo(cameraPos));
        double priority = (portal.getPortalWidth() * (double) portal.getPortalHeight()) / distance;
        Candidate candidate = new Candidate(key, portal, priority);
        VISIBLE_CANDIDATES.merge(key, candidate,
                (left, right) -> right.priority > left.priority ? right : left);
    }

    public static Identifier getTextureIdFor(PortalBlockEntity portal) {
        if (portal == null || portal.getWorld() == null || RENDERING.get()) {
            return null;
        }
        PortalLinkData link = portal.getLinkData();
        if (link == null) {
            return null;
        }

        RenderSlot live = LIVE_SLOTS.get(keyFor(portal));
        if (live != null && live.ready && link.targetPos().equals(live.targetPos)) {
            return null;
        }

        PortalKey targetKey = new PortalKey(
                portal.getWorld().getRegistryKey().getValue(),
                link.targetPos().toImmutable()
        );
        RenderSlot preview = PREVIEW_SLOTS.get(targetKey);
        return preview != null && preview.ready ? preview.frontTextureId() : null;
    }

    public static boolean shouldUseLiveScreenComposite(PortalBlockEntity portal) {
        if (portal == null || portal.getWorld() == null || RENDERING.get()) {
            return false;
        }
        PortalLinkData link = portal.getLinkData();
        if (link == null) {
            return false;
        }
        PortalKey key = keyFor(portal);
        RenderSlot live = LIVE_SLOTS.get(key);
        if (live == null || !live.ready || !link.targetPos().equals(live.targetPos)) {
            return false;
        }
        return lastLiveCandidate != null && key.equals(lastLiveCandidate.key())
                || lastRequestedSource != null && lastRequestedSource.equals(portal.getPos());
    }

    public static void renderNearestPortal(RenderTickCounter tickCounter, Camera mainCamera,
                                           Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            VISIBLE_CANDIDATES.clear();
            return;
        }
        boolean irisShaders = PortalIrisCompat.isShaderPackActive();
        Matrix4f mainViewMatrix = new Matrix4f(positionMatrix);
        Matrix4f perspectiveProjection = new Matrix4f(projectionMatrix);
        int viewDistanceChunks = Math.max(
                client.options.getClampedViewDistance(),
                PortalRemoteChunkCache.getViewRadius()
        );
        RenderFrame frame = new RenderFrame(
                client,
                tickCounter,
                snapshotMainCamera(mainCamera),
                mainViewMatrix,
                perspectiveProjection,
                viewDistanceChunks,
                irisShaders
                        ? PortalViewConfig.IRIS_RENDER_BUDGET
                        : PortalViewConfig.VANILLA_RENDER_BUDGET,
                irisShaders
                        ? PortalViewConfig.IRIS_MAX_SURFACE_RESOLUTION
                        : PortalViewConfig.VANILLA_MAX_SURFACE_RESOLUTION
        );

        frameIndex++;
        List<Candidate> candidates = new ArrayList<>(VISIBLE_CANDIDATES.values());
        VISIBLE_CANDIDATES.clear();

        if (processPendingCapture(frame)) {
            return;
        }
        candidates.removeIf(candidate -> !isLiveCandidateValid(candidate.portal()));
        Candidate recovered = recoverLastLiveCandidate(frame);
        if (recovered != null) {
            candidates.removeIf(candidate -> candidate.key.equals(recovered.key));
            candidates.add(recovered);
        }
        if (candidates.isEmpty()) {
            closeLastRequestedRemoteView();
            return;
        }
        if (RENDERING.getAndSet(true)) {
            return;
        }

        int budget = frame.renderBudget();
        int updateInterval = PortalViewConfig.LIVE_VIEW_UPDATE_INTERVAL_FRAMES;
        candidates.sort(PortalFramebufferRenderer::compareCandidates);
        int remoteViewCandidateIndex = 0;
        if (lastRequestedSource != null) {
            for (int index = 0; index < candidates.size(); index++) {
                if (lastRequestedSource.equals(candidates.get(index).portal.getPos())) {
                    remoteViewCandidateIndex = index;
                    break;
                }
            }
        }
        if (remoteViewCandidateIndex > 0) {
            Candidate remoteViewCandidate = candidates.remove(remoteViewCandidateIndex);
            candidates.add(0, remoteViewCandidate);
        }
        Candidate remoteViewCandidate = candidates.getFirst();
        lastLiveCandidate = remoteViewCandidate;
        lastLiveCandidateFrame = frameIndex;
        requestRemoteView(frame, remoteViewCandidate.portal);

        try {
            int rendered = 0;
            for (Candidate candidate : candidates) {
                if (rendered >= budget) {
                    break;
                }
                PortalBlockEntity portal = candidate.portal;
                if (!isLiveCandidateValid(portal)) {
                    continue;
                }

                RenderSlot slot = LIVE_SLOTS.computeIfAbsent(
                        candidate.key,
                        key -> new RenderSlot(client, key, "live")
                );
                if (frameIndex - slot.lastAttemptFrame < updateInterval) {
                    continue;
                }
                slot.lastAttemptFrame = frameIndex;
                rendered++;

                try {
                    renderLivePortal(frame, portal, slot);
                } catch (RuntimeException exception) {
                    logRenderFailure(client, "live portal", exception);
                }
            }
        } finally {
            RENDERING.set(false);
            trimSlots(LIVE_SLOTS, PortalViewConfig.MAX_LIVE_RENDER_SLOTS);
        }
    }

    public static void onRemoteChunkLoaded(int chunkX, int chunkZ) {
        remoteTerrainDirty = true;
        queueRemoteSodiumChunk(chunkX, chunkZ, true);
    }

    public static void onRemoteViewChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!RenderSystem.isOnRenderThread()) {
            client.execute(PortalFramebufferRenderer::onRemoteViewChanged);
            return;
        }
        DhCompat.resetPortalRenderState();
        shutdownRemoteRenderer();
    }

    public static void onRemoteChunksRemoved(LongOpenHashSet removedChunks) {
        if (removedChunks == null || removedChunks.isEmpty()) {
            return;
        }
        QUEUED_REMOTE_SODIUM_CHUNKS.removeAll(removedChunks);
        DIRTY_REMOTE_SODIUM_CHUNKS.removeAll(removedChunks);
        PENDING_REMOTE_SODIUM_CHUNKS.removeIf(removedChunks::contains);

        if (remoteWorldRenderer == null
                || !(remoteWorldRenderer instanceof LevelRendererExtension extension)) {
            return;
        }
        SodiumWorldRenderer sodiumRenderer = extension.sodium$getWorldRenderer();
        if (!(sodiumRenderer instanceof SodiumWorldRendererAccessor accessor)) {
            shutdownRemoteRenderer();
            return;
        }
        RenderSectionManager sectionManager = accessor.monvhua$getRenderSectionManager();
        if (sectionManager == null) {
            return;
        }

        boolean removedAny = false;
        for (long chunkKey : removedChunks) {
            if (REMOTE_SODIUM_CHUNKS.remove(chunkKey)) {
                sectionManager.onChunkRemoved(
                        ChunkPos.getPackedX(chunkKey),
                        ChunkPos.getPackedZ(chunkKey)
                );
                removedAny = true;
            }
        }
        if (removedAny) {
            sodiumRenderer.scheduleTerrainUpdate();
        }
    }

    public static boolean isRemoteWorldRenderer(WorldRenderer renderer) {
        return renderer != null && renderer == remoteWorldRenderer;
    }

    public static boolean isRenderingPortalView() {
        return RENDERING.get();
    }

    public static boolean isCreatingRemoteRenderer() {
        return creatingRemoteRenderer;
    }

    public static void shutdownRemoteRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!RenderSystem.isOnRenderThread()) {
            client.execute(PortalFramebufferRenderer::shutdownRemoteRenderer);
            return;
        }
        try {
            if (remoteWorldRenderer != null) {
                remoteWorldRenderer.setWorld(null);
                if (client.world != null) {
                    client.getEntityRenderDispatcher().setWorld(client.world);
                }
                remoteWorldRenderer.close();
            }
            if (remoteFogRenderer != null) {
                remoteFogRenderer.close();
            }
            if (remoteProjectionMatrix != null) {
                remoteProjectionMatrix.close();
            }
        } finally {
            remoteWorldRenderer = null;
            remoteBufferBuilders = null;
            remoteRendererWorld = null;
            remoteFogRenderer = null;
            remoteProjectionMatrix = null;
            PortalRemoteRenderContext.clearRemoteRendererAlive();
            if (portalAreaBuffer != null) {
                portalAreaBuffer.close();
                portalAreaBuffer = null;
            }
            REMOTE_SODIUM_CHUNKS.clear();
            QUEUED_REMOTE_SODIUM_CHUNKS.clear();
            DIRTY_REMOTE_SODIUM_CHUNKS.clear();
            PENDING_REMOTE_SODIUM_CHUNKS.clear();
            remoteTerrainDirty = true;
        }
    }

    public static void resetAll(boolean clearLastFrames) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!RenderSystem.isOnRenderThread()) {
            client.execute(() -> resetAll(clearLastFrames));
            return;
        }
        VISIBLE_CANDIDATES.clear();
        PENDING_CAPTURES.clear();
        REMOTE_REQUESTS.clear();
        lastRequestedSource = null;
        lastLiveCandidate = null;
        lastLiveCandidateFrame = Long.MIN_VALUE / 2L;
        shutdownRemoteRenderer();
        if (clearLastFrames) {
            closeSlots(LIVE_SLOTS);
            closeSlots(PREVIEW_SLOTS);
        }
    }

    public static void closeRemoteView(BlockPos sourcePos) {
        if (sourcePos == null) {
            return;
        }
        BlockPos immutableSource = sourcePos.toImmutable();
        REMOTE_REQUESTS.remove(immutableSource);
        if (immutableSource.equals(lastRequestedSource)) {
            lastRequestedSource = null;
        }
        if (lastLiveCandidate != null && immutableSource.equals(lastLiveCandidate.portal().getPos())) {
            lastLiveCandidate = null;
            lastLiveCandidateFrame = Long.MIN_VALUE / 2L;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new PortalPackets.CloseRemoteViewC2S(immutableSource));
        }
    }

    private static boolean isLiveCandidateValid(PortalBlockEntity portal) {
        return portal != null
                && !portal.isRemoved()
                && portal.isActive()
                && portal.getLinkData() != null;
    }

    private static Candidate recoverLastLiveCandidate(RenderFrame frame) {
        if (frame == null || frame.client().world == null) {
            return null;
        }

        PortalBlockEntity portal = null;
        if (lastRequestedSource != null) {
            BlockEntity blockEntity = frame.client().world.getBlockEntity(lastRequestedSource);
            if (blockEntity instanceof PortalBlockEntity recoveredPortal) {
                portal = recoveredPortal;
            }
        }
        if (portal == null && lastLiveCandidate != null) {
            portal = lastLiveCandidate.portal();
        }
        if (!isLiveCandidateValid(portal) || !isPortalFrontVisible(frame, portal)) {
            return null;
        }
        boolean crosshairInside = isCrosshairInsidePortal(frame, portal);
        boolean withinGrace = frameIndex - lastLiveCandidateFrame <= PortalViewConfig.PORTAL_CANDIDATE_GRACE_FRAMES
                && screenPolygonForPortal(frame, portal) != null;
        if (!crosshairInside && !withinGrace) {
            return null;
        }

        double priority = lastLiveCandidate != null && lastLiveCandidate.portal() == portal
                ? lastLiveCandidate.priority()
                : 0.0D;
        return new Candidate(keyFor(portal), portal, priority);
    }

    private static boolean isCrosshairInsidePortal(RenderFrame frame, PortalBlockEntity portal) {
        return PortalSelector.isCrosshairInside(
                portal,
                frame.mainCamera().position(),
                rotateUnit(frame.mainCamera().rotation(), 0.0F, 0.0F, -1.0F)
        );
    }

    private static boolean isPortalFrontVisible(RenderFrame frame, PortalBlockEntity portal) {
        PortalFrame portalFrame = portal.getFrame();
        return frame.mainCamera().position().subtract(portalFrame.center())
                .dotProduct(portalFrame.normal()) > 0.0D;
    }

    private static void closeLastRequestedRemoteView() {
        if (lastRequestedSource != null) {
            closeRemoteView(lastRequestedSource);
        }
    }

    private static void requestRemoteView(RenderFrame frame, PortalBlockEntity portal) {
        PortalLinkData link = portal == null ? null : portal.getLinkData();
        if (link == null) {
            if (portal != null) {
                closeRemoteView(portal.getPos());
            }
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TargetPortalView targetView = targetView(client.world, link);
        PortalViewTransform.View view = portalViewTransform(frame, portal, targetView);
        if (view == null) {
            return;
        }
        BlockPos sourcePos = portal.getPos();
        BlockPos viewCenter = view.remoteViewCenter();
        RemoteRequestState previous = REMOTE_REQUESTS.get(sourcePos);
        boolean sameRequest = previous != null && viewCenter.equals(previous.viewCenter());
        if (sameRequest
                && frameIndex - previous.frame() < PortalViewConfig.REMOTE_REQUEST_INTERVAL_FRAMES) {
            return;
        }
        BlockPos immutableSource = sourcePos.toImmutable();
        if (lastRequestedSource != null && !lastRequestedSource.equals(immutableSource)) {
            closeRemoteView(lastRequestedSource);
        }
        lastRequestedSource = immutableSource;
        BlockPos immutableViewCenter = viewCenter.toImmutable();
        REMOTE_REQUESTS.put(lastRequestedSource, new RemoteRequestState(immutableViewCenter, frameIndex));
        ClientPlayNetworking.send(new PortalPackets.RequestRemoteViewC2S(lastRequestedSource, immutableViewCenter));
    }

    private static boolean processPendingCapture(RenderFrame frame) {
        MinecraftClient client = frame.client();
        Identifier currentDimension = client.world.getRegistryKey().getValue();
        Iterator<Map.Entry<PortalKey, Integer>> iterator = PENDING_CAPTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PortalKey, Integer> pending = iterator.next();
            if (!pending.getKey().dimension.equals(currentDimension)) {
                continue;
            }

            BlockEntity blockEntity = client.world.getBlockEntity(pending.getKey().pos);
            if (!(blockEntity instanceof PortalBlockEntity portal) || !portal.isController()) {
                int attempts = pending.getValue() + 1;
                if (attempts >= PortalViewConfig.CAPTURE_RETRY_LIMIT_FRAMES) {
                    iterator.remove();
                } else {
                    pending.setValue(attempts);
                }
                continue;
            }

            iterator.remove();
            if (RENDERING.getAndSet(true)) {
                PENDING_CAPTURES.put(pending.getKey(), pending.getValue());
                return false;
            }
            try {
                RenderSlot slot = PREVIEW_SLOTS.computeIfAbsent(
                        pending.getKey(),
                        key -> new RenderSlot(client, key, "preview")
                );
                renderPreview(frame, portal, slot, frame.maximumSurfaceResolution());
            } catch (RuntimeException exception) {
                logRenderFailure(client, "portal preview", exception);
            } finally {
                RENDERING.set(false);
                trimSlots(PREVIEW_SLOTS, PortalViewConfig.MAX_PREVIEW_RENDER_SLOTS);
            }
            return true;
        }
        return false;
    }

    private static void renderLivePortal(RenderFrame frame, PortalBlockEntity sourcePortal,
                                         RenderSlot slot) {
        MinecraftClient client = frame.client();
        PortalLinkData link = sourcePortal.getLinkData();
        if (link == null) {
            return;
        }
        PortalRemoteChunkCache.activate(sourcePortal.getPos());
        TargetPortalView targetView = targetView(client.world, link);

        PortalRenderParams params = liveRenderParams(frame, sourcePortal, targetView);
        if (params == null) {
            slot.freeze("missing_view_transform", frameIndex);
            return;
        }
        SimpleFramebuffer framebuffer = slot.prepare(params.resolution(), true);
        logLiveViewContract(params, framebuffer, frame);

        if (!renderPortalScene(
                frame,
                framebuffer,
                params.pose(),
                params.targetView().portal(),
                params.aspect(),
                false,
                params.viewTransform().aperture(),
                params.viewTransform().clipPlane()
        )) {
            slot.freeze("render_scene_failed", frameIndex);
            compositeLivePortalArea(frame, sourcePortal, slot, params);
            return;
        }

        PublishDecision decision = livePublishDecision(client.world, link.targetPos(), params.viewTransform().remoteViewCenter());
        if (!decision.publish()) {
            slot.freeze(decision.detail(), frameIndex);
            compositeLivePortalArea(frame, sourcePortal, slot, params);
            return;
        }
        slot.logStatus(decision.detail() + " viewMode=aperture displayPath=screen_projective_composite", frameIndex);
        slot.publish(link.targetPos(), frameIndex, frame.mainCamera());
        compositeLivePortalArea(frame, sourcePortal, slot, params);
    }

    private static PortalRenderParams liveRenderParams(RenderFrame frame, PortalBlockEntity sourcePortal,
                                                       TargetPortalView targetView) {
        PortalViewTransform.View viewTransform = portalViewTransform(frame, sourcePortal, targetView);
        if (viewTransform == null) {
            return null;
        }
        Resolution resolution = resolutionFor(sourcePortal, frame.maximumSurfaceResolution());
        CameraPose pose = cameraPoseForViewTransform(viewTransform);
        float aspect = sourcePortal.getPortalWidth() / (float) sourcePortal.getPortalHeight();
        PortalApertureProjection.CornerUvs apertureUvs = sourceMappedApertureUvs(
                PortalApertureProjection.textureCoordinates(
                        pose.position(),
                        pose.rotation(),
                        viewTransform.aperture()
                )
        );
        if (apertureUvs == null) {
            return null;
        }
        return new PortalRenderParams(sourcePortal, targetView, viewTransform, pose, resolution, aspect, apertureUvs);
    }

    private static void logLiveViewContract(PortalRenderParams params, SimpleFramebuffer framebuffer, RenderFrame frame) {
        if (params == null
                || frameIndex - lastLiveContractLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
            return;
        }
        lastLiveContractLogFrame = frameIndex;
        PortalFrame sourceFrame = params.sourcePortal().getFrame();
        PortalFrame targetFrame = targetFrameFor(params.targetView(), params.sourcePortal());
        LocalPortalOffset sourceLocal = localOffset(frame.mainCamera().position(), sourceFrame);
        LocalPortalOffset remoteLocal = localOffset(params.viewTransform().position(), targetFrame);
        int mainWidth = Math.max(1, frame.client().getFramebuffer().textureWidth);
        int mainHeight = Math.max(1, frame.client().getFramebuffer().textureHeight);
        int fboWidth = framebuffer == null ? 0 : framebuffer.textureWidth;
        int fboHeight = framebuffer == null ? 0 : framebuffer.textureHeight;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal live view contract: source={} mode={} displayPath={} textureUv={} projection={} requested={}x{} fbo={}x{} main={}x{} aspect={} sourceLocal={} remoteLocal={} remotePos={} mappedForward={} renderForward={} renderUp={} apertureCenterRay={} remoteCenter={}",
                params.sourcePortal().getPos(),
                "projective_aperture_texture",
                "screen_projective_composite",
                "aperture_projected_uv",
                "aperture",
                params.resolution().width(),
                params.resolution().height(),
                fboWidth,
                fboHeight,
                mainWidth,
                mainHeight,
                params.aspect(),
                sourceLocal,
                remoteLocal,
                params.viewTransform().position(),
                params.viewTransform().forward(),
                params.viewTransform().forward(),
                params.viewTransform().up(),
                params.viewTransform().centerRay(),
                params.viewTransform().remoteViewCenter()
        );
    }

    private static LocalPortalOffset localOffset(Vec3d position, PortalFrame frame) {
        if (position == null || frame == null) {
            return null;
        }
        Vec3d local = position.subtract(frame.center());
        return new LocalPortalOffset(
                local.dotProduct(frame.widthAxis()),
                local.dotProduct(frame.heightAxis()),
                local.dotProduct(frame.normal())
        );
    }

    private static void renderPreview(RenderFrame frame, PortalBlockEntity portal,
                                      RenderSlot slot, int maximumSide) {
        Resolution resolution = resolutionFor(portal, maximumSide);
        SimpleFramebuffer framebuffer = slot.prepare(resolution);
        PortalTransform.Rotation rotation = PortalTransform.rotationFromVector(normal(portal.getFacing()).multiply(-1.0D));
        CameraPose pose = new CameraPose(
                portal.getPortalCenter().add(normal(portal.getFacing()).multiply(0.55D)),
                rotation.yaw(),
                rotation.pitch(),
                null
        );
        float aspect = portal.getPortalWidth() / (float) portal.getPortalHeight();
        if (renderPortalScene(frame, framebuffer, pose, null, aspect, false, null, null)) {
            slot.publish(null, frameIndex);
        }
    }

    private static boolean renderPortalScene(RenderFrame frame, SimpleFramebuffer targetFramebuffer,
                                              CameraPose pose, PortalBlockEntity targetAperture,
                                              float aspect, boolean matchMainProjection,
                                              PortalViewTransform.Aperture aperture,
                                              PortalClipPlane clipPlane) {
        if (!PortalViewConfig.USE_INDEPENDENT_PORTAL_RENDERER) {
            try {
                return renderScene(frame, targetFramebuffer, pose, aspect, matchMainProjection, aperture, clipPlane);
            } catch (RuntimeException exception) {
                logRenderFailure(frame.client(), "portal world renderer", exception);
                return false;
            }
        }
        return IndependentPortalRenderer.render(targetFramebuffer, pose.position, pose.yaw, pose.pitch, aspect,
                targetAperture);
    }

    private static PublishDecision livePublishDecision(ClientWorld world, BlockPos targetPos, BlockPos expectedViewCenter) {
        if (world == null) {
            return PublishDecision.block("missing_client_world");
        }
        if (!PortalRemoteChunkCache.isCurrentTarget(targetPos)) {
            return PublishDecision.block("remote_target_mismatch target=" + targetPos
                    + " cached=" + PortalRemoteChunkCache.getTargetPos()
                    + " " + PortalRemoteChunkCache.debugSummary(world));
        }
        int loaded = PortalRemoteChunkCache.loadedChunkCount();
        if (loaded <= 0) {
            return PublishDecision.block("no_remote_chunks " + PortalRemoteChunkCache.debugSummary(world));
        }

        ChunkPos targetChunk = new ChunkPos(targetPos);
        if (!PortalRemoteChunkCache.isRenderable(world, targetChunk.x, targetChunk.z)) {
            return PublishDecision.block("missing_renderable_target_chunk chunk=" + targetChunk.x + "," + targetChunk.z
                    + " targetAccepted=" + PortalRemoteChunkCache.accepts(world, targetChunk.x, targetChunk.z)
                    + " targetFresh=" + PortalRemoteChunkCache.isFresh(world, targetChunk.x, targetChunk.z)
                    + " " + PortalRemoteChunkCache.debugSummary(world));
        }
        boolean staleTarget = !PortalRemoteChunkCache.isFresh(world, targetChunk.x, targetChunk.z);

        BlockPos centerPos = PortalRemoteChunkCache.getViewCenter();
        if (centerPos == null) {
            return PublishDecision.block("missing_view_center " + PortalRemoteChunkCache.debugSummary(world));
        }
        String centerDriftDetail = "";
        if (expectedViewCenter != null && !sameChunk(centerPos, expectedViewCenter)) {
            ChunkPos cachedCenter = new ChunkPos(centerPos);
            ChunkPos expectedCenter = new ChunkPos(expectedViewCenter);
            int drift = Math.max(
                    Math.abs(cachedCenter.x - expectedCenter.x),
                    Math.abs(cachedCenter.z - expectedCenter.z)
            );
            int allowedDrift = Math.max(0, PortalViewConfig.REMOTE_VIEW_RECENTER_HYSTERESIS_CHUNKS);
            if (drift > allowedDrift) {
                return PublishDecision.block("remote_view_center_mismatch cached=" + centerPos
                        + " cachedChunk=" + cachedCenter.x + "," + cachedCenter.z
                        + " expected=" + expectedViewCenter
                        + " expectedChunk=" + expectedCenter.x + "," + expectedCenter.z
                        + " drift=" + drift
                        + " allowed=" + allowedDrift
                        + " " + PortalRemoteChunkCache.debugSummary(world));
            }
            centerDriftDetail = " centerDrift=" + drift
                    + " expectedCenter=" + expectedCenter.x + "," + expectedCenter.z;
        }
        ChunkPos center = new ChunkPos(centerPos);
        int radius = Math.max(0, PortalViewConfig.REMOTE_PUBLISH_CORE_RADIUS_CHUNKS);
        int expected = 0;
        int missingRenderable = 0;
        int staleRenderable = staleTarget ? 1 : 0;
        StringBuilder missingSamples = new StringBuilder();
        for (int z = center.z - radius; z <= center.z + radius; z++) {
            for (int x = center.x - radius; x <= center.x + radius; x++) {
                expected++;
                if (!PortalRemoteChunkCache.isRenderable(world, x, z)) {
                    missingRenderable++;
                    appendChunkSample(missingSamples, x, z);
                } else if (!PortalRemoteChunkCache.isFresh(world, x, z)) {
                    staleRenderable++;
                }
            }
        }
        if (missingRenderable > 0) {
            return PublishDecision.block("missing_renderable_core_chunks missing=" + missingRenderable + "/" + expected
                    + " center=" + center.x + "," + center.z
                    + " radius=" + radius
                    + " samples=" + missingSamples
                    + " " + PortalRemoteChunkCache.debugSummary(world));
        }
        if (staleRenderable > 0) {
            return PublishDecision.block("stale_remote_chunks stale=" + staleRenderable
                    + " core=" + expected
                    + " fresh=" + PortalRemoteChunkCache.freshChunkCount()
                    + " loaded=" + loaded
                    + " accepted=" + PortalRemoteChunkCache.acceptedChunkCount()
                    + " gen=" + PortalRemoteChunkCache.getGeneration());
        }
        return PublishDecision.allow("fresh_remote_chunks core=" + expected
                + " loaded=" + loaded
                + " accepted=" + PortalRemoteChunkCache.acceptedChunkCount()
                + " center=" + center.x + "," + center.z
                + centerDriftDetail
                + " gen=" + PortalRemoteChunkCache.getGeneration());
    }

    private static boolean sameChunk(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return false;
        }
        ChunkPos firstChunk = new ChunkPos(first);
        ChunkPos secondChunk = new ChunkPos(second);
        return firstChunk.x == secondChunk.x && firstChunk.z == secondChunk.z;
    }

    private static void appendChunkSample(StringBuilder samples, int chunkX, int chunkZ) {
        if (samples.length() > 0 && samples.toString().split(";").length >= 8) {
            return;
        }
        if (!samples.isEmpty()) {
            samples.append(';');
        }
        samples.append(chunkX).append(',').append(chunkZ);
    }

    private static PortalViewTransform.View portalViewTransform(RenderFrame frame, PortalBlockEntity sourcePortal,
                                                                TargetPortalView targetPortal) {
        if (frame == null || sourcePortal == null || targetPortal == null) {
            return null;
        }
        return PortalViewTransform.compute(
                frame.mainCamera().position(),
                rotateUnit(frame.mainCamera().rotation(), 0.0F, 0.0F, -1.0F),
                rotateUnit(frame.mainCamera().rotation(), 0.0F, 1.0F, 0.0F),
                sourcePortal.getFrame(),
                targetFrameFor(targetPortal, sourcePortal),
                PortalViewConfig.PORTAL_VIEW_MIN_EXIT_OFFSET,
                PortalViewConfig.REMOTE_VIEW_CENTER_LEAD_BLOCKS
        );
    }

    private static CameraPose cameraPoseForViewTransform(PortalViewTransform.View view) {
        Vec3d renderForward = view.forward();
        PortalTransform.Rotation rotation = PortalTransform.rotationFromVector(renderForward);
        return new CameraPose(
                view.position(),
                rotation.yaw(),
                rotation.pitch(),
                cameraRotation(renderForward, view.up())
        );
    }

    private static Vec3d rotateUnit(Quaternionf rotation, float x, float y, float z) {
        Vector3f vector = new Vector3f(x, y, z).rotate(rotation);
        return new Vec3d(vector.x, vector.y, vector.z);
    }

    private static PortalFrame targetFrameFor(TargetPortalView targetPortal, PortalBlockEntity sourcePortal) {
        if (targetPortal.portal() != null) {
            return targetPortal.portal().getFrame();
        }
        return PortalFrame.centered(
                targetPortal.center(),
                targetPortal.facing(),
                sourcePortal.getPortalWidth(),
                sourcePortal.getPortalHeight()
        );
    }

    private static MainCameraSnapshot snapshotMainCamera(Camera camera) {
        return new MainCameraSnapshot(
                camera.getPos(),
                new Quaternionf(camera.getRotation()).normalize()
        );
    }

    private static Quaternionf cameraRotation(Vec3d forward, Vec3d up) {
        Vec3d normalizedForward = forward.normalize();
        Vec3d normalizedUp = up.normalize();
        if (normalizedForward.crossProduct(normalizedUp).lengthSquared() < 1.0E-8D) {
            normalizedUp = new Vec3d(0.0D, 1.0D, 0.0D);
        }
        return new Quaternionf().lookAlong(toVector3f(normalizedForward), toVector3f(normalizedUp)).normalize();
    }

    private static TargetPortalView targetView(ClientWorld world, PortalLinkData link) {
        if (world != null) {
            BlockEntity blockEntity = world.getBlockEntity(link.targetPos());
            if (blockEntity instanceof PortalBlockEntity portal && portal.isController()) {
                return new TargetPortalView(
                        portal.getPortalCenter(),
                        portal.getFacing(),
                        portal
                );
            }
        }
        return new TargetPortalView(
                link.targetCenter(),
                link.targetFacing(),
                null
        );
    }

    private static boolean renderScene(RenderFrame frame, SimpleFramebuffer targetFramebuffer,
                                        CameraPose pose, float aspect, boolean matchMainProjection,
                                        PortalViewTransform.Aperture aperture,
                                        PortalClipPlane clipPlane) {
        MinecraftClient client = frame.client();
        RenderTickCounter tickCounter = frame.tickCounter();

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice previousFog = RenderSystem.getShaderFog();
        Camera globalCamera = client.gameRenderer.getCamera();
        CameraState previousCameraState = snapshotCameraState(globalCamera);
        boolean globalCameraOverridden = false;
        boolean portalDhStateInstalled = false;
        boolean dhSuspended = false;
        FogRenderer sceneFogRenderer = null;
        RenderSystem.backupProjectionMatrix();
        IrisMirrorCompat.beginMirrorRender();
        PortalFramebufferOverride.set(targetFramebuffer);
        RenderSystem.outputColorTextureOverride = targetFramebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = targetFramebuffer.getDepthAttachmentView();
        try {
            WorldRenderer sceneRenderer = getRemoteWorldRenderer(client);
            Camera portalCamera = new Camera();
            portalCamera.update(client.world, client.player, false, false, tickCounter.getTickProgress(false));
            applyCameraPose(portalCamera, pose);

            applyCameraPose(globalCamera, pose);
            globalCameraOverridden = true;

            Quaternionf worldToCamera = portalCamera.getRotation().conjugate(new Quaternionf());
            Matrix4f view = new Matrix4f().rotation(worldToCamera);
            PortalProjectionSet projections = PortalProjectionSet.create(
                    frame.perspectiveProjection(),
                    aspect,
                    matchMainProjection,
                    pose.position(),
                    portalCamera.getRotation(),
                    aperture,
                    clipPlane
            );
            Matrix4f projection = projections.renderProjection();
            RenderSystem.setProjectionMatrix(
                    getRemoteProjectionMatrix().set(projection),
                    ProjectionType.PERSPECTIVE
            );

            PortalRemoteRenderContext.beginPortalPass(PortalRemoteChunkCache.getActiveSourcePos());
            try {
                PortalRemoteChunkCache.forEachLoadedChunk(PortalFramebufferRenderer::queueRemoteSodiumChunk);
                if (remoteTerrainDirty) {
                    sceneRenderer.scheduleTerrainUpdate();
                    remoteTerrainDirty = false;
                }
                pumpRemoteSodiumChunks(client, sceneRenderer);

                boolean thickFog = client.world.getDimensionEffects().useThickFog(
                        MathHelper.floor(pose.position.x),
                        MathHelper.floor(pose.position.z)
                ) || client.inGameHud.getBossBarHud().shouldThickenFog();
                sceneFogRenderer = getRemoteFogRenderer();
                Vector4f sceneFogColor = sceneFogRenderer.applyFog(
                        portalCamera,
                        frame.viewDistanceChunks(),
                        thickFog,
                        tickCounter,
                        client.gameRenderer.getSkyDarkness(tickCounter.getTickProgress(false)),
                        client.world
                );
                GpuBufferSlice sceneFog = sceneFogRenderer.getFogBuffer(FogRenderer.FogType.WORLD);
                clearPortalFramebuffer(targetFramebuffer, sceneFogColor);

                sceneRenderer.setupFrustum(pose.position, view, projections.cullingProjection());
                portalDhStateInstalled = DhCompat.beginPortalRender(
                        pose.position.x,
                        pose.position.z
                );
                if (!portalDhStateInstalled && PortalViewConfig.SUSPEND_DH_DURING_PORTAL_RENDER) {
                    DhCompat.suspend();
                    dhSuspended = true;
                }
                sceneRenderer.render(
                        ObjectAllocator.TRIVIAL,
                        tickCounter,
                        false,
                        portalCamera,
                        view,
                        projection,
                        sceneFog,
                        sceneFogColor,
                        !thickFog
                );
                logRemoteRendererState(sceneRenderer);
            } finally {
                PortalRemoteRenderContext.endPortalPass();
                if (dhSuspended) {
                    DhCompat.resume();
                    dhSuspended = false;
                }
                if (portalDhStateInstalled) {
                    DhCompat.endPortalRender();
                    portalDhStateInstalled = false;
                }
            }
            return true;
        } finally {
            if (dhSuspended) {
                DhCompat.resume();
            }
            if (portalDhStateInstalled) {
                DhCompat.endPortalRender();
            }
            if (globalCameraOverridden) {
                restoreCameraState(globalCamera, previousCameraState);
            }
            if (sceneFogRenderer != null) {
                sceneFogRenderer.rotate();
            }
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFog(previousFog);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            PortalFramebufferOverride.clear();
            IrisMirrorCompat.endMirrorRender();
        }
    }

    private static void clearPortalFramebuffer(SimpleFramebuffer targetFramebuffer, Vector4f fogColor) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                targetFramebuffer.getColorAttachment(),
                colorToArgb(fogColor),
                targetFramebuffer.getDepthAttachment(),
                1.0
        );
    }

    private static int colorToArgb(Vector4f color) {
        int r = MathHelper.clamp(Math.round(color.x() * 255.0F), 0, 255);
        int g = MathHelper.clamp(Math.round(color.y() * 255.0F), 0, 255);
        int b = MathHelper.clamp(Math.round(color.z() * 255.0F), 0, 255);
        int a = MathHelper.clamp(Math.round(color.w() * 255.0F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void compositePortalArea(RenderFrame frame, PortalBlockEntity portal,
                                            SimpleFramebuffer sourceFramebuffer,
                                            boolean screenAligned) {
        compositePortalArea(frame, portal, sourceFramebuffer, screenAligned, null);
    }

    private static void compositePortalArea(RenderFrame frame, PortalBlockEntity portal,
                                            SimpleFramebuffer sourceFramebuffer,
                                            boolean screenAligned,
                                            PortalApertureProjection.CornerUvs textureUvs) {
        Framebuffer mainFramebuffer = frame.client().getFramebuffer();
        if (sourceFramebuffer == null || mainFramebuffer == null
                || sourceFramebuffer.getColorAttachmentView() == null
                || mainFramebuffer.getColorAttachmentView() == null) {
            return;
        }

        PortalScreenPolygon polygon = screenPolygonForPortal(frame, portal, textureUvs);
        if (polygon == null || polygon.vertexCount() <= 0) {
            return;
        }

        GpuTextureView mainDepth = mainFramebuffer.getDepthAttachmentView();
        boolean depthTest = mainDepth != null;
        boolean useScreenPositionAsUv = screenAligned;
        GpuBuffer vertexBuffer = getPortalAreaBuffer(polygon, useScreenPositionAsUv);
        try (RenderPass pass = depthTest
                ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Monvhua portal framebuffer area",
                mainFramebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                mainDepth,
                OptionalDouble.empty()
        )
                : RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Monvhua portal framebuffer area",
                mainFramebuffer.getColorAttachmentView(),
                OptionalInt.empty()
        )) {
            pass.setPipeline(screenAligned
                    ? PortalRenderPipelines.framebufferScreenArea(depthTest)
                    : PortalRenderPipelines.framebufferArea(depthTest));
            pass.setVertexBuffer(0, vertexBuffer);
            pass.bindSampler("InSampler", sourceFramebuffer.getColorAttachmentView());
            pass.draw(0, polygon.vertexCount());
        }
    }

    private static void compositeLivePortalArea(RenderFrame frame, PortalBlockEntity portal,
                                                RenderSlot slot, PortalRenderParams params) {
        if (portal == null || slot == null || params == null || !slot.ready) {
            return;
        }
        PortalLinkData link = portal.getLinkData();
        if (link == null || !link.targetPos().equals(slot.targetPos)) {
            return;
        }
        SimpleFramebuffer liveFramebuffer = slot.frontFramebuffer();
        if (liveFramebuffer == null) {
            return;
        }
        String blockReason = slot.cachedCompositeBlockReason(frame, false);
        if (blockReason != null) {
            slot.logCacheSkip(blockReason + " displayPath=screen_projective_composite", frameIndex);
            return;
        }
        compositePortalArea(frame, portal, liveFramebuffer, false, params.apertureUvs());
        logLiveComposite(params, liveFramebuffer);
    }

    private static void logLiveComposite(PortalRenderParams params, SimpleFramebuffer framebuffer) {
        if (params == null
                || frameIndex - lastLiveCompositeLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
            return;
        }
        lastLiveCompositeLogFrame = frameIndex;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal live view composited: source={} displayPath={} textureUv={} fbo={}x{}",
                params.sourcePortal().getPos(),
                "screen_projective_composite",
                "aperture_projected_uv",
                framebuffer.textureWidth,
                framebuffer.textureHeight
        );
    }

    private static void compositeCachedPortalArea(RenderFrame frame, PortalBlockEntity portal, RenderSlot slot) {
        SimpleFramebuffer cachedFramebuffer = slot.frontFramebuffer();
        boolean screenAligned = false;
        if (!slot.ready) {
            slot.logCacheSkip("cache_not_ready viewMode=" + (screenAligned ? "screen_aligned" : "aperture"), frameIndex);
            return;
        }
        if (cachedFramebuffer == null) {
            slot.logCacheSkip("cache_framebuffer_missing viewMode=" + (screenAligned ? "screen_aligned" : "aperture"), frameIndex);
            return;
        }
        String blockReason = slot.cachedCompositeBlockReason(frame, screenAligned);
        if (blockReason != null) {
            if (!shouldRetainCachedPortalFrame(frame, portal, blockReason)) {
                slot.logCacheSkip(blockReason, frameIndex);
                return;
            }
            slot.logStatus("retained_cached_frame reason=" + blockReason, frameIndex);
        }
        compositePortalArea(frame, portal, cachedFramebuffer, screenAligned);
    }

    private static boolean shouldRetainCachedPortalFrame(RenderFrame frame, PortalBlockEntity portal, String blockReason) {
        if (!isCrosshairInsidePortal(frame, portal)) {
            return false;
        }
        return blockReason.startsWith("cached_frame_too_old")
                || blockReason.startsWith("cached_camera_position_delta")
                || blockReason.startsWith("cached_camera_rotation_delta");
    }

    private static GpuBuffer getPortalAreaBuffer(PortalScreenPolygon polygon, boolean useScreenPositionAsUv) {
        if (portalAreaBuffer != null) {
            portalAreaBuffer.close();
            portalAreaBuffer = null;
        }

        int vertexCount = polygon.vertexCount();
        ByteBuffer vertices = ByteBuffer.allocateDirect(vertexCount * 7 * Float.BYTES).order(ByteOrder.nativeOrder());
        List<PortalScreenVertex> points = polygon.vertices();
        for (int index = 1; index + 1 < points.size(); index++) {
            putPortalAreaVertex(vertices, points.get(0), useScreenPositionAsUv);
            putPortalAreaVertex(vertices, points.get(index), useScreenPositionAsUv);
            putPortalAreaVertex(vertices, points.get(index + 1), useScreenPositionAsUv);
        }
        vertices.flip();
        portalAreaBuffer = RenderSystem.getDevice()
                .createBuffer(() -> "Monvhua portal framebuffer area vertices", 40, vertices);
        return portalAreaBuffer;
    }

    private static void putPortalAreaVertex(ByteBuffer buffer, PortalScreenVertex vertex,
                                            boolean useScreenPositionAsUv) {
        buffer.putFloat(vertex.clipX());
        buffer.putFloat(vertex.clipY());
        buffer.putFloat(vertex.clipZ());
        buffer.putFloat(vertex.clipW());
        buffer.putFloat(useScreenPositionAsUv ? vertex.x() : vertex.uNumerator());
        buffer.putFloat(useScreenPositionAsUv ? vertex.y() : vertex.vNumerator());
        buffer.putFloat(useScreenPositionAsUv ? 1.0F : vertex.textureW());
    }

    private static void applyCameraPose(Camera camera, CameraPose pose) {
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.invokeSetPos(pose.position.x, pose.position.y, pose.position.z);
        accessor.invokeSetRotation(pose.yaw, pose.pitch);
        if (pose.rotation != null) {
            applyCameraRotation(accessor, pose.rotation);
        }
    }

    private static void applyCameraRotation(CameraAccessor accessor, Quaternionf rotation) {
        Quaternionf normalized = new Quaternionf(rotation).normalize();
        accessor.monvhua$getRotation().set(normalized);
        new Vector3f(0.0F, 0.0F, -1.0F).rotate(normalized, accessor.monvhua$getHorizontalPlane());
        new Vector3f(0.0F, 1.0F, 0.0F).rotate(normalized, accessor.monvhua$getVerticalPlane());
        new Vector3f(-1.0F, 0.0F, 0.0F).rotate(normalized, accessor.monvhua$getDiagonalPlane());
    }

    private static CameraState snapshotCameraState(Camera camera) {
        CameraAccessor accessor = (CameraAccessor) camera;
        return new CameraState(
                camera.getPos(),
                camera.getYaw(),
                camera.getPitch(),
                new Quaternionf(accessor.monvhua$getRotation()),
                new Vector3f(accessor.monvhua$getHorizontalPlane()),
                new Vector3f(accessor.monvhua$getVerticalPlane()),
                new Vector3f(accessor.monvhua$getDiagonalPlane())
        );
    }

    private static void restoreCameraState(Camera camera, CameraState state) {
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.invokeSetPos(state.position().x, state.position().y, state.position().z);
        accessor.invokeSetRotation(state.yaw(), state.pitch());
        accessor.monvhua$getRotation().set(state.rotation());
        accessor.monvhua$getHorizontalPlane().set(state.horizontalPlane());
        accessor.monvhua$getVerticalPlane().set(state.verticalPlane());
        accessor.monvhua$getDiagonalPlane().set(state.diagonalPlane());
    }

    private static Vector3f toVector3f(Vec3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private static WorldRenderer getRemoteWorldRenderer(MinecraftClient client) {
        if (remoteWorldRenderer != null && remoteRendererWorld == client.world) {
            return remoteWorldRenderer;
        }
        shutdownRemoteRenderer();
        creatingRemoteRenderer = true;
        try {
            remoteBufferBuilders = new BufferBuilderStorage(PortalViewConfig.REMOTE_BUFFER_BUILDER_COUNT);
            remoteWorldRenderer = new WorldRenderer(
                    client,
                    client.getEntityRenderDispatcher(),
                    client.getBlockEntityRenderDispatcher(),
                    remoteBufferBuilders
            );
            remoteRendererWorld = client.world;
            remoteWorldRenderer.setWorld(client.world);
            PortalRemoteRenderContext.markRemoteRendererAlive();
        } finally {
            creatingRemoteRenderer = false;
        }
        PortalRemoteChunkCache.forEachLoadedChunk(PortalFramebufferRenderer::queueRemoteSodiumChunk);
        remoteTerrainDirty = true;
        return remoteWorldRenderer;
    }

    private static FogRenderer getRemoteFogRenderer() {
        if (remoteFogRenderer == null) {
            remoteFogRenderer = new FogRenderer();
        }
        return remoteFogRenderer;
    }

    private static RawProjectionMatrix getRemoteProjectionMatrix() {
        if (remoteProjectionMatrix == null) {
            remoteProjectionMatrix = new RawProjectionMatrix("monvhua portal projection");
        }
        return remoteProjectionMatrix;
    }

    private static void queueRemoteSodiumChunk(int chunkX, int chunkZ) {
        queueRemoteSodiumChunk(chunkX, chunkZ, false);
    }

    private static void queueRemoteSodiumChunk(int chunkX, int chunkZ, boolean dirty) {
        long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
        if (dirty) {
            DIRTY_REMOTE_SODIUM_CHUNKS.add(chunkKey);
        } else if (REMOTE_SODIUM_CHUNKS.contains(chunkKey)) {
            return;
        }
        if (QUEUED_REMOTE_SODIUM_CHUNKS.add(chunkKey)) {
            PENDING_REMOTE_SODIUM_CHUNKS.addLast(chunkKey);
        }
    }

    private static void pumpRemoteSodiumChunks(MinecraftClient client, WorldRenderer sceneRenderer) {
        if (PENDING_REMOTE_SODIUM_CHUNKS.isEmpty()
                || remoteRendererWorld != client.world
                || sceneRenderer != remoteWorldRenderer) {
            return;
        }

        if (!(sceneRenderer instanceof LevelRendererExtension extension)) {
            pumpVanillaRemoteChunks(client, sceneRenderer);
            return;
        }
        SodiumWorldRenderer sodiumRenderer = extension.sodium$getWorldRenderer();
        if (!(sodiumRenderer instanceof SodiumWorldRendererAccessor accessor)) {
            pumpVanillaRemoteChunks(client, sceneRenderer);
            return;
        }
        RenderSectionManager sectionManager = accessor.monvhua$getRenderSectionManager();
        if (sectionManager == null) {
            return;
        }

        var builder = sectionManager.getBuilder();
        if (builder.getScheduledJobCount() >= PortalViewConfig.REMOTE_MAX_QUEUED_JOBS
                || builder.getBusyThreadCount() >= builder.getTotalThreadCount()) {
            return;
        }

        int budget = PortalViewConfig.REMOTE_CHUNKS_PER_FRAME;
        while (budget-- > 0 && !PENDING_REMOTE_SODIUM_CHUNKS.isEmpty()) {
            long chunkKey = PENDING_REMOTE_SODIUM_CHUNKS.removeFirst();
            QUEUED_REMOTE_SODIUM_CHUNKS.remove(chunkKey);
            int chunkX = ChunkPos.getPackedX(chunkKey);
            int chunkZ = ChunkPos.getPackedZ(chunkKey);
            if (PortalRemoteChunkCache.get(client.world, chunkX, chunkZ) == null) {
                DIRTY_REMOTE_SODIUM_CHUNKS.remove(chunkKey);
                continue;
            }

            if (REMOTE_SODIUM_CHUNKS.add(chunkKey)) {
                sectionManager.onChunkAdded(chunkX, chunkZ);
                DIRTY_REMOTE_SODIUM_CHUNKS.remove(chunkKey);
            } else if (DIRTY_REMOTE_SODIUM_CHUNKS.remove(chunkKey)) {
                sodiumRenderer.scheduleRebuildForChunks(
                        chunkX,
                        client.world.getBottomSectionCoord(),
                        chunkZ,
                        chunkX,
                        client.world.getTopSectionCoord(),
                        chunkZ,
                        false
                );
            }
            sodiumRenderer.scheduleTerrainUpdate();
        }
    }

    private static void pumpVanillaRemoteChunks(MinecraftClient client, WorldRenderer sceneRenderer) {
        int budget = PortalViewConfig.REMOTE_CHUNKS_PER_FRAME;
        while (budget-- > 0 && !PENDING_REMOTE_SODIUM_CHUNKS.isEmpty()) {
            long chunkKey = PENDING_REMOTE_SODIUM_CHUNKS.removeFirst();
            QUEUED_REMOTE_SODIUM_CHUNKS.remove(chunkKey);
            int chunkX = ChunkPos.getPackedX(chunkKey);
            int chunkZ = ChunkPos.getPackedZ(chunkKey);
            for (int sectionY = client.world.getBottomSectionCoord();
                 sectionY <= client.world.getTopSectionCoord();
                 sectionY++) {
                sceneRenderer.scheduleChunkRender(chunkX, sectionY, chunkZ);
            }
        }
    }

    private static void logRemoteRendererState(WorldRenderer sceneRenderer) {
        if (frameIndex - lastRemoteRendererStateLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS * 2L
                || !(sceneRenderer instanceof LevelRendererExtension extension)) {
            return;
        }
        SodiumWorldRenderer sodiumRenderer = extension.sodium$getWorldRenderer();
        if (!(sodiumRenderer instanceof SodiumWorldRendererAccessor accessor)) {
            return;
        }
        RenderSectionManager sectionManager = accessor.monvhua$getRenderSectionManager();
        if (sectionManager == null) {
            return;
        }
        lastRemoteRendererStateLogFrame = frameIndex;
        var builder = sectionManager.getBuilder();
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote renderer state: source={} cache={} registered={} pending={} dirty={} sections={} visible={} jobs={} busy={}/{}",
                PortalRemoteChunkCache.getActiveSourcePos(),
                PortalRemoteChunkCache.loadedChunkCount(),
                REMOTE_SODIUM_CHUNKS.size(),
                PENDING_REMOTE_SODIUM_CHUNKS.size(),
                DIRTY_REMOTE_SODIUM_CHUNKS.size(),
                sectionManager.getTotalSections(),
                sectionManager.getVisibleChunkCount(),
                builder.getScheduledJobCount(),
                builder.getBusyThreadCount(),
                builder.getTotalThreadCount()
        );
    }

    private static Matrix4f projectionForAspect(Matrix4f original, float aspect) {
        Matrix4f projection = new Matrix4f(original);
        float safeAspect = Math.max(0.05F, aspect);
        if (Math.abs(projection.m11()) > 1.0E-5F) {
            projection.m00(projection.m11() / safeAspect);
        }
        return projection;
    }

    private static PortalScreenPolygon screenPolygonForPortal(RenderFrame frame, PortalBlockEntity portal) {
        return screenPolygonForPortal(frame, portal, null);
    }

    private static PortalScreenPolygon screenPolygonForPortal(RenderFrame frame, PortalBlockEntity portal,
                                                              PortalApertureProjection.CornerUvs textureUvs) {
        if (portal == null) {
            return null;
        }

        PortalApertureProjection.CornerUvs uvs = textureUvs == null
                ? defaultPortalUvs()
                : textureUvs;
        PortalFrame portalFrame = portal.getFrame();
        Vec3d center = portalFrame.center();
        Vec3d horizontal = portalFrame.widthAxis();
        Vec3d vertical = portalFrame.heightAxis();
        double halfWidth = Math.max(
                0.01D,
                portal.getPortalWidth() * 0.5D - PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET
        );
        double halfHeight = Math.max(
                0.01D,
                portal.getPortalHeight() * 0.5D - PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET
        );
        List<PortalCameraVertex> cameraVertices = new ArrayList<>(4);
        cameraVertices.add(cameraVertexForPortalCorner(
                frame,
                center.subtract(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                portalU(uvs.bottomLeft().u()),
                portalV(uvs.bottomLeft().v()),
                uvs.bottomLeft().textureW()
        ));
        cameraVertices.add(cameraVertexForPortalCorner(
                frame,
                center.add(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                portalU(uvs.bottomRight().u()),
                portalV(uvs.bottomRight().v()),
                uvs.bottomRight().textureW()
        ));
        cameraVertices.add(cameraVertexForPortalCorner(
                frame,
                center.add(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight)),
                portalU(uvs.topRight().u()),
                portalV(uvs.topRight().v()),
                uvs.topRight().textureW()
        ));
        cameraVertices.add(cameraVertexForPortalCorner(
                frame,
                center.subtract(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight)),
                portalU(uvs.topLeft().u()),
                portalV(uvs.topLeft().v()),
                uvs.topLeft().textureW()
        ));
        if (cameraVertices.stream().anyMatch(vertex -> vertex == null)) {
            return null;
        }

        List<PortalCameraVertex> clipped = clipPortalCameraPolygon(cameraVertices);
        if (clipped.size() < 3) {
            return null;
        }

        List<PortalScreenVertex> screenVertices = new ArrayList<>(clipped.size());
        for (PortalCameraVertex cameraVertex : clipped) {
            PortalScreenVertex screenVertex = projectPortalCameraVertex(frame, cameraVertex);
            if (screenVertex == null) {
                return null;
            }
            screenVertices.add(screenVertex);
        }
        if (!screenBoundsVisible(screenVertices) || Math.abs(screenPolygonArea(screenVertices)) < 1.0E-6F) {
            return null;
        }
        return new PortalScreenPolygon(screenVertices);
    }

    private static PortalApertureProjection.CornerUvs defaultPortalUvs() {
        return new PortalApertureProjection.CornerUvs(
                new PortalApertureProjection.CornerUv(0.0F, 0.0F, 1.0F),
                new PortalApertureProjection.CornerUv(1.0F, 0.0F, 1.0F),
                new PortalApertureProjection.CornerUv(1.0F, 1.0F, 1.0F),
                new PortalApertureProjection.CornerUv(0.0F, 1.0F, 1.0F)
        );
    }

    private static PortalApertureProjection.CornerUvs sourceMappedApertureUvs(
            PortalApertureProjection.CornerUvs targetUvs) {
        if (targetUvs == null || !PortalViewConfig.PORTAL_LIVE_VIEW_MIRROR_WIDTH) {
            return targetUvs;
        }
        return new PortalApertureProjection.CornerUvs(
                targetUvs.bottomRight(),
                targetUvs.bottomLeft(),
                targetUvs.topLeft(),
                targetUvs.topRight()
        );
    }

    private static float portalU(float u) {
        return PortalViewConfig.PORTAL_VIEW_FLIP_U ? 1.0F - u : u;
    }

    private static float portalV(float v) {
        return PortalViewConfig.PORTAL_VIEW_FLIP_V ? 1.0F - v : v;
    }

    private static PortalCameraVertex cameraVertexForPortalCorner(RenderFrame frame, Vec3d corner,
                                                                  float u, float v, float textureW) {
        Vector4f camera = new Vector4f(
                (float) (corner.x - frame.mainCamera().position().x),
                (float) (corner.y - frame.mainCamera().position().y),
                (float) (corner.z - frame.mainCamera().position().z),
                1.0F
        );
        float safeTextureW = Math.max(1.0E-6F, textureW);
        frame.mainViewMatrix().transform(camera);
        if (!Float.isFinite(camera.x) || !Float.isFinite(camera.y) || !Float.isFinite(camera.z)
                || !Float.isFinite(u) || !Float.isFinite(v) || !Float.isFinite(safeTextureW)) {
            return null;
        }
        return new PortalCameraVertex(camera.x, camera.y, camera.z, u * safeTextureW, v * safeTextureW, safeTextureW);
    }

    private static List<PortalCameraVertex> clipPortalCameraPolygon(List<PortalCameraVertex> vertices) {
        List<PortalCameraVertex> clipped = new ArrayList<>();
        if (vertices.isEmpty()) {
            return clipped;
        }

        PortalCameraVertex previous = vertices.getLast();
        boolean previousInside = isInsideProjectionNearPlane(previous);
        for (PortalCameraVertex current : vertices) {
            boolean currentInside = isInsideProjectionNearPlane(current);
            if (previousInside != currentInside) {
                clipped.add(interpolateAtProjectionNearPlane(previous, current));
            }
            if (currentInside) {
                clipped.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private static boolean isInsideProjectionNearPlane(PortalCameraVertex vertex) {
        return vertex.cameraZ() <= -(float) PortalViewConfig.MIN_PROJECTION_DEPTH;
    }

    private static PortalCameraVertex interpolateAtProjectionNearPlane(PortalCameraVertex start,
                                                                       PortalCameraVertex end) {
        float nearZ = -(float) PortalViewConfig.MIN_PROJECTION_DEPTH;
        float denominator = end.cameraZ() - start.cameraZ();
        float t = Math.abs(denominator) < 1.0E-6F
                ? 0.0F
                : MathHelper.clamp((nearZ - start.cameraZ()) / denominator, 0.0F, 1.0F);
        return new PortalCameraVertex(
                MathHelper.lerp(t, start.cameraX(), end.cameraX()),
                MathHelper.lerp(t, start.cameraY(), end.cameraY()),
                nearZ,
                MathHelper.lerp(t, start.uNumerator(), end.uNumerator()),
                MathHelper.lerp(t, start.vNumerator(), end.vNumerator()),
                MathHelper.lerp(t, start.textureW(), end.textureW())
        );
    }

    private static PortalScreenVertex projectPortalCameraVertex(RenderFrame frame, PortalCameraVertex vertex) {
        Vector4f clip = new Vector4f(
                vertex.cameraX(),
                vertex.cameraY(),
                vertex.cameraZ(),
                1.0F
        );
        frame.perspectiveProjection().transform(clip);
        if (clip.w <= 1.0E-5F) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY) || !Float.isFinite(ndcZ)) {
            return null;
        }
        return new PortalScreenVertex(
                ndcX * 0.5F + 0.5F,
                ndcY * 0.5F + 0.5F,
                clip.x,
                clip.y,
                MathHelper.clamp(ndcZ, -0.9999F, 0.9999F) * clip.w,
                clip.w,
                vertex.uNumerator(),
                vertex.vNumerator(),
                vertex.textureW()
        );
    }

    private static boolean screenBoundsVisible(List<PortalScreenVertex> vertices) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (PortalScreenVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxY = Math.max(maxY, vertex.y());
        }
        return maxX > 0.0F && minX < 1.0F && maxY > 0.0F && minY < 1.0F;
    }

    private static float screenPolygonArea(List<PortalScreenVertex> vertices) {
        float area = 0.0F;
        for (int index = 0; index < vertices.size(); index++) {
            PortalScreenVertex a = vertices.get(index);
            PortalScreenVertex b = vertices.get((index + 1) % vertices.size());
            area += a.x() * b.y() - a.y() * b.x();
        }
        return area * 0.5F;
    }

    private static Resolution resolutionFor(PortalBlockEntity portal, int maximumSide) {
        float aspect = portal.getPortalWidth() / (float) portal.getPortalHeight();
        if (aspect >= 1.0F) {
            return new Resolution(maximumSide, Math.max(16, Math.round(maximumSide / aspect)));
        }
        return new Resolution(Math.max(16, Math.round(maximumSide * aspect)), maximumSide);
    }

    private static Resolution resolutionForAspect(float aspect, int maximumSide) {
        float safeAspect = Math.max(0.05F, aspect);
        int safeMaximumSide = Math.max(PortalViewConfig.MIN_SURFACE_RESOLUTION, maximumSide);
        if (safeAspect >= 1.0F) {
            return new Resolution(safeMaximumSide, Math.max(16, Math.round(safeMaximumSide / safeAspect)));
        }
        return new Resolution(Math.max(16, Math.round(safeMaximumSide * safeAspect)), safeMaximumSide);
    }

    private static Resolution resolutionForLiveScreenView(RenderFrame frame) {
        Framebuffer mainFramebuffer = frame.client().getFramebuffer();
        int width = Math.max(PortalViewConfig.MIN_SURFACE_RESOLUTION, mainFramebuffer.textureWidth);
        int height = Math.max(PortalViewConfig.MIN_SURFACE_RESOLUTION, mainFramebuffer.textureHeight);
        return new Resolution(width, height);
    }

    private static float windowAspect(RenderFrame frame) {
        int width = Math.max(1, frame.client().getWindow().getFramebufferWidth());
        int height = Math.max(1, frame.client().getWindow().getFramebufferHeight());
        return width / (float) height;
    }

    private static int quantizeResolution(int resolution) {
        int step = Math.max(1, PortalViewConfig.SURFACE_RESOLUTION_STEP);
        return Math.max(step, MathHelper.ceil(resolution / (float) step) * step);
    }

    private static void trimSlots(Map<PortalKey, RenderSlot> slots, int maximumSize) {
        Iterator<Map.Entry<PortalKey, RenderSlot>> iterator = slots.entrySet().iterator();
        while (slots.size() > maximumSize && iterator.hasNext()) {
            RenderSlot slot = iterator.next().getValue();
            iterator.remove();
            slot.close();
        }
    }

    private static void closeSlots(Map<PortalKey, RenderSlot> slots) {
        Iterator<RenderSlot> iterator = slots.values().iterator();
        while (iterator.hasNext()) {
            RenderSlot slot = iterator.next();
            iterator.remove();
            slot.close();
        }
    }

    private static int compareCandidates(Candidate left, Candidate right) {
        RenderSlot leftSlot = LIVE_SLOTS.get(left.key);
        RenderSlot rightSlot = LIVE_SLOTS.get(right.key);
        boolean leftReady = leftSlot != null && leftSlot.ready;
        boolean rightReady = rightSlot != null && rightSlot.ready;
        if (leftReady != rightReady) {
            return leftReady ? 1 : -1;
        }

        long leftFrame = leftSlot == null ? Long.MIN_VALUE : leftSlot.lastAttemptFrame;
        long rightFrame = rightSlot == null ? Long.MIN_VALUE : rightSlot.lastAttemptFrame;
        int ageOrder = Long.compare(leftFrame, rightFrame);
        if (ageOrder != 0) {
            return ageOrder;
        }
        return Double.compare(right.priority, left.priority);
    }

    private static PortalKey keyFor(PortalBlockEntity portal) {
        return new PortalKey(
                portal.getWorld().getRegistryKey().getValue(),
                portal.getPos().toImmutable()
        );
    }

    private static void logRenderFailure(MinecraftClient client, String stage, RuntimeException exception) {
        int tick = client.player == null ? 0 : client.player.age;
        if (tick - lastFailureLogTick >= 100 || lastFailureLogTick == Integer.MIN_VALUE) {
            lastFailureLogTick = tick;
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to render {}; keeping the last valid portal frame", stage, exception);
        }
    }

    private static Vec3d normal(Direction direction) {
        return PortalTransform.normal(direction);
    }

    private record PortalKey(Identifier dimension, BlockPos pos) {
    }

    private record Candidate(PortalKey key, PortalBlockEntity portal, double priority) {
    }

    private record RemoteRequestState(BlockPos viewCenter, long frame) {
    }

    private record RenderFrame(MinecraftClient client, RenderTickCounter tickCounter,
                               MainCameraSnapshot mainCamera, Matrix4f mainViewMatrix,
                               Matrix4f perspectiveProjection,
                               int viewDistanceChunks, int renderBudget,
                               int maximumSurfaceResolution) {
    }

    private record MainCameraSnapshot(Vec3d position, Quaternionf rotation) {
    }

    private record CameraPose(Vec3d position, float yaw, float pitch, Quaternionf rotation) {
    }

    private record CameraState(Vec3d position, float yaw, float pitch, Quaternionf rotation,
                               Vector3f horizontalPlane, Vector3f verticalPlane,
                               Vector3f diagonalPlane) {
    }

    private record TargetPortalView(Vec3d center, Direction facing, PortalBlockEntity portal) {
    }

    private record PortalRenderParams(PortalBlockEntity sourcePortal, TargetPortalView targetView,
                                       PortalViewTransform.View viewTransform, CameraPose pose,
                                       Resolution resolution, float aspect,
                                       PortalApertureProjection.CornerUvs apertureUvs) {
    }

    private record Resolution(int width, int height) {
    }

    private record PortalScreenPolygon(List<PortalScreenVertex> vertices) {
        private int vertexCount() {
            return Math.max(0, (vertices.size() - 2) * 3);
        }
    }

    private record PortalCameraVertex(float cameraX, float cameraY, float cameraZ,
                                      float uNumerator, float vNumerator, float textureW) {
    }

    private record PortalScreenVertex(float x, float y,
                                       float clipX, float clipY, float clipZ, float clipW,
                                       float uNumerator, float vNumerator, float textureW) {
    }

    private record LocalPortalOffset(double width, double height, double depth) {
    }

    private record PublishDecision(boolean publish, String detail) {
        private static PublishDecision allow(String detail) {
            return new PublishDecision(true, detail);
        }

        private static PublishDecision block(String detail) {
            return new PublishDecision(false, detail);
        }
    }

    private static final class RenderSlot {
        private final MinecraftClient client;
        private final PortalKey key;
        private final String kind;
        private BufferHandle buffer;
        private boolean ready;
        private BlockPos targetPos;
        private Vec3d lastCameraPosition;
        private Quaternionf lastCameraRotation;
        private long lastPublishedFrame = Long.MIN_VALUE / 2L;
        private long lastAttemptFrame = Long.MIN_VALUE / 2L;
        private long lastFreezeLogFrame = Long.MIN_VALUE / 2L;
        private long lastStatusLogFrame = Long.MIN_VALUE / 2L;
        private long lastCacheSkipLogFrame = Long.MIN_VALUE / 2L;

        private RenderSlot(MinecraftClient client, PortalKey key, String kind) {
            this.client = client;
            this.key = key;
            this.kind = kind;
        }

        private SimpleFramebuffer prepare(Resolution requestedResolution) {
            return prepare(requestedResolution, true);
        }

        private SimpleFramebuffer prepare(Resolution requestedResolution, boolean stabilize) {
            if (buffer == null) {
                buffer = new BufferHandle(textureId());
            }
            Resolution resolution = stabilize ? buffer.stabilize(requestedResolution) : requestedResolution;
            return buffer.ensureBack(client, resolution.width, resolution.height);
        }

        private void publish(BlockPos targetPos, long renderedFrame) {
            publish(targetPos, renderedFrame, null);
        }

        private void publish(BlockPos targetPos, long renderedFrame, MainCameraSnapshot cameraSnapshot) {
            buffer.publish(client);
            ready = true;
            this.targetPos = targetPos == null ? null : targetPos.toImmutable();
            lastAttemptFrame = renderedFrame;
            lastPublishedFrame = renderedFrame;
            if (cameraSnapshot != null) {
                lastCameraPosition = cameraSnapshot.position();
                lastCameraRotation = new Quaternionf(cameraSnapshot.rotation()).normalize();
            } else {
                lastCameraPosition = null;
                lastCameraRotation = null;
            }
        }

        private String cachedCompositeBlockReason(RenderFrame frame, boolean screenAligned) {
            String viewMode = screenAligned ? "screen_aligned" : "aperture";
            if (lastCameraPosition == null || lastCameraRotation == null) {
                return null;
            }
            int maxFrameAge = screenAligned
                    ? PortalViewConfig.SCREEN_ALIGNED_MAX_CACHED_PORTAL_FRAME_AGE
                    : PortalViewConfig.MAX_CACHED_PORTAL_FRAME_AGE;
            long age = frameIndex - lastPublishedFrame;
            if (age > maxFrameAge) {
                return "cached_frame_too_old age=" + age
                        + " maxAge=" + maxFrameAge
                        + " viewMode=" + viewMode;
            }
            double maxDelta = Math.max(0.0D, screenAligned
                    ? PortalViewConfig.SCREEN_ALIGNED_MAX_CACHED_PORTAL_POSITION_DELTA
                    : PortalViewConfig.MAX_CACHED_PORTAL_POSITION_DELTA);
            double positionDeltaSq = frame.mainCamera().position().squaredDistanceTo(lastCameraPosition);
            if (positionDeltaSq > maxDelta * maxDelta) {
                return "cached_camera_position_delta delta=" + Math.sqrt(positionDeltaSq)
                        + " maxDelta=" + maxDelta
                        + " viewMode=" + viewMode;
            }
            float maxRotationDelta = screenAligned
                    ? PortalViewConfig.SCREEN_ALIGNED_MAX_CACHED_PORTAL_ROTATION_DOT_DELTA
                    : PortalViewConfig.MAX_CACHED_PORTAL_ROTATION_DOT_DELTA;
            float dot = Math.abs(lastCameraRotation.dot(frame.mainCamera().rotation()));
            if (!Float.isFinite(dot)) {
                return "cached_camera_rotation_invalid viewMode=" + viewMode;
            }
            float rotationDelta = 1.0F - MathHelper.clamp(dot, 0.0F, 1.0F);
            if (rotationDelta > maxRotationDelta) {
                return "cached_camera_rotation_delta delta=" + rotationDelta
                        + " maxDelta=" + maxRotationDelta
                        + " viewMode=" + viewMode;
            }
            return null;
        }

        private void freeze(String reason, long frame) {
            lastAttemptFrame = frame;
            if (frame - lastFreezeLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
                return;
            }
            lastFreezeLogFrame = frame;
            MonvhuaMod.LOGGER.info("[Monvhua] Portal frame frozen; keeping last valid frame. kind={}, pos={}, ready={}, reason={}",
                    kind, key.pos, ready, reason);
        }

        private void logStatus(String detail, long frame) {
            if (detail == null || detail.isEmpty()
                    || frame - lastStatusLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
                return;
            }
            lastStatusLogFrame = frame;
            MonvhuaMod.LOGGER.info("[Monvhua] Portal frame rendered from remote chunks. kind={}, pos={}, detail={}",
                    kind, key.pos, detail);
        }

        private void logCacheSkip(String reason, long frame) {
            if (frame - lastCacheSkipLogFrame < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
                return;
            }
            lastCacheSkipLogFrame = frame;
            MonvhuaMod.LOGGER.info("[Monvhua] Portal cached frame skipped. kind={}, pos={}, ready={}, reason={}",
                    kind, key.pos, ready, reason);
        }

        private Identifier frontTextureId() {
            return buffer == null ? null : buffer.textureId;
        }

        private SimpleFramebuffer frontFramebuffer() {
            return buffer == null ? null : buffer.frontFramebuffer();
        }

        private Identifier textureId() {
            String dimension = Integer.toUnsignedString(key.dimension.hashCode(), 36);
            String position = Long.toUnsignedString(key.pos.asLong(), 36);
            return Identifier.of(
                    "monvhua",
                    "dynamic/portal_" + kind + "/" + dimension + "_" + position
            );
        }

        private void close() {
            if (buffer != null) {
                buffer.close(client);
                buffer = null;
            }
            ready = false;
            targetPos = null;
            lastCameraPosition = null;
            lastCameraRotation = null;
            lastPublishedFrame = Long.MIN_VALUE / 2L;
        }
    }

    private static final class BufferHandle {
        private final Identifier textureId;
        private PortalFramebufferTexture texture;
        private SimpleFramebuffer frontFramebuffer;
        private SimpleFramebuffer backFramebuffer;

        private BufferHandle(Identifier textureId) {
            this.textureId = textureId;
        }

        private SimpleFramebuffer ensureBack(MinecraftClient client, int width, int height) {
            if (backFramebuffer == null || backFramebuffer.textureWidth != width || backFramebuffer.textureHeight != height) {
                if (backFramebuffer != null) {
                    backFramebuffer.delete();
                }
                backFramebuffer = new SimpleFramebuffer("monvhua_portal_slot_back", width, height, true);
            }
            if (texture == null) {
                texture = new PortalFramebufferTexture();
                texture.setFramebuffer(frontFramebuffer);
                client.getTextureManager().registerTexture(textureId, texture);
            }
            return backFramebuffer;
        }

        private void publish(MinecraftClient client) {
            if (backFramebuffer == null) {
                return;
            }
            SimpleFramebuffer previousFront = frontFramebuffer;
            frontFramebuffer = backFramebuffer;
            backFramebuffer = previousFront;
            if (texture == null) {
                texture = new PortalFramebufferTexture();
                client.getTextureManager().registerTexture(textureId, texture);
            }
            texture.setFramebuffer(frontFramebuffer);
        }

        private SimpleFramebuffer frontFramebuffer() {
            return frontFramebuffer;
        }

        private Resolution stabilize(Resolution requested) {
            SimpleFramebuffer stableFramebuffer = backFramebuffer != null ? backFramebuffer : frontFramebuffer;
            if (stableFramebuffer == null) {
                return requested;
            }
            double widthChange = Math.abs(requested.width - stableFramebuffer.textureWidth)
                    / (double) Math.max(1, stableFramebuffer.textureWidth);
            double heightChange = Math.abs(requested.height - stableFramebuffer.textureHeight)
                    / (double) Math.max(1, stableFramebuffer.textureHeight);
            if (Math.max(widthChange, heightChange) <= PortalViewConfig.SURFACE_RESIZE_HYSTERESIS) {
                return new Resolution(stableFramebuffer.textureWidth, stableFramebuffer.textureHeight);
            }
            return requested;
        }

        private void close(MinecraftClient client) {
            if (texture != null) {
                if (client != null) {
                    client.getTextureManager().destroyTexture(textureId);
                } else {
                    texture.close();
                }
                texture = null;
            }
            if (frontFramebuffer != null) {
                frontFramebuffer.delete();
                frontFramebuffer = null;
            }
            if (backFramebuffer != null) {
                backFramebuffer.delete();
                backFramebuffer = null;
            }
        }
    }
}
