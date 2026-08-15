package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class PossessionBlockStateSyncMixin {
    @Inject(method = "updateListeners", at = @At("TAIL"))
    private void monvhua$syncObservedBlockState(BlockPos pos, BlockState oldState, BlockState newState,
                                                int flags, CallbackInfo ci) {
        PossessionManager.syncObservedBlock((ServerWorld) (Object) this, pos);
    }
}
