package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps command-panel right-click callbacks while skipping vanilla hand swing transforms. */
@Mixin(MinecraftClient.class)
public abstract class CommandPanelUseMixin {
    @Shadow private int itemUseCooldown;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void monvhua$useCommandPanelWithoutSwing(CallbackInfo callbackInfo) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null || client.interactionManager == null
                || !client.player.getMainHandStack().isOf(CommandPanelItems.COMMAND_PANEL)) {
            return;
        }

        ActionResult result = client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            itemUseCooldown = 4;
            callbackInfo.cancel();
        }
    }
}
