package com.kuilunfuzhe.monvhua.mixin;
import com.kuilunfuzhe.monvhua.features.physics_preview.CorpseGrabClient;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Keyboard.class,priority=1100)
public abstract class CorpseGrabKeyboardMixin {
    @Inject(method="onKey",at=@At("HEAD"),cancellable=true)
    private void grabKey(long window,int key,int scan,int action,int mods,CallbackInfo ci){if(CorpseGrabClient.key(key,scan,action))ci.cancel();}
}
