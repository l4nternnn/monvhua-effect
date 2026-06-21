package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAxiomIntegration;
import com.moulberry.axiom.UserAction;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.UserAction", remap = false)
public class AxiomUserActionDeleteModeMixin {
    @Inject(
            method = "call(Ljava/lang/Object;)Lcom/moulberry/axiom/UserAction$ActionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    @Dynamic("Optional Axiom compatibility target. Intercepts Delete before any Axiom tool can delete blocks.")
    private void monvhua$deleteAreaTipsOnly(Object argument, CallbackInfoReturnable<UserAction.ActionResult> cir) {
        if ((Object) this == UserAction.DELETE && AreaTipAxiomIntegration.handleDeleteModeDelete()) {
            cir.setReturnValue(UserAction.ActionResult.USED_STOP);
        }
    }
}
