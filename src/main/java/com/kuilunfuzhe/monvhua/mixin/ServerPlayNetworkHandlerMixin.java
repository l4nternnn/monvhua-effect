package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceServerManager;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
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
                        .append(Text.literal("\n"))
                        .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                        .append(Text.literal(plainMessage));
            } else {
                normalMessage = Text.empty()
                        .append(player.getName())
                        .append(Text.literal("\n"))
                        .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                        .append(Text.literal(plainMessage));
            }
        } else {
            normalMessage = Text.empty()
                    .append(player.getName())
                    .append(Text.literal("\n"))
                    .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                    .append(Text.literal(plainMessage));
        }

        String garbled = garbleText(plainMessage);
        Text garbledText = Text.literal(garbled)
                .formatted(Formatting.OBFUSCATED)
                .formatted(Formatting.RED);
        Text silenceMessage = Text.empty()
                .append(player.getName())
                .append(Text.literal("\n"))
                .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                .append(garbledText);

        if (ImitateManager.isImitating(player)) {
            Vec3d center;
            double radius;

            if (ImitateManager.hasAreaImitate(player)) {
                ImitateManager.AreaInfo areaInfo = ImitateManager.getAreaInfo(player);
                center = new Vec3d(areaInfo.centerX, areaInfo.centerY, areaInfo.centerZ);
                radius = areaInfo.radius;
            } else {
                center = player.getPos();
                int stage = WitchStage.fromScore(ImitateManager.getWitchScore(player)).ordinal() + 1;
                ImitateConfig config = ImitateConfig.getInstance();
                radius = config != null ? config.getImitateRadius(stage) : 5.0;
            }

            for (ServerPlayerEntity recipient : Objects.requireNonNull(player.getServer()).getPlayerManager().getPlayerList()) {
                if (recipient.equals(player)) {
                    if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
                        recipient.sendMessage(silenceMessage, false);
                    } else {
                        recipient.sendMessage(normalMessage, false);
                    }
                    continue;
                }

                double distance = recipient.getPos().distanceTo(center);
                if (distance <= radius) {
                    if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
                        recipient.sendMessage(silenceMessage, false);
                    } else {
                        recipient.sendMessage(normalMessage, false);
                    }
                }
            }
        } else {
            for (ServerPlayerEntity recipient : Objects.requireNonNull(player.getServer()).getPlayerManager().getPlayerList()) {
                if (SilenceServerManager.isPlayerSilenced(recipient.getUuid())) {
                    recipient.sendMessage(silenceMessage, false);
                } else {
                    recipient.sendMessage(normalMessage, false);
                }
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
