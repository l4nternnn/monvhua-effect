package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.daynight.DayNightClientState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClientWorld.class)
public abstract class DayNightClientWorldMixin {
    @ModifyArg(method = "tickTime", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld$Properties;setTimeOfDay(J)V"), index = 0)
    private long monvhua$predictDayNight(long vanilla) {
        return DayNightClientState.nextTime((ClientWorld) (Object) this, vanilla);
    }
}
