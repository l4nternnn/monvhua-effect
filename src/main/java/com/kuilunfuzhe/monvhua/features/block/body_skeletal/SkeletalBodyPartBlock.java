package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public abstract class SkeletalBodyPartBlock extends BlockWithEntity {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.25, 0.0, 0.25, 0.75, 0.85, 0.75);

    private final SkeletalBodyPart part;

    protected SkeletalBodyPartBlock(AbstractBlock.Settings settings, SkeletalBodyPart part) {
        super(settings);
        this.part = part;
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    public SkeletalBodyPart getPart() {
        return part;
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

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && !player.isSneaking()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BodyPartBlockEntity bodyPart) {
                player.openHandledScreen(bodyPart);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!(world.getBlockEntity(pos) instanceof BodyPartBlockEntity bodyPart)) {
            return;
        }

        ProfileComponent itemProfile = itemStack.get(DataComponentTypes.PROFILE);
        if (itemProfile != null) {
            bodyPart.setOwner(itemProfile);
        } else if (placer instanceof PlayerEntity player) {
            bodyPart.setOwner(new ProfileComponent(player.getGameProfile()));
        }

        NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            if (nbt.contains("arm_model")) {
                nbt.getString("arm_model").ifPresent(bodyPart::setSkinType);
            }
            if (nbt.contains("local_skin")) {
                nbt.getString("local_skin").ifPresent(bodyPart::setLocalSkin);
            }
            if (bodyPart instanceof SkeletalBodyPartBlockEntity skeletal) {
                float pitch = nbt.getFloat("joint_pitch").orElse(0.0F);
                float yaw = nbt.getFloat("joint_yaw").orElse(0.0F);
                float roll = nbt.getFloat("joint_roll").orElse(0.0F);
                float bendPitch = nbt.getFloat("bend_pitch").orElse(0.0F);
                float bendYaw = nbt.getFloat("bend_yaw").orElse(0.0F);
                float bendRoll = nbt.getFloat("bend_roll").orElse(0.0F);
                skeletal.setSkeletalPose(pitch, yaw, roll, bendPitch, bendYaw, bendRoll);
            }
        }
        bodyPart.markDirty();
    }
}
