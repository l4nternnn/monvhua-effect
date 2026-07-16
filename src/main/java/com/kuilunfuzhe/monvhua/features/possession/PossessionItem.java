package com.kuilunfuzhe.monvhua.features.possession;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class PossessionItem extends Item {
    private static final String SILENCED_TAG = "Silenced";

    public PossessionItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity controller) || !(entity instanceof ServerPlayerEntity target)) {
            return ActionResult.PASS;
        }
        if (controller.getCommandTags().contains(SILENCED_TAG)) {
            controller.sendMessage(Text.literal("Cannot focus while silenced."), true);
            return ActionResult.FAIL;
        }
        PossessionManager.start(controller, target);
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity controller) {
            if (PossessionManager.isController(controller) || user.isSneaking()) {
                PossessionManager.stopByController(controller, controller.getServer());
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }
}
