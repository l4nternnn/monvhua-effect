package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.forensics.EvidenceWorldPreview;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World UI consumes use before held-item, entity and block interaction handlers. */
@Mixin(value=MinecraftClient.class,priority=1100)
public abstract class EvidencePanelUseMixin {
    @Shadow private int itemUseCooldown;
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void monvhua$useEvidencePanel(CallbackInfo ci){
        if(EvidenceWorldPreview.usePanel()){itemUseCooldown=4;ci.cancel();}
    }
}
