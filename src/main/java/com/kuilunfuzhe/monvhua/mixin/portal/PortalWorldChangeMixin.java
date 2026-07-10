package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.PortalManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class PortalWorldChangeMixin {
    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void monvhua$markRemotePortalChunkDirty(BlockPos pos, BlockState state, int flags,
                                                    int maxUpdateDepth,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this instanceof ServerWorld serverWorld) {
            PortalManager.markRemoteChunkDirty(serverWorld, pos);
        }
    }
}
