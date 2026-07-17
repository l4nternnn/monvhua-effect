package com.kuilunfuzhe.monvhua.features.evil_eyes;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.evil_eyes.server.CameraWatchManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 观看模式操作拦截器 —— 阻断观看模式下的玩家攻击/破坏/交互/使用操作。
 * <p>
 * 同时提供"邪恶印记"(signed_evil)功能：玩家可以在潜行副手持千里眼时右键实体/方块，
 * 给主手物品打上签名的NBT标签。携带签名物品的玩家将被自动标记。
 */
public class ViewingModeBlocker {

	/** 已通知的 signed_evil 标记者-被标记者对集合（会话级别，永不过期），用于避免重复提示 */
	private static final java.util.Set<String> SIGNED_EVIL_NOTIFIED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * 注册所有事件拦截器。
	 * <ol>
	 *   <li>阻止实体攻击 —— 潜行+副手千里眼→签名物品；观看模式→拒绝攻击</li>
	 *   <li>阻止破坏方块 —— 同上逻辑</li>
	 *   <li>物品栏扫描：检测携带signed_evil物品的玩家并自动标记</li>
	 *   <li>阻止使用方块/实体交互/使用物品 —— 观看模式下全部拒绝</li>
	 * </ol>
	 */
	public static void register() {



		// 阻止实体攻击
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer) {
				if (serverPlayer.isSneaking() && serverPlayer.getOffHandStack().getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM && !serverPlayer.getMainHandStack().isEmpty()) {
					ItemStack mainHand = serverPlayer.getMainHandStack();
					NbtComponent existing = mainHand.get(DataComponentTypes.CUSTOM_DATA);
					NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
					if (!nbt.contains(Evil_Eyes.SIGNED_EVIL_KEY)) {
						nbt.putString(Evil_Eyes.SIGNED_EVIL_KEY, serverPlayer.getUuid().toString());
						nbt.putString(Evil_Eyes.SIGNED_EVIL_ID_KEY, UUID.randomUUID().toString());
						mainHand.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
						serverPlayer.sendMessage(Text.literal("§8这个物品似乎哪里怪怪的"), true);
					} else {
						serverPlayer.sendMessage(Text.literal("§c该物品已被标记"), true);
					}
					return ActionResult.SUCCESS;
				}
				if (isViewing(serverPlayer)) {
					serverPlayer.sendMessage(Text.literal("§c观看模式下无法攻击"), true);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});

		// 阻止破坏方块（左键）
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player instanceof ServerPlayerEntity serverPlayer) {
				if (serverPlayer.isSneaking() && serverPlayer.getOffHandStack().getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM && !serverPlayer.getMainHandStack().isEmpty()) {
					ItemStack mainHand = serverPlayer.getMainHandStack();
					NbtComponent existing = mainHand.get(DataComponentTypes.CUSTOM_DATA);
					NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
					if (!nbt.contains(Evil_Eyes.SIGNED_EVIL_KEY)) {
						nbt.putString(Evil_Eyes.SIGNED_EVIL_KEY, serverPlayer.getUuid().toString());
						nbt.putString(Evil_Eyes.SIGNED_EVIL_ID_KEY, UUID.randomUUID().toString());
						mainHand.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
						serverPlayer.sendMessage(Text.literal("§a物品已标记§c洞察印记§a"), true);
					} else {
						serverPlayer.sendMessage(Text.literal("§c该物品已被标记"), true);
					}
					return ActionResult.SUCCESS;
				}
				if (isViewing(serverPlayer)) {
					serverPlayer.sendMessage(Text.literal("§c观看模式下无法破坏方块"), true);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});


		// Scan player inventories for signed_evil items and auto-mark holders
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getWorld(World.OVERWORLD).getTime();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					ItemStack stack = player.getInventory().getStack(i);
					if (stack.isEmpty()) continue;
					NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
					if (customData == null) continue;
					NbtCompound nbt = customData.copyNbt();
					if (!nbt.contains(Evil_Eyes.SIGNED_EVIL_KEY)) continue;
					String uuidStr = nbt.getString(Evil_Eyes.SIGNED_EVIL_KEY).orElse(null);
					if (uuidStr == null) continue;
					UUID markerUuid;
					try {
						markerUuid = UUID.fromString(uuidStr);
					} catch (IllegalArgumentException e) {
						continue;
					}
					Evil_Eyes.ensureSignedItemId(stack);
					if (markerUuid.equals(player.getUuid())) continue;
					if (!Evil_Eyes.canMarkTarget(player)) continue;
					if (Evil_Eyes.isUnmarkCoolingDownFor(markerUuid, player.getUuid(), now)) continue;
					String pairKey = markerUuid.toString() + ":" + player.getUuid().toString();
					boolean firstTime = SIGNED_EVIL_NOTIFIED.add(pairKey);
					if (!Evil_Eyes.hasActiveMark(markerUuid, player.getUuid())) {
						Evil_Eyes.addMark(markerUuid, player.getUuid(), now + 36000);
						ServerPlayerEntity markerPlayer = server.getPlayerManager().getPlayer(markerUuid);
						if (markerPlayer != null) {
							Evil_Eyes.syncMarkedListToClient(markerPlayer);
						}
						if (firstTime) {
							Text markerChatMessage = Text.literal("\u00A7c[\u6D1E\u5BDF\u5370\u8BB0] " + tag_pitch.entityDisplayName(player) + " \u643A\u5E26\u4E86\u4F60\u7684\u6807\u8BB0\u7269\u54C1");
							Text holderChatMessage = Text.literal("\u00A7c\u8FD9\u4E2A\u7269\u54C1\u611F\u89C9\u54EA\u91CC\u602A\u602A\u7684");
							if (markerPlayer != null) {
								markerPlayer.sendMessage(markerChatMessage, false);
							}
							player.sendMessage(holderChatMessage, false);
							if (markerPlayer != null) {
								markerPlayer.sendMessage(Text.literal("§c[洞察印记] " + tag_pitch.entityDisplayName(player) + " 携带了你的标记物品"), true);
							}
							player.sendMessage(Text.literal("§c这个物品感觉哪里怪怪的"), true);
						}
					}
					break;
				}
			}
		});

		// 阻止使用方块（右键）
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isViewing(serverPlayer)) {
				serverPlayer.sendMessage(Text.literal("§c观看模式下无法使用方块"), true);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		// 阻止实体交互（右键实体）
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isViewing(serverPlayer)) {
				serverPlayer.sendMessage(Text.literal("§c观看模式下无法与实体交互"), true);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		// 阻止使用物品（右键空气）
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isViewing(serverPlayer)) {
				serverPlayer.sendMessage(Text.literal("§c观看模式下无法使用物品"), true);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}

	/**
	 * 判断玩家是否处于观看模式（综合检查 Evil_Eyes 和 CameraWatchManager 两处状态）。
	 *
	 * @param player 目标玩家
	 * @return 是否正在观看某个实体
	 */
	public static boolean isViewing(ServerPlayerEntity player) {
		if (Evil_Eyes.isWatching(player)) return true;
		if (CameraWatchManager.isWatching(player)) return true;
		return false;
	}
}
