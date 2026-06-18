package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceServerManager;
import com.kuilunfuzhe.monvhua.role.RoleInfo;
import com.kuilunfuzhe.monvhua.role.RoleRegistry;
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

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "handleDecoratedMessage", at = @At("HEAD"), cancellable = true)
    private void onHandleDecoratedMessage(SignedMessage message, CallbackInfo ci) {
        String plainMessage = message.getContent().getString();
        Text senderName = getSenderName(player);
        Text normalMessage = Text.empty()
                .append(senderName)
                .append(Text.literal("\n"))
                .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                .append(Text.literal(plainMessage));

        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            if (SilenceServerManager.isPlayerSilenced(p.getUuid())) {
                String garbled = garbleText(plainMessage);
                Text silencedMessage = Text.empty()
                        .append(senderName)
                        .append(Text.literal("\n"))
                        .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                        .append(Text.literal(garbled).formatted(Formatting.RED));
                p.sendMessage(silencedMessage, false);
            } else {
                p.sendMessage(normalMessage, false);
            }
        }
        ci.cancel();
    }

    private Text getSenderName(ServerPlayerEntity player) {
        if (ImitateManager.isImitating(player)) {
            String roleName = ImitateManager.getImitateName(player);
            if (roleName != null) {
                return ImitateManager.getColoredRoleName(roleName);
            }
        }
        return getRoleNameByTag(player);
    }

    private Text getRoleNameByTag(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            RoleInfo info = RoleRegistry.getRoleInfoByTag(tag);
            if (info != null) {
                return Text.literal("◆ " + info.name()).withColor(info.color());
            }
        }
        return player.getDisplayName();
    }

    private String garbleText(String originalText) {
        StringBuilder garbled = new StringBuilder();
        for (char c : originalText.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                if (Character.isWhitespace(c)) {
                    garbled.append(c);
                } else if (Character.isLetter(c)) {
                    garbled.append((char) ('A' + (int) (Math.random() * 26)));
                } else {
                    garbled.append((char) ('0' + (int) (Math.random() * 10)));
                }
            } else {
                garbled.append(c);
            }
        }
        return garbled.toString();
    }
}
