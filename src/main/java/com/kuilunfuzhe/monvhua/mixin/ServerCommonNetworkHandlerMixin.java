package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceServerManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(value = ServerCommonNetworkHandler.class, priority = 100)
public abstract class ServerCommonNetworkHandlerMixin {

    @ModifyVariable(method = "sendPacket", at = @At("HEAD"), argsOnly = true)
    private Packet<?> monvhua$rewriteChatLimitGameMessage(Packet<?> packet) {
        if (!(packet instanceof GameMessageS2CPacket gameMessage) || gameMessage.overlay()) {
            return packet;
        }
        if (!((Object) this instanceof ServerPlayNetworkHandler networkHandler)) {
            return packet;
        }

        ServerPlayerEntity recipient = networkHandler.player;
        ServerPlayerEntity sender = monvhua$findChatLimitSender(recipient, gameMessage.content());
        if (sender == null) {
            return packet;
        }

        Text messageContent = monvhua$extractChatLimitMessageContent(gameMessage.content());
        if (messageContent == null) {
            return packet;
        }

        String roleName = monvhua$getVisibleRoleName(recipient, sender);
        boolean imitateStyled = roleName != null;
        boolean silenced = SilenceServerManager.isPlayerSilenced(recipient.getUuid());
        if (!imitateStyled && !silenced) {
            return packet;
        }

        Text visibleName = imitateStyled ? ImitateManager.getColoredRoleName(roleName) : sender.getDisplayName();
        Text visibleContent = silenced ? monvhua$createGarbledText(messageContent.getString()) : messageContent;
        return new GameMessageS2CPacket(monvhua$createChatContent(visibleName, visibleContent), gameMessage.overlay());
    }

    private ServerPlayerEntity monvhua$findChatLimitSender(ServerPlayerEntity recipient, Text content) {
        List<Text> siblings = content.getSiblings();
        if (siblings.isEmpty()) {
            return null;
        }

        String visibleName = siblings.get(0).getString();
        if (visibleName.isEmpty() || !content.getString().contains("\n")) {
            return null;
        }

        for (ServerPlayerEntity player : recipient.getServer().getPlayerManager().getPlayerList()) {
            if (visibleName.equals(player.getDisplayName().getString()) || visibleName.equals(player.getName().getString())) {
                return player;
            }
        }
        return null;
    }

    private Text monvhua$extractChatLimitMessageContent(Text content) {
        List<Text> siblings = content.getSiblings();
        if (siblings.size() >= 4) {
            Text result = Text.empty();
            for (int i = 3; i < siblings.size(); i++) {
                result = result.copy().append(siblings.get(i));
            }
            return result;
        }

        String string = content.getString();
        int newline = string.indexOf('\n');
        if (newline < 0 || newline + 1 >= string.length()) {
            return null;
        }

        String line = string.substring(newline + 1).trim();
        int split = line.indexOf(' ');
        if (split >= 0 && split + 1 < line.length()) {
            return Text.literal(line.substring(split + 1));
        }
        return Text.literal(line);
    }

    private String monvhua$getVisibleRoleName(ServerPlayerEntity recipient, ServerPlayerEntity sender) {
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
        return recipient.getPos().distanceTo(center) <= areaInfo.radius;
    }

    private Text monvhua$createChatContent(Text visibleName, Text visibleContent) {
        return Text.empty()
                .append(visibleName)
                .append(Text.literal("\n"))
                .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                .append(visibleContent);
    }

    private Text monvhua$createGarbledText(String text) {
        return Text.literal(monvhua$garbleText(text))
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);
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
