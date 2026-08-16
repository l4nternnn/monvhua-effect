package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import com.kuilunfuzhe.monvhua.features.possession.PossessionPackets;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class PossessionInputRedirectMixin {
    private static boolean monvhua$wasBreaking;

    @Shadow
    private int itemUseCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!PossessionClient.isActive()) {
            return;
        }
        SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.ATTACK));
        cir.setReturnValue(true);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionBreaking(boolean breaking, CallbackInfo ci) {
        if (!PossessionClient.isActive()) {
            monvhua$wasBreaking = false;
            return;
        }
        if (breaking) {
            SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.BREAKING));
            MinecraftClient client = (MinecraftClient) (Object) this;
            if (client.player != null && !client.player.isUsingItem()) {
                client.player.swingHand(Hand.MAIN_HAND, true);
            }
        } else if (monvhua$wasBreaking) {
            SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.BREAK_ABORT));
        }
        monvhua$wasBreaking = breaking;
        ci.cancel();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (!PossessionClient.isActive()) {
            return;
        }
        if (client.options.useKey.isPressed() && PossessionClient.beginCancelGesture(client)) {
            ci.cancel();
            return;
        }
        if (itemUseCooldown > 0) {
            ci.cancel();
            return;
        }
        itemUseCooldown = 4;
        SafeClientNetworking.send(new PossessionPackets.ActionC2S(PossessionPackets.ActionC2S.USE));
        ci.cancel();
    }
}
