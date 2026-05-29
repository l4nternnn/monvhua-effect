package com.kuilunfuzhe.monvhua.item.mirror;

import com.kuilunfuzhe.monvhua.command.mirror.MirrorCommand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;

public class MirrorOfThenAndNowItem extends Item {
	public MirrorOfThenAndNowItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
			UUID uuid = serverPlayer.getUuid();
			// If viewport is active, click to deactivate
			if (MirrorCommand.VIEWPORT_ACTIVE.getOrDefault(uuid, false)) {
				MirrorCommand.toggleViewport(serverPlayer);
			}
			// If viewport not active, do nothing — charging is handled via hold-to-charge
		}
		return ActionResult.SUCCESS;
	}
}
