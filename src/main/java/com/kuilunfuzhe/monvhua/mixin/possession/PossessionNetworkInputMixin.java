package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import com.kuilunfuzhe.monvhua.features.possession.PossessionPackets;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
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
        cancelPossessedMovement(ci);
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        cancelPossessedMovement(ci);
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        redirectOrCancel(ci, handler -> handler.onUpdateSelectedSlot(packet));
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        redirectOrCancel(ci, handler -> handler.onPlayerAction(packet));
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedEntityInteract(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        redirectOrCancel(ci, handler -> handler.onPlayerInteractEntity(packet));
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedBlockInteract(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        redirectOrCancel(ci, handler -> handler.onPlayerInteractBlock(packet));
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedItemInteract(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        redirectOrCancel(ci, handler -> handler.onPlayerInteractItem(packet));
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessedCustomPayload(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        if (!isPossessionPayload(packet)) {
            redirectOrCancel(ci, handler -> handler.onCustomPayload(packet));
        }
    }

    private void cancelPossessedMovement(CallbackInfo ci) {
        if (!PossessionManager.isReplayingNetworkPacket()
                && (PossessionManager.isTarget(player) || PossessionManager.isController(player))) {
            ci.cancel();
        }
    }

    private void redirectOrCancel(CallbackInfo ci, java.util.function.Consumer<ServerPlayNetworkHandler> replay) {
        if (PossessionManager.isReplayingNetworkPacket()) {
            return;
        }
        if (PossessionManager.replayControllerPacket(player, replay) || PossessionManager.isTarget(player)) {
            ci.cancel();
        }
    }

    private boolean isPossessionPayload(CustomPayloadC2SPacket packet) {
        CustomPayload payload = packet.payload();
        CustomPayload.Id<? extends CustomPayload> id = payload.getId();
        return id.equals(PossessionPackets.StopC2S.ID)
                || id.equals(PossessionPackets.InputC2S.ID)
                || id.equals(PossessionPackets.ActionC2S.ID);
    }
}
