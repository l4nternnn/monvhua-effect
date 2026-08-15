package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import com.kuilunfuzhe.monvhua.features.possession.PossessionHotbarHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class PossessionHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void monvhua$renderPossessedHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!PossessionClient.isActive()) {
            return;
        }
        PossessionHotbarHud.render(context);
        ci.cancel();
    }
}
