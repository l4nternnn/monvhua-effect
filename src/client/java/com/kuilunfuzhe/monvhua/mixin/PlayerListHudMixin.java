package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.playerlist.PlayerListRestrictClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 混合 PlayerListHud.render()，在玩家列表限制启用时阻止生存/冒险模式玩家看到玩家列表。
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void monvhua$cancelPlayerListRender(CallbackInfo ci) {
        if (!PlayerListRestrictClient.isRestricted()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            return;
        }

        GameMode gameMode = client.interactionManager.getCurrentGameMode();
        if (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) {
            ci.cancel();
        }
    }
}
