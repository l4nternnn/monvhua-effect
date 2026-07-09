package com.kuilunfuzhe.monvhua.mixin.gravity;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface SurfaceGravityEntityAccessor {
    @Accessor("onGround")
    void monvhua$setOnGround(boolean onGround);
}
