package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.daynight.DayNightFeature;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Only replaces the daylight increment; world age and scheduled events stay vanilla. */
@Mixin(ServerWorld.class)
public abstract class DayNightServerWorldMixin {
    @ModifyArg(method = "tickTime", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"), index = 0)
    private long monvhua$dayNightTime(long vanilla) {
        return DayNightFeature.nextTime((ServerWorld) (Object) this, vanilla);
    }
}
