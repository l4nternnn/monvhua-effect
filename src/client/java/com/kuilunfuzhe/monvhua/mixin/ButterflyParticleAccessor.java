package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ButterflyParticleAccessor {
    @Accessor("x") double monvhua$x();
    @Accessor("y") double monvhua$y();
    @Accessor("z") double monvhua$z();
    @Accessor("lastX") double monvhua$prevX();
    @Accessor("lastY") double monvhua$prevY();
    @Accessor("lastZ") double monvhua$prevZ();
}
