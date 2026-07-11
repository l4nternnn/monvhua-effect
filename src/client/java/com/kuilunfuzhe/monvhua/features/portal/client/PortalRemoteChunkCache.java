package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PortalRemoteChunkCache {
    private static final Long2ObjectOpenHashMap<WorldChunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet ACCEPTED_CHUNKS = new LongOpenHashSet();

    private static ClientWorld world;
    private static BlockPos targetPos;
    private static BlockPos viewCenter;
    private static long generation;
    private static int viewRadius;
    private static int lastViewLogTick = Integer.MIN_VALUE;
    private static int lastAcceptedPacketLogTick = Integer.MIN_VALUE;
    private static int lastRejectedPacketLogTick = Integer.MIN_VALUE;

    private PortalRemoteChunkCache() {
    }

    public static void updateView(boolean active, BlockPos newTargetPos, BlockPos newViewCenter,
                                  int radius, long newGeneration) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || client.world == null) {
            clear();
            generation = Math.max(generation, newGeneration);
            return;
        }
        if (newGeneration < generation) {
            return;
        }

        ClientWorld clientWorld = client.world;
        BlockPos immutableTarget = newTargetPos.toImmutable();
        BlockPos immutableViewCenter = newViewCenter.toImmutable();
        int safeRadius = PortalViewConfig.clampRemoteRadius(radius);
        ClientWorld oldWorld = world;
        BlockPos oldTarget = targetPos;
        BlockPos oldViewCenter = viewCenter;
        long oldGeneration = generation;
        int oldRadius = viewRadius;
        int loadedBefore = CHUNKS.size();
        boolean worldChanged = oldWorld != clientWorld;
        boolean generationChanged = oldGeneration != newGeneration;
        boolean targetChanged = !immutableTarget.equals(oldTarget);
        boolean centerChanged = !immutableViewCenter.equals(oldViewCenter);
        boolean radiusChanged = oldRadius != safeRadius;
        boolean fullReset = worldChanged || generationChanged || targetChanged;
        if (fullReset) {
            clear();
        }

        LongOpenHashSet nextAccepted = buildAcceptedChunks(immutableTarget, immutableViewCenter, safeRadius);
        pruneStaleChunks(clientWorld, nextAccepted, immutableTarget, immutableViewCenter);

        world = clientWorld;
        targetPos = immutableTarget;
        viewCenter = immutableViewCenter;
        generation = newGeneration;
        viewRadius = safeRadius;
        ACCEPTED_CHUNKS.clear();
        ACCEPTED_CHUNKS.addAll(nextAccepted);

        int tick = clientTick();
        if (fullReset || centerChanged || radiusChanged || shouldLog(tick, lastViewLogTick)) {
            lastViewLogTick = tick;
            ChunkPos targetChunk = new ChunkPos(immutableTarget);
            ChunkPos centerChunk = new ChunkPos(immutableViewCenter);
            MonvhuaMod.LOGGER.info(
                    "[Monvhua] Portal remote view update: gen={} oldGen={} reset={} reason={} target={} targetChunk={},{} center={} centerChunk={},{} radius={} oldRadius={} accepted={} loadedBefore={} loadedAfter={} fresh={}",
                    newGeneration,
                    oldGeneration,
                    fullReset,
                    resetReason(worldChanged, generationChanged, targetChanged),
                    immutableTarget,
                    targetChunk.x,
                    targetChunk.z,
                    immutableViewCenter,
                    centerChunk.x,
                    centerChunk.z,
                    safeRadius,
                    oldRadius,
                    ACCEPTED_CHUNKS.size(),
                    loadedBefore,
                    CHUNKS.size(),
                    freshChunkCount()
            );
        }
    }

    public static boolean accepts(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return world == clientWorld && ACCEPTED_CHUNKS.contains(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isCurrentTarget(BlockPos pos) {
        return targetPos != null && targetPos.equals(pos);
    }

    public static BlockPos getTargetPos() {
        return targetPos;
    }

    public static BlockPos getViewCenter() {
        return viewCenter;
    }

    public static long getGeneration() {
        return generation;
    }

    public static int acceptedChunkCount() {
        return ACCEPTED_CHUNKS.size();
    }

    public static String debugSummary(ClientWorld clientWorld) {
        return "worldMatch=" + (world == clientWorld)
                + " gen=" + generation
                + " target=" + targetPos
                + " targetChunk=" + chunkString(targetPos)
                + " center=" + viewCenter
                + " centerChunk=" + chunkString(viewCenter)
                + " radius=" + viewRadius
                + " accepted=" + ACCEPTED_CHUNKS.size()
                + " loaded=" + CHUNKS.size()
                + " fresh=" + freshChunkCount();
    }

    public static WorldChunk get(ClientWorld clientWorld, int chunkX, int chunkZ) {
        if (world != clientWorld) {
            return null;
        }
        return CHUNKS.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isFresh(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return accepts(clientWorld, chunkX, chunkZ)
                && CHUNKS.containsKey(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isRenderable(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return world == clientWorld && CHUNKS.containsKey(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static void adopt(ClientWorld clientWorld, WorldChunk chunk) {
        if (chunk == null || !accepts(clientWorld, chunk.getPos().x, chunk.getPos().z)) {
            return;
        }
        CHUNKS.put(chunk.getPos().toLong(), chunk);
        PortalFramebufferRenderer.onRemoteChunkLoaded(chunk.getPos().x, chunk.getPos().z);
    }

    public static WorldChunk load(ClientWorld clientWorld, int chunkX, int chunkZ,
                                  PacketByteBuf sectionsData,
                                  Map<Heightmap.Type, long[]> heightmaps,
                                  Consumer<ChunkData.BlockEntityVisitor> blockEntityVisitor) {
        if (!accepts(clientWorld, chunkX, chunkZ)) {
            return null;
        }
        return loadAccepted(clientWorld, chunkX, chunkZ, sectionsData, heightmaps, blockEntityVisitor);
    }

    public static WorldChunk loadRemoteChunkPacket(ClientWorld clientWorld, PortalPackets.RemoteChunkS2C packet) {
        if (packet == null || clientWorld == null) {
            return null;
        }
        if (packet.generation() != generation) {
            logRemotePacketDropped(clientWorld, packet.chunkX(), packet.chunkZ(), packet.generation(), "stale_generation");
            return null;
        }
        if (!accepts(clientWorld, packet.chunkX(), packet.chunkZ())) {
            logRemotePacketDropped(clientWorld, packet.chunkX(), packet.chunkZ(), packet.generation(), "outside_remote_accept_set");
            return null;
        }
        ChunkData chunkData = packet.chunkData();
        return loadAccepted(
                clientWorld,
                packet.chunkX(),
                packet.chunkZ(),
                chunkData.getSectionsDataBuf(),
                chunkData.getHeightmap(),
                chunkData.getBlockEntities(packet.chunkX(), packet.chunkZ())
        );
    }

    private static WorldChunk loadAccepted(ClientWorld clientWorld, int chunkX, int chunkZ,
                                           PacketByteBuf sectionsData,
                                           Map<Heightmap.Type, long[]> heightmaps,
                                           Consumer<ChunkData.BlockEntityVisitor> blockEntityVisitor) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        WorldChunk chunk = CHUNKS.get(key);
        boolean createdChunk = chunk == null;
        if (createdChunk) {
            chunk = new WorldChunk(clientWorld, new ChunkPos(chunkX, chunkZ));
            CHUNKS.put(key, chunk);
        }
        chunk.loadFromPacket(sectionsData, heightmaps, blockEntityVisitor);
        clientWorld.resetChunkColor(chunk.getPos());
        PortalFramebufferRenderer.onRemoteChunkLoaded(chunkX, chunkZ);
        logAcceptedPacket(clientWorld, chunkX, chunkZ, createdChunk);
        return chunk;
    }

    private static void logRemotePacketDropped(ClientWorld clientWorld, int chunkX, int chunkZ,
                                               long packetGeneration, String reason) {
        if (targetPos == null || world != clientWorld) {
            return;
        }
        int tick = clientTick();
        if (!shouldLog(tick, lastRejectedPacketLogTick)) {
            return;
        }
        lastRejectedPacketLogTick = tick;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote chunk packet dropped: chunk={},{} packetGen={} reason={} {}",
                chunkX,
                chunkZ,
                packetGeneration,
                reason,
                debugSummary(clientWorld)
        );
    }

    public static void appendActiveSections(ClientWorld clientWorld, LongOpenHashSet sections) {
        if (world != clientWorld) {
            return;
        }
        for (WorldChunk chunk : CHUNKS.values()) {
            ChunkSection[] chunkSections = chunk.getSectionArray();
            ChunkPos pos = chunk.getPos();
            for (int index = 0; index < chunkSections.length; index++) {
                if (!chunkSections[index].isEmpty()) {
                    sections.add(ChunkSectionPos.asLong(
                            pos.x,
                            clientWorld.sectionIndexToCoord(index),
                            pos.z
                    ));
                }
            }
        }
    }

    public static int size(ClientWorld clientWorld) {
        return world == clientWorld ? CHUNKS.size() : 0;
    }

    public static int loadedChunkCount() {
        return CHUNKS.size();
    }

    public static int freshChunkCount() {
        int count = 0;
        for (long chunkKey : ACCEPTED_CHUNKS) {
            if (CHUNKS.containsKey(chunkKey)) {
                count++;
            }
        }
        return count;
    }

    public static WorldChunk getLoadedChunk(int chunkX, int chunkZ) {
        return CHUNKS.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static int getViewRadius() {
        return Math.max(2, viewRadius);
    }

    public static void forEachLoadedChunk(BiConsumer<Integer, Integer> consumer) {
        for (WorldChunk chunk : CHUNKS.values()) {
            consumer.accept(chunk.getPos().x, chunk.getPos().z);
        }
    }

    public static void forEachLoadedWorldChunk(Consumer<WorldChunk> consumer) {
        for (WorldChunk chunk : CHUNKS.values()) {
            consumer.accept(chunk);
        }
    }

    public static void clear() {
        if (world != null || !CHUNKS.isEmpty() || !ACCEPTED_CHUNKS.isEmpty()) {
            MonvhuaMod.LOGGER.info(
                    "[Monvhua] Portal remote cache clear: gen={} target={} center={} radius={} accepted={} loaded={} fresh={}",
                    generation,
                    targetPos,
                    viewCenter,
                    viewRadius,
                    ACCEPTED_CHUNKS.size(),
                    CHUNKS.size(),
                    freshChunkCount()
            );
        }
        CHUNKS.clear();
        ACCEPTED_CHUNKS.clear();
        world = null;
        targetPos = null;
        viewCenter = null;
        viewRadius = 0;
        PortalHorizonCache.clear();
        PortalFramebufferRenderer.onRemoteViewChanged();
    }

    private static LongOpenHashSet buildAcceptedChunks(BlockPos target, BlockPos centerPos, int radius) {
        LongOpenHashSet accepted = new LongOpenHashSet();
        ChunkPos center = new ChunkPos(centerPos);
        for (int z = center.z - radius; z <= center.z + radius; z++) {
            for (int x = center.x - radius; x <= center.x + radius; x++) {
                accepted.add(ChunkPos.toLong(x, z));
            }
        }
        accepted.add(new ChunkPos(target).toLong());
        return accepted;
    }

    private static void pruneStaleChunks(ClientWorld clientWorld, LongOpenHashSet nextAccepted,
                                         BlockPos target, BlockPos centerPos) {
        int retainRadius = Math.max(
                PortalViewConfig.MAX_REMOTE_VIEW_RADIUS,
                PortalViewConfig.REMOTE_STALE_RETAIN_RADIUS_CHUNKS
        );
        ChunkPos targetChunk = new ChunkPos(target);
        ChunkPos center = new ChunkPos(centerPos);
        LongOpenHashSet removed = new LongOpenHashSet();
        CHUNKS.long2ObjectEntrySet().removeIf(entry -> {
            long chunkKey = entry.getLongKey();
            if (nextAccepted.contains(chunkKey)) {
                return false;
            }
            int chunkX = ChunkPos.getPackedX(chunkKey);
            int chunkZ = ChunkPos.getPackedZ(chunkKey);
            boolean keepNearCenter = Math.max(Math.abs(chunkX - center.x), Math.abs(chunkZ - center.z)) <= retainRadius;
            boolean keepNearTarget = Math.max(Math.abs(chunkX - targetChunk.x), Math.abs(chunkZ - targetChunk.z)) <= retainRadius;
            if (keepNearCenter || keepNearTarget) {
                return false;
            }
            removed.add(chunkKey);
            return true;
        });
        if (!removed.isEmpty()) {
            MonvhuaMod.LOGGER.info(
                    "[Monvhua] Portal remote cache pruned stale chunks: removed={} retained={} nextAccepted={} targetChunk={} centerChunk={}",
                    removed.size(),
                    CHUNKS.size(),
                    nextAccepted.size(),
                    chunkString(target),
                    chunkString(centerPos)
            );
            PortalFramebufferRenderer.onRemoteChunksRemoved(removed);
        }
    }

    private static void logAcceptedPacket(ClientWorld clientWorld, int chunkX, int chunkZ, boolean createdChunk) {
        int tick = clientTick();
        if (!isProbeChunk(chunkX, chunkZ) && !shouldLog(tick, lastAcceptedPacketLogTick)) {
            return;
        }
        if (!shouldLog(tick, lastAcceptedPacketLogTick)) {
            return;
        }
        lastAcceptedPacketLogTick = tick;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote chunk packet loaded: chunk={},{} created={} {}",
                chunkX,
                chunkZ,
                createdChunk,
                debugSummary(clientWorld)
        );
    }

    private static boolean isProbeChunk(int chunkX, int chunkZ) {
        ChunkPos targetChunk = targetPos == null ? null : new ChunkPos(targetPos);
        if (targetChunk != null && targetChunk.x == chunkX && targetChunk.z == chunkZ) {
            return true;
        }
        ChunkPos centerChunk = viewCenter == null ? null : new ChunkPos(viewCenter);
        return centerChunk != null
                && Math.max(Math.abs(chunkX - centerChunk.x), Math.abs(chunkZ - centerChunk.z))
                <= Math.max(0, PortalViewConfig.REMOTE_PUBLISH_CORE_RADIUS_CHUNKS);
    }

    private static int clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? 0 : client.player.age;
    }

    private static boolean shouldLog(int tick, int lastTick) {
        return lastTick == Integer.MIN_VALUE
                || tick - lastTick >= PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS
                || tick < lastTick;
    }

    private static String resetReason(boolean worldChanged, boolean generationChanged, boolean targetChanged) {
        if (!worldChanged && !generationChanged && !targetChanged) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        appendReason(builder, worldChanged, "world");
        appendReason(builder, generationChanged, "generation");
        appendReason(builder, targetChanged, "target");
        return builder.toString();
    }

    private static void appendReason(StringBuilder builder, boolean active, String reason) {
        if (!active) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('+');
        }
        builder.append(reason);
    }

    private static String chunkString(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        ChunkPos chunk = new ChunkPos(pos);
        return chunk.x + "," + chunk.z;
    }
}
