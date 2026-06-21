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
@Mixin(targets = "com.moulberry.axiom.clipboard.Selection", remap = false)
public class AxiomSelectionDeleteModeMixin {
    @Inject(
            method = "callAction(Lcom/moulberry/axiom/UserAction;Ljava/lang/Object;)Lcom/moulberry/axiom/UserAction$ActionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    @Dynamic("Optional Axiom compatibility target. Stops Axiom block deletion while Monvhua area-tip delete mode is active.")
    private static void monvhua$deleteAreaTipsOnly(UserAction action, Object argument,
                                                   CallbackInfoReturnable<UserAction.ActionResult> cir) {
        if (action == UserAction.DELETE && AreaTipAxiomIntegration.handleDeleteModeDelete()) {
            cir.setReturnValue(UserAction.ActionResult.USED_STOP);
        }
    }
}
