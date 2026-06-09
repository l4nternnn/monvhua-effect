package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallMountedBlock.class)
public abstract class InvertedWallMountedPlacementMixin {
    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void monvhua$keepInvertedVerticalFaceAttached(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                          Direction direction, BlockPos neighborPos, BlockState neighborState,
                                                          Random random, CallbackInfoReturnable<BlockState> cir) {
        Direction supportDirection = monvhua$invertedSupportDirection(state, world, pos);
        if (supportDirection == null || direction.getAxis() != Direction.Axis.Y) {
            return;
        }

        boolean hasSupport = WallMountedBlock.canPlaceAt(world, pos, supportDirection);
        if (!hasSupport) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        } else if (direction == supportDirection.getOpposite()) {
            cir.setReturnValue(state);
        }
    }

    private Direction monvhua$invertedSupportDirection(BlockState state, WorldView world, BlockPos pos) {
        if (!(world instanceof World realWorld)
                || !state.contains(Properties.BLOCK_FACE)
                || !GravityMagic.isInInvertedArea(realWorld.getRegistryKey(), pos.toCenterPos())) {
            return null;
        }

        BlockFace face = state.get(Properties.BLOCK_FACE);
        if (face == BlockFace.FLOOR) {
            return Direction.UP;
        }
        if (face == BlockFace.CEILING) {
            return Direction.DOWN;
        }
        return null;
    }
}
