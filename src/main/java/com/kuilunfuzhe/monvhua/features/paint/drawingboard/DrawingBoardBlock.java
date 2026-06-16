package com.kuilunfuzhe.monvhua.features.paint.drawingboard;

import com.kuilunfuzhe.monvhua.network.drawingboard.DrawingBoardPackets;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

public class DrawingBoardBlock extends BlockWithEntity {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1.0D, 0.0D, 5.2D, 15.0D, 16.0D, 11.5D),
            Block.createCuboidShape(2.2D, 0.0D, 7.0D, 4.0D, 16.0D, 9.0D),
            Block.createCuboidShape(12.0D, 0.0D, 7.0D, 13.8D, 16.0D, 9.0D)
    );
    private static final VoxelShape UPPER_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(2.5D, 0.0D, 5.8D, 13.5D, 13.9D, 10.6D),
            Block.createCuboidShape(3.0D, 0.0D, 6.4D, 13.0D, 9.4D, 7.9D)
    );

    public DrawingBoardBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(DrawingBoardBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? BlockRenderType.MODEL : BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        if (!ctx.getWorld().getBlockState(pos.up()).isReplaceable()) {
            return null;
        }
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient() && state.get(HALF) == DoubleBlockHalf.LOWER) {
            world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState,
                                                   Random random) {
        DoubleBlockHalf half = state.get(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            return isMatchingHalf(neighborState, state, half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                    ? state
                    : Blocks.AIR.getDefaultState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
        return rotateShape(shape, state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        BlockPos lowerPos = lowerPos(pos, state);
        if (world.getBlockEntity(lowerPos) instanceof DrawingBoardBlockEntity board) {
            ServerPlayNetworking.send(serverPlayer, new DrawingBoardPackets.SyncS2C(lowerPos, board.copyPixels()));
            ServerPlayNetworking.send(serverPlayer, new DrawingBoardPackets.OpenS2C(lowerPos));
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.PASS;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new DrawingBoardBlockEntity(pos, state) : null;
    }

    public static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    }

    private static boolean isMatchingHalf(BlockState other, BlockState own, DoubleBlockHalf half) {
        return other.isOf(own.getBlock())
                && other.contains(HALF)
                && other.get(HALF) == half
                && other.get(FACING) == own.get(FACING);
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        return switch (facing) {
            case EAST -> rotateY(shape, 1);
            case SOUTH -> rotateY(shape, 2);
            case WEST -> rotateY(shape, 3);
            default -> shape;
        };
    }

    private static VoxelShape rotateY(VoxelShape shape, int turns) {
        VoxelShape result = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape rotated = VoxelShapes.empty();
            for (net.minecraft.util.math.Box box : result.getBoundingBoxes()) {
                rotated = VoxelShapes.union(rotated, VoxelShapes.cuboid(
                        1.0D - box.maxZ, box.minY, box.minX,
                        1.0D - box.minZ, box.maxY, box.maxX
                ));
            }
            result = rotated;
        }
        return result;
    }
}
