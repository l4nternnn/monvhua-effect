package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CosmicBoxBlock extends BlockWithEntity {
    private static final VoxelShape SHAPE = VoxelShapes.fullCube();
    private static final double TARGET_SCAN_RADIUS = 256.0D;
    private static final int MAX_TARGETS = 96;

    public CosmicBoxBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(CosmicBoxBlock::new);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CosmicBoxBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        if (!(world.getBlockEntity(pos) instanceof CosmicBoxBlockEntity cosmicBox)) {
            return ActionResult.PASS;
        }
        if (!CosmicBoxNetworking.canUseCosmicBox(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("只有创造模式或拥有 zhuizong 标签的玩家可以使用宇宙盒追踪功能"), true);
            return ActionResult.SUCCESS;
        }

        if (player.isSneaking()) {
            if (!cosmicBox.hasTarget()) {
                serverPlayer.sendMessage(Text.literal("先右键宇宙盒选择一个或多个玩家/实体"), true);
                return ActionResult.SUCCESS;
            }
            cosmicBox.toggleBeam();
            serverPlayer.sendMessage(Text.literal(cosmicBox.isBeamActive() ? "光束已开启" : "光束已关闭"), true);
            return ActionResult.SUCCESS;
        }

        List<CosmicBoxTargetListS2CPacket.TargetEntry> targets = collectTargets((ServerWorld) world, pos);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                serverPlayer,
                new CosmicBoxTargetListS2CPacket(pos, targets)
        );
        return ActionResult.SUCCESS;
    }

    private static List<CosmicBoxTargetListS2CPacket.TargetEntry> collectTargets(ServerWorld world, BlockPos pos) {
        VecCenter center = new VecCenter(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        List<CosmicBoxTargetListS2CPacket.TargetEntry> targets = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive()) {
                continue;
            }
            seen.add(player.getUuid());
            targets.add(new CosmicBoxTargetListS2CPacket.TargetEntry(
                    player.getUuid(),
                    player.getName().getString(),
                    "玩家",
                    center.distanceTo(player.getX(), player.getY(), player.getZ())
            ));
        }

        Box box = Box.of(pos.toCenterPos(), TARGET_SCAN_RADIUS * 2.0D, TARGET_SCAN_RADIUS * 2.0D, TARGET_SCAN_RADIUS * 2.0D);
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, entity -> entity instanceof LivingEntity && entity.isAlive())) {
            if (seen.contains(entity.getUuid())) {
                continue;
            }
            String name = entity.getName().getString();
            String typeName = entity.getType().getName().getString();
            if (!name.equals(typeName)) {
                name = name + " (" + typeName + ")";
            } else {
                name = name + " [" + entity.getBlockPos().toShortString() + "]";
            }
            targets.add(new CosmicBoxTargetListS2CPacket.TargetEntry(
                    entity.getUuid(),
                    name,
                    "实体",
                    center.distanceTo(entity.getX(), entity.getY(), entity.getZ())
            ));
        }

        targets.sort(Comparator.comparing(CosmicBoxTargetListS2CPacket.TargetEntry::kind)
                .thenComparingDouble(CosmicBoxTargetListS2CPacket.TargetEntry::distance)
                .thenComparing(CosmicBoxTargetListS2CPacket.TargetEntry::name));
        if (targets.size() > MAX_TARGETS) {
            return new ArrayList<>(targets.subList(0, MAX_TARGETS));
        }
        return targets;
    }

    private record VecCenter(double x, double y, double z) {
        double distanceTo(double tx, double ty, double tz) {
            double dx = tx - x;
            double dy = ty - y;
            double dz = tz - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
