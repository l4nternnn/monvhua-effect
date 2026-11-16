package com.kuilunfuzhe.monvhua.mixin.compat;

import com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.example.dying.DyingManager", remap=false)
public abstract class KomDyingManagerMixin {
    @Inject(method="startDyingState", at=@At("RETURN"), require=0)
    private static void monvhua$downed(@Coerce Object victim, @Coerce Object killer, @Coerce Object server, CallbackInfo ci) {
        if(victim instanceof ServerPlayerEntity p) KomDyingCompat.enter(p);
    }
    @Inject(method="startPostCancelCountdown", at=@At("RETURN"), require=0)
    private static void monvhua$countdown(@Coerce Object victim, @Coerce Object state, CallbackInfo ci) {
        if(victim instanceof ServerPlayerEntity p) KomDyingCompat.countdown(p);
    }
    @Inject(method={"finishDyingByRescue", "clearDyingStateForDebug"}, at=@At("RETURN"), require=0)
    private static void monvhua$recovered(@Coerce Object victim, CallbackInfo ci) {
        if(victim instanceof ServerPlayerEntity p) KomDyingCompat.recovered(p);
    }
}
