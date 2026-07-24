package com.kuilunfuzhe.monvhua.features.portal.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public interface PortalChunkSource {
    BlockPos sourcePos();

    WorldChunk getChunk(ClientWorld world, int chunkX, int chunkZ);

    void appendActiveSections(ClientWorld world, LongOpenHashSet sections);

    int loadedChunkCount(ClientWorld world);

    String debugSummary(ClientWorld world);
}
