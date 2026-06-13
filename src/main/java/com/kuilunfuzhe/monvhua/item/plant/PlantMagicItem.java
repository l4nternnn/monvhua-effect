package com.kuilunfuzhe.monvhua.item.plant;

import com.kuilunfuzhe.monvhua.features.plant.PlantMagic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlantMagicItem extends Item {
    private static final int COBWEB_HEIGHT = 3;

    public PlantMagicItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!user.isSneaking()) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (user instanceof ServerPlayerEntity player && PlantMagic.startLeafPull(player)) {
            int cooldownTicks = PlantMagic.getCooldownTicks(player);
            if (cooldownTicks > 0) {
                player.getItemCooldownManager().set(user.getStackInHand(hand), cooldownTicks);
            }
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.FAIL;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player != null && player.isSneaking()) {
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }
            if (player instanceof ServerPlayerEntity serverPlayer && PlantMagic.startLeafPull(serverPlayer)) {
                int cooldownTicks = PlantMagic.getCooldownTicks(serverPlayer);
                if (cooldownTicks > 0) {
                    serverPlayer.getItemCooldownManager().set(context.getStack(), cooldownTicks);
                }
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.FAIL;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }

        BlockPos base = context.getBlockPos();
        BlockState targetState = serverWorld.getBlockState(base);
        if (!targetState.isReplaceable()) {
            base = base.offset(context.getSide());
        }

        int placed = 0;
        for (int i = 0; i < COBWEB_HEIGHT; i++) {
            BlockPos pos = base.up(i);
            if (pos.getY() < serverWorld.getBottomY() || pos.getY() >= serverWorld.getTopYInclusive()) {
                continue;
            }
            if (serverWorld.getBlockState(pos).isReplaceable()) {
                serverWorld.setBlockState(pos, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
                placed++;
            }
        }

        if (placed > 0) {
            serverWorld.playSound(null, base, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.8F, 0.65F);
            if (player != null) {
                player.getItemCooldownManager().set(this.getDefaultStack(), 12);
            }
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.FAIL;
    }
}
