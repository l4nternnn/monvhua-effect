package com.kuilunfuzhe.monvhua.command.mirror;

import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.ChargeSyncS2C;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.StateS2C;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MirrorCommand {
	public static final Map<UUID, Boolean> VIEWPORT_ACTIVE = new ConcurrentHashMap<>();
	public static final Map<UUID, Integer> VIEWPORT_ACCUMULATED_TICKS = new ConcurrentHashMap<>();
	public static final Map<UUID, Integer> VIEWPORT_USES_LEFT = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> VIEWPORT_ACTIVATION_DELAY_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> VIEWPORT_CLOSE_PROTECTION_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> VIEWPORT_STAGE = new ConcurrentHashMap<>();
	private static final int VIEWPORT_ACTIVATION_DELAY = 20;
	private static final int VIEWPORT_CLOSE_PROTECTION = 20;

	// Charging state
	public static final Map<UUID, Integer> CHARGING_PLAYERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> CHARGING_STAGE = new ConcurrentHashMap<>();

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("clairvoyance-mirror_镜像")
			.requires(source -> source.hasPermissionLevel(2))
			.then(CommandManager.literal("set_设置")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
					.then(CommandManager.literal("hspos_设定点")
						.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
							.executes(ctx -> executeSetHsPos(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								Vec3ArgumentType.getVec3(ctx, "pos")))))
					.then(CommandManager.literal("mappos_映射点")
						.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
							.executes(ctx -> executeSetMapPos(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								Vec3ArgumentType.getVec3(ctx, "pos")))))
					.then(CommandManager.literal("radius_半径")
						.then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 200.0))
							.executes(ctx -> executeSetRadius(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								DoubleArgumentType.getDouble(ctx, "radius")))))))
			.then(CommandManager.literal("remove_移除")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
					.executes(ctx -> executeRemove(ctx.getSource(),
						IntegerArgumentType.getInteger(ctx, "slot")))))
			.then(CommandManager.literal("list_列出所有")
				.executes(ctx -> executeList(ctx.getSource())))
			.then(CommandManager.literal("clear_清除")
				.executes(ctx -> executeClear(ctx.getSource())))
		);
	}

	private static int executeSetHsPos(ServerCommandSource source, int slot, Vec3d hsPos) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.setHsPos(player.getUuid(), slot, hsPos);
		source.sendMessage(Text.literal("§a镜面 " + slot + " hs_发生点已设置为: §f" + fmtPos(hsPos)));
		syncToClient(player);
		return 1;
	}

	private static int executeSetMapPos(ServerCommandSource source, int slot, Vec3d mapPos) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.setMapPos(player.getUuid(), slot, mapPos);
		source.sendMessage(Text.literal("§a镜面 " + slot + " map_映射点已设置为: §f" + fmtPos(mapPos)));
		syncToClient(player);
		return 1;
	}

	private static int executeSetRadius(ServerCommandSource source, int slot, double radius) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.setRadius(player.getUuid(), slot, radius);
		source.sendMessage(Text.literal("§a镜面 " + slot + " 触发半径已设置为: §f" + String.format("%.1f", radius)));
		syncToClient(player);
		return 1;
	}

	private static int executeRemove(ServerCommandSource source, int slot) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.removeSlot(player.getUuid(), slot);
		source.sendMessage(Text.literal("§a镜面 " + slot + " 已清除"));
		syncToClient(player);
		return 1;
	}

	private static int executeList(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(player.getUuid());
		source.sendMessage(Text.literal("§b=== 镜面列表 ==="));

		boolean anyActive = false;
		for (int i = 0; i < 2; i++) {
			MirrorDataStore.SlotData slot = (data.slots != null && i < data.slots.length) ? data.slots[i] : null;
			if (slot != null) {
				anyActive = true;
				source.sendMessage(Text.literal("§a镜面 " + (i + 1) + ":"));
				source.sendMessage(Text.literal("  §fhs: " + fmtPos(slot.hsPos()) + "  map: " + fmtPos(slot.mapPos()) + "  r=" + String.format("%.1f", slot.radius())));
			} else {
				source.sendMessage(Text.literal("§7镜面 " + (i + 1) + ": 未设置"));
			}
		}

		boolean active = VIEWPORT_ACTIVE.getOrDefault(player.getUuid(), false);
		int stage = VIEWPORT_STAGE.getOrDefault(player.getUuid(), Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager));
		int usesLeft = VIEWPORT_USES_LEFT.getOrDefault(player.getUuid(), MirrorConfig.getInstance().getViewCount(stage));
		source.sendMessage(Text.literal("§6镜像视图: " + (active ? "§a开启" : "§7关闭") + " §7剩余使用次数: §f" + usesLeft));

		if (!anyActive) {
			source.sendMessage(Text.literal("§7使用 /clairvoyance-mirror_镜像 set_设置 <1|2> {hspos_设定点|mappos_映射点|radius_半径} <值> 设置镜面"));
		}
		return 1;
	}

	private static int executeClear(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		MirrorDataStore.clearPlayer(player.getUuid());
		source.sendMessage(Text.literal("§a所有镜面已清除"));
		syncToClient(player);
		return 1;
	}

	public static void toggleViewport(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
            if (player.getCommandTags().contains("Silenced")) { player.sendMessage(net.minecraft.text.Text.literal("§c你难以集中精神"), true); return; }
		if (VIEWPORT_ACTIVE.getOrDefault(uuid, false)) {
			if (VIEWPORT_CLOSE_PROTECTION_TICKS.containsKey(uuid)) {
				player.sendMessage(Text.literal("§7镜像视角已启动"), true);
				return;
			}
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_ACTIVATION_DELAY_TICKS.remove(uuid);
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§7镜像视角已关闭"), true);
			return;
		}

		if (VIEWPORT_ACTIVATION_DELAY_TICKS.containsKey(uuid)) {
			player.sendMessage(Text.literal("§7镜像视角正在启动"), true);
			return;
		}

		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);

		if (!hasAnySlot(data)) {
			player.sendMessage(Text.literal("§c请先使用 /clairvoyance-mirror_镜像 set_设置 <1|2> {hspos_设定点|mappos_映射点|radius_半径} <值> 设置至少一个镜面"), false);
			return;
		}

		int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
		MirrorConfig config = MirrorConfig.getInstance();
		int maxUses = config.getViewCount(stage);
		int storedStage = VIEWPORT_STAGE.getOrDefault(uuid, stage);
		int usesLeft = storedStage == stage ? Math.min(VIEWPORT_USES_LEFT.getOrDefault(uuid, maxUses), maxUses) : maxUses;

		if (usesLeft <= 0) {
			player.sendMessage(Text.literal("§c当前阶段镜子使用次数已用完"), true);
			return;
		}

		usesLeft--;
		VIEWPORT_STAGE.put(uuid, stage);
		VIEWPORT_USES_LEFT.put(uuid, usesLeft);

		boolean current = VIEWPORT_ACTIVE.getOrDefault(uuid, false);
		if (current) {
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§7昔日重现已不再，剩余 " + usesLeft + " 次"), true);
			return;
		}

		if (!isInAnyRange(player, data)) {
			syncToClient(player);
			player.sendMessage(Text.literal("§c这里没有往昔残片，§k123§r " + usesLeft + " §1k§r"), true);
			return;
		}

		if (player.getRandom().nextDouble() >= config.getSuccessRate(stage)) {
			syncToClient(player);
			player.sendMessage(Text.literal("§7观看判定失败，已消耗 1 次，剩余 " + usesLeft + " 次"), true);
			return;
		}

		scheduleViewportActivation(player);
		syncToClient(player);
		player.sendMessage(Text.literal("§a观看判定成功，已消耗 1 次，剩余 " + usesLeft + " 次"), true);
	}

	// ========== Charging system ==========

	public static void startCharging(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
        if (player.getCommandTags().contains("Silenced")) { player.sendMessage(net.minecraft.text.Text.literal("§c你难以集中精神"), true); return; }

		// Don't start charging if viewport is already active or waiting to activate.
		if (VIEWPORT_ACTIVE.getOrDefault(uuid, false) || VIEWPORT_ACTIVATION_DELAY_TICKS.containsKey(uuid)) return;

		// Don't start charging if already charging
		if (CHARGING_PLAYERS.containsKey(uuid)) return;

		// Validate slots
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);
		if (!hasAnySlot(data)) {
			player.sendMessage(Text.literal("§c请先使用 /clairvoyance-mirror_镜像 set_设置 指令设置镜面"), false);
			return;
		}

		// Check range
		if (!isInAnyRange(player, data)) {
			player.sendMessage(Text.literal("§c你不在镜面触发范围内"), true);
			return;
		}

		// Check uses
		int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
		MirrorConfig config = MirrorConfig.getInstance();
		int maxUses = config.getViewCount(stage);
		int storedStage = VIEWPORT_STAGE.getOrDefault(uuid, stage);
		int usesLeft = storedStage == stage ? Math.min(VIEWPORT_USES_LEFT.getOrDefault(uuid, maxUses), maxUses) : maxUses;

		if (usesLeft <= 0) {
			player.sendMessage(Text.literal("§c当前阶段镜子使用次数已用完"), true);
			return;
		}

		CHARGING_PLAYERS.put(uuid, 0);
		CHARGING_STAGE.put(uuid, stage);
		syncChargeToClient(player, 0, config.getChargeTime(stage));
	}

	public static void stopCharging(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (CHARGING_PLAYERS.containsKey(uuid)) {
			CHARGING_PLAYERS.remove(uuid);
			CHARGING_STAGE.remove(uuid);
			// Send zero charge to hide the HUD bar
			syncChargeToClient(player, 0, 0);
		}
	}

	public static void tickCharging(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (!CHARGING_PLAYERS.containsKey(uuid)) return;

		// Verify player still holds the mirror item
		if (player.getMainHandStack().getItem() != com.kuilunfuzhe.monvhua.item.mirror.mirror_of_then_and_now.MIRROR_ITEM) {
			stopCharging(player);
			return;
		}

		// Verify still in range
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);
		if (!isInAnyRange(player, data)) {
			stopCharging(player);
			player.sendMessage(Text.literal("§c已离开镜面触发范围，充能取消"), true);
			return;
		}

		int stage = CHARGING_STAGE.getOrDefault(uuid, Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager));
		MirrorConfig config = MirrorConfig.getInstance();
		int chargeTime = config.getChargeTime(stage);

		int ticks = CHARGING_PLAYERS.get(uuid) + 1;
		CHARGING_PLAYERS.put(uuid, ticks);

		// Sync every 2 ticks to reduce bandwidth
		if (ticks % 2 == 0 || ticks >= chargeTime) {
			syncChargeToClient(player, ticks, chargeTime);
		}

		if (ticks >= chargeTime) {
			// Fully charged — clear charging state
			CHARGING_PLAYERS.remove(uuid);
			CHARGING_STAGE.remove(uuid);
			syncChargeToClient(player, 0, 0);

			// Consume a use and do success check
			int maxUses = config.getViewCount(stage);
			int storedStage = VIEWPORT_STAGE.getOrDefault(uuid, stage);
			int usesLeft = storedStage == stage ? Math.min(VIEWPORT_USES_LEFT.getOrDefault(uuid, maxUses), maxUses) : maxUses;

			if (usesLeft <= 0) {
				player.sendMessage(Text.literal("§c今天没有魔力了。。"), true);
				return;
			}

			usesLeft--;
			VIEWPORT_USES_LEFT.put(uuid, usesLeft);
			VIEWPORT_STAGE.put(uuid, stage);

			if (player.getRandom().nextDouble() >= config.getSuccessRate(stage)) {
				syncToClient(player);
				player.sendMessage(Text.literal("§7观看判定失败，已消耗 1 次，剩余 " + usesLeft + " 次"), true);
				return;
			}

			// Success — activate viewport
			scheduleViewportActivation(player);
			syncToClient(player);
			player.sendMessage(Text.literal("§a观看判定成功，已消耗 1 次，剩余 " + usesLeft + " 次"), true);
		}
	}

	public static void syncChargeToClient(ServerPlayerEntity player, int currentTicks, int maxTicks) {
		if (player.networkHandler != null) {
			ServerPlayNetworking.send(player, new ChargeSyncS2C(currentTicks, maxTicks));
		}
	}

	public static void tickViewports(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (tickActivationDelay(player)) return;
		if (!VIEWPORT_ACTIVE.getOrDefault(uuid, false)) return;
		tickCloseProtection(uuid);

		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);
		if (!isInAnyRange(player, data)) {
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§7已离开范围，"), true);
			return;
		}

		int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
		MirrorConfig config = MirrorConfig.getInstance();
		int watchTimeTicks = Math.max(1, config.getWatchTime(stage)) * 20;

		int accumulated = VIEWPORT_ACCUMULATED_TICKS.getOrDefault(uuid, 0) + 1;
		VIEWPORT_ACCUMULATED_TICKS.put(uuid, accumulated);

		if (accumulated >= watchTimeTicks) {
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			player.sendMessage(Text.literal("§a头昏脑涨了,"), true);
			syncToClient(player);
		}
	}

	public static void cleanup(UUID uuid) {
		VIEWPORT_ACTIVE.remove(uuid);
		VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
		VIEWPORT_ACTIVATION_DELAY_TICKS.remove(uuid);
		VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
		VIEWPORT_USES_LEFT.remove(uuid);
		VIEWPORT_STAGE.remove(uuid);
		CHARGING_PLAYERS.remove(uuid);
		CHARGING_STAGE.remove(uuid);
	}

	private static void scheduleViewportActivation(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		VIEWPORT_ACTIVE.put(uuid, false);
		VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
		VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
		VIEWPORT_ACTIVATION_DELAY_TICKS.put(uuid, VIEWPORT_ACTIVATION_DELAY);
	}

	private static boolean tickActivationDelay(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		Integer ticksLeft = VIEWPORT_ACTIVATION_DELAY_TICKS.get(uuid);
		if (ticksLeft == null) return false;

		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);
		if (!isInAnyRange(player, data)) {
			VIEWPORT_ACTIVATION_DELAY_TICKS.remove(uuid);
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§7已离开范围，镜像视角启动取消"), true);
			return true;
		}

		if (ticksLeft <= 1) {
			VIEWPORT_ACTIVATION_DELAY_TICKS.remove(uuid);
			VIEWPORT_ACCUMULATED_TICKS.put(uuid, 0);
			VIEWPORT_ACTIVE.put(uuid, true);
			VIEWPORT_CLOSE_PROTECTION_TICKS.put(uuid, VIEWPORT_CLOSE_PROTECTION);
			syncToClient(player);
			return true;
		}

		VIEWPORT_ACTIVATION_DELAY_TICKS.put(uuid, ticksLeft - 1);
		return true;
	}

	private static void tickCloseProtection(UUID uuid) {
		Integer ticksLeft = VIEWPORT_CLOSE_PROTECTION_TICKS.get(uuid);
		if (ticksLeft == null) return;
		if (ticksLeft <= 1) {
			VIEWPORT_CLOSE_PROTECTION_TICKS.remove(uuid);
			return;
		}
		VIEWPORT_CLOSE_PROTECTION_TICKS.put(uuid, ticksLeft - 1);
	}

	public static void syncToClient(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);

		MirrorDataStore.SlotData s1 = (data.slots != null && data.slots.length > 0) ? data.slots[0] : null;
		MirrorDataStore.SlotData s2 = (data.slots != null && data.slots.length > 1) ? data.slots[1] : null;
		boolean active = VIEWPORT_ACTIVE.getOrDefault(uuid, false);

		ServerPlayNetworking.send(player, new StateS2C(
			s1 != null, s1 != null ? s1.hsPos().x : 0, s1 != null ? s1.hsPos().y : 0, s1 != null ? s1.hsPos().z : 0,
			s1 != null ? s1.mapPos().x : 0, s1 != null ? s1.mapPos().y : 0, s1 != null ? s1.mapPos().z : 0,
			s1 != null ? s1.radius() : 0,
			s2 != null, s2 != null ? s2.hsPos().x : 0, s2 != null ? s2.hsPos().y : 0, s2 != null ? s2.hsPos().z : 0,
			s2 != null ? s2.mapPos().x : 0, s2 != null ? s2.mapPos().y : 0, s2 != null ? s2.mapPos().z : 0,
			s2 != null ? s2.radius() : 0,
			active
		));
	}

	private static boolean hasAnySlot(MirrorDataStore.PlayerData data) {
		if (data.slots == null) return false;
		for (MirrorDataStore.SlotData slot : data.slots) {
			if (slot != null) return true;
		}
		return false;
	}

	private static boolean isInAnyRange(ServerPlayerEntity player, MirrorDataStore.PlayerData data) {
		if (data.slots == null) return false;
		Vec3d playerPos = player.getPos();
		for (MirrorDataStore.SlotData slot : data.slots) {
			if (slot != null && playerPos.distanceTo(slot.hsPos()) <= slot.radius()) {
				return true;
			}
		}
		return false;
	}

	private static String fmtPos(Vec3d v) {
		return String.format("%.1f, %.1f, %.1f", v.x, v.y, v.z);
	}
}
