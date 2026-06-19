package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceServerManager;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "handleDecoratedMessage", at = @At("HEAD"), cancellable = true)
    private void onHandleDecoratedMessage(SignedMessage message, CallbackInfo ci) {

        String plainMessage = message.getContent().getString();

        Text normalMessage;
        if (ImitateManager.isImitating(player)) {
            String roleName = ImitateManager.getImitateName(player);
            if (roleName != null) {
                Text coloredName = ImitateManager.getColoredRoleName(roleName);
                normalMessage = Text.empty()
                        .append(coloredName)
                        .append(Text.literal("\n§7 └─ §f" + plainMessage));
            } else {
                normalMessage = Text.empty()
                        .append(player.getName())
                        .append(Text.literal("\n§7 └─ §f" + plainMessage));
            }
        } else {
            normalMessage = Text.empty()
                    .append(player.getName())
                    .append(Text.literal("\n§7 └─ §f" + plainMessage));
        }

        String garbled = garbleText(plainMessage);
        Text garbledText = Text.literal(garbled)
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);
        Text silenceMessage = Text.empty()
                .append(Text.literal("消息:").formatted(Formatting.GRAY))
                .append(garbledText);

        for (ServerPlayerEntity recipient : player.getServer().getPlayerManager().getPlayerList()) {
            if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
                recipient.sendMessage(silenceMessage, false);
            } else {
                recipient.sendMessage(normalMessage, false);
            }
        }

        ci.cancel();
    }

    private String garbleText(String text) {
        StringBuilder garbled = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                garbled.append(c);
            } else if (Character.isLetter(c)) {
                garbled.append((char) ('A' + (int) (Math.random() * 26)));
            } else if (Character.isDigit(c)) {
                garbled.append((char) ('0' + (int) (Math.random() * 10)));
            } else {
                garbled.append(c);
            }
        }
        return garbled.toString();
    }
}
