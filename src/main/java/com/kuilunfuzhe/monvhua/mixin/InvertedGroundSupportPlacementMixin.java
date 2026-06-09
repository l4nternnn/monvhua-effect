package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedSupportWorldView;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class InvertedGroundSupportPlacementMixin {
    @Unique
    private static final ThreadLocal<Boolean> MONVHUA_CHECKING_INVERTED_SUPPORT = ThreadLocal.withInitial(() -> false);

    @Inject(method = "canPlaceAt", at = @At("RETURN"), cancellable = true)
    private void monvhua$retryWithInvertedGroundSupport(WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                || MONVHUA_CHECKING_INVERTED_SUPPORT.get()
                || !(world instanceof World realWorld)
                || !GravityMagic.isInInvertedArea(realWorld.getRegistryKey(), pos.toCenterPos())) {
            return;
        }

        MONVHUA_CHECKING_INVERTED_SUPPORT.set(true);
        try {
            BlockState state = (BlockState) (Object) this;
            if (state.getBlock() instanceof TallPlantBlock) {
                return;
            }
            if (state.canPlaceAt(new InvertedSupportWorldView(world, pos), pos)) {
                cir.setReturnValue(true);
            }
        } finally {
            MONVHUA_CHECKING_INVERTED_SUPPORT.set(false);
        }
    }
}
