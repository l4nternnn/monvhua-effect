package com.kuilunfuzhe.monvhua.mixin.portal;

import com.kuilunfuzhe.monvhua.features.portal.PortalManager;
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
public abstract class PortalChunkManagerChangeMixin {
    @Shadow
    @Final
    private ServerWorld world;

    @Inject(method = "markForUpdate", at = @At("TAIL"))
    private void monvhua$markRemotePortalBlockEntityDirty(BlockPos pos, CallbackInfo ci) {
        PortalManager.markRemoteChunkDirty(world, pos);
    }
}
