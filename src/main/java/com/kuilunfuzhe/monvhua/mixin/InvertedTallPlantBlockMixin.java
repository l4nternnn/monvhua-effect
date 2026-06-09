package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.features.gravity.InvertedSupportWorldView;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TallPlantBlock.class)
public abstract class InvertedTallPlantBlockMixin {
    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedPlacementState(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (!GravityMagic.isInInvertedArea(world.getRegistryKey(), pos.toCenterPos())
                || pos.getY() <= world.getBottomY()
                || !world.getBlockState(pos.down()).isReplaceable()) {
            return;
        }

        BlockState state = ((TallPlantBlock) (Object) this).getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        if (state.canPlaceAt(new InvertedSupportWorldView(world, pos), pos)) {
            cir.setReturnValue(state);
        }
    }

    @Inject(method = "onPlaced", at = @At("HEAD"), cancellable = true)
    private void monvhua$placeInvertedUpperHalf(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (!monvhua$isInvertedLowerHalf(world, pos, state)) {
            return;
        }

        BlockPos upperPos = pos.down();
        BlockState upperState = ((TallPlantBlock) (Object) this).getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        world.setBlockState(upperPos, TallPlantBlock.withWaterloggedState(world, upperPos, upperState), Block.NOTIFY_ALL);
        ci.cancel();
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void monvhua$allowInvertedHalfSupport(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(world instanceof World realWorld)
                || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        if (half == DoubleBlockHalf.UPPER) {
            if (monvhua$isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.LOWER)
                    && monvhua$isInInvertedArea(realWorld, pos.up())) {
                cir.setReturnValue(true);
            }
            return;
        }

        if (monvhua$hasNormalCounterpart(world, pos, state) && !monvhua$hasInvertedCounterpart(world, pos, state)) {
            return;
        }

        if (!monvhua$isInInvertedArea(realWorld, pos)) {
            return;
        }

        if (state.canPlaceAt(new InvertedSupportWorldView(world, pos), pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void monvhua$keepInvertedHalvesAttached(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                    Direction direction, BlockPos neighborPos, BlockState neighborState,
                                                    Random random, CallbackInfoReturnable<BlockState> cir) {
        if (!(world instanceof World realWorld) || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return;
        }

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        boolean hasNormalCounterpart = monvhua$hasNormalCounterpart(world, pos, state);
        boolean hasInvertedCounterpart = monvhua$hasInvertedCounterpart(world, pos, state);
        boolean invertedContext = hasInvertedCounterpart || !hasNormalCounterpart && monvhua$isInInvertedArea(realWorld, pos);
        if (half == DoubleBlockHalf.LOWER) {
            if (direction == Direction.DOWN && invertedContext) {
                cir.setReturnValue(monvhua$isMatchingHalf(world, pos.down(), state, DoubleBlockHalf.UPPER) ? state : Blocks.AIR.getDefaultState());
            } else if (direction == Direction.UP && invertedContext) {
                cir.setReturnValue(state.canPlaceAt(world, pos) ? state : Blocks.AIR.getDefaultState());
            }
            return;
        }

        if (direction == Direction.UP && invertedContext) {
            cir.setReturnValue(monvhua$isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.LOWER) ? state : Blocks.AIR.getDefaultState());
        } else if (direction == Direction.DOWN && hasInvertedCounterpart) {
            cir.setReturnValue(state);
        }
    }

    @Unique
    private boolean monvhua$isInvertedLowerHalf(World world, BlockPos pos, BlockState state) {
        return state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                && GravityMagic.isInInvertedArea(world.getRegistryKey(), pos.toCenterPos())
                && world.getBlockState(pos.down()).isReplaceable()
                && monvhua$hasInvertedTallPlantSupport(world, pos, state);
    }

    @Unique
    private boolean monvhua$hasInvertedTallPlantSupport(World world, BlockPos pos, BlockState state) {
        if (!world.getBlockState(pos.up()).isReplaceable()) {
            return true;
        }
        return state.canPlaceAt(new InvertedSupportWorldView(world, pos), pos);
    }

    @Unique
    private boolean monvhua$hasNormalCounterpart(WorldView world, BlockPos pos, BlockState state) {
        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        return half == DoubleBlockHalf.LOWER
                ? monvhua$isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.UPPER)
                : monvhua$isMatchingHalf(world, pos.down(), state, DoubleBlockHalf.LOWER);
    }

    @Unique
    private boolean monvhua$hasInvertedCounterpart(WorldView world, BlockPos pos, BlockState state) {
        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        return half == DoubleBlockHalf.LOWER
                ? monvhua$isMatchingHalf(world, pos.down(), state, DoubleBlockHalf.UPPER)
                : monvhua$isMatchingHalf(world, pos.up(), state, DoubleBlockHalf.LOWER);
    }

    @Unique
    private boolean monvhua$isInInvertedArea(World world, BlockPos pos) {
        return GravityMagic.isInInvertedArea(world.getRegistryKey(), pos.toCenterPos());
    }

    @Unique
    private boolean monvhua$isMatchingHalf(WorldView world, BlockPos pos, BlockState state, DoubleBlockHalf half) {
        BlockState other = world.getBlockState(pos);
        return other.isOf(state.getBlock())
                && other.contains(Properties.DOUBLE_BLOCK_HALF)
                && other.get(Properties.DOUBLE_BLOCK_HALF) == half;
    }
}
