package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class PaintInputMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void monvhua$consumePaintBrushColorSlots(CallbackInfo ci) {
        PaintOverlayClient.consumeBrushColorSlotKeys((MinecraftClient) (Object) this);
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void monvhua$replacePaintBrushPickBlock(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (!PaintOverlayClient.isBrushInputActive(client)) {
            return;
        }
        PaintOverlayClient.handleBrushPickInput(client);
        ci.cancel();
    }
}
