package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferRenderer;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkBuilder.class, remap = false)
public abstract class SodiumChunkBuilderThreadLimitMixin {
    @Inject(method = "getThreadCount", at = @At("HEAD"), cancellable = true)
    private static void monvhua$limitPortalBuilderThreads(CallbackInfoReturnable<Integer> cir) {
        if (PortalFramebufferRenderer.isCreatingRemoteRenderer()) {
            cir.setReturnValue(PortalViewConfig.REMOTE_RENDER_THREADS);
        }
    }
}
