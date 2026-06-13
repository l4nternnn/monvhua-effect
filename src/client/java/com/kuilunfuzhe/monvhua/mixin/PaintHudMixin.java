package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public abstract class PaintHudMixin {
    @Inject(method = "shouldRenderCrosshair", at = @At("HEAD"), cancellable = true)
    private void monvhua$hidePaintEditorCrosshair(CallbackInfoReturnable<Boolean> cir) {
        if (PaintOverlayClient.shouldSuppressVanillaCrosshair(MinecraftClient.getInstance())) {
            cir.setReturnValue(false);
        }
    }
}
