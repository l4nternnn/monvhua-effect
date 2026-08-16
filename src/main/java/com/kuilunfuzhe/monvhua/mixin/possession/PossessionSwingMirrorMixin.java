package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import com.kuilunfuzhe.monvhua.features.possession.PossessionPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class PossessionSwingMirrorMixin {
    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("TAIL"))
    private void monvhua$mirrorPossessedSwing(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity target)) {
            return;
        }
        ServerPlayerEntity controller = PossessionManager.getController(target);
        if (controller != null) {
            ServerPlayNetworking.send(controller, new PossessionPackets.SwingS2C(hand));
        }
    }
}
