package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class PaintMouseMixin {
    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void monvhua$restorePaintEditorAfterViewControl(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE && PaintOverlayClient.isEditorViewPassthrough()) {
            PaintOverlayClient.endEditorViewPassthrough();
        }
    }
}
