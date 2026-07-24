package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalChunkSource;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalPassContext;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteRenderContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerRemoteMixin {
    @Unique
    private static int monvhua$lastMissingChunkLogTick = Integer.MIN_VALUE;
    @Unique
    private static int monvhua$missingChunkHits;

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
        PortalPassContext context = PortalRemoteRenderContext.current();
        if (context == null) {
            return;
        }
        PortalChunkSource chunkSource = context.chunkSource();
        if (chunkSource == null) {
            return;
        }
        WorldChunk remote = chunkSource.getChunk(world, chunkX, chunkZ);
        if (remote != null) {
            cir.setReturnValue(remote);
            return;
        }
        monvhua$logMissingRemoteChunk(context, chunkX, chunkZ, leastStatus, create);
        cir.setReturnValue(emptyChunk);
    }

    @Inject(method = "getActiveSections", at = @At("RETURN"), cancellable = true)
    private void monvhua$appendRemotePortalSections(CallbackInfoReturnable<LongOpenHashSet> cir) {
        PortalPassContext context = PortalRemoteRenderContext.current();
        if (context == null || context.chunkSource() == null) {
            return;
        }
        LongOpenHashSet remoteSections = new LongOpenHashSet();
        context.chunkSource().appendActiveSections(world, remoteSections);
        cir.setReturnValue(remoteSections);
    }

    @Inject(method = "getLoadedChunkCount", at = @At("RETURN"), cancellable = true)
    private void monvhua$countRemotePortalChunks(CallbackInfoReturnable<Integer> cir) {
        PortalPassContext context = PortalRemoteRenderContext.current();
        if (context == null || context.chunkSource() == null) {
            return;
        }
        cir.setReturnValue(context.chunkSource().loadedChunkCount(world));
    }

    @Unique
    private void monvhua$logMissingRemoteChunk(PortalPassContext context, int chunkX, int chunkZ,
                                              ChunkStatus leastStatus, boolean create) {
        monvhua$missingChunkHits++;
        int tick = monvhua$clientTick();
        if (monvhua$lastMissingChunkLogTick != Integer.MIN_VALUE
                && tick >= monvhua$lastMissingChunkLogTick
                && tick - monvhua$lastMissingChunkLogTick < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
            return;
        }
        monvhua$lastMissingChunkLogTick = tick;
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal chunk source miss: source={} chunk={},{} status={} create={} misses={} {}",
                context.sourcePos(),
                chunkX,
                chunkZ,
                leastStatus,
                create,
                monvhua$missingChunkHits,
                context.debugSummary(world)
        );
        monvhua$missingChunkHits = 0;
    }

    @Unique
    private static int monvhua$clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? 0 : client.player.age;
    }
}
