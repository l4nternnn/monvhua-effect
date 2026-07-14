package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
    @Invoker("refreshData")
    void monvhua$refreshData(boolean shouldLerp, float lerpProgress);
}
