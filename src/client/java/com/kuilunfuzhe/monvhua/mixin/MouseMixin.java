package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.client.imitate.AreaSelectClientManager;
import com.kuilunfuzhe.monvhua.features.gravity.GravityClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == 1 && GravityClient.onMouseClicked(button, mods)) {
            ci.cancel();
            return;
        }
        if (action == 1 && AreaSelectClientManager.onMouseClicked(button)) {
            ci.cancel();
        }
    }
}
