package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PossessionSelectedSlotMixin {
    @Shadow @Final private PlayerEntity player;
    @Shadow private int selectedSlot;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionSelectedSlot(int slot, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        int lockedSlot = PossessionClient.getLockedWandSlot();
        if (!PossessionClient.isActive() || player != client.player || lockedSlot < 0) {
            return;
        }

        if (slot != lockedSlot) {
            PossessionClient.requestTargetSlot(slot);
        }
        selectedSlot = lockedSlot;
        ci.cancel();
    }
}
