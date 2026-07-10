package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.compat.IrisMirrorCompat;
import com.kuilunfuzhe.monvhua.features.portal.PortalBlockEntity;
import com.kuilunfuzhe.monvhua.features.portal.PortalLinkData;
import com.kuilunfuzhe.monvhua.features.portal.PortalTransform;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.mixin.CameraAccessor;
import com.kuilunfuzhe.monvhua.mixin.GameRendererAccessor;
import com.kuilunfuzhe.monvhua.mixin.portal.SodiumWorldRendererAccessor;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalFramebufferRenderer {
    private static final AtomicBoolean RENDERING = new AtomicBoolean(false);
    private static final Map<PortalKey, RenderSlot> LIVE_SLOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<PortalKey, RenderSlot> PREVIEW_SLOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<PortalKey, Candidate> VISIBLE_CANDIDATES = new LinkedHashMap<>();
    private static final Map<PortalKey, Integer> PENDING_CAPTURES = new LinkedHashMap<>();

    private static long frameIndex;
    private static int lastFailureLogTick = Integer.MIN_VALUE;
    private static BlockPos lastRequestedSource;
    private static long lastRemoteRequestFrame = Long.MIN_VALUE / 2L;
    private static boolean remoteTerrainDirty;
    private static WorldRenderer remoteWorldRenderer;
    private static BufferBuilderStorage remoteBufferBuilders;
    private static ClientWorld remoteRendererWorld;
    private static FogRenderer remoteFogRenderer;
    private static RawProjectionMatrix remoteProjectionMatrix;
    private static final LongOpenHashSet REMOTE_SODIUM_CHUNKS = new LongOpenHashSet();
    private static final LongOpenHashSet QUEUED_REMOTE_SODIUM_CHUNKS = new LongOpenHashSet();
    private static final ArrayDeque<Long> PENDING_REMOTE_SODIUM_CHUNKS = new ArrayDeque<>();
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
                                           Matrix4f projectionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            VISIBLE_CANDIDATES.clear();
            return;
        }
        boolean irisShaders = IrisMirrorCompat.isShaderPackActive();
        float tickProgress = tickCounter.getTickProgress(false);
        float fov = ((GameRendererAccessor) client.gameRenderer)
                .monvhua$invokeGetFov(mainCamera, tickProgress, true);
        Matrix4f perspectiveProjection = client.gameRenderer.getBasicProjectionMatrix(fov);
        Matrix4f viewEffect = new Matrix4f(perspectiveProjection)
                .invert()
                .mul(projectionMatrix);
        int viewDistanceChunks = Math.max(
                client.options.getClampedViewDistance(),
                PortalRemoteChunkCache.getViewRadius()
        );
        RenderFrame frame = new RenderFrame(
                client,
                tickCounter,
                mainCamera,
                perspectiveProjection,
                viewEffect,
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
        if (candidates.isEmpty() || RENDERING.getAndSet(true)) {
            return;
        }

        int budget = frame.renderBudget();
        int updateInterval = PortalViewConfig.LIVE_VIEW_UPDATE_INTERVAL_FRAMES;
        candidates.sort(PortalFramebufferRenderer::compareCandidates);
        Candidate remoteViewCandidate = candidates.getFirst();
        if (lastRequestedSource != null) {
            for (Candidate candidate : candidates) {
                if (lastRequestedSource.equals(candidate.portal.getPos())) {
                    remoteViewCandidate = candidate;
                    break;
                }
            }
        }
        requestRemoteView(remoteViewCandidate.portal);

        try {
            int rendered = 0;
            for (Candidate candidate : candidates) {
                if (rendered >= budget) {
                    break;
                }
                PortalBlockEntity portal = candidate.portal;
                if (portal.isRemoved() || !portal.isActive() || portal.getLinkData() == null) {
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
        queueRemoteSodiumChunk(chunkX, chunkZ);
    }

    public static void onRemoteViewChanged() {
        shutdownRemoteRenderer();
    }

    public static void onRemoteChunksRemoved(LongOpenHashSet removedChunks) {
        if (removedChunks == null || removedChunks.isEmpty()) {
            return;
        }
        QUEUED_REMOTE_SODIUM_CHUNKS.removeAll(removedChunks);
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
            REMOTE_SODIUM_CHUNKS.clear();
            QUEUED_REMOTE_SODIUM_CHUNKS.clear();
            PENDING_REMOTE_SODIUM_CHUNKS.clear();
            remoteTerrainDirty = true;
        }
    }

    private static void requestRemoteView(PortalBlockEntity portal) {
        if (portal == null || portal.getLinkData() == null) {
            return;
        }
        BlockPos sourcePos = portal.getPos();
        boolean sameSource = sourcePos.equals(lastRequestedSource);
        if (sameSource
                && frameIndex - lastRemoteRequestFrame < PortalViewConfig.REMOTE_REQUEST_INTERVAL_FRAMES) {
            return;
        }
        lastRequestedSource = sourcePos.toImmutable();
        lastRemoteRequestFrame = frameIndex;
        ClientPlayNetworking.send(new PortalPackets.RequestRemoteViewC2S(lastRequestedSource));
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
        BlockEntity targetBlockEntity = client.world.getBlockEntity(link.targetPos());
        if (!(targetBlockEntity instanceof PortalBlockEntity targetPortal) || !targetPortal.isController()) {
            return;
        }

        Resolution resolution = resolutionForLiveView(frame, sourcePortal);
        SimpleFramebuffer framebuffer = slot.prepare(resolution);
        CameraPose pose = transformCamera(frame.mainCamera(), sourcePortal, targetPortal);
        float aspect = sourcePortal.getPortalWidth() / (float) sourcePortal.getPortalHeight();

        if (renderScene(frame, framebuffer, pose, targetPortal, aspect)) {
            slot.publish(link.targetPos(), frameIndex);
        }
    }

    private static void renderPreview(RenderFrame frame, PortalBlockEntity portal,
                                      RenderSlot slot, int maximumSide) {
        Resolution resolution = resolutionFor(portal, maximumSide);
        SimpleFramebuffer framebuffer = slot.prepare(resolution);
        CameraPose pose = new CameraPose(
                portal.getPortalCenter().add(normal(portal.getFacing()).multiply(0.55D)),
                portal.getFacing().getOpposite().getPositiveHorizontalDegrees(),
                0.0F
        );
        float aspect = portal.getPortalWidth() / (float) portal.getPortalHeight();
        if (renderScene(frame, framebuffer, pose, null, aspect)) {
            slot.publish(null, frameIndex);
        }
    }

    private static CameraPose transformCamera(Camera mainCamera,
                                              PortalBlockEntity sourcePortal,
                                              PortalBlockEntity targetPortal) {
        Vec3d mappedPosition = PortalTransform.mapPosition(
                mainCamera.getPos(),
                sourcePortal.getPortalCenter(),
                sourcePortal.getFacing(),
                targetPortal.getPortalCenter(),
                targetPortal.getFacing()
        );
        double targetSide = mappedPosition.subtract(targetPortal.getPortalCenter())
                .dotProduct(normal(targetPortal.getFacing()));
        Direction viewDirection = targetSide <= 0.0D
                ? targetPortal.getFacing()
                : targetPortal.getFacing().getOpposite();
        return new CameraPose(
                mappedPosition,
                viewDirection.getPositiveHorizontalDegrees(),
                0.0F
        );
    }

    private static boolean renderScene(RenderFrame frame, SimpleFramebuffer targetFramebuffer,
                                       CameraPose pose, PortalBlockEntity aperturePortal,
                                       float aspect) {
        MinecraftClient client = frame.client();
        RenderTickCounter tickCounter = frame.tickCounter();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                targetFramebuffer.getColorAttachment(),
                0xFF000000,
                targetFramebuffer.getDepthAttachment(),
                1.0
        );

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice previousFog = RenderSystem.getShaderFog();
        Camera globalCamera = client.gameRenderer.getCamera();
        Vec3d previousCameraPos = globalCamera.getPos();
        float previousCameraYaw = globalCamera.getYaw();
        float previousCameraPitch = globalCamera.getPitch();
        boolean globalCameraOverridden = false;
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
            CameraAccessor accessor = (CameraAccessor) portalCamera;
            accessor.invokeSetPos(pose.position.x, pose.position.y, pose.position.z);
            accessor.invokeSetRotation(pose.yaw, pose.pitch);

            CameraAccessor globalCameraAccessor = (CameraAccessor) globalCamera;
            globalCameraAccessor.invokeSetPos(pose.position.x, pose.position.y, pose.position.z);
            globalCameraAccessor.invokeSetRotation(pose.yaw, pose.pitch);
            globalCameraOverridden = true;

            Quaternionf worldToCamera = portalCamera.getRotation().conjugate(new Quaternionf());
            Matrix4f view = new Matrix4f().rotation(worldToCamera);
            Matrix4f projection = aperturePortal == null
                    ? projectionForAspect(frame.perspectiveProjection(), aspect)
                    : projectionForPortal(
                            frame.perspectiveProjection(),
                            aperturePortal,
                            pose.position,
                            worldToCamera,
                            aspect
                    );
            projection.mul(frame.viewEffect());
            RenderSystem.setProjectionMatrix(
                    getRemoteProjectionMatrix().set(projection),
                    ProjectionType.PERSPECTIVE
            );

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

            sceneRenderer.setupFrustum(pose.position, view, projection);
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
            return true;
        } finally {
            if (globalCameraOverridden) {
                CameraAccessor globalCameraAccessor = (CameraAccessor) globalCamera;
                globalCameraAccessor.invokeSetPos(
                        previousCameraPos.x,
                        previousCameraPos.y,
                        previousCameraPos.z
                );
                globalCameraAccessor.invokeSetRotation(previousCameraYaw, previousCameraPitch);
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
        long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
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
                continue;
            }

            if (REMOTE_SODIUM_CHUNKS.add(chunkKey)) {
                sectionManager.onChunkAdded(chunkX, chunkZ);
            } else {
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

    private static Matrix4f projectionForAspect(Matrix4f original, float aspect) {
        Matrix4f projection = new Matrix4f(original);
        float safeAspect = Math.max(0.05F, aspect);
        if (Math.abs(projection.m11()) > 1.0E-5F) {
            projection.m00(projection.m11() / safeAspect);
        }
        return projection;
    }

    private static Matrix4f projectionForPortal(Matrix4f original,
                                                PortalBlockEntity portal,
                                                Vec3d cameraPos,
                                                Quaternionf worldToCamera,
                                                float fallbackAspect) {
        Vec3d center = portal.getPortalCenter();
        Vec3d horizontal = portal.getFacing().getAxis() == Direction.Axis.X
                ? new Vec3d(0.0D, 0.0D, 1.0D)
                : new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d vertical = new Vec3d(0.0D, 1.0D, 0.0D);
        double halfWidth = Math.max(
                0.01D,
                portal.getPortalWidth() * 0.5D - PortalViewConfig.PORTAL_SURFACE_HORIZONTAL_INSET
        );
        double halfHeight = Math.max(
                0.01D,
                portal.getPortalHeight() * 0.5D - PortalViewConfig.PORTAL_SURFACE_VERTICAL_INSET
        );

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minimumDepth = Float.POSITIVE_INFINITY;
        for (int horizontalSign : new int[]{-1, 1}) {
            for (int verticalSign : new int[]{-1, 1}) {
                Vec3d corner = center
                        .add(horizontal.multiply(halfWidth * horizontalSign))
                        .add(vertical.multiply(halfHeight * verticalSign));
                Vec3d relative = corner.subtract(cameraPos);
                Vector3f cameraCorner = new Vector3f(
                        (float) relative.x,
                        (float) relative.y,
                        (float) relative.z
                );
                worldToCamera.transform(cameraCorner);
                float depth = -cameraCorner.z;
                if (depth < PortalViewConfig.MIN_PROJECTION_DEPTH) {
                    return projectionForAspect(original, fallbackAspect);
                }
                minimumDepth = Math.min(minimumDepth, depth);
                float slopeX = cameraCorner.x / depth;
                float slopeY = cameraCorner.y / depth;
                minX = Math.min(minX, slopeX);
                maxX = Math.max(maxX, slopeX);
                minY = Math.min(minY, slopeY);
                maxY = Math.max(maxY, slopeY);
            }
        }

        float width = maxX - minX;
        float height = maxY - minY;
        if (width < 1.0E-5F || height < 1.0E-5F) {
            return projectionForAspect(original, fallbackAspect);
        }

        Matrix4f projection = new Matrix4f(original);
        projection.m00(2.0F / width);
        projection.m20((maxX + minX) / width);
        projection.m11(2.0F / height);
        projection.m21((maxY + minY) / height);
        setPerspectiveDepth(
                projection,
                minimumDepth + (float) PortalViewConfig.PORTAL_NEAR_PLANE_BIAS,
                Math.max(extractFarPlane(original), PortalViewConfig.PORTAL_MINIMUM_FAR_PLANE)
        );
        return projection;
    }

    private static float extractFarPlane(Matrix4f projection) {
        float denominator = projection.m22() + 1.0F;
        if (Math.abs(denominator) < 1.0E-6F) {
            return 4096.0F;
        }
        return Math.max(1.0F, Math.abs(projection.m32() / denominator));
    }

    private static void setPerspectiveDepth(Matrix4f projection, float requestedNear, float requestedFar) {
        float near = Math.max(0.001F, requestedNear);
        float far = Math.max(near + 1.0F, requestedFar);
        float inverseRange = 1.0F / (far - near);
        projection.m22(-(far + near) * inverseRange);
        projection.m32(-(2.0F * far * near) * inverseRange);
    }

    private static Resolution resolutionFor(PortalBlockEntity portal, int maximumSide) {
        float aspect = portal.getPortalWidth() / (float) portal.getPortalHeight();
        if (aspect >= 1.0F) {
            return new Resolution(maximumSide, Math.max(16, Math.round(maximumSide / aspect)));
        }
        return new Resolution(Math.max(16, Math.round(maximumSide * aspect)), maximumSide);
    }

    private static Resolution resolutionForLiveView(RenderFrame frame, PortalBlockEntity portal) {
        Vec3d offset = portal.getPortalCenter().subtract(frame.mainCamera().getPos());
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
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    private record PortalKey(Identifier dimension, BlockPos pos) {
    }

    private record Candidate(PortalKey key, PortalBlockEntity portal, double priority) {
    }

    private record RenderFrame(MinecraftClient client, RenderTickCounter tickCounter,
                               Camera mainCamera, Matrix4f perspectiveProjection,
                               Matrix4f viewEffect, int viewDistanceChunks,
                               int renderBudget,
                               int maximumSurfaceResolution) {
    }

    private record CameraPose(Vec3d position, float yaw, float pitch) {
    }

    private record Resolution(int width, int height) {
    }

    private static final class RenderSlot {
        private final MinecraftClient client;
        private final PortalKey key;
        private final String kind;
        private BufferHandle buffer;
        private boolean ready;
        private BlockPos targetPos;
        private long lastAttemptFrame = Long.MIN_VALUE / 2L;

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
            return buffer.ensure(client, resolution.width, resolution.height);
        }

        private void publish(BlockPos targetPos, long renderedFrame) {
            ready = true;
            this.targetPos = targetPos == null ? null : targetPos.toImmutable();
            lastAttemptFrame = renderedFrame;
        }

        private Identifier frontTextureId() {
            return buffer == null ? null : buffer.textureId;
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
                buffer.close();
            }
        }
    }

    private static final class BufferHandle {
        private final Identifier textureId;
        private PortalFramebufferTexture texture;
        private SimpleFramebuffer framebuffer;

        private BufferHandle(Identifier textureId) {
            this.textureId = textureId;
        }

        private SimpleFramebuffer ensure(MinecraftClient client, int width, int height) {
            if (framebuffer == null || framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
                if (framebuffer != null) {
                    framebuffer.delete();
                }
                framebuffer = new SimpleFramebuffer("monvhua_portal_slot", width, height, true);
            }
            if (texture == null) {
                texture = new PortalFramebufferTexture();
                texture.setFramebuffer(framebuffer);
                client.getTextureManager().registerTexture(textureId, texture);
            } else {
                texture.setFramebuffer(framebuffer);
            }
            return framebuffer;
        }

        private Resolution stabilize(Resolution requested) {
            if (framebuffer == null) {
                return requested;
            }
            double widthChange = Math.abs(requested.width - framebuffer.textureWidth)
                    / (double) Math.max(1, framebuffer.textureWidth);
            double heightChange = Math.abs(requested.height - framebuffer.textureHeight)
                    / (double) Math.max(1, framebuffer.textureHeight);
            if (Math.max(widthChange, heightChange) <= PortalViewConfig.SURFACE_RESIZE_HYSTERESIS) {
                return new Resolution(framebuffer.textureWidth, framebuffer.textureHeight);
            }
            return requested;
        }

        private void close() {
            if (texture != null) {
                texture.close();
            }
            if (framebuffer != null) {
                framebuffer.delete();
                framebuffer = null;
            }
        }
    }
}
