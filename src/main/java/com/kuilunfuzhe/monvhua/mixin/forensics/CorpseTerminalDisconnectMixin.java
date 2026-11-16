package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseDeathFallback;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture before disconnect listeners remove kom_dying state, proxies or player data. */
@Mixin(value=ServerPlayNetworkHandler.class,priority=2000)
public abstract class CorpseTerminalDisconnectMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onDisconnected",at=@At("HEAD"))
    private void monvhua$terminalDisconnect(DisconnectionInfo info,CallbackInfo ci){CorpseDeathFallback.departure(player,"disconnect");}
}
