package com.kuilunfuzhe.monvhua.command.mirror;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
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

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("clairvoyance-mirror")
			.requires(source -> source.hasPermissionLevel(2))
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
					.then(CommandManager.literal("hsPos-设定点")
						.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
							.executes(ctx -> executeSetHsPos(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								Vec3ArgumentType.getVec3(ctx, "pos")))))
					.then(CommandManager.literal("mapPos-映射点")
						.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
							.executes(ctx -> executeSetMapPos(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								Vec3ArgumentType.getVec3(ctx, "pos")))))
					.then(CommandManager.literal("radius-半径")
						.then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 200.0))
							.executes(ctx -> executeSetRadius(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "slot"),
								DoubleArgumentType.getDouble(ctx, "radius")))))))
			.then(CommandManager.literal("remove-移除")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
					.executes(ctx -> executeRemove(ctx.getSource(),
						IntegerArgumentType.getInteger(ctx, "slot")))))
			.then(CommandManager.literal("list-列出所有")
				.executes(ctx -> executeList(ctx.getSource())))
			.then(CommandManager.literal("clear-清除")
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
				source.sendMessage(Text.literal("§7镜面 " + (i + 1) + ": §7未设置"));
			}
		}

		boolean active = VIEWPORT_ACTIVE.getOrDefault(player.getUuid(), false);
		source.sendMessage(Text.literal("§6镜像视图: " + (active ? "§a开启" : "§7关闭")));

		if (!anyActive) {
			source.sendMessage(Text.literal("§7使用 /clairvoyance-mirror set <1|2> {hsPos|mapPos|radius} <值> 设置镜面"));
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

	/** 切换镜面视口 */
	public static void toggleViewport(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);

		boolean hasAny = false;
		if (data.slots != null) {
			for (MirrorDataStore.SlotData s : data.slots) {
				if (s != null) { hasAny = true; break; }
			}
		}

		if (!hasAny) {
			player.sendMessage(Text.literal("§c请先使用 /clairvoyance-mirror set <1|2> {hsPos|mapPos|radius} <值> 设置至少一个镜面"), false);
			return;
		}

		boolean current = VIEWPORT_ACTIVE.getOrDefault(uuid, false);

		if (!current) {
			// 检查玩家是否在某个镜面的 hs 触发范围内
			boolean inRange = false;
			for (MirrorDataStore.SlotData s : data.slots) {
				if (s != null && player.getPos().distanceTo(s.hsPos()) <= s.radius()) {
					inRange = true;
					break;
				}
			}
			if (!inRange) {
				player.sendMessage(Text.literal("§c你不在任何镜面的触发范围内"), true);
				return;
			}

			// 检查阶段配置
			int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
			MirrorConfig config = MirrorConfig.getInstance();
			int usesLeft = config.getViewCount(stage);

			if (usesLeft <= 0) {
				player.sendMessage(Text.literal("§c当前阶段无法使用镜子"), true);
				return;
			}

			VIEWPORT_USES_LEFT.put(uuid, usesLeft);
			VIEWPORT_ACCUMULATED_TICKS.put(uuid, 0);
			player.sendMessage(Text.literal("§a镜像视图已开启 (剩余" + usesLeft + "次)"), true);
		} else {
			player.sendMessage(Text.literal("§7镜像视图已关闭"), true);
		}

		VIEWPORT_ACTIVE.put(uuid, !current);
		syncToClient(player);
	}

	/** 每 tick 处理视口逻辑（次数消耗 + 离开范围自动结束） */
	public static void tickViewports(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (!VIEWPORT_ACTIVE.getOrDefault(uuid, false)) return;

		// 检查是否在所有镜面的触发范围外
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);
		boolean inAnyRange = false;
		if (data.slots != null) {
			Vec3d playerPos = player.getPos();
			for (MirrorDataStore.SlotData s : data.slots) {
				if (s != null && playerPos.distanceTo(s.hsPos()) <= s.radius()) {
					inAnyRange = true;
					break;
				}
			}
		}
		if (!inAnyRange) {
			// 离开范围自动结束
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_USES_LEFT.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§7已离开镜面触发范围，镜像视图结束"), true);
			return;
		}

		int stage = Evil_Eyes.getPlayerStage(player, Evil_Eyes.configManager);
		MirrorConfig config = MirrorConfig.getInstance();

		int watchTimeTicks = config.getWatchTime(stage) * 20;
		double successRate = config.getSuccessRate(stage);
		int usesLeft = VIEWPORT_USES_LEFT.getOrDefault(uuid, 0);

		if (usesLeft <= 0) {
			VIEWPORT_ACTIVE.put(uuid, false);
			VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
			VIEWPORT_USES_LEFT.remove(uuid);
			syncToClient(player);
			player.sendMessage(Text.literal("§c本次观看次数已用完"), true);
			return;
		}

		int accumulated = VIEWPORT_ACCUMULATED_TICKS.getOrDefault(uuid, 0) + 1;
		VIEWPORT_ACCUMULATED_TICKS.put(uuid, accumulated);

		if (accumulated >= watchTimeTicks) {
			VIEWPORT_ACCUMULATED_TICKS.put(uuid, 0);

			if (player.getRandom().nextDouble() < successRate) {
				usesLeft--;
				VIEWPORT_USES_LEFT.put(uuid, usesLeft);

				if (usesLeft <= 0) {
					VIEWPORT_ACTIVE.put(uuid, false);
					VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
					VIEWPORT_USES_LEFT.remove(uuid);
					player.sendMessage(Text.literal("§c本次观看次数已用完"), true);
				} else {
					player.sendMessage(Text.literal("§a观看成功! 剩余" + usesLeft + "次"), true);
				}
				syncToClient(player);
			}
		}
	}

	/** 清理玩家所有镜像状态 */
	public static void cleanup(UUID uuid) {
		VIEWPORT_ACTIVE.remove(uuid);
		VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
		VIEWPORT_USES_LEFT.remove(uuid);
	}

	/** 同步镜面状态给客户端 */
	public static void syncToClient(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		MirrorDataStore.PlayerData data = MirrorDataStore.getOrCreate(uuid);

		MirrorDataStore.SlotData s1 = (data.slots != null && data.slots.length > 0) ? data.slots[0] : null;
		MirrorDataStore.SlotData s2 = (data.slots != null && data.slots.length > 1) ? data.slots[1] : null;
		boolean active = VIEWPORT_ACTIVE.getOrDefault(uuid, false);

		ServerPlayNetworking.send(player, new MirrorStateS2CPacket(
			s1 != null, s1 != null ? s1.hsPos().x : 0, s1 != null ? s1.hsPos().y : 0, s1 != null ? s1.hsPos().z : 0,
			s1 != null ? s1.mapPos().x : 0, s1 != null ? s1.mapPos().y : 0, s1 != null ? s1.mapPos().z : 0,
			s1 != null ? s1.radius() : 0,
			s2 != null, s2 != null ? s2.hsPos().x : 0, s2 != null ? s2.hsPos().y : 0, s2 != null ? s2.hsPos().z : 0,
			s2 != null ? s2.mapPos().x : 0, s2 != null ? s2.mapPos().y : 0, s2 != null ? s2.mapPos().z : 0,
			s2 != null ? s2.radius() : 0,
			active
		));
	}

	private static String fmtPos(Vec3d v) {
		return String.format("%.1f, %.1f, %.1f", v.x, v.y, v.z);
	}
}
