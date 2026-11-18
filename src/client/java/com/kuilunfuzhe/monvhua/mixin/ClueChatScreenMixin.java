package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.forensics.ClueChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep the evidence host visible through all vanilla chat close paths. */
@Mixin(MinecraftClient.class)
public abstract class ClueChatScreenMixin {
    @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
    private Screen monvhua$returnFromClueChat(Screen next){return ClueChat.destination(next);}
}
