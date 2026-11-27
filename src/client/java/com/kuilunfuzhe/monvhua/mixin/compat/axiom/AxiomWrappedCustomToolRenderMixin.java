package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipAxiomIntegration;
import com.kuilunfuzhe.monvhua.features.area_tip.AxiomSelectionRenderBridge;
import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.moulberry.axiom.clipboard.Selection;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import com.moulberry.axiomclientapi.CustomTool;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.services.ToolRegistryService$WrappedCustomTool", remap = false)
public abstract class AxiomWrappedCustomToolRenderMixin {
    @Unique private static AxiomSelectionRenderBridge monvhua$selectionRenderer;
    @Unique private static boolean monvhua$selectionRenderDisabled;
    @Shadow(remap = false)
    public abstract CustomTool customTool();

    @Inject(
            method = "render(Lcom/moulberry/axiom/render/AxiomWorldRenderContext;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    @Dynamic("Optional Axiom compatibility target. Mirrors built-in tools that call Selection.render from Tool.render.")
    private void monvhua$renderAreaTipSelection(AxiomWorldRenderContext context, CallbackInfo ci) {
        CustomTool tool = customTool();
        if (tool != null && AreaTipAxiomIntegration.AXIOM_TOOL_NAME.equals(tool.name())) {
            if (monvhua$selectionRenderDisabled) return;
            try {
                if (monvhua$selectionRenderer == null) {
                    monvhua$selectionRenderer = new AxiomSelectionRenderBridge(Selection.class, AxiomWorldRenderContext.class);
                }
                monvhua$selectionRenderer.render(context);
            } catch (ReflectiveOperationException | LinkageError exception) {
                monvhua$selectionRenderDisabled = true;
                MonvhuaMod.LOGGER.warn("[Monvhua] Axiom selection preview disabled after render failure", exception);
            }
        }
    }
}
