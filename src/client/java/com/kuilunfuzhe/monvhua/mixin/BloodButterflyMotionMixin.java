package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional dependency. Both named development and intermediary production tick names are supported. */
@Pseudo
@Mixin(targets="com.example.manosaba_ice_expand.client.particleAnimation.BloodButterflyAnimation",remap=false)
public abstract class BloodButterflyMotionMixin implements com.kuilunfuzhe.monvhua.features.forensics.butterfly.ControlledButterfly {
    @Inject(method={"tick()V","method_3070()V"},at=@At("TAIL"),require=0,remap=false)
    private void monvhua$controlledFlight(CallbackInfo ci){
        com.kuilunfuzhe.monvhua.features.forensics.butterfly.BloodButterflyClient.afterNativeTick((Particle)(Object)this);
    }
}
