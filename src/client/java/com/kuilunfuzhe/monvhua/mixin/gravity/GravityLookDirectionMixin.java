package com.kuilunfuzhe.monvhua.mixin.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class GravityLookDirectionMixin {
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double monvhua$invertInvertedAreaYaw(double cursorDeltaX) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (client.player == entity && GravityMagic.isInInvertedArea(entity)) {
            return -cursorDeltaX;
        }
        return cursorDeltaX;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double monvhua$invertInvertedAreaPitch(double cursorDeltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (client.player == entity && GravityMagic.isInInvertedArea(entity)) {
            return -cursorDeltaY;
        }
        return cursorDeltaY;
    }
}
