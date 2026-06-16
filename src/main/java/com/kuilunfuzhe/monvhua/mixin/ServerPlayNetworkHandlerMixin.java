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

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    private static final Map<String, RoleInfo> TAG_TO_ROLE = new HashMap<>();

    static {
        TAG_TO_ROLE.put("ema", new RoleInfo("樱羽艾玛", 0xfc8eac));
        TAG_TO_ROLE.put("cero", new RoleInfo("二阶堂希罗", 0x8b0000));
        TAG_TO_ROLE.put("nnk", new RoleInfo("黑部奈叶香", 0x555555));
        TAG_TO_ROLE.put("mago", new RoleInfo("宝生玛格", 0xAA00AA));
        TAG_TO_ROLE.put("leiya", new RoleInfo("莲见蕾雅", 0xFFAA00));
        TAG_TO_ROLE.put("milya", new RoleInfo("佐伯米利亚", 0xFFFF55));
        TAG_TO_ROLE.put("sherry", new RoleInfo("橘雪莉", 0x1e90ff));
        TAG_TO_ROLE.put("yalisa", new RoleInfo("紫藤亚里沙", 0xB1B7AC));
        TAG_TO_ROLE.put("noa", new RoleInfo("城崎诺亚", 0x55FFFF));
        TAG_TO_ROLE.put("anan", new RoleInfo("夏目安安", 0x240090));
        TAG_TO_ROLE.put("yuki", new RoleInfo("月代雪", 0xe0ffff));
        TAG_TO_ROLE.put("mll", new RoleInfo("冰上梅露露", 0xddb6ff));
        TAG_TO_ROLE.put("coco", new RoleInfo("泽渡可可", 0xff6700));
        TAG_TO_ROLE.put("hanna", new RoleInfo("远野汉娜", 0x5f9e3f));
    }

    private static class RoleInfo {
        final String name;
        final int color;

        RoleInfo(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

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
            RoleInfo info = TAG_TO_ROLE.get(tag);
            if (info != null) {
                return Text.literal("◆ " + info.name).withColor(info.color);
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