package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
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

@Mixin(value = PlayerManager.class, priority = 100)
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
        String roleName = monvhua$getVisibleRoleName(recipient, sender);
        boolean imitateStyled = roleName != null;

        SentMessage recipientMessage = message;
        MessageType.Parameters recipientParameters = monvhua$decorateImitateName(parameters, roleName);
        if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
            recipientMessage = SentMessage.of(signedMessage.withUnsignedContent(monvhua$createSilenceContent(message.content(), imitateStyled)));
        } else if (imitateStyled) {
            recipientMessage = SentMessage.of(signedMessage.withUnsignedContent(monvhua$createImitateContent(message.content())));
        }

        original.call(recipient, recipientMessage, filterMaskEnabled, recipientParameters);
    }

    private MessageType.Parameters monvhua$decorateImitateName(MessageType.Parameters parameters, String roleName) {
        if (roleName == null) {
            return parameters;
        }

        return new MessageType.Parameters(parameters.type(), ImitateManager.getColoredRoleName(roleName), parameters.targetName());
    }

    private String monvhua$getVisibleRoleName(ServerPlayerEntity recipient, ServerPlayerEntity sender) {
        if (sender == null) {
            return null;
        }

        if (monvhua$isAreaImitateVisibleTo(recipient, sender)) {
            return ImitateManager.getAreaImitateName(sender);
        }

        return ImitateManager.getImitateName(sender);
    }

    private boolean monvhua$isAreaImitateVisibleTo(ServerPlayerEntity recipient, ServerPlayerEntity sender) {
        if (!ImitateManager.hasAreaImitate(sender)) {
            return false;
        }

        if (recipient.equals(sender)) {
            return true;
        }

        ImitateManager.AreaInfo areaInfo = ImitateManager.getAreaInfo(sender);
        Vec3d center = new Vec3d(areaInfo.centerX, areaInfo.centerY, areaInfo.centerZ);
        double radius = areaInfo.radius;
        return recipient.getPos().distanceTo(center) <= radius;
    }

    private Text monvhua$createImitateContent(Text content) {
        return Text.empty()
                .append(Text.literal("\n"))
                .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                .append(content);
    }

    private Text monvhua$createSilenceContent(Text content, boolean imitateStyled) {
        Text garbledText = Text.literal(monvhua$garbleText(content.getString()))
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);

        if (imitateStyled) {
            return monvhua$createImitateContent(garbledText);
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
