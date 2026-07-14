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
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PortalRemoteChunkCache {
    private static final Map<BlockPos, CacheSlot> SLOTS = new HashMap<>();
    private static BlockPos activeSourcePos;
    private static int lastViewLogTick = Integer.MIN_VALUE;
    private static int lastAcceptedPacketLogTick = Integer.MIN_VALUE;
    private static int lastRejectedPacketLogTick = Integer.MIN_VALUE;

    private PortalRemoteChunkCache() {
    }

    public static void updateView(boolean active, BlockPos sourcePos, BlockPos newTargetPos, BlockPos newViewCenter,
                                  int radius, long newGeneration) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos immutableSource = sourcePos.toImmutable();
        CacheSlot slot = SLOTS.get(immutableSource);
        if (!active || client.world == null) {
            if (slot != null && newGeneration >= slot.generation) {
                slot.generation = newGeneration;
                slot.active = false;
                MonvhuaMod.LOGGER.info(
                        "[Monvhua] Portal remote cache slot retained: source={} gen={} target={} accepted={} loaded={} fresh={}",
                        immutableSource,
                        slot.generation,
                        slot.targetPos,
                        slot.acceptedChunks.size(),
                        slot.chunks.size(),
                        freshChunkCount(slot)
                );
            }
            return;
        }
        if (slot != null && newGeneration < slot.generation) {
            return;
        }
        if (slot == null) {
            slot = new CacheSlot();
            SLOTS.put(immutableSource, slot);
        }

        ClientWorld clientWorld = client.world;
        BlockPos immutableTarget = newTargetPos.toImmutable();
        BlockPos immutableViewCenter = newViewCenter.toImmutable();
        int safeRadius = PortalViewConfig.clampRemoteRadius(radius);
        ClientWorld oldWorld = slot.world;
        BlockPos oldTarget = slot.targetPos;
        BlockPos oldViewCenter = slot.viewCenter;
        long oldGeneration = slot.generation;
        int oldRadius = slot.viewRadius;
        int loadedBefore = slot.chunks.size();
        boolean worldChanged = oldWorld != clientWorld;
        boolean generationChanged = oldGeneration != newGeneration;
        boolean targetChanged = !immutableTarget.equals(oldTarget);
        boolean centerChanged = !immutableViewCenter.equals(oldViewCenter);
        boolean radiusChanged = oldRadius != safeRadius;
        boolean fullReset = worldChanged || targetChanged;
        if (fullReset) {
            slot.clearData();
        }

        LongOpenHashSet nextAccepted = buildAcceptedChunks(immutableTarget, immutableViewCenter, safeRadius);
        pruneStaleChunks(immutableSource, slot, nextAccepted, immutableTarget, immutableViewCenter);

        slot.world = clientWorld;
        slot.targetPos = immutableTarget;
        slot.viewCenter = immutableViewCenter;
        slot.generation = newGeneration;
        slot.viewRadius = safeRadius;
        slot.active = true;
        slot.acceptedChunks.clear();
        slot.acceptedChunks.addAll(nextAccepted);

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
                    slot.acceptedChunks.size(),
                    loadedBefore,
                    slot.chunks.size(),
                    freshChunkCount(slot)
            );
        }
        if (immutableSource.equals(activeSourcePos) && fullReset) {
            PortalFramebufferRenderer.onRemoteViewChanged();
        }
    }

    public static boolean accepts(ClientWorld clientWorld, int chunkX, int chunkZ) {
        CacheSlot slot = activeSlot();
        return accepts(slot, clientWorld, chunkX, chunkZ);
    }

    public static boolean isCurrentTarget(BlockPos pos) {
        CacheSlot slot = activeSlot();
        return slot != null && slot.targetPos != null && slot.targetPos.equals(pos);
    }

    public static BlockPos getTargetPos() {
        CacheSlot slot = activeSlot();
        return slot == null ? null : slot.targetPos;
    }

    public static BlockPos getViewCenter() {
        CacheSlot slot = activeSlot();
        return slot == null ? null : slot.viewCenter;
    }

    public static long getGeneration() {
        CacheSlot slot = activeSlot();
        return slot == null ? 0L : slot.generation;
    }

    public static int acceptedChunkCount() {
        CacheSlot slot = activeSlot();
        return slot == null ? 0 : slot.acceptedChunks.size();
    }

    public static String debugSummary(ClientWorld clientWorld) {
        CacheSlot slot = activeSlot();
        if (slot == null) {
            return "source=" + activeSourcePos + " slot=false worldMatch=false gen=0 target=null targetChunk=null center=null centerChunk=null radius=0 accepted=0 loaded=0 fresh=0";
        }
        return "source=" + activeSourcePos
                + " slot=true worldMatch=" + (slot.world == clientWorld)
                + " gen=" + slot.generation
                + " target=" + slot.targetPos
                + " targetChunk=" + chunkString(slot.targetPos)
                + " center=" + slot.viewCenter
                + " centerChunk=" + chunkString(slot.viewCenter)
                + " radius=" + slot.viewRadius
                + " accepted=" + slot.acceptedChunks.size()
                + " loaded=" + slot.chunks.size()
                + " fresh=" + freshChunkCount(slot);
    }

    public static WorldChunk get(ClientWorld clientWorld, int chunkX, int chunkZ) {
        CacheSlot slot = activeSlot();
        if (slot == null || slot.world != clientWorld) {
            return null;
        }
        return slot.chunks.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isFresh(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return accepts(clientWorld, chunkX, chunkZ)
                && activeSlot().chunks.containsKey(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isRenderable(ClientWorld clientWorld, int chunkX, int chunkZ) {
        CacheSlot slot = activeSlot();
        return slot != null && slot.world == clientWorld && slot.chunks.containsKey(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static void adopt(ClientWorld clientWorld, WorldChunk chunk) {
        if (chunk == null || !accepts(clientWorld, chunk.getPos().x, chunk.getPos().z)) {
            return;
        }
        activeSlot().chunks.put(chunk.getPos().toLong(), chunk);
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
        BlockPos sourcePos = packet.sourcePos();
        CacheSlot slot = SLOTS.get(sourcePos);
        if (slot == null) {
            logRemotePacketDropped(sourcePos, null, clientWorld, packet.chunkX(), packet.chunkZ(),
                    packet.generation(), "missing_source_slot");
            return null;
        }
        if (packet.generation() != slot.generation) {
            logRemotePacketDropped(sourcePos, slot, clientWorld, packet.chunkX(), packet.chunkZ(),
                    packet.generation(), "stale_generation");
            return null;
        }
        if (!accepts(slot, clientWorld, packet.chunkX(), packet.chunkZ())) {
            logRemotePacketDropped(sourcePos, slot, clientWorld, packet.chunkX(), packet.chunkZ(),
                    packet.generation(), "outside_remote_accept_set");
            return null;
        }
        ChunkData chunkData = packet.chunkData();
        return loadAccepted(
                sourcePos,
                slot,
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
        CacheSlot slot = activeSlot();
        if (slot == null) {
            return null;
        }
        return loadAccepted(activeSourcePos, slot, clientWorld, chunkX, chunkZ,
                sectionsData, heightmaps, blockEntityVisitor);
    }

    private static WorldChunk loadAccepted(BlockPos sourcePos, CacheSlot slot,
                                           ClientWorld clientWorld, int chunkX, int chunkZ,
                                           PacketByteBuf sectionsData,
                                           Map<Heightmap.Type, long[]> heightmaps,
                                           Consumer<ChunkData.BlockEntityVisitor> blockEntityVisitor) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        WorldChunk chunk = slot.chunks.get(key);
        boolean createdChunk = chunk == null;
        if (createdChunk) {
            chunk = new WorldChunk(clientWorld, new ChunkPos(chunkX, chunkZ));
            slot.chunks.put(key, chunk);
        }
        chunk.loadFromPacket(sectionsData, heightmaps, blockEntityVisitor);
        clientWorld.resetChunkColor(chunk.getPos());
        if (sourcePos.equals(activeSourcePos)) {
            PortalFramebufferRenderer.onRemoteChunkLoaded(chunkX, chunkZ);
        }
        logAcceptedPacket(sourcePos, slot, clientWorld, chunkX, chunkZ, createdChunk);
        return chunk;
    }

    private static void logRemotePacketDropped(BlockPos sourcePos, CacheSlot slot,
                                               ClientWorld clientWorld, int chunkX, int chunkZ,
                                               long packetGeneration, String reason) {
        if (slot != null && (slot.targetPos == null || slot.world != clientWorld)) {
            return;
        }
        int tick = clientTick();
        if (!shouldLog(tick, lastRejectedPacketLogTick)) {
            return;
        }
        lastRejectedPacketLogTick = tick;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote chunk packet dropped: source={} chunk={},{} packetGen={} reason={} {}",
                sourcePos,
                chunkX,
                chunkZ,
                packetGeneration,
                reason,
                debugSummary(sourcePos, slot, clientWorld)
        );
    }

    public static void appendActiveSections(ClientWorld clientWorld, LongOpenHashSet sections) {
        CacheSlot slot = activeSlot();
        if (slot == null || slot.world != clientWorld) {
            return;
        }
        for (WorldChunk chunk : slot.chunks.values()) {
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
        CacheSlot slot = activeSlot();
        return slot != null && slot.world == clientWorld ? slot.chunks.size() : 0;
    }

    public static int loadedChunkCount() {
        CacheSlot slot = activeSlot();
        return slot == null ? 0 : slot.chunks.size();
    }

    public static int freshChunkCount() {
        return freshChunkCount(activeSlot());
    }

    public static WorldChunk getLoadedChunk(int chunkX, int chunkZ) {
        CacheSlot slot = activeSlot();
        return slot == null ? null : slot.chunks.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static int getViewRadius() {
        CacheSlot slot = activeSlot();
        return Math.max(2, slot == null ? 0 : slot.viewRadius);
    }

    public static void forEachLoadedChunk(BiConsumer<Integer, Integer> consumer) {
        CacheSlot slot = activeSlot();
        if (slot == null) {
            return;
        }
        for (WorldChunk chunk : slot.chunks.values()) {
            consumer.accept(chunk.getPos().x, chunk.getPos().z);
        }
    }

    public static void forEachLoadedWorldChunk(Consumer<WorldChunk> consumer) {
        CacheSlot slot = activeSlot();
        if (slot == null) {
            return;
        }
        for (WorldChunk chunk : slot.chunks.values()) {
            consumer.accept(chunk);
        }
    }

    public static void clear() {
        for (Map.Entry<BlockPos, CacheSlot> entry : SLOTS.entrySet()) {
            logSlotClear(entry.getKey(), entry.getValue());
        }
        SLOTS.clear();
        activeSourcePos = null;
        PortalHorizonCache.clear();
        PortalFramebufferRenderer.onRemoteViewChanged();
    }

    public static void activate(BlockPos sourcePos) {
        BlockPos immutableSource = sourcePos == null ? null : sourcePos.toImmutable();
        if (java.util.Objects.equals(activeSourcePos, immutableSource)) {
            return;
        }
        activeSourcePos = immutableSource;
        PortalHorizonCache.activate(immutableSource);
        PortalFramebufferRenderer.onRemoteViewChanged();
    }

    public static BlockPos getActiveSourcePos() {
        return activeSourcePos;
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

    private static void pruneStaleChunks(BlockPos sourcePos, CacheSlot slot, LongOpenHashSet nextAccepted,
                                          BlockPos target, BlockPos centerPos) {
        int retainRadius = Math.max(
                PortalViewConfig.MAX_REMOTE_VIEW_RADIUS,
                PortalViewConfig.REMOTE_STALE_RETAIN_RADIUS_CHUNKS
        );
        ChunkPos targetChunk = new ChunkPos(target);
        ChunkPos center = new ChunkPos(centerPos);
        LongOpenHashSet removed = new LongOpenHashSet();
        slot.chunks.long2ObjectEntrySet().removeIf(entry -> {
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
                    slot.chunks.size(),
                    nextAccepted.size(),
                    chunkString(target),
                    chunkString(centerPos)
            );
            if (sourcePos.equals(activeSourcePos)) {
                PortalFramebufferRenderer.onRemoteChunksRemoved(removed);
            }
        }
    }

    private static void logAcceptedPacket(BlockPos sourcePos, CacheSlot slot, ClientWorld clientWorld,
                                          int chunkX, int chunkZ, boolean createdChunk) {
        int tick = clientTick();
        if (!isProbeChunk(slot, chunkX, chunkZ) && !shouldLog(tick, lastAcceptedPacketLogTick)) {
            return;
        }
        if (!shouldLog(tick, lastAcceptedPacketLogTick)) {
            return;
        }
        lastAcceptedPacketLogTick = tick;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote chunk packet loaded: source={} chunk={},{} created={} {}",
                sourcePos,
                chunkX,
                chunkZ,
                createdChunk,
                debugSummary(sourcePos, slot, clientWorld)
        );
    }

    private static boolean isProbeChunk(CacheSlot slot, int chunkX, int chunkZ) {
        ChunkPos targetChunk = slot.targetPos == null ? null : new ChunkPos(slot.targetPos);
        if (targetChunk != null && targetChunk.x == chunkX && targetChunk.z == chunkZ) {
            return true;
        }
        ChunkPos centerChunk = slot.viewCenter == null ? null : new ChunkPos(slot.viewCenter);
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

    private static CacheSlot activeSlot() {
        BlockPos contextSource = PortalRemoteRenderContext.getRemoteSourcePos();
        BlockPos sourcePos = contextSource == null ? activeSourcePos : contextSource;
        return sourcePos == null ? null : SLOTS.get(sourcePos);
    }

    private static boolean accepts(CacheSlot slot, ClientWorld clientWorld, int chunkX, int chunkZ) {
        return slot != null
                && slot.world == clientWorld
                && slot.acceptedChunks.contains(ChunkPos.toLong(chunkX, chunkZ));
    }

    private static int freshChunkCount(CacheSlot slot) {
        if (slot == null) {
            return 0;
        }
        int count = 0;
        for (long chunkKey : slot.acceptedChunks) {
            if (slot.chunks.containsKey(chunkKey)) {
                count++;
            }
        }
        return count;
    }

    private static String debugSummary(BlockPos sourcePos, CacheSlot slot, ClientWorld clientWorld) {
        if (slot == null) {
            return "source=" + sourcePos + " slot=false worldMatch=false gen=0 target=null targetChunk=null center=null centerChunk=null radius=0 accepted=0 loaded=0 fresh=0";
        }
        return "source=" + sourcePos
                + " slot=true worldMatch=" + (slot.world == clientWorld)
                + " gen=" + slot.generation
                + " target=" + slot.targetPos
                + " targetChunk=" + chunkString(slot.targetPos)
                + " center=" + slot.viewCenter
                + " centerChunk=" + chunkString(slot.viewCenter)
                + " radius=" + slot.viewRadius
                + " accepted=" + slot.acceptedChunks.size()
                + " loaded=" + slot.chunks.size()
                + " fresh=" + freshChunkCount(slot);
    }

    private static void logSlotClear(BlockPos sourcePos, CacheSlot slot) {
        if (slot.world == null && slot.chunks.isEmpty() && slot.acceptedChunks.isEmpty()) {
            return;
        }
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal remote cache slot clear: source={} gen={} target={} center={} radius={} accepted={} loaded={} fresh={}",
                sourcePos,
                slot.generation,
                slot.targetPos,
                slot.viewCenter,
                slot.viewRadius,
                slot.acceptedChunks.size(),
                slot.chunks.size(),
                freshChunkCount(slot)
        );
    }

    private static final class CacheSlot {
        private final Long2ObjectOpenHashMap<WorldChunk> chunks = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet acceptedChunks = new LongOpenHashSet();
        private ClientWorld world;
        private BlockPos targetPos;
        private BlockPos viewCenter;
        private long generation;
        private int viewRadius;
        private boolean active;

        private void clearData() {
            chunks.clear();
            acceptedChunks.clear();
            world = null;
            targetPos = null;
            viewCenter = null;
            viewRadius = 0;
            active = false;
        }
    }
}
