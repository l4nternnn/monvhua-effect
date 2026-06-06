package com.kuilunfuzhe.monvhua.item.gravity;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class GravityWandItem extends Item {
    private static final String SILENCED_TAG = "Silenced";

    public GravityWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (isSilenced(player)) {
                return ActionResult.FAIL;
            }
            GravityMagic.toggleMode(player);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity user = context.getPlayer();
        if (!world.isClient && user instanceof ServerPlayerEntity player && world instanceof ServerWorld serverWorld) {
            if (isSilenced(player)) {
                return ActionResult.FAIL;
            }
            GravityMagic.launch(serverWorld, player, context.getBlockPos(), player.isSneaking());
        }
        return ActionResult.SUCCESS;
    }

    private boolean isSilenced(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains(SILENCED_TAG)) {
            return false;
        }
        player.sendMessage(Text.literal("\u00a7cYou cannot focus enough to use gravity magic"), true);
        return true;
    }
}
