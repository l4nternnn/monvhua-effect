package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import com.moulberry.axiom.render.ChunkRenderOverrider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkRenderOverrider.class, remap = false)
public abstract class AxiomPortalChunkOverrideGuardMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void monvhua$skipPortalOverrideRender(CallbackInfo ci) {
        cancelDuringPortalRender(ci);
    }

    @Inject(method = "uploadDirty", at = @At("HEAD"), cancellable = true)
    private static void monvhua$skipPortalOverrideUpload(CallbackInfo ci) {
        cancelDuringPortalRender(ci);
    }

    @Inject(method = "afterRenderLevel", at = @At("HEAD"), cancellable = true)
    private static void monvhua$skipPortalOverrideCleanup(CallbackInfo ci) {
        cancelDuringPortalRender(ci);
    }

    private static void cancelDuringPortalRender(CallbackInfo ci) {
        if (PortalFramebufferOverride.get() != null) {
            ci.cancel();
        }
    }
}
