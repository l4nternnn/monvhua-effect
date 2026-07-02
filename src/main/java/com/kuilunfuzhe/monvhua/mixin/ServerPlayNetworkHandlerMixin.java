package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

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
