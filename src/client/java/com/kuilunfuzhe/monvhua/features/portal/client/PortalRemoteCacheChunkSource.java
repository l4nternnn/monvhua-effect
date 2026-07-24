package com.kuilunfuzhe.monvhua.features.portal.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public record PortalRemoteCacheChunkSource(BlockPos sourcePos) implements PortalChunkSource {
    public PortalRemoteCacheChunkSource {
        sourcePos = sourcePos == null ? null : sourcePos.toImmutable();
    }

    @Override
    public WorldChunk getChunk(ClientWorld world, int chunkX, int chunkZ) {
        return PortalRemoteChunkCache.get(sourcePos, world, chunkX, chunkZ);
    }

    @Override
    public void appendActiveSections(ClientWorld world, LongOpenHashSet sections) {
        PortalRemoteChunkCache.appendSections(sourcePos, world, sections);
    }

    @Override
    public int loadedChunkCount(ClientWorld world) {
        return PortalRemoteChunkCache.size(sourcePos, world);
    }

    @Override
    public String debugSummary(ClientWorld world) {
        return PortalRemoteChunkCache.debugSummary(sourcePos, world);
    }
}
