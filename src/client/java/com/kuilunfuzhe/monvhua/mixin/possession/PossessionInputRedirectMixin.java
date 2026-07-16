package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class PossessionInputRedirectMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (PossessionClient.isActive()
                && PossessionClient.isHoldingActualPossessionItem(client)
                && client.options.useKey.isPressed()) {
            ci.cancel();
        }
    }
}
