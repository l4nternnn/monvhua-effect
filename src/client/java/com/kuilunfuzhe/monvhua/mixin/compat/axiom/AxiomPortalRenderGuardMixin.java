package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferOverride;
import com.moulberry.axiom.AxiomClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AxiomClient.class, remap = false)
public abstract class AxiomPortalRenderGuardMixin {
    @Inject(method = "isAxiomActive()Z", at = @At("HEAD"), cancellable = true)
    private static void monvhua$disableAxiomDuringPortalRender(CallbackInfoReturnable<Boolean> cir) {
        if (PortalFramebufferOverride.get() != null) {
            cir.setReturnValue(false);
        }
    }
}
