package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.client.imitate.SilenceClientManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlay(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null) {
            return;
        }

        if (SilenceClientManager.isPlayerSilenced(client.player.getUuid())) {
            cir.cancel();
        }
    }
}