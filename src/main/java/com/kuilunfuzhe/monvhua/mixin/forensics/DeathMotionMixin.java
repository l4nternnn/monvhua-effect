package com.kuilunfuzhe.monvhua.mixin.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.Forensics;
import com.kuilunfuzhe.monvhua.features.forensics.physics.DeathMotionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class DeathMotionMixin {
    @WrapMethod(method="damage")
    private boolean monvhua$motionScope(ServerWorld world,DamageSource source,float amount,Operation<Boolean> original) {
        if((Object)this instanceof ServerPlayerEntity player&&Forensics.enabled(player)) {
            try(var scope=DeathMotionContext.begin(player,source)){return original.call(world,source,amount);}
        }
        return original.call(world,source,amount);
    }
}
