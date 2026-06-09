package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class InvertedDoorSupportMixin {
    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void monvhua$allowInvertedCeilingDoorSupport(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(world instanceof World realWorld)
                || !state.contains(Properties.DOUBLE_BLOCK_HALF)
                || state.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER
                || !GravityMagic.isInInvertedArea(realWorld.getRegistryKey(), pos.toCenterPos())) {
            return;
        }

        BlockPos supportPos = pos.up(2);
        BlockState supportState = world.getBlockState(supportPos);
        if (supportState.isSideSolidFullSquare(world, supportPos, Direction.DOWN)) {
            cir.setReturnValue(true);
        }
    }
}
