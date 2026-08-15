package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors target-owned mod state to the controller's client. */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class PossessionS2CBridgeMixin {
    private static final ThreadLocal<Boolean> FORWARDING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void monvhua$mirrorPossessedTargetPayload(Packet<?> packet, CallbackInfo ci) {
        if (Boolean.TRUE.equals(FORWARDING.get())
                || !(packet instanceof CustomPayloadS2CPacket)
                || !((Object) this instanceof ServerPlayNetworkHandler handler)) {
            return;
        }

        ServerPlayerEntity target = handler.player;
        ServerPlayerEntity controller = PossessionManager.getController(target);
        if (controller == null || controller == target) {
            return;
        }

        FORWARDING.set(true);
        try {
            controller.networkHandler.sendPacket(packet);
        } finally {
            FORWARDING.set(false);
        }
    }
}
