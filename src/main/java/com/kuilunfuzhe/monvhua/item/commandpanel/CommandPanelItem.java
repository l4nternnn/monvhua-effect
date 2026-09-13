package com.kuilunfuzhe.monvhua.item.commandpanel;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class CommandPanelItem extends Item {
    public CommandPanelItem(Settings settings) { super(settings); }
    @Override public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ActionResult.SUCCESS;
    }
}
