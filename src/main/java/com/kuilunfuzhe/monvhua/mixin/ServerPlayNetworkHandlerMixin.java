package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedEntityInteract(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedBlockInteract(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockPossessedItemInteract(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (PossessionManager.isTarget(player) || PossessionManager.isController(player)) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "handleDecoratedMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V"
            ),
            index = 0
    )
    private SignedMessage monvhua$decorateImitateMessage(SignedMessage message) {
        if (!ImitateManager.isImitating(player)) {
            return message;
        }

        Text content = Text.empty()
                .append(Text.literal("\n"))
                .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                .append(Text.literal(message.getSignedContent()));
        return message.withUnsignedContent(content);
    }

    @ModifyArg(
            method = "handleDecoratedMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V"
            ),
            index = 2
    )
    private MessageType.Parameters monvhua$decorateImitateName(MessageType.Parameters parameters) {
        if (!ImitateManager.isImitating(player)) {
            return parameters;
        }

        String roleName = ImitateManager.getImitateName(player);
        if (roleName == null) {
            return parameters;
        }

        return new MessageType.Parameters(parameters.type(), ImitateManager.getColoredRoleName(roleName), parameters.targetName());
    }
}
