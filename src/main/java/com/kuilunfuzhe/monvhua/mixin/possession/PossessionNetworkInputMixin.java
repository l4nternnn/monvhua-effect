package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class PossessionNetworkInputMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedEntityInteract(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedBlockInteract(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedItemInteract(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    @Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        cancelPossessedInput(ci);
    }

    private void cancelPossessedInput(CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }
}
