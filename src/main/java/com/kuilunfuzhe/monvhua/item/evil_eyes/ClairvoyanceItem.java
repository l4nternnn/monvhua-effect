package com.kuilunfuzhe.monvhua.item.evil_eyes;

import com.kuilunfuzhe.monvhua.network.evil_eyes.OpenUIPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class ClairvoyanceItem extends Item {
    private static final String SILENCED_TAG = "Silenced";

    public ClairvoyanceItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            if (player.getCommandTags().contains(SILENCED_TAG)) {
                player.sendMessage(Text.literal("§c你难以集中精神"), true);
                return ActionResult.FAIL;
            }
            ServerPlayNetworking.send(player, new OpenUIPacket());
        }
        return ActionResult.SUCCESS;
    }
}