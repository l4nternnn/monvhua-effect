package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.client.imitate.SilenceClientManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private Text modifyMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            if (SilenceClientManager.isPlayerSilenced(client.player.getUuid())) {
                String originalText = message.getString();
                String garbledText = SilenceClientManager.garbleText(originalText);
                return Text.literal(garbledText);
            }
        }
        return message;
    }
}