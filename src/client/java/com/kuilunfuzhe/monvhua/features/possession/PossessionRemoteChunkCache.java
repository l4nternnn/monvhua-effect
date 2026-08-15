package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;

public final class PossessionRemoteChunkCache {
    private static final Long2ObjectOpenHashMap<WorldChunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static ClientWorld world;

    private PossessionRemoteChunkCache() {
    }

    public static void clear() {
        CHUNKS.clear();
        world = null;
    }

    public static void load(MinecraftClient client, PortalPackets.RemoteChunkS2C packet) {
        if (!PossessionClient.isActive() || client.world == null || packet == null) {
            return;
        }
        if (world != client.world) {
            CHUNKS.clear();
            world = client.world;
        }

        ChunkData data = packet.chunkData();
        WorldChunk chunk = CHUNKS.get(ChunkPos.toLong(packet.chunkX(), packet.chunkZ()));
        if (chunk == null) {
            chunk = new WorldChunk(client.world, new ChunkPos(packet.chunkX(), packet.chunkZ()));
            CHUNKS.put(chunk.getPos().toLong(), chunk);
        }
        PacketByteBuf sections = data.getSectionsDataBuf();
        Map<Heightmap.Type, long[]> heightmaps = data.getHeightmap();
        chunk.loadFromPacket(sections, heightmaps,
                data.getBlockEntities(packet.chunkX(), packet.chunkZ()));
    }

    public static WorldChunk get(ClientWorld clientWorld, int chunkX, int chunkZ) {
        return world == clientWorld ? CHUNKS.get(ChunkPos.toLong(chunkX, chunkZ)) : null;
    }

    public static void appendActiveSections(ClientWorld clientWorld, LongOpenHashSet sections) {
        if (world != clientWorld) {
            return;
        }
        for (WorldChunk chunk : CHUNKS.values()) {
            ChunkSection[] chunkSections = chunk.getSectionArray();
            for (int index = 0; index < chunkSections.length; index++) {
                if (!chunkSections[index].isEmpty()) {
                    sections.add(ChunkSectionPos.asLong(
                            chunk.getPos().x,
                            clientWorld.sectionIndexToCoord(index),
                            chunk.getPos().z
                    ));
                }
            }
        }
    }

    public static int loadedChunkCount(ClientWorld clientWorld) {
        return world == clientWorld ? CHUNKS.size() : 0;
    }
}
