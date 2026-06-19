package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "eu.pb4.styledchat.StyledChatStyles")
public abstract class StyledChatStylesMixin {

    @Dynamic("Styled Chat is an optional compatibility target and is not present on the compile classpath.")
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private static void onGetDisplayName(ServerPlayerEntity player, Text vanillaDisplayName, CallbackInfoReturnable<Text> cir) {
        if (ImitateManager.isImitating(player)) {
            Text coloredName = ImitateManager.getFormattedName(player);
            if (coloredName != null) {
                cir.setReturnValue(coloredName);
            }
        }
    }
}
