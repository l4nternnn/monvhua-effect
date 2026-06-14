package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Mixin(FlowableFluid.class)
public abstract class InvertedFluidFlowMixin extends Fluid {
    @Shadow
    public static BooleanProperty FALLING;

    @Shadow
    public abstract Fluid getFlowing();

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Shadow
    public abstract FluidState getStill(boolean falling);

    @Shadow
    protected abstract boolean isInfinite(ServerWorld world);

    @Shadow
    protected abstract void flow(WorldAccess world, BlockPos pos, BlockState state, Direction direction, FluidState fluidState);

    @Shadow
    protected abstract FluidState getUpdatedState(ServerWorld world, BlockPos pos, BlockState state);

    @Shadow
    protected abstract int getLevelDecreasePerBlock(WorldView world);

    @Shadow
    protected abstract int getMaxFlowDistance(WorldView world);

    @Inject(method = "tryFlow", at = @At("HEAD"), cancellable = true)
    private void monvhua$tryInvertedFlow(ServerWorld world, BlockPos pos, BlockState state, FluidState fluidState, CallbackInfo ci) {
        if (!monvhua$isInverted(world, pos)) {
            return;
        }

        ci.cancel();
        if (fluidState.isEmpty()) {
            return;
        }

        BlockPos upPos = pos.up();
        BlockState upState = world.getBlockState(upPos);
        FluidState upFluidState = upState.getFluidState();

        if (monvhua$canFlowThrough(world, pos, state, Direction.UP, upPos, upState, upFluidState)) {
            FluidState updatedState = this.getUpdatedState(world, upPos, upState);
            Fluid updatedFluid = updatedState.getFluid();
            if (upFluidState.canBeReplacedWith(world, upPos, updatedFluid, Direction.UP)
                    && monvhua$canFillWithFluid(world, upPos, upState, updatedFluid)) {
                this.flow(world, upPos, upState, Direction.UP, updatedState);
                if (monvhua$countNeighboringSources(world, pos) >= 3) {
                    monvhua$flowToSidesInverted(world, pos, fluidState, state);
                }
                return;
            }
        }

        if (fluidState.isStill() || !monvhua$canFlowUpTo(world, pos, state, upPos, upState)) {
            monvhua$flowToSidesInverted(world, pos, fluidState, state);
        }
    }

