package com.kuilunfuzhe.monvhua.features.swing;

import com.kuilunfuzhe.monvhua.item.swing.SwingAssemblyItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** Entry point for assembly. Keeping the item gate here prevents alternate callers bypassing it. */
public final class SwingAssembly {
    private SwingAssembly() {}

    public static void initialize() {
        com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] diagnostics-v1 registered; main-hand assembly callback enabled");
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (canAssemble(player.getMainHandStack()) || canAssemble(player.getOffHandStack())) {
                com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] CLICK side={} player={} dimension={} hand={} main={} off={} pos={} face={} sneaking={} spectator={}",
                        world.isClient() ? "CLIENT" : "SERVER", player.getName().getString(), world.getRegistryKey().getValue(), hand,
                        player.getMainHandStack(), player.getOffHandStack(), hit.getBlockPos().toShortString(), hit.getSide(), player.isSneaking(), player.isSpectator());
            }
            if (world.isClient() || hand != Hand.MAIN_HAND || !canAssemble(player.getMainHandStack())) return ActionResult.PASS;
            if (!(world instanceof ServerWorld server)) return ActionResult.PASS;
            SwingDiagnostics trace = new SwingDiagnostics();
            BlockPos pivot = hit.getBlockPos().toImmutable();
            trace.log("ASSEMBLY_BEGIN player=" + player.getName().getString() + " dimension=" + world.getRegistryKey().getValue() + " " + SwingDiagnostics.describe(server, pivot));
            if (!server.getEntitiesByClass(SwingEntity.class, new Box(pivot).expand(4), e -> true).isEmpty()) {
                for (SwingEntity existing : server.getEntitiesByClass(SwingEntity.class, new Box(pivot).expand(4), e -> true))
                    trace.log("BLOCKED_EXISTING uuid=" + existing.getUuid() + " pos=" + existing.getPos() + " box=" + existing.getBoundingBox() + " blocks=" + existing.structure().blocks().size());
                player.sendMessage(net.minecraft.text.Text.literal("附近已有秋千，诊断 #" + trace.id), false);
                return ActionResult.FAIL;
            }
            try {
                SwingEntity entity = assemble(server, player, pivot, false, hit.getSide(), trace);
                if (entity == null) {
                    player.sendMessage(net.minecraft.text.Text.literal("未匹配到秋千，诊断 #" + trace.id + "；详见日志 [SwingDiag]"), false);
                    return ActionResult.FAIL;
                }
                player.sendMessage(net.minecraft.text.Text.literal("秋千结构已组装"), true);
                return ActionResult.SUCCESS;
            } catch (IllegalArgumentException ex) {
                com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.error("[SwingDiag #" + trace.id + "] ASSEMBLY_EXCEPTION", ex);
                player.sendMessage(net.minecraft.text.Text.literal("无法组装秋千：" + ex.getMessage() + "，诊断 #" + trace.id), false);
                return ActionResult.FAIL;
            }
        });
    }

    public static boolean canAssemble(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(SwingAssemblyItems.ASSEMBLE_STICK);
    }

    public static SwingEntity assemble(ServerWorld world, PlayerEntity player, BlockPos pivot, boolean zAxis) {
        return assemble(world, player, pivot, zAxis, Direction.NORTH, new SwingDiagnostics());
    }

    private static SwingEntity assemble(ServerWorld world, PlayerEntity player, BlockPos pivot, boolean zAxis, SwingDiagnostics trace) {
        return assemble(world, player, pivot, zAxis, Direction.NORTH, trace);
    }
    private static SwingEntity assemble(ServerWorld world, PlayerEntity player, BlockPos pivot, boolean zAxis, Direction face, SwingDiagnostics trace) {
        if (!canAssemble(player.getMainHandStack())) return null;
        SwingAssemblyDetector.Result result = SwingAssemblyDetector.detect(world, pivot, trace);
        if (result == null) return null;
        SwingStructure structure = result.structure();
        if (player.isSpectator() || !player.getAbilities().allowModifyWorld
                || structure.blocks().isEmpty() || structure.blocks().size() > SwingStructure.MAX_BLOCKS)
            throw new IllegalArgumentException("当前无法修改此结构");
        java.util.Set<BlockPos> selected = new java.util.HashSet<>();
        for (SwingBlock block : structure.blocks()) {
            BlockPos p = pivot.add(block.localPos());
            if (!selected.add(p) || block.localPos().getY() >= 0 || !world.isChunkLoaded(p)
                    || !world.getWorldBorder().contains(p) || !player.canModifyAt(world, p)
                    || world.getBlockEntity(p) != null || !world.getBlockState(p).equals(block.state())
                    || block.state().getHardness(world, p) < 0
                    || !(SwingBlockRoles.rope(block.state()) || SwingBlockRoles.seat(block.state()) || SwingBlockRoles.backrest(block.state())))
                throw new IllegalArgumentException("结构预检查失败：" + p.toShortString());
        }
        int inputSign = inputSign(result.zAxis(), face, player, pivot);
        SwingEntity entity = new SwingEntity(world, pivot.toCenterPos(), structure, result.zAxis(), inputSign);
        trace.log("COMMIT_BEGIN blocks=" + structure.blocks().size() + " zAxis=" + result.zAxis());
        java.util.List<SwingBlock> removed = new java.util.ArrayList<>();
        try {
            for (SwingBlock block : structure.blocks()) {
                BlockPos p = pivot.add(block.localPos());
                // Defer neighbor updates until the complete snapshot has been removed.
                if (!world.setBlockState(p, block.state().getFluidState().getBlockState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE))
                    throw new IllegalStateException("方块移除失败：" + p.toShortString());
                removed.add(block);
            }
            if (!world.spawnEntity(entity)) throw new IllegalStateException("实体生成失败");
        } catch (RuntimeException failure) {
            entity.discard();
            for (SwingBlock block : removed) {
                BlockPos p = pivot.add(block.localPos());
                world.setBlockState(p, block.state(), net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
                if (!world.getBlockState(p).equals(block.state()))
                    trace.log("ROLLBACK_FAILED pos=" + p.toShortString() + " saved=" + block.state());
            }
            trace.log("ROLLBACK restored=" + removed.size() + " reason=" + failure);
            throw new IllegalArgumentException("组装失败，已尝试恢复原结构", failure);
        }
        for (SwingBlock block : removed) {
            BlockPos p = pivot.add(block.localPos());
            world.updateNeighbors(p, block.state().getBlock());
        }
        trace.log("SPAWN result=true uuid=" + entity.getUuid() + " pos=" + entity.getPos() + " box=" + entity.getBoundingBox());
        return entity;
    }
    private static int inputSign(boolean zAxis, Direction face, PlayerEntity player, BlockPos pivot) {
        if (zAxis) {
            if (face == Direction.WEST) return -1;
            if (face == Direction.EAST) return 1;
            return player.getX() < pivot.getX() ? -1 : 1;
        }
        if (face == Direction.NORTH) return -1;
        if (face == Direction.SOUTH) return 1;
        return player.getZ() < pivot.getZ() ? -1 : 1;
    }
}
