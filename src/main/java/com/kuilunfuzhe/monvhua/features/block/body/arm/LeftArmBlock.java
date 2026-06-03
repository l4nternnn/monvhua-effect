package com.kuilunfuzhe.monvhua.features.block.body.arm;

import com.mojang.serialization.MapCodec;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 左臂方块，继承自 {@link BlockWithEntity}，关联 {@link LeftArmBlockEntity} 进行自定义渲染。
 * 方块本身不可见（{@link BlockRenderType#INVISIBLE}），通过 BlockEntity 渲染玩家左臂模型。
 * 放置时从物品 NBT 读取皮肤类型（arm_model、local_skin）并传递给方块实体。
 */
public class LeftArmBlock extends BlockWithEntity {
    /** 水平朝向属性，放置时面向玩家的相反方向 */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    /** 碰撞箱：宽0.5格（x/z方向居中），高0.75格，底部对齐 */
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.25, 0, 0.25, 0.75, 0.75, 0.75);



    public LeftArmBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(LeftArmBlock::new);
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
        return new LeftArmBlockEntity(pos, state);   // 确保这个类存在
    }

    /**
     * 方块放置回调：将放置者的皮肤信息（Profile）和模型覆盖标记（arm_model、local_skin）
     * 从物品 NBT 写入方块实体，用于后续渲染。
     */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.getBlockEntity(pos) instanceof LeftArmBlockEntity armEntity) {
            ProfileComponent itemProfile = itemStack.get(DataComponentTypes.PROFILE);
            if (itemProfile != null) {
                armEntity.setOwner(itemProfile);
            } else if (placer instanceof PlayerEntity player) {
                armEntity.setOwner(new ProfileComponent(player.getGameProfile()));
            }
            // 读取物品栈中的手臂模型覆盖标记
            NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                if (nbt.contains("arm_model")) {
                    nbt.getString("arm_model").ifPresent(armEntity::setSkinType);
				}
				if (nbt.contains("local_skin")) {
					nbt.getString("local_skin").ifPresent(armEntity::setLocalSkin);
				}
            }
            armEntity.markDirty();
        }
    }

    /**
     * 返回方块破坏时的粒子纹理。当前返回石头纹理作为占位。
     * @param state 方块状态（未使用）
     * @return 粒子纹理标识符
     */
//    @Override
    public Identifier getParticleTexture(BlockState state) {
        // 可以改为任意你想要的纹理，例如石头的粒子
        return Identifier.of("minecraft", "block/stone");
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
}