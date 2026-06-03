package com.kuilunfuzhe.monvhua.features.block.body.torso;

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
 * 躯干方块，继承自 {@link BlockWithEntity}，关联 {@link TorsoBlockEntity} 进行自定义渲染。
 * 方块本身不可见（{@link BlockRenderType#INVISIBLE}），通过 BlockEntity 渲染玩家躯干模型。
 * 放置时从物品 NBT 读取皮肤类型（arm_model、local_skin）并传递给方块实体。
 */
public class TorsoBlock extends BlockWithEntity {
    /** 水平朝向属性，放置时面向玩家的相反方向 */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    /** 碰撞箱：宽0.5格（x/z方向居中），高0.75格，底部对齐 */
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.25, 0, 0.25, 0.75, 0.75, 0.75);

    public TorsoBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }


    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        // 必须实现抽象方法，返回一个能够反序列化该方块实例的Codec
        return createCodec(TorsoBlock::new);
    }

    // 去掉注释，并添加上日志
    /**
     * 右键交互：在服务端打开方块实体的配置界面。
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
//        System.out.println("TorsoBlock.onUse called on " + (world.isClient ? "client" : "server"));
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
//            System.out.println("BlockEntity: " + be);
            if (be instanceof BodyPartBlockEntity bodyPart) {
//                System.out.println("Opening screen...");
                player.openHandledScreen(bodyPart);
            }
//            else {
//                System.out.println("BlockEntity is not BodyPartBlockEntity");
//            }
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
//        System.out.println("TorsoBlock.createBlockEntity called at " + pos);
        return new TorsoBlockEntity(pos, state);
    }

    /**
     * 方块放置回调：将放置者的皮肤信息（Profile）和模型覆盖标记（arm_model、local_skin）
     * 从物品 NBT 写入方块实体，用于后续渲染。
     */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.getBlockEntity(pos) instanceof TorsoBlockEntity torsoEntity) {
            ProfileComponent itemProfile = itemStack.get(DataComponentTypes.PROFILE);
            if (itemProfile != null) {
                torsoEntity.setOwner(itemProfile);
            } else if (placer instanceof PlayerEntity player) {
                torsoEntity.setOwner(new ProfileComponent(player.getGameProfile()));
            }
            // 读取物品栈中的手臂模型覆盖标记
            NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                if (nbt.contains("arm_model")) {
                    nbt.getString("arm_model").ifPresent(torsoEntity::setSkinType);
                }
                if (nbt.contains("local_skin")) {
                    nbt.getString("local_skin").ifPresent(torsoEntity::setLocalSkin);
                }
            }
            torsoEntity.markDirty();
        }
    }
}