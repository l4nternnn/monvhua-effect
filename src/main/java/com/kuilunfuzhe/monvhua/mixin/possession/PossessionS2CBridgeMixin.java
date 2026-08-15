package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
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
                || !isPossessionViewPacket(packet)
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

    private static boolean isPossessionViewPacket(Packet<?> packet) {
        return packet instanceof CustomPayloadS2CPacket
                || packet instanceof ChunkDataS2CPacket
                || packet instanceof BlockUpdateS2CPacket
                || packet instanceof ChunkDeltaUpdateS2CPacket
                || packet instanceof BlockEntityUpdateS2CPacket
                || packet instanceof LightUpdateS2CPacket
                || packet instanceof EntitySpawnS2CPacket
                || packet instanceof EntitiesDestroyS2CPacket
                || packet instanceof EntityPositionS2CPacket
                || packet instanceof EntityPositionSyncS2CPacket
                || packet instanceof EntityVelocityUpdateS2CPacket
                || packet instanceof EntityTrackerUpdateS2CPacket
                || packet instanceof EntityAnimationS2CPacket
                || packet instanceof EntityDamageS2CPacket
                || packet instanceof EntityStatusS2CPacket
                || packet instanceof EntityStatusEffectS2CPacket
                || packet instanceof EntityEquipmentUpdateS2CPacket;
    }
}
