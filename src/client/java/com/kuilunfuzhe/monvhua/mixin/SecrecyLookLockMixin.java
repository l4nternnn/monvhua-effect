package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.secrecy.SecrecyClientAudioManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SecrecyLookLockMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void monvhua$cancelLookWhenSecrecyPhaseLocked(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == (Entity) (Object) this && SecrecyClientAudioManager.isPhaseLocked()) {
            ci.cancel();
        }
    }
}
