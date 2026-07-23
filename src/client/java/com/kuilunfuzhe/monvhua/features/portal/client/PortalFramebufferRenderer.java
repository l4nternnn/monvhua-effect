package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.compat.DhCompat;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.compat.PortalIrisCompat;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
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
    private static BlockPos lastRequestedSource;
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
            return live.frontTextureId();
        }

        PortalKey targetKey = new PortalKey(
                portal.getWorld().getRegistryKey().getValue(),
                link.targetPos().toImmutable()
        );
        RenderSlot preview = PREVIEW_SLOTS.get(targetKey);
        return preview != null && preview.ready ? preview.frontTextureId() : null;
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
                    compositeCachedPortalArea(frame, portal, slot);
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
        CameraPose pose = view == null
                ? exitCameraPose(portal, targetView)
                : cameraPoseForViewTransform(view);
        BlockPos sourcePos = portal.getPos();
        BlockPos viewCenter = view == null
                ? remoteViewCenterFor(pose)
                : view.remoteViewCenter(PortalViewConfig.REMOTE_VIEW_CENTER_LEAD_BLOCKS);
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

        Resolution resolution = resolutionForLiveView(frame, sourcePortal);
        SimpleFramebuffer framebuffer = slot.prepare(resolution);
        PortalViewTransform.View viewTransform = portalViewTransform(frame, sourcePortal, targetView);
        CameraPose pose = viewTransform == null
                ? exitCameraPose(sourcePortal, targetView)
                : cameraPoseForViewTransform(viewTransform);
        float aspect = resolution.width / (float) Math.max(1, resolution.height);

        if (!renderPortalScene(
                frame,
                framebuffer,
                pose,
                targetView.portal(),
                aspect,
                false,
                viewTransform == null ? null : viewTransform.aperture()
        )) {
            slot.freeze("render_scene_failed", frameIndex);
            compositeCachedPortalArea(frame, sourcePortal, slot);
            return;
        }

        PublishDecision decision = livePublishDecision(client.world, link.targetPos());
        if (!decision.publish()) {
            slot.freeze(decision.detail(), frameIndex);
            compositeCachedPortalArea(frame, sourcePortal, slot);
            return;
        }
        slot.logStatus(decision.detail(), frameIndex);
        compositeLivePortalArea(frame, sourcePortal, framebuffer);
        slot.publish(link.targetPos(), frameIndex);
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
        if (renderPortalScene(frame, framebuffer, pose, null, aspect, false, null)) {
            slot.publish(null, frameIndex);
        }
    }

    private static boolean renderPortalScene(RenderFrame frame, SimpleFramebuffer targetFramebuffer,
                                              CameraPose pose, PortalBlockEntity targetAperture,
                                              float aspect, boolean matchMainProjection,
                                              PortalViewTransform.Aperture aperture) {
        if (!PortalViewConfig.USE_INDEPENDENT_PORTAL_RENDERER) {
            try {
                return renderScene(frame, targetFramebuffer, pose, aspect, matchMainProjection, aperture);
            } catch (RuntimeException exception) {
                logRenderFailure(frame.client(), "portal world renderer", exception);
                return false;
            }
        }
        return IndependentPortalRenderer.render(targetFramebuffer, pose.position, pose.yaw, pose.pitch, aspect,
                targetAperture);
    }

    private static PublishDecision livePublishDecision(ClientWorld world, BlockPos targetPos) {
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
            return PublishDecision.allow("using_stale_remote_chunks stale=" + staleRenderable
                    + " core=" + expected
                    + " fresh=" + PortalRemoteChunkCache.freshChunkCount()
                    + " loaded=" + loaded
                    + " accepted=" + PortalRemoteChunkCache.acceptedChunkCount()
                    + " gen=" + PortalRemoteChunkCache.getGeneration());
        }
        return PublishDecision.allow(null);
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

    private static CameraPose exitCameraPose(PortalBlockEntity sourcePortal,
                                             TargetPortalView targetPortal) {
        PortalFrame targetFrame = targetFrameFor(targetPortal, sourcePortal);
        Vec3d position = targetFrame.center()
                .add(targetFrame.normal().multiply(PortalViewConfig.TELEPORT_EXIT_OFFSET));
        Vec3d forward = targetFrame.normal();
        PortalTransform.Rotation rotation = PortalTransform.rotationFromVector(forward);
        return new CameraPose(
                position,
                rotation.yaw(),
                rotation.pitch(),
                cameraRotation(forward, targetFrame.heightAxis())
        );
    }

    private static CameraPose immersiveExitCameraPose(RenderFrame frame, PortalBlockEntity sourcePortal,
                                                      TargetPortalView targetPortal) {
        PortalFrame sourceFrame = sourcePortal.getFrame();
        PortalFrame targetFrame = targetFrameFor(targetPortal, sourcePortal);
        Vec3d position = PortalTransform.mapPointForView(
                frame.mainCamera().position(),
                sourceFrame,
                targetFrame,
                PortalViewConfig.PORTAL_VIEW_MIN_EXIT_OFFSET
        );
        Vec3d forward = PortalTransform.mapVector(
                rotateUnit(frame.mainCamera().rotation(), 0.0F, 0.0F, -1.0F),
                sourceFrame,
                targetFrame
        ).normalize();
        Vec3d up = PortalTransform.mapVector(
                rotateUnit(frame.mainCamera().rotation(), 0.0F, 1.0F, 0.0F),
                sourceFrame,
                targetFrame
        ).normalize();
        PortalTransform.Rotation rotation = PortalTransform.rotationFromVector(forward);
        return new CameraPose(
                position,
                rotation.yaw(),
                rotation.pitch(),
                cameraRotation(forward, up)
        );
    }

    private static PortalViewTransform.View portalViewTransform(RenderFrame frame, PortalBlockEntity sourcePortal,
                                                                TargetPortalView targetPortal) {
        if (frame == null || sourcePortal == null || targetPortal == null) {
            return null;
        }
        return PortalViewTransform.compute(
                frame.mainCamera().position(),
                sourcePortal.getFrame(),
                targetFrameFor(targetPortal, sourcePortal),
                PortalViewConfig.PORTAL_VIEW_MIN_EXIT_OFFSET
        );
    }

    private static CameraPose cameraPoseForViewTransform(PortalViewTransform.View view) {
        PortalTransform.Rotation rotation = PortalTransform.rotationFromVector(view.forward());
        return new CameraPose(
                view.position(),
                rotation.yaw(),
                rotation.pitch(),
                cameraRotation(view.forward(), view.up())
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
                                       PortalViewTransform.Aperture aperture) {
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
            Matrix4f apertureProjection = PortalApertureProjection.create(pose.position, portalCamera.getRotation(), aperture);
            Matrix4f projection = apertureProjection != null
                    ? apertureProjection
                    : matchMainProjection
                    ? new Matrix4f(frame.perspectiveProjection())
                    : projectionForAspect(frame.perspectiveProjection(), aspect);
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

                sceneRenderer.setupFrustum(pose.position, view, projection);
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
                                            SimpleFramebuffer sourceFramebuffer) {
        compositePortalArea(frame, portal, sourceFramebuffer, false);
    }

    private static void compositeLivePortalArea(RenderFrame frame, PortalBlockEntity portal,
                                                SimpleFramebuffer sourceFramebuffer) {
        compositePortalArea(
                frame,
                portal,
                sourceFramebuffer,
                false
        );
    }

    private static void compositePortalArea(RenderFrame frame, PortalBlockEntity portal,
                                            SimpleFramebuffer sourceFramebuffer,
                                            boolean screenAligned) {
        Framebuffer mainFramebuffer = frame.client().getFramebuffer();
        if (sourceFramebuffer == null || mainFramebuffer == null
                || sourceFramebuffer.getColorAttachmentView() == null
                || mainFramebuffer.getColorAttachmentView() == null) {
            return;
        }

        PortalScreenQuad quad = screenQuadForPortal(frame, portal);
        if (quad == null) {
            return;
        }

        GpuTextureView mainDepth = mainFramebuffer.getDepthAttachmentView();
        boolean depthTest = mainDepth != null;
        GpuBuffer vertexBuffer = getPortalAreaBuffer(quad, screenAligned);
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
            pass.draw(0, 6);
        }
    }

    private static void compositeCachedPortalArea(RenderFrame frame, PortalBlockEntity portal, RenderSlot slot) {
        SimpleFramebuffer cachedFramebuffer = slot.frontFramebuffer();
        if (!slot.ready || cachedFramebuffer == null) {
            return;
        }
        compositeLivePortalArea(frame, portal, cachedFramebuffer);
    }

    private static GpuBuffer getPortalAreaBuffer(PortalScreenQuad quad, boolean screenAligned) {
        if (portalAreaBuffer != null) {
            portalAreaBuffer.close();
            portalAreaBuffer = null;
        }

        ByteBuffer vertices = ByteBuffer.allocateDirect(6 * 6 * Float.BYTES).order(ByteOrder.nativeOrder());
        putPortalAreaVertex(vertices, quad.bottomLeft(), screenAligned);
        putPortalAreaVertex(vertices, quad.bottomRight(), screenAligned);
        putPortalAreaVertex(vertices, quad.topRight(), screenAligned);
        putPortalAreaVertex(vertices, quad.bottomLeft(), screenAligned);
        putPortalAreaVertex(vertices, quad.topRight(), screenAligned);
        putPortalAreaVertex(vertices, quad.topLeft(), screenAligned);
        vertices.flip();
        portalAreaBuffer = RenderSystem.getDevice()
                .createBuffer(() -> "Monvhua portal framebuffer area vertices", 40, vertices);
        return portalAreaBuffer;
    }

    private static void putPortalAreaVertex(ByteBuffer buffer, PortalScreenVertex vertex, boolean screenAligned) {
        buffer.putFloat(vertex.clipX());
        buffer.putFloat(vertex.clipY());
        buffer.putFloat(vertex.clipZ());
        buffer.putFloat(vertex.clipW());
        buffer.putFloat(screenAligned ? vertex.x() : vertex.u());
        buffer.putFloat(screenAligned ? vertex.y() : vertex.v());
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

    private static BlockPos remoteViewCenterFor(CameraPose pose) {
        return BlockPos.ofFloored(pose.position);
    }

    private static PortalScreenQuad screenQuadForPortal(RenderFrame frame, PortalBlockEntity portal) {
        if (portal == null) {
            return null;
        }

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
        PortalScreenVertex bottomLeft = projectPortalCorner(
                frame,
                center.subtract(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                portalU(0.0F),
                portalV(0.0F)
        );
        PortalScreenVertex bottomRight = projectPortalCorner(
                frame,
                center.add(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight)),
                portalU(1.0F),
                portalV(0.0F)
        );
        PortalScreenVertex topRight = projectPortalCorner(
                frame,
                center.add(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight)),
                portalU(1.0F),
                portalV(1.0F)
        );
        PortalScreenVertex topLeft = projectPortalCorner(
                frame,
                center.subtract(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight)),
                portalU(0.0F),
                portalV(1.0F)
        );
        if (bottomLeft == null || bottomRight == null || topRight == null || topLeft == null) {
            return null;
        }

        float minX = Math.min(Math.min(bottomLeft.x(), bottomRight.x()), Math.min(topRight.x(), topLeft.x()));
        float maxX = Math.max(Math.max(bottomLeft.x(), bottomRight.x()), Math.max(topRight.x(), topLeft.x()));
        float minY = Math.min(Math.min(bottomLeft.y(), bottomRight.y()), Math.min(topRight.y(), topLeft.y()));
        float maxY = Math.max(Math.max(bottomLeft.y(), bottomRight.y()), Math.max(topRight.y(), topLeft.y()));
        if (maxX <= 0.0F || minX >= 1.0F || maxY <= 0.0F || minY >= 1.0F) {
            return null;
        }
        if (Math.abs(screenArea(bottomLeft, bottomRight, topRight) + screenArea(bottomLeft, topRight, topLeft)) < 1.0E-6F) {
            return null;
        }
        return new PortalScreenQuad(bottomLeft, bottomRight, topRight, topLeft);
    }

    private static float portalU(float u) {
        return PortalViewConfig.PORTAL_VIEW_FLIP_U ? 1.0F - u : u;
    }

    private static float portalV(float v) {
        return PortalViewConfig.PORTAL_VIEW_FLIP_V ? 1.0F - v : v;
    }

    private static PortalScreenVertex projectPortalCorner(RenderFrame frame, Vec3d corner, float u, float v) {
        Vector4f clip = new Vector4f(
                (float) (corner.x - frame.mainCamera().position().x),
                (float) (corner.y - frame.mainCamera().position().y),
                (float) (corner.z - frame.mainCamera().position().z),
                1.0F
        );
        frame.mainViewMatrix().transform(clip);
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
                u,
                v
        );
    }

    private static float screenArea(PortalScreenVertex a, PortalScreenVertex b, PortalScreenVertex c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
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
        return resolutionForAspect(windowAspect(frame), frame.maximumSurfaceResolution());
    }

    private static float windowAspect(RenderFrame frame) {
        int width = Math.max(1, frame.client().getWindow().getFramebufferWidth());
        int height = Math.max(1, frame.client().getWindow().getFramebufferHeight());
        return width / (float) height;
    }

    private static Resolution resolutionForLiveView(RenderFrame frame, PortalBlockEntity portal) {
        Vec3d offset = portal.getPortalCenter().subtract(frame.mainCamera().position());
        double planeDistance = Math.abs(offset.dotProduct(normal(portal.getFacing())));
        double safeDistance = Math.max(PortalViewConfig.MIN_PROJECTION_DEPTH, planeDistance);
        int framebufferWidth = frame.client().getWindow().getFramebufferWidth();
        int framebufferHeight = frame.client().getWindow().getFramebufferHeight();

        double projectedWidth = portal.getPortalWidth()
                * Math.abs(frame.perspectiveProjection().m00())
                / safeDistance
                * framebufferWidth
                * 0.5D;
        double projectedHeight = portal.getPortalHeight()
                * Math.abs(frame.perspectiveProjection().m11())
                / safeDistance
                * framebufferHeight
                * 0.5D;
        int requestedMaximumSide = quantizeResolution((int) Math.ceil(
                Math.max(projectedWidth, projectedHeight)
                        * PortalViewConfig.SURFACE_RESOLUTION_SCALE
        ));
        int maximumSide = MathHelper.clamp(
                requestedMaximumSide,
                PortalViewConfig.MIN_SURFACE_RESOLUTION,
                frame.maximumSurfaceResolution()
        );
        return resolutionFor(portal, maximumSide);
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

    private record Resolution(int width, int height) {
    }

    private record PortalScreenQuad(PortalScreenVertex bottomLeft,
                                    PortalScreenVertex bottomRight,
                                    PortalScreenVertex topRight,
                                    PortalScreenVertex topLeft) {
    }

    private record PortalScreenVertex(float x, float y,
                                      float clipX, float clipY, float clipZ, float clipW,
                                      float u, float v) {
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
        private long lastAttemptFrame = Long.MIN_VALUE / 2L;
        private long lastFreezeLogFrame = Long.MIN_VALUE / 2L;
        private long lastStatusLogFrame = Long.MIN_VALUE / 2L;

        private RenderSlot(MinecraftClient client, PortalKey key, String kind) {
            this.client = client;
            this.key = key;
            this.kind = kind;
        }

        private SimpleFramebuffer prepare(Resolution requestedResolution) {
            if (buffer == null) {
                buffer = new BufferHandle(textureId());
            }
            Resolution resolution = buffer.stabilize(requestedResolution);
            return buffer.ensureBack(client, resolution.width, resolution.height);
        }

        private void publish(BlockPos targetPos, long renderedFrame) {
            buffer.publish(client);
            ready = true;
            this.targetPos = targetPos == null ? null : targetPos.toImmutable();
            lastAttemptFrame = renderedFrame;
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
            MonvhuaMod.LOGGER.info("[Monvhua] Portal frame rendered from cached remote chunks. kind={}, pos={}, detail={}",
                    kind, key.pos, detail);
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
