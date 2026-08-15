package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public abstract class PossessionEntitySyncMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "sendToNearbyPlayers", at = @At("HEAD"))
    private void monvhua$syncObservedEntity(Entity entity, Packet<?> packet, CallbackInfo ci) {
        PossessionManager.syncObservedEntityPacket(world, entity, packet);
    }

    @Inject(method = "sendToOtherNearbyPlayers", at = @At("HEAD"))
    private void monvhua$syncObservedEntityExceptSource(Entity entity, Packet<?> packet, CallbackInfo ci) {
        PossessionManager.syncObservedEntityPacket(world, entity, packet);
    }
}
