package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteChunkCache;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteRenderContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerRemoteMixin {
    @Shadow
    @Final
    private WorldChunk emptyChunk;

    @Shadow
    @Final
    private ClientWorld world;

    @Inject(method = "getChunk", at = @At("RETURN"), cancellable = true)
    private void monvhua$getRemotePortalChunk(int chunkX, int chunkZ, ChunkStatus leastStatus,
                                              boolean create,
                                              CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk remote = PortalRemoteChunkCache.get(world, chunkX, chunkZ);
        if (PortalRemoteRenderContext.isPortalPass() && remote != null) {
            cir.setReturnValue(remote);
        }
    }

    @Inject(method = "getActiveSections", at = @At("RETURN"))
    private void monvhua$appendRemotePortalSections(CallbackInfoReturnable<LongOpenHashSet> cir) {
        if (PortalRemoteRenderContext.isPortalPass()) {
            PortalRemoteChunkCache.appendActiveSections(world, cir.getReturnValue());
        }
    }

    @Inject(method = "getLoadedChunkCount", at = @At("RETURN"), cancellable = true)
    private void monvhua$countRemotePortalChunks(CallbackInfoReturnable<Integer> cir) {
        if (PortalRemoteRenderContext.isPortalPass()) {
            cir.setReturnValue(cir.getReturnValue() + PortalRemoteChunkCache.size(world));
        }
    }
}
