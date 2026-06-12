package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.through.ThroughClientManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class ThroughPhaseInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void monvhua$lockSecrecyPhaseInput(CallbackInfo ci) {
        if (!ThroughClientManager.isPhaseLocked()) {
            return;
        }
        boolean phaseStalled = ThroughClientManager.isPhaseStalled();
        this.playerInput = new PlayerInput(
                !phaseStalled && this.playerInput.forward(),
                !phaseStalled && this.playerInput.backward(),
                false,
                false,
                false,
                !phaseStalled && this.playerInput.sneak(),
                !phaseStalled && this.playerInput.sprint()
        );
        this.movementVector = phaseStalled ? Vec2f.ZERO : new Vec2f(0.0F, this.movementVector.y);
    }
}
