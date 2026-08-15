package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public abstract class PossessionBlockEntitySyncMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "markForUpdate(Lnet/minecraft/util/math/BlockPos;)V", at = @At("TAIL"))
    private void monvhua$syncObservedBlockEntity(BlockPos pos, CallbackInfo ci) {
        PossessionManager.syncObservedBlock(world, pos);
    }
}
