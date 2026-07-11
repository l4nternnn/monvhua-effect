package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
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
        boolean fullReset = world != clientWorld
                || generation != newGeneration
                || !immutableTarget.equals(targetPos);
        if (fullReset) {
            clear();
        }

        LongOpenHashSet nextAccepted = buildAcceptedChunks(immutableTarget, immutableViewCenter, safeRadius);
        if (!fullReset && world == clientWorld) {
            LongOpenHashSet removed = new LongOpenHashSet(ACCEPTED_CHUNKS);
            removed.removeAll(nextAccepted);
            if (!removed.isEmpty()) {
                for (long chunkKey : removed) {
                    WorldChunk chunk = CHUNKS.remove(chunkKey);
                    if (chunk != null) {
                        clientWorld.getChunkManager().getLightingProvider().setColumnEnabled(chunk.getPos(), false);
                    }
                }
                PortalFramebufferRenderer.onRemoteChunksRemoved(removed);
            }
        }

        world = clientWorld;
        targetPos = immutableTarget;
        viewCenter = immutableViewCenter;
        generation = newGeneration;
        viewRadius = safeRadius;
        ACCEPTED_CHUNKS.clear();
        ACCEPTED_CHUNKS.addAll(nextAccepted);
    }

    public static boolean accepts(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return world == clientWorld && ACCEPTED_CHUNKS.contains(ChunkPos.toLong(chunkX, chunkZ));
    }

    public static boolean isCurrentTarget(BlockPos pos) {
        return targetPos != null && targetPos.equals(pos);
    }

    public static WorldChunk get(ClientWorld clientWorld, int chunkX, int chunkZ) {
        if (!accepts(clientWorld, chunkX, chunkZ)) {
            return null;
        }
        return CHUNKS.get(ChunkPos.toLong(chunkX, chunkZ));
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
        long key = ChunkPos.toLong(chunkX, chunkZ);
        WorldChunk chunk = CHUNKS.get(key);
        if (chunk == null) {
            chunk = new WorldChunk(clientWorld, new ChunkPos(chunkX, chunkZ));
            CHUNKS.put(key, chunk);
        }
        chunk.loadFromPacket(sectionsData, heightmaps, blockEntityVisitor);
        clientWorld.resetChunkColor(chunk.getPos());
        PortalFramebufferRenderer.onRemoteChunkLoaded(chunkX, chunkZ);
        return chunk;
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
        if (world != null) {
            for (WorldChunk chunk : CHUNKS.values()) {
                world.getChunkManager().getLightingProvider().setColumnEnabled(chunk.getPos(), false);
            }
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
}
