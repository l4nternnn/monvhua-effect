package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
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
        if (ImitateManager.isImitating(player)) {
            String roleName = ImitateManager.getImitateName(player);
            if (roleName != null) {
                Text coloredName = ImitateManager.getColoredRoleName(roleName);
                String plainMessage = message.getContent().getString();
                Text customMessage = Text.empty()
                        .append(coloredName)
                        .append(Text.literal("\n"))
                        .append(Text.literal(" └─ ").formatted(Formatting.GRAY))
                        .append(Text.literal(plainMessage));
                player.getServer().getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(customMessage, false));
                ci.cancel();
            }
        }
    }
}