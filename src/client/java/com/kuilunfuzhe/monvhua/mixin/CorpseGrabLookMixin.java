package com.kuilunfuzhe.monvhua.mixin;
import com.kuilunfuzhe.monvhua.features.physics_preview.CorpseGrabClient;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Entity.class,priority=1200)
public abstract class CorpseGrabLookMixin {
    @Inject(method="changeLookDirection",at=@At("HEAD"),cancellable=true)
    private void grabLook(double x,double y,CallbackInfo ci){if((Object)this==MinecraftClient.getInstance().player&&CorpseGrabClient.look(x,y))ci.cancel();}
}
