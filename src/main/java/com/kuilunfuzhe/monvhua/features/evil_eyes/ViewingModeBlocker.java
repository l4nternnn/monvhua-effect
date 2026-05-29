package com.kuilunfuzhe.monvhua.features.evil_eyes;

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

public class ViewingModeBlocker {

	// Track already-notified signed_evil marker:marked pairs (never expires in session)
	private static final java.util.Set<String> SIGNED_EVIL_NOTIFIED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	public static void register() {



		// 阻止实体攻击
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer) {
				if (serverPlayer.isSneaking() && serverPlayer.getOffHandStack().getItem() == Evil_Eyes.CLAIRVOYANCE_ITEM && !serverPlayer.getMainHandStack().isEmpty()) {
					ItemStack mainHand = serverPlayer.getMainHandStack();
					NbtComponent existing = mainHand.get(DataComponentTypes.CUSTOM_DATA);
					NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
					if (!nbt.contains("signed_evil")) {
						nbt.putString("signed_evil", serverPlayer.getUuid().toString());
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
					if (!nbt.contains("signed_evil")) {
						nbt.putString("signed_evil", serverPlayer.getUuid().toString());
						mainHand.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
						serverPlayer.sendMessage(Text.literal("§a物品已标记§c邪恶印记§a"), true);
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
					if (!nbt.contains("signed_evil")) continue;
					String uuidStr = nbt.getString("signed_evil").orElse(null);
					if (uuidStr == null) continue;
					UUID markerUuid;
					try {
						markerUuid = UUID.fromString(uuidStr);
					} catch (IllegalArgumentException e) {
						continue;
					}
					if (markerUuid.equals(player.getUuid())) continue;
					String pairKey = markerUuid.toString() + ":" + player.getUuid().toString();
					boolean firstTime = SIGNED_EVIL_NOTIFIED.add(pairKey);
					if (!Evil_Eyes.hasActiveMark(markerUuid, player.getUuid())) {
						Evil_Eyes.addMark(markerUuid, player.getUuid(), now + 36000);
						ServerPlayerEntity markerPlayer = server.getPlayerManager().getPlayer(markerUuid);
						if (markerPlayer != null) {
							Evil_Eyes.syncMarkedListToClient(markerPlayer);
						}
						if (firstTime) {
							if (markerPlayer != null) {
								markerPlayer.sendMessage(Text.literal("§c[邪恶印记] " + player.getName().getString() + " 携带了你的标记物品"), true);
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

	public static boolean isViewing(ServerPlayerEntity player) {
		if (Evil_Eyes.isWatching(player)) return true;
		if (CameraWatchManager.isWatching(player)) return true;
		return false;
	}
}
