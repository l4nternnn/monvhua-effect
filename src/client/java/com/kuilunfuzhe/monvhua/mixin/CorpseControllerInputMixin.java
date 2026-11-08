package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.physics_preview.CorpseControllerClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MinecraftClient.class)
public abstract class CorpseControllerInputMixin {
    @Shadow private int itemUseCooldown;
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void monvhua$corpseAttack(CallbackInfoReturnable<Boolean> cir){if(CorpseControllerClient.attack())cir.setReturnValue(false);}
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void monvhua$corpseUse(CallbackInfo ci){if(CorpseControllerClient.use()){itemUseCooldown=4;ci.cancel();}}
}
