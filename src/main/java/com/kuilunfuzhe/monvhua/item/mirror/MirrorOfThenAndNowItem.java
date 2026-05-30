package com.kuilunfuzhe.monvhua.item.mirror;

import com.kuilunfuzhe.monvhua.command.mirror.MirrorCommand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 古今镜物品，右键使用时检查Silenced标签，
 * 若当前视口已激活则点击关闭视口；视口未激活时不做操作（充能由长按机制处理）。
 */
public class MirrorOfThenAndNowItem extends Item {
	/** 沉默标签，有此标签的玩家无法使用古今镜 */
	private static final String SILENCED_TAG = "Silenced";

	public MirrorOfThenAndNowItem(Settings settings) {
		super(settings);
	}

	/**
	 * 玩家右键使用古今镜。
	 * 服务端检查Silenced标签，被沉默则提示失败；
	 * 若视口已激活则切换关闭视口（视口未激活时由hold-to-charge机制处理充能，此处不做操作）。
	 * @return 客户端始终返回SUCCESS，服务端根据检查返回SUCCESS或FAIL
	 */
	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
			if (serverPlayer.getCommandTags().contains(SILENCED_TAG)) {
				serverPlayer.sendMessage(Text.literal("§c你难以集中精神"), true);
				return ActionResult.FAIL;
			}
			UUID uuid = serverPlayer.getUuid();
			// 如果视口已激活，右键关闭视口
			if (MirrorCommand.VIEWPORT_ACTIVE.getOrDefault(uuid, false)) {
				MirrorCommand.toggleViewport(serverPlayer);
			}
			// 视口未激活时不做操作 —— 充能由长按机制处理
		}
		return ActionResult.SUCCESS;
	}
}
