package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class PossessionCrosshairTargetMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("HEAD"))
    private void monvhua$preparePossessionView(float tickProgress, CallbackInfo ci) {
        PossessionClient.prepareRenderView(client);
    }
}
