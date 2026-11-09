package com.kuilunfuzhe.monvhua.mixin;
import com.kuilunfuzhe.monvhua.features.physics_preview.CorpseGrabClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Mouse.class,priority=1100)
public abstract class CorpseGrabMouseMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void grabButton(long window,int button,int action,int mods,CallbackInfo ci){if(CorpseGrabClient.button(button,action))ci.cancel();}
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void grabScroll(long window,double horizontal,double vertical,CallbackInfo ci){if(CorpseGrabClient.scroll(vertical))ci.cancel();}
}
