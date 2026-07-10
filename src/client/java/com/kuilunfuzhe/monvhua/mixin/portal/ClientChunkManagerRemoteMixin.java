package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalRemoteChunkCache;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferRenderer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

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
        // Remote Sodium workers finish after the portal pass, so this fallback must
        // remain available outside the render-thread scope.
        WorldChunk current = cir.getReturnValue();
        if (current != null && current != emptyChunk) {
            return;
        }
        WorldChunk remote = PortalRemoteChunkCache.get(world, chunkX, chunkZ);
        if (remote != null) {
            cir.setReturnValue(remote);
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void monvhua$adoptRemotePortalChunkBeforeUnload(ChunkPos pos, CallbackInfo ci) {
        WorldChunk loaded = ((ClientChunkManager) (Object) this)
                .getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        PortalRemoteChunkCache.adopt(world, loaded);
    }

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"), cancellable = true)
    private void monvhua$loadRemotePortalChunk(int chunkX, int chunkZ,
                                               PacketByteBuf sectionsData,
                                               Map<Heightmap.Type, long[]> heightmaps,
                                               Consumer<ChunkData.BlockEntityVisitor> blockEntityVisitor,
                                               CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk loaded = cir.getReturnValue();
        if (loaded != null) {
            PortalRemoteChunkCache.adopt(world, loaded);
            return;
        }
        WorldChunk remote = PortalRemoteChunkCache.load(
                world,
                chunkX,
                chunkZ,
                sectionsData,
                heightmaps,
                blockEntityVisitor
        );
        if (remote != null) {
            cir.setReturnValue(remote);
        }
    }

    @Inject(method = "getActiveSections", at = @At("RETURN"))
    private void monvhua$appendRemotePortalSections(CallbackInfoReturnable<LongOpenHashSet> cir) {
        if (PortalFramebufferRenderer.isRenderingPortalView()) {
            PortalRemoteChunkCache.appendActiveSections(world, cir.getReturnValue());
        }
    }

    @Inject(method = "getLoadedChunkCount", at = @At("RETURN"), cancellable = true)
    private void monvhua$countRemotePortalChunks(CallbackInfoReturnable<Integer> cir) {
        if (PortalFramebufferRenderer.isRenderingPortalView()) {
            cir.setReturnValue(cir.getReturnValue() + PortalRemoteChunkCache.size(world));
        }
    }
}