    @Inject(method = "getUpdatedState", at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedUpdatedState(ServerWorld world, BlockPos pos, BlockState state, CallbackInfoReturnable<FluidState> cir) {
        if (!monvhua$isInverted(world, pos)) {
            return;
        }

        int maxNeighborLevel = 0;
        int stillNeighborCount = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = mutable.set(pos, direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            FluidState neighborFluidState = neighborState.getFluidState();
            if (!neighborFluidState.getFluid().matchesType(this)) {
                continue;
            }
            if (monvhua$receivesFlow(direction, world, pos, state, neighborPos, neighborState)) {
                if (neighborFluidState.isStill()) {
                    stillNeighborCount++;
                }
                maxNeighborLevel = Math.max(maxNeighborLevel, neighborFluidState.getLevel());
            }
        }

        if (stillNeighborCount >= 2 && this.isInfinite(world)) {
            BlockState gravityBlockedState = world.getBlockState(mutable.set(pos, Direction.UP));
            FluidState gravityBlockedFluidState = gravityBlockedState.getFluidState();
            if (gravityBlockedState.isSolid() || monvhua$isMatchingAndStill(gravityBlockedFluidState)) {
                cir.setReturnValue(this.getStill(false));
                return;
            }
        }

        BlockPos belowPos = mutable.set(pos, Direction.DOWN);
        BlockState belowState = world.getBlockState(belowPos);
        FluidState belowFluidState = belowState.getFluidState();
        if (!belowFluidState.isEmpty()
                && belowFluidState.getFluid().matchesType(this)
                && monvhua$receivesFlow(Direction.DOWN, world, pos, state, belowPos, belowState)) {
            cir.setReturnValue(this.getFlowing(8, true));
            return;
        }

        int nextLevel = maxNeighborLevel - this.getLevelDecreasePerBlock(world);
        cir.setReturnValue(nextLevel <= 0 ? Fluids.EMPTY.getDefaultState() : this.getFlowing(nextLevel, false));
    }

    @Inject(method = "getVelocity", at = @At("RETURN"), cancellable = true)
    private void monvhua$invertFluidVelocity(BlockView world, BlockPos pos, FluidState state, CallbackInfoReturnable<Vec3d> cir) {
        if (!monvhua$isInverted(world, pos)) {
            return;
        }

        Vec3d velocity = cir.getReturnValue();
        cir.setReturnValue(new Vec3d(velocity.x, -velocity.y, velocity.z));
    }

    @Inject(method = "getHeight(Lnet/minecraft/fluid/FluidState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
            at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedHeight(FluidState state, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (!monvhua$isInverted(world, pos)) {
            return;
        }

        FluidState belowState = world.getFluidState(pos.down());
        cir.setReturnValue(state.getFluid().matchesType(belowState.getFluid()) ? 1.0F : state.getHeight());
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void monvhua$getInvertedShape(FluidState state, BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (!monvhua$isInverted(world, pos)) {
            return;
        }

        float height = Math.clamp(state.getHeight(world, pos), 0.0F, 1.0F);
        cir.setReturnValue(height >= 1.0F ? VoxelShapes.fullCube() : VoxelShapes.cuboid(0.0D, 1.0D - height, 0.0D, 1.0D, 1.0D, 1.0D));
    }

    private void monvhua$flowToSidesInverted(ServerWorld world, BlockPos pos, FluidState fluidState, BlockState blockState) {
        int nextLevel = fluidState.getLevel() - this.getLevelDecreasePerBlock(world);
        if (fluidState.get(FALLING)) {
            nextLevel = 7;
        }
        if (nextLevel <= 0) {
            return;
        }

        Map<Direction, FluidState> spread = monvhua$getSpreadInverted(world, pos, blockState);
        for (Map.Entry<Direction, FluidState> entry : spread.entrySet()) {
            Direction direction = entry.getKey();
            BlockPos targetPos = pos.offset(direction);
            this.flow(world, targetPos, world.getBlockState(targetPos), direction, entry.getValue());
        }
    }

    private Map<Direction, FluidState> monvhua$getSpreadInverted(ServerWorld world, BlockPos pos, BlockState state) {
        int minDistance = 1000;
        Map<Direction, FluidState> spread = new EnumMap<>(Direction.class);
        Map<BlockPos, BlockState> stateCache = new HashMap<>();
        Map<BlockPos, Boolean> upFlowCache = new HashMap<>();

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos targetPos = pos.offset(direction);
            BlockState targetState = world.getBlockState(targetPos);
            FluidState targetFluidState = targetState.getFluidState();
            if (!monvhua$canFlowThrough(world, pos, state, direction, targetPos, targetState, targetFluidState)) {
                continue;
            }

            FluidState updatedState = this.getUpdatedState(world, targetPos, targetState);
            Fluid updatedFluid = updatedState.getFluid();
            if (!monvhua$canFillWithFluid(world, targetPos, targetState, updatedFluid)) {
                continue;
            }

            int distance = monvhua$canFlowUpTo(world, targetPos, targetState, stateCache, upFlowCache)
                    ? 0
                    : monvhua$getMinFlowUpDistance(world, targetPos, 1, direction.getOpposite(), targetState, stateCache, upFlowCache);
            if (distance < minDistance) {
                spread.clear();
            }
            if (distance <= minDistance
                    && targetFluidState.canBeReplacedWith(world, targetPos, updatedFluid, direction)) {
                spread.put(direction, updatedState);
                minDistance = distance;
            }
        }

        return spread;
    }

    private int monvhua$getMinFlowUpDistance(WorldView world, BlockPos pos, int distance, Direction flowDirection, BlockState state,
                                            Map<BlockPos, BlockState> stateCache, Map<BlockPos, Boolean> upFlowCache) {
        int minDistance = 1000;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (direction == flowDirection) {
                continue;
            }

            BlockPos targetPos = pos.offset(direction);
            BlockState targetState = monvhua$getCachedBlockState(world, targetPos, stateCache);
            FluidState targetFluidState = targetState.getFluidState();
            if (!monvhua$canFlowThrough(world, this.getFlowing(), pos, state, direction, targetPos, targetState, targetFluidState)) {
                continue;
            }
            if (monvhua$canFlowUpTo(world, targetPos, targetState, stateCache, upFlowCache)) {
                return distance;
            }
            if (distance >= this.getMaxFlowDistance(world)) {
                continue;
            }

            int targetDistance = monvhua$getMinFlowUpDistance(world, targetPos, distance + 1, direction.getOpposite(),
                    targetState, stateCache, upFlowCache);
            if (targetDistance < minDistance) {
                minDistance = targetDistance;
            }
        }
        return minDistance;
    }

    private boolean monvhua$canFlowUpTo(BlockView world, BlockPos pos, BlockState state, BlockPos upPos, BlockState upState) {
        if (!monvhua$receivesFlow(Direction.UP, world, pos, state, upPos, upState)) {
            return false;
        }
        if (upState.getFluidState().getFluid().matchesType(this)) {
            return true;
        }
        return monvhua$canFill(world, upPos, upState, this.getFlowing());
    }

    private boolean monvhua$canFlowUpTo(WorldView world, BlockPos pos, BlockState state,
                                       Map<BlockPos, BlockState> stateCache, Map<BlockPos, Boolean> upFlowCache) {
        BlockPos key = pos.toImmutable();
        Boolean cached = upFlowCache.get(key);
        if (cached != null) {
            return cached;
        }

        BlockPos upPos = pos.up();
        BlockState upState = monvhua$getCachedBlockState(world, upPos, stateCache);
        boolean result = monvhua$canFlowUpTo(world, pos, state, upPos, upState);
        upFlowCache.put(key, result);
        return result;
    }

    private boolean monvhua$canFlowThrough(BlockView world, BlockPos pos, BlockState state, Direction direction,
                                          BlockPos targetPos, BlockState targetState, FluidState targetFluidState) {
        return !monvhua$isMatchingAndStill(targetFluidState)
                && monvhua$canFill(targetState)
                && monvhua$receivesFlow(direction, world, pos, state, targetPos, targetState);
    }

    private boolean monvhua$canFlowThrough(BlockView world, Fluid fluid, BlockPos pos, BlockState state, Direction direction,
                                          BlockPos targetPos, BlockState targetState, FluidState targetFluidState) {
        return monvhua$canFlowThrough(world, pos, state, direction, targetPos, targetState, targetFluidState)
                && monvhua$canFillWithFluid(world, targetPos, targetState, fluid);
    }

    private int monvhua$countNeighboringSources(WorldView world, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (world.getFluidState(pos.offset(direction)).isStill()) {
                count++;
            }
        }
        return count;
    }

