package com.kuilunfuzhe.monvhua.command;

import com.mojang.brigadier.CommandDispatcher;
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
	public static final Map<UUID, Vec3d[]> PLAYER_MIRRORS = new ConcurrentHashMap<>();
	public static final Map<UUID, Boolean> VIEWPORT_ACTIVE = new ConcurrentHashMap<>();

	// Config-driven tracking
	public static final Map<UUID, Integer> VIEWPORT_ACCUMULATED_TICKS = new ConcurrentHashMap<>();
	public static final Map<UUID, Integer> VIEWPORT_USES_LEFT = new ConcurrentHashMap<>();

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("mirror")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
								.executes(ctx -> executeSet(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "slot"),
										null))
								.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
										.executes(ctx -> executeSet(ctx.getSource(),
												IntegerArgumentType.getInteger(ctx, "slot"),
												Vec3ArgumentType.getVec3(ctx, "pos"))))))
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 2))
								.executes(ctx -> executeRemove(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "slot")))))
				.then(CommandManager.literal("list")
						.executes(ctx -> executeList(ctx.getSource())))
				.then(CommandManager.literal("clear")
						.executes(ctx -> executeClear(ctx.getSource())))
		);
	}

	private static int executeSet(ServerCommandSource source, int slot, Vec3d pos) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		Vec3d targetPos = (pos != null) ? pos : player.getPos();

		PLAYER_MIRRORS.compute(player.getUuid(), (uuid, existing) -> {
			if (existing == null) {
				Vec3d[] arr = new Vec3d[2];
				arr[slot - 1] = targetPos;
				return arr;
			}
			existing[slot - 1] = targetPos;
			return existing;
		});

		source.sendMessage(Text.literal("§a镜位 " + slot + " 已设置为: " +
				String.format("%.1f, %.1f, %.1f", targetPos.x, targetPos.y, targetPos.z)));

		syncToClient(player);
		return 1;
	}

	private static int executeRemove(ServerCommandSource source, int slot) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		Vec3d[] mirrors = PLAYER_MIRRORS.get(player.getUuid());
		if (mirrors != null) {
			mirrors[slot - 1] = null;
		}

		source.sendMessage(Text.literal("§a镜位 " + slot + " 已清除"));
		syncToClient(player);
		return 1;
	}

	private static int executeList(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		Vec3d[] mirrors = PLAYER_MIRRORS.get(player.getUuid());
		source.sendMessage(Text.literal("§b=== 镜位列表 ==="));

		boolean anyActive = false;
		for (int i = 0; i < 2; i++) {
			Vec3d p = (mirrors != null) ? mirrors[i] : null;
			if (p != null) {
				anyActive = true;
				source.sendMessage(Text.literal("§a镜位 " + (i + 1) + ": §f" +
						String.format("%.1f, %.1f, %.1f", p.x, p.y, p.z)));
			} else {
				source.sendMessage(Text.literal("§7镜位 " + (i + 1) + ": §7未设置"));
			}
		}

		boolean active = VIEWPORT_ACTIVE.getOrDefault(player.getUuid(), false);
		source.sendMessage(Text.literal("§6镜像视图: " + (active ? "§a开启" : "§7关闭")));

		if (!anyActive) {
			source.sendMessage(Text.literal("§7使用 /mirror set <1|2> [x y z] 设置镜位"));
		}
		return 1;
	}

	private static int executeClear(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) return 0;

		PLAYER_MIRRORS.remove(player.getUuid());
		source.sendMessage(Text.literal("§a所有镜位已清除"));
		syncToClient(player);
		return 1;
	}

	public static void toggleViewport(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		Vec3d[] mirrors = PLAYER_MIRRORS.get(uuid);

		boolean hasAny = false;
		if (mirrors != null) {
			for (Vec3d p : mirrors) {
				if (p != null) { hasAny = true; break; }
			}
		}

		if (!hasAny) {
			player.sendMessage(Text.literal("§c请先使用 /mirror set <1|2> 设置至少一个镜位"), false);
			return;
		}

		boolean current = VIEWPORT_ACTIVE.getOrDefault(uuid, false);

		if (!current) {
			// Enabling — check config
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

	public static void tickViewports(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (!VIEWPORT_ACTIVE.getOrDefault(uuid, false)) return;

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

	public static void cleanup(UUID uuid) {
		PLAYER_MIRRORS.remove(uuid);
		VIEWPORT_ACTIVE.remove(uuid);
		VIEWPORT_ACCUMULATED_TICKS.remove(uuid);
		VIEWPORT_USES_LEFT.remove(uuid);
	}

	public static void syncToClient(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		Vec3d[] mirrors = PLAYER_MIRRORS.get(uuid);

		Vec3d p1 = (mirrors != null && mirrors[0] != null) ? mirrors[0] : null;
		Vec3d p2 = (mirrors != null && mirrors[1] != null) ? mirrors[1] : null;
		boolean active = VIEWPORT_ACTIVE.getOrDefault(uuid, false);

		ServerPlayNetworking.send(player, new MirrorStateS2CPacket(
				p1 != null, p1 != null ? p1.x : 0, p1 != null ? p1.y : 0, p1 != null ? p1.z : 0,
				p2 != null, p2 != null ? p2.x : 0, p2 != null ? p2.y : 0, p2 != null ? p2.z : 0,
				active
		));
	}
}
