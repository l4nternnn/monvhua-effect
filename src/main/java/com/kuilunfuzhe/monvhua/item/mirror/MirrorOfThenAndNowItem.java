package com.kuilunfuzhe.monvhua.item.mirror;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class MirrorOfThenAndNowItem extends Item {
	public MirrorOfThenAndNowItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		// 切换逻辑由以下两部分处理：
		// 客户端：ClairvoyanceClient 中的 UseItemCallback 发送 MirrorToggleC2SPacket
		// 服务端：Clairvoyance.java 中的 C2S 接收器 -> MirrorCommand.toggleViewport()
		return ActionResult.SUCCESS;
	}
}
