package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class InvertedClientPlayerTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void monvhua$runClientInvertedTravel(Vec3d movementInput, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (client.player != entity) {
            return;
        }

        if (GravityMagic.tickInvertedPlayer(entity, client.player.input.playerInput)) {
            ci.cancel();
        }
    }
}
