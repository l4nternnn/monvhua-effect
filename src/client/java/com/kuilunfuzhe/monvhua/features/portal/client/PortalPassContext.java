package com.kuilunfuzhe.monvhua.features.portal.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public record PortalPassContext(BlockPos sourcePos, PortalChunkSource chunkSource) {
    public PortalPassContext {
        if (sourcePos == null && chunkSource != null) {
            sourcePos = chunkSource.sourcePos();
        }
        sourcePos = sourcePos == null ? null : sourcePos.toImmutable();
    }

    public static PortalPassContext remoteCache(BlockPos sourcePos) {
        if (sourcePos == null) {
            return null;
        }
        return new PortalPassContext(sourcePos, new PortalRemoteCacheChunkSource(sourcePos));
    }

    public String debugSummary(ClientWorld world) {
        return chunkSource == null
                ? "chunkSource=null source=" + sourcePos
                : chunkSource.debugSummary(world);
    }
}