    private boolean monvhua$isMatchingAndStill(FluidState fluidState) {
        return fluidState.getFluid().matchesType(this) && fluidState.isStill();
    }

    private static BlockState monvhua$getCachedBlockState(BlockView world, BlockPos pos, Map<BlockPos, BlockState> cache) {
        BlockPos key = pos.toImmutable();
        return cache.computeIfAbsent(key, world::getBlockState);
    }

    private static boolean monvhua$receivesFlow(Direction direction, BlockView world, BlockPos pos, BlockState state,
                                               BlockPos targetPos, BlockState targetState) {
        VoxelShape targetShape = targetState.getCollisionShape(world, targetPos);
        if (targetShape == VoxelShapes.fullCube()) {
            return false;
        }

        VoxelShape sourceShape = state.getCollisionShape(world, pos);
        if (sourceShape == VoxelShapes.fullCube()) {
            return false;
        }
        if (sourceShape == VoxelShapes.empty() && targetShape == VoxelShapes.empty()) {
            return true;
        }
        return !VoxelShapes.adjacentSidesCoverSquare(sourceShape, targetShape, direction);
    }

    private static boolean monvhua$canFill(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FluidFillable) {
            return true;
        }
        if (state.blocksMovement()) {
            return false;
        }
        return !(block instanceof DoorBlock)
                && !state.isIn(BlockTags.SIGNS)
                && !state.isOf(Blocks.LADDER)
                && !state.isOf(Blocks.SUGAR_CANE)
                && !state.isOf(Blocks.BUBBLE_COLUMN)
                && !state.isOf(Blocks.NETHER_PORTAL)
                && !state.isOf(Blocks.END_PORTAL)
                && !state.isOf(Blocks.END_GATEWAY)
                && !state.isOf(Blocks.STRUCTURE_VOID);
    }

    private static boolean monvhua$canFill(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return monvhua$canFill(state) && monvhua$canFillWithFluid(world, pos, state, fluid);
    }

    private static boolean monvhua$canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        Block block = state.getBlock();
        if (block instanceof FluidFillable fluidFillable) {
            return fluidFillable.canFillWithFluid(null, world, pos, state, fluid);
        }
        return true;
    }

    private static boolean monvhua$isInverted(BlockView world, BlockPos pos) {
        return world instanceof World realWorld
                && GravityMagic.isInInvertedArea(realWorld.getRegistryKey(), pos.toCenterPos());
    }
}
