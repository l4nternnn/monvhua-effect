package com.kuilunfuzhe.monvhua.features.block.body.head;

import com.mojang.serialization.MapCodec;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 头部方块，继承自 {@link BlockWithEntity}，关联 {@link HeadBlockEntity} 进行自定义渲染。
 * 方块本身不可见（{@link BlockRenderType#INVISIBLE}），通过 BlockEntity 渲染玩家头部模型。
 * 放置时从物品 NBT 读取本地皮肤（local_skin）并传递给方块实体。
 * 与其他身体部件不同，头部仅支持 local_skin 皮肤来源。
 */
public class HeadBlock extends BlockWithEntity {
    /** 水平朝向属性，放置时面向玩家的相反方向 */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    /** 碰撞箱：宽0.5格（x/z方向居中），高0.5格（头部为立方体），底部对齐 */
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.25, 0, 0.25, 0.75, 0.5, 0.75);

    public HeadBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(HeadBlock::new);
    }

    /**
     * 右键交互：在服务端打开方块实体的配置界面。
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof BodyPartBlockEntity bodyPart) {
                player.openHandledScreen(bodyPart);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HeadBlockEntity(pos, state);
    }

    /**
     * 方块放置回调：将放置者的皮肤信息和本地皮肤覆盖标记（local_skin）
     * 从物品 NBT 写入方块实体，用于后续渲染。
     */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.getBlockEntity(pos) instanceof HeadBlockEntity headEntity) {
            ProfileComponent itemProfile = itemStack.get(DataComponentTypes.PROFILE);
            if (itemProfile != null) {
                headEntity.setOwner(itemProfile);
            } else if (placer instanceof PlayerEntity player) {
                headEntity.setOwner(new ProfileComponent(player.getGameProfile()));
            }
            NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                if (nbt.contains("local_skin")) {
                    nbt.getString("local_skin").ifPresent(headEntity::setLocalSkin);
                }
            }
            headEntity.markDirty();
        }
    }
}
