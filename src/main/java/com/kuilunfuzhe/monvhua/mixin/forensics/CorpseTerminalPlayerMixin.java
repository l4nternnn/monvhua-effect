package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseDeathFallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class CorpseTerminalPlayerMixin {
    @Inject(method="changeGameMode",at=@At("RETURN"))
    private void monvhua$terminalSpectator(GameMode mode,CallbackInfoReturnable<Boolean> cir){
        if(mode==GameMode.SPECTATOR&&cir.getReturnValueZ())
            CorpseDeathFallback.departure((ServerPlayerEntity)(Object)this,"spectator");
    }
}
