package com.kuilunfuzhe.monvhua.item.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public class GravityWandItem extends Item {
    private static final String SILENCED_TAG = "Silenced";

    public GravityWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (isSilenced(player)) {
                return ActionResult.FAIL;
            }
            ActionResult surfaceResult = GravityMagic.useGravityWand(player, null);
            if (surfaceResult != ActionResult.PASS) {
                return surfaceResult;
            }
            if (player.isSneaking()) {
                return GravityMagic.applySelfGravityForce(player) ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
            }
            if (!GravityMagic.throwHeldBlocks(player)) {
                player.sendMessage(Text.literal("\u00a7c[重力] 请先按住控制键并用鼠标中键选中方块"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (context.getHand() != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (isSilenced(player)) {
                return ActionResult.FAIL;
            }
            BlockHitResult hit = new BlockHitResult(context.getHitPos(), context.getSide(), context.getBlockPos(), context.hitsInsideBlock());
            ActionResult surfaceResult = GravityMagic.useGravityWand(player, hit);
            if (surfaceResult != ActionResult.PASS) {
                return surfaceResult;
            }
            if (player.isSneaking()) {
                return GravityMagic.applySelfGravityForce(player) ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
            }
            if (!GravityMagic.throwHeldBlocks(player)) {
                player.sendMessage(Text.literal("\u00a7c[重力] 请先按住控制键并用鼠标中键选中方块"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.SUCCESS;
    }

    private boolean isSilenced(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains(SILENCED_TAG)) {
            return false;
        }
        player.sendMessage(Text.literal("\u00a7c你无法集中精神使用重力魔法"), true);
        return true;
    }
}
