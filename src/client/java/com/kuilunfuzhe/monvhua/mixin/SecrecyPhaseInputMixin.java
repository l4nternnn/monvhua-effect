package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.secrecy.SecrecyClientAudioManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SecrecyPhaseInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void monvhua$lockSecrecyPhaseInput(CallbackInfo ci) {
        if (!SecrecyClientAudioManager.isPhaseLocked()) {
            return;
        }
        this.playerInput = new PlayerInput(
                this.playerInput.forward(),
                this.playerInput.backward(),
                false,
                false,
                false,
                this.playerInput.sneak(),
                this.playerInput.sprint()
        );
        this.movementVector = new Vec2f(0.0F, this.movementVector.y);
    }
}
