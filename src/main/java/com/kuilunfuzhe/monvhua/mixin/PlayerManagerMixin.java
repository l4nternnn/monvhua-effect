package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceServerManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @WrapOperation(
            method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V"
            )
    )
    private void monvhua$handleImitateChatRecipient(
            ServerPlayerEntity recipient,
            SentMessage message,
            boolean filterMaskEnabled,
            MessageType.Parameters parameters,
            Operation<Void> original,
            SignedMessage signedMessage,
            Predicate<ServerPlayerEntity> shouldSendFiltered,
            ServerPlayerEntity sender,
            MessageType.Parameters originalParameters
    ) {
        if (!monvhua$isInImitateChatRange(recipient, sender)) {
            return;
        }

        SentMessage recipientMessage = message;
        if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
            recipientMessage = SentMessage.of(signedMessage.withUnsignedContent(monvhua$createSilenceContent(signedMessage, sender)));
        }

        original.call(recipient, recipientMessage, filterMaskEnabled, parameters);
    }

    private boolean monvhua$isInImitateChatRange(ServerPlayerEntity recipient, ServerPlayerEntity sender) {
        if (sender == null || !ImitateManager.isImitating(sender)) {
            return true;
        }
        if (recipient.equals(sender)) {
            return true;
        }

        Vec3d center;
        double radius;
        if (ImitateManager.hasAreaImitate(sender)) {
            ImitateManager.AreaInfo areaInfo = ImitateManager.getAreaInfo(sender);
            center = new Vec3d(areaInfo.centerX, areaInfo.centerY, areaInfo.centerZ);
            radius = areaInfo.radius;
        } else {
            center = sender.getPos();
            int stage = Math.min(WitchStage.fromScore(ImitateManager.getWitchScore(sender)).ordinal() + 1, 7);
            ImitateConfig config = ImitateConfig.getInstance();
            radius = config != null ? config.getImitateRadius(stage) : 5.0;
        }

        return recipient.getPos().distanceTo(center) <= radius;
    }

    private Text monvhua$createSilenceContent(SignedMessage signedMessage, ServerPlayerEntity sender) {
        Text garbledText = Text.literal(monvhua$garbleText(signedMessage.getSignedContent()))
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);

        if (sender != null && ImitateManager.isImitating(sender)) {
            return Text.empty()
                    .append(Text.literal("\n"))
                    .append(Text.literal(" └─BB ").formatted(Formatting.GRAY))
                    .append(garbledText);
        }

        return garbledText;
    }

    private String monvhua$garbleText(String text) {
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
