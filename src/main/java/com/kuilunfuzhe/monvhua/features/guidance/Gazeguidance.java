package com.kuilunfuzhe.monvhua.features.guidance;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.item.gazeguidance.ModItems;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
//import com.shushuwonie.client.network.gazeguidance.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Gazeguidance {
	public static final String MOD_ID = "gazeguidance";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final long COOLDOWN_TICKS = 100;
	private static final Map<UUID, Long> lastFocusEndTime = new ConcurrentHashMap<>();

	private static final Map<UUID, Double> playerEnergy = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<UUID, Long>> playerMarkedEntities = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> markedEntityOwners = new ConcurrentHashMap<>();
	private static final Map<UUID, FocusState> playerFocusStates = new ConcurrentHashMap<>();

	private static final Map<UUID, Integer> lastSentStage = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> focusStartTime = new ConcurrentHashMap<>();

	private static int globalEnergySyncTick = 0;
	private static int particleTickCounter = 0;

	// 焦点类型枚举
	private enum FocusType { SELF, ENTITY, STATIC }
	private record FocusState(UUID focusEntityId, FocusType focusType) {}

	private static final Set<String> SPECIAL_NAMES = Set.of("shushuwonie", "Remio","Ice_in_North");

	// 禁用状态（内存存储，服务器重启后重置）
	private static final Set<UUID> IMAGES_DISABLED = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> PARTICLES_DISABLED = ConcurrentHashMap.newKeySet();


	// ========== 阶段计算 ==========
	private static int getStageFromScore(int score) {
		if (score <= 5) return 1;
		else if (score <= 20) return 2;
		else if (score <= 40) return 3;
		else if (score <= 60) return 4;
		else if (score <= 70) return 5;
		else if (score <= 80) return 6;
		else return 7;
	}

	private static int getPlayerStage(PlayerEntity player) {
		Scoreboard scoreboard = player.getWorld().getScoreboard();
		var objective = scoreboard.getNullableObjective("monvhua");
		if (objective == null) return 1;
		var score = scoreboard.getScore(player, objective);
		if (score == null) return 1;
		int rawScore = Math.max(0, Math.min(100, score.getScore()));
		return getStageFromScore(rawScore);
	}

	// ========== 阶段属性 ==========
	private static double getAttractRadiusFromStage(int stage) {
		return GazeConfig.getInstance().getRadius(stage);
	}

	private static int getMaxMarkedCount(int stage) {
		return GazeConfig.getInstance().getMaxMarks(stage);
	}

	private static void sendStageIfChanged(ServerPlayerEntity player, int newStage) {
		UUID uuid = player.getUuid();
		Integer last = lastSentStage.get(uuid);
		if (last == null || last != newStage) {
			lastSentStage.put(uuid, newStage);
			double drain = GazeConfig.getInstance().getEnergyDrain(newStage);
			double regen = GazeConfig.getInstance().getEnergyRegen(newStage);
			double range = GazeConfig.getInstance().getRadius(newStage);
			int maxMarks = GazeConfig.getInstance().getMaxMarks(newStage);
			ServerPlayNetworking.send(player, new StrengthPacket(newStage, drain, regen, range, maxMarks));
//			LOGGER.info("发送阶段 {} 消耗={} 回复={} 范围={} 最大标记={}", newStage, drain, regen, range, maxMarks);
		}
	}


	public static void initialize() {


		// 命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("clairvoyance_千里眼")
						.then(CommandManager.literal("resetcooldown_重置冷却")
								.executes(context -> {
									PlayerEntity player = context.getSource().getPlayer();
									if (player != null) {
										lastFocusEndTime.remove(player.getUuid());
										context.getSource().sendFeedback(() -> Text.literal("§a已重置你的诱导冷却"), false);
									}
									return 1;
								})
								.then(CommandManager.argument("target", EntityArgumentType.player())
										.requires(source -> source.hasPermissionLevel(2))
										.executes(context -> {
											PlayerEntity target = EntityArgumentType.getPlayer(context, "target");
											lastFocusEndTime.remove(target.getUuid());
											context.getSource().sendFeedback(() -> Text.literal("§a已重置 " + tag_pitch.entityDisplayName(target) + " 的诱导冷却"), false);
											return 1;
										})
								)

						)
						.then(CommandManager.literal("clearmarks_清除诱导标记实体")
								.requires(source -> source.hasPermissionLevel(2))  // 需要 OP 权限（2级）或创造模式可自行调整
								.executes(context -> {
									playerMarkedEntities.clear();
									markedEntityOwners.clear();
									// 广播标记数量清零给所有在线玩家
									for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
										syncMarkedState(player);
									}
									context.getSource().sendFeedback(() -> Text.literal("§a已清除所有标记实体"), false);
									return 1;
								})
						)

						// 在 CommandRegistrationCallback 内部添加以下两个 then
						.then(CommandManager.literal("toggleimages_开关图片")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.argument("target", EntityArgumentType.player())
										.executes(context -> {
											ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
											UUID uuid = target.getUuid();
											// 当前是否被禁用？如果禁用，则要开启；否则要关闭。
											boolean currentlyDisabled = IMAGES_DISABLED.contains(uuid);
											boolean newState = currentlyDisabled;  // 新状态：true=开启，false=关闭
											if (currentlyDisabled) {
												IMAGES_DISABLED.remove(uuid);
											} else {
												IMAGES_DISABLED.add(uuid);
											}
											ServerPlayNetworking.send(target, new ToggleImagesS2CPacket(newState));
											context.getSource().sendFeedback(() ->
													Text.literal("§a已" + (newState ? "开启" : "关闭") + "玩家 " + tag_pitch.entityDisplayName(target) + " 的图片显示"), false);
											return 1;
										})
								)
						)
						.then(CommandManager.literal("toggleparticles_开关粒子")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.argument("target", EntityArgumentType.player())
										.executes(context -> {
											ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
											UUID uuid = target.getUuid();
											boolean currentlyDisabled = PARTICLES_DISABLED.contains(uuid);
											boolean newState = currentlyDisabled;  // 新状态：true=开启，false=关闭
											if (currentlyDisabled) {
												PARTICLES_DISABLED.remove(uuid);
											} else {
												PARTICLES_DISABLED.add(uuid);
											}
											// 粒子环不需要客户端包，服务端直接判断集合即可
											context.getSource().sendFeedback(() ->
													Text.literal("§a已" + (newState ? "开启" : "关闭") + "玩家 " + tag_pitch.entityDisplayName(target) + " 的粒子环显示"), false);
											return 1;
										})
								)
						)
				)
		);

		// 注册网络包


		// 禁止左键破坏方块
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			ItemStack stack = player.getStackInHand(hand);
			return stack.getItem() == ModItems.MAGIC_STICK ? ActionResult.FAIL : ActionResult.PASS;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanupPlayerState(handler.player));

		// 右键长按开始/结束诱导
		ServerPlayNetworking.registerGlobalReceiver(RightClickActionPacket.ID, (payload, context) -> {
			context.server().execute(() -> {
				PlayerEntity player = context.player();
                    if (player.getCommandTags().contains("Silenced")) { player.sendMessage(net.minecraft.text.Text.literal("§c你难以集中精神"), true); return; }
				if (player.getMainHandStack().getItem() != ModItems.MAGIC_STICK) return;
				if (payload.start()) {
					setTemporaryFocus(player, player.getWorld(), player.isSneaking());
				} else {
					endFocus(player);
				}
			});
		});
		// 客户端请求配置
		ServerPlayNetworking.registerGlobalReceiver(RequestConfigC2SPacket.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			String json = GazeConfig.getInstance().toJson(); // 需要 GazeConfig 有 toJson()
			ServerPlayNetworking.send(player, new SyncConfigS2CPacket(json));
		});

// 客户端更新配置
		ServerPlayNetworking.registerGlobalReceiver(UpdateConfigC2SPacket.ID, (payload, context) -> {
			context.server().execute(() -> {
				GazeConfig newConfig = GazeConfig.fromJson(payload.json());
				GazeConfig.setInstance(newConfig);
				// 广播新配置给所有在线玩家（无论模式）
				for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
					ServerPlayNetworking.send(player, new SyncConfigS2CPacket(newConfig.toJson()));
				}
				// 仅当发起更新的玩家是创造模式时，显示成功提示（通常已经是创造模式，但二次确认）
				if (context.player() != null && context.player().isCreative()) {
					context.player().sendMessage(Text.literal("§a配置已更新并同步至所有玩家"), false);
				}
			});
		});
		// G 键标记实体（支持取消）
		ServerPlayNetworking.registerGlobalReceiver(MagicPacket.ID, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				var world = player.getWorld();
                    if (player.getCommandTags().contains("Silenced")) { player.sendMessage(net.minecraft.text.Text.literal("§c你难以集中精神"), true); return; }
				var entity = world.getEntityById(payload.entityId());
				if (entity instanceof LivingEntity target && player.getMainHandStack().getItem() == ModItems.MAGIC_STICK) {
					double maxRange = 30.0;
					if (player.squaredDistanceTo(target) > maxRange * maxRange) {
						player.sendMessage(Text.literal("§c目标过远，无法标记"), true);
						return;
					}

					UUID targetUuid = target.getUuid();
					int stage = getPlayerStage(player);
					int maxMarks = getMaxMarkedCount(stage);

					Map<UUID, Long> marks = getMarks(player.getUuid());
					if (marks.containsKey(targetUuid)) {
						// 取消标记
						removeMark(player.getUuid(), targetUuid);
						player.sendMessage(Text.literal("§e已取消标记 " + tag_pitch.entityDisplayName(target)), true);
						if (player instanceof ServerPlayerEntity sp) {
							sendStageIfChanged(sp, stage);
							syncMarkedState(sp);
						}
					} else {
						UUID owner = markedEntityOwners.get(targetUuid);
						if (owner != null && !owner.equals(player.getUuid())) {
							player.sendMessage(Text.literal("§c该实体已被其他玩家标记"), true);
							return;
						}
						if (marks.size() >= maxMarks) {
							player.sendMessage(Text.literal("§c标记已达上限，无法继续标记"), true);
							return;
						}
						long expiryTime = world.getTime() + 1200;
						addMark(player.getUuid(), targetUuid, expiryTime);
						player.sendMessage(Text.literal("§a已标记 " + tag_pitch.entityDisplayName(target) + " (持续60秒)"), true);
						if (player instanceof ServerPlayerEntity sp) {
							sendStageIfChanged(sp, stage);
							syncMarkedState(sp);
						}
//						LOGGER.info("标记 {} (阶段 {})", target.getName().getString(), stage);
					}
				}
			});
		});

		// 每 tick 处理
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				FocusState state = playerFocusStates.get(player.getUuid());
				if (state == null) continue;

				int newStage = getPlayerStage(player);
				sendStageIfChanged(player, newStage);

				Entity focus = findEntity(server, state.focusEntityId());
				if (focus == null || !focus.isAlive()) {
					endFocus(player);
					continue;
				}

				boolean valid = player.getMainHandStack().getItem() == ModItems.MAGIC_STICK;
				if (valid && state.focusType() == FocusType.SELF && !player.isSneaking()) {
					valid = false;
				}
				if (!valid) {
					endFocus(player);
					player.sendMessage(Text.literal("§c诱导条件不满足，已结束"), true);
				}
			}

			cleanupExpiredMarks(server);

			if (server.getTicks() % 5 == 0) {
				autoMarkEntitiesLookingAtHolders(server);
			}

			// 能量管理：各玩家独立
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				FocusState state = playerFocusStates.get(player.getUuid());
				if (state == null) continue;
				Entity focus = findEntity(server, state.focusEntityId());
				if (focus == null || !focus.isAlive()) continue;

				int stage = getPlayerStage(player);
				int markedCount = getMarkCount(player.getUuid());
				double drainRate = GazeConfig.getInstance().getEnergyDrain(stage);
				double energyCost = markedCount * drainRate / 20.0;
				double currentEnergy = playerEnergy.getOrDefault(player.getUuid(), GazeConfig.getInstance().maxEnergy);
				currentEnergy -= energyCost;
				if (currentEnergy <= 0) {
					endFocus(player);
					player.sendMessage(Text.literal("§c能量耗尽，诱导结束"), true);
				} else {
					playerEnergy.put(player.getUuid(), currentEnergy);
				}
			}

			// 能量回复
			for (Map.Entry<UUID, Double> entry : playerEnergy.entrySet()) {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
				if (p == null) continue;
				if (!playerFocusStates.containsKey(p.getUuid())) {
					double regenRate = GazeConfig.getInstance().getEnergyRegen(getPlayerStage(p));
					double newEnergy = entry.getValue() + regenRate / 20.0;
					if (newEnergy > GazeConfig.getInstance().maxEnergy) newEnergy = GazeConfig.getInstance().maxEnergy;
					playerEnergy.put(p.getUuid(), newEnergy);
				}
			}

			// 每 5 tick 同步能量
			globalEnergySyncTick++;
			if (globalEnergySyncTick >= 5) {
				globalEnergySyncTick = 0;
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					if (player.getMainHandStack().getItem() == ModItems.MAGIC_STICK) {
						double current = playerEnergy.getOrDefault(player.getUuid(), GazeConfig.getInstance().maxEnergy);
						double max = GazeConfig.getInstance().maxEnergy;
						ServerPlayNetworking.send(player, new EnergySyncPacket(current, max));
					}
				}
			}

			// 每 tick 递增粒子计数器
			// 每 tick 递增粒子计数器
			particleTickCounter++;
			if (particleTickCounter >= 15) {
				particleTickCounter = 0;
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					Map<UUID, Long> marks = playerMarkedEntities.get(player.getUuid());
					if (marks == null || marks.isEmpty()) continue;
					for (UUID uuid : marks.keySet()) {
						Entity e = findEntity(server, uuid);
						if (e instanceof LivingEntity living && living.isAlive()) {
							ServerPlayNetworking.send(player, new MarkParticleS2CPacket(living.getPos()));
						}
					}
				}
			}





			// 吸引逻辑
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				FocusState state = playerFocusStates.get(player.getUuid());
				if (state == null) continue;
				Entity focus = findEntity(server, state.focusEntityId());
				if (focus == null || !focus.isAlive()) continue;
				Vec3d focusPos = focus.getPos();
				double attractRadius = getAttractRadiusFromStage(getPlayerStage(player));
				Map<UUID, Long> marks = playerMarkedEntities.get(player.getUuid());
				if (marks == null || marks.isEmpty()) continue;
				for (UUID uuid : marks.keySet()) {
					Entity e = findEntity(server, uuid);
					if (e instanceof LivingEntity living && living.isAlive() && !living.getUuid().equals(state.focusEntityId())) {
						if (living.getPos().squaredDistanceTo(focusPos) <= attractRadius * attractRadius) {
							living.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, focusPos);
						}
					}
				}
			}


		});

//		LOGGER.info("GazeGuidance initialized with energy system. Press I (creative mode) to config stages.");

			//==============生成环================//
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 10 != 0) return; // 每 10 tick 生成一次
			World world = server.getWorld(World.OVERWORLD);
			if (!(world instanceof ServerWorld serverWorld)) return;
			long tick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				// 1. 检查粒子显示开关是否被禁用
				if (PARTICLES_DISABLED.contains(player.getUuid())) continue;
				// 2. 获取玩家阶段，只有 stage 7 才显示粒子环（与图片逻辑对齐）
				int stage = getPlayerStage(player);
				if (stage != 7) continue;

				boolean isSpecial = SPECIAL_NAMES.contains(player.getName().getString());
				for (ParticleRingConfig config : PARTICLE_RINGS) {
					boolean match = (config.onlyForPlayer != null && config.onlyForPlayer.equals(player.getName().getString()))
							|| (config.onlyForSpecial && isSpecial);
					if (!match) continue;
					spawnCustomRingParticle(serverWorld, player, config, tick);
				}
			}
		});


		// 每秒检查所有玩家阶段变化，并推送 StrengthPacket 给手持魔法棒的玩家
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) return; // 每秒一次
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				ItemStack mainHand = player.getMainHandStack();
				if (mainHand.getItem() != ModItems.MAGIC_STICK) continue; // 只处理手持魔法棒的玩家

				int newStage = getPlayerStage(player);
				UUID uuid = player.getUuid();
				Integer lastStage = lastSentStage.get(uuid);
				if (lastStage == null || lastStage != newStage) {
					// 阶段变化，发送 StrengthPacket
					sendStageIfChanged(player, newStage);
					lastSentStage.put(uuid, newStage);
				}
			}
		});


	}

	// 结束诱导
	private static void endFocus(PlayerEntity player) {
		FocusState state = playerFocusStates.remove(player.getUuid());
		if (state == null) {
			return;
		}

		Long startTime = focusStartTime.get(player.getUuid());
		long duration = 0;
		if (startTime != null) {
			duration = player.getWorld().getTime() - startTime;
			focusStartTime.remove(player.getUuid());
		}
		if (duration >= 40) {
			lastFocusEndTime.put(player.getUuid(), player.getWorld().getTime());
			player.sendMessage(Text.literal("§e诱导结束，进入5秒冷却"), true);
		} else {
			player.sendMessage(Text.literal("§7诱导结束（持续时间过短，无冷却）"), true);
		}

		if (state.focusType() == FocusType.STATIC) {
			Entity old = findEntity(player.getServer(), state.focusEntityId());
			if (old instanceof ArmorStandEntity) old.discard();
		}

		clearMarksForPlayer(player.getUuid());

		if (player instanceof ServerPlayerEntity sp) {
			ServerPlayNetworking.send(sp, new FocusStatusPacket(false));
			syncMarkedState(sp);
		}
	}

	private static void cleanupPlayerState(PlayerEntity player) {
		FocusState state = playerFocusStates.remove(player.getUuid());
		if (state != null && state.focusType() == FocusType.STATIC) {
			Entity old = findEntity(player.getServer(), state.focusEntityId());
			if (old instanceof ArmorStandEntity) old.discard();
		}
		clearMarksForPlayer(player.getUuid());
		focusStartTime.remove(player.getUuid());
		lastSentStage.remove(player.getUuid());
	}

	private static void setTemporaryFocus(PlayerEntity player, World world, boolean self) {
		long now = world.getTime();
		Long lastEnd = lastFocusEndTime.get(player.getUuid());
		if (lastEnd != null && now - lastEnd < COOLDOWN_TICKS) {
			long remaining = (COOLDOWN_TICKS - (now - lastEnd)) / 20;
			player.sendMessage(Text.literal("§c诱导冷却中，还需 " + remaining + " 秒"), true);
			return;
		}

		if (playerFocusStates.containsKey(player.getUuid())) {
			player.sendMessage(Text.literal("§c你已经有一个诱导焦点，请先结束"), true);
			return;
		}

		ServerWorld serverWorld = (ServerWorld) world;

		// 初始化能量
		double maxEnergy = GazeConfig.getInstance().maxEnergy;
		playerEnergy.compute(player.getUuid(), (uuid, energy) ->
				energy == null ? maxEnergy : Math.max(0.0, Math.min(energy, maxEnergy)));

		int stage = getPlayerStage(player);
		if (player instanceof ServerPlayerEntity sp) {
			sendStageIfChanged(sp, stage);
		}

		if (self) {
			playerFocusStates.put(player.getUuid(), new FocusState(player.getUuid(), FocusType.SELF));
			player.sendMessage(Text.literal("诱导开启 (自己)"), true);
		} else {
			double placementRange = 20.0;
			Entity targetEntity = getTargetEntity(player, placementRange);
			if (targetEntity != null) {
				playerFocusStates.put(player.getUuid(), new FocusState(targetEntity.getUuid(), FocusType.ENTITY));
				player.sendMessage(Text.literal("诱导开启 (实体)"), true);
			} else {
				Vec3d hitPos = getPlayerLookBlockPosition(player, placementRange);
				if (hitPos != null) {
					ArmorStandEntity focus = new ArmorStandEntity(EntityType.ARMOR_STAND, serverWorld);
					focus.setInvisible(true);
					focus.setInvulnerable(true);
					focus.setNoGravity(true);
					focus.setPosition(hitPos.x, hitPos.y, hitPos.z);
					serverWorld.spawnEntity(focus);
					playerFocusStates.put(player.getUuid(), new FocusState(focus.getUuid(), FocusType.STATIC));
					if (player instanceof ServerPlayerEntity sp) {
						ServerPlayNetworking.send(sp, new ParticlePacket(hitPos));
					}
					player.sendMessage(Text.literal("诱导开启 (静态点)"), true);
				} else {
					player.sendMessage(Text.literal("无法诱导，请对准实体或方块"), true);
					return;
				}
			}
		}
		if (player instanceof ServerPlayerEntity sp) {
			ServerPlayNetworking.send(sp, new FocusStatusPacket(true));
			ServerPlayNetworking.send(sp, new EnergySyncPacket(playerEnergy.getOrDefault(player.getUuid(), maxEnergy), maxEnergy));
		}
		focusStartTime.put(player.getUuid(), world.getTime());
	}

	private static Map<UUID, Long> getMarks(UUID playerUuid) {
		return playerMarkedEntities.computeIfAbsent(playerUuid, key -> new ConcurrentHashMap<>());
	}

	private static int getMarkCount(UUID playerUuid) {
		Map<UUID, Long> marks = playerMarkedEntities.get(playerUuid);
		return marks == null ? 0 : marks.size();
	}

	private static void syncMarkedState(ServerPlayerEntity player) {
		List<MarkedListPacket.Entry> entries = new ArrayList<>();
		Map<UUID, Long> marks = playerMarkedEntities.get(player.getUuid());
		if (marks != null) {
			for (UUID entityUuid : marks.keySet()) {
				Entity entity = findEntity(player.getServer(), entityUuid);
				entries.add(new MarkedListPacket.Entry(
						entity == null ? "未知实体" : entity.getName().getString(),
						tag_pitch.tagForEntity(entity)));
			}
		}
		ServerPlayNetworking.send(player, new MarkCountPacket(entries.size()));
		ServerPlayNetworking.send(player, new MarkedListPacket(entries));
	}

	private static void addMark(UUID playerUuid, UUID entityUuid, long expiryTime) {
		getMarks(playerUuid).put(entityUuid, expiryTime);
		markedEntityOwners.put(entityUuid, playerUuid);
	}

	private static void removeMark(UUID playerUuid, UUID entityUuid) {
		Map<UUID, Long> marks = playerMarkedEntities.get(playerUuid);
		if (marks != null) {
			marks.remove(entityUuid);
			if (marks.isEmpty()) {
				playerMarkedEntities.remove(playerUuid);
			}
		}
		markedEntityOwners.remove(entityUuid, playerUuid);
	}

	private static void clearMarksForPlayer(UUID playerUuid) {
		Map<UUID, Long> marks = playerMarkedEntities.remove(playerUuid);
		if (marks == null) {
			return;
		}
		for (UUID entityUuid : marks.keySet()) {
			markedEntityOwners.remove(entityUuid, playerUuid);
		}
	}

	private static void cleanupExpiredMarks(MinecraftServer server) {
		long now = getServerTime(server);
		for (Map.Entry<UUID, Map<UUID, Long>> playerEntry : new ArrayList<>(playerMarkedEntities.entrySet())) {
			UUID playerUuid = playerEntry.getKey();
			Map<UUID, Long> marks = playerEntry.getValue();
			boolean removed = false;
			Iterator<Map.Entry<UUID, Long>> iterator = marks.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Long> mark = iterator.next();
				Entity entity = findEntity(server, mark.getKey());
				if (mark.getValue() <= now || entity == null || !entity.isAlive()) {
					iterator.remove();
					markedEntityOwners.remove(mark.getKey(), playerUuid);
					removed = true;
				}
			}
			if (marks.isEmpty()) {
				playerMarkedEntities.remove(playerUuid);
			}
			if (removed) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
				if (player != null) {
					syncMarkedState(player);
				}
			}
		}
	}

	private static void autoMarkEntitiesLookingAtHolders(MinecraftServer server) {
		long expiryTime = getServerTime(server) + 1200;
		for (ServerPlayerEntity holder : server.getPlayerManager().getPlayerList()) {
			if (holder.getMainHandStack().getItem() != ModItems.MAGIC_STICK) continue;
			if (holder.getCommandTags().contains("Silenced")) continue;

			int stage = getPlayerStage(holder);
			int maxMarks = getMaxMarkedCount(stage);
			Map<UUID, Long> marks = getMarks(holder.getUuid());

			ServerWorld world = holder.getWorld();
			Box searchBox = holder.getBoundingBox().expand(30.0);
			for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox,
					e -> e.isAlive() && !e.getUuid().equals(holder.getUuid()))) {
				UUID entityUuid = entity.getUuid();
				if (marks.containsKey(entityUuid)) {
					addMark(holder.getUuid(), entityUuid, expiryTime);
					continue;
				}
				UUID owner = markedEntityOwners.get(entityUuid);
				if (owner != null && !owner.equals(holder.getUuid())) continue;
				if (marks.size() >= maxMarks) break;
				if (!canEntitySeePlayer(entity, holder, 30.0)) continue;

				addMark(holder.getUuid(), entityUuid, expiryTime);
				sendStageIfChanged(holder, stage);
				syncMarkedState(holder);
			}
		}
	}

	private static boolean canEntitySeePlayer(LivingEntity entity, ServerPlayerEntity player, double maxRange) {
		Vec3d start = entity.getEyePos();
		Vec3d target = player.getEyePos();
		if (start.squaredDistanceTo(target) > maxRange * maxRange) {
			return false;
		}
		Vec3d toPlayer = target.subtract(start).normalize();
		if (entity.getRotationVec(1.0F).dotProduct(toPlayer) < 0.75D) {
			return false;
		}
		BlockHitResult blockHit = entity.getWorld().raycast(new RaycastContext(start, target,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
		return blockHit.getType() == HitResult.Type.MISS || blockHit.getPos().squaredDistanceTo(start) >= target.squaredDistanceTo(start) - 0.01D;
	}

	private static Entity findEntity(MinecraftServer server, UUID entityUuid) {
		if (server == null || entityUuid == null) {
			return null;
		}
		for (ServerWorld world : server.getWorlds()) {
			Entity entity = world.getEntity(entityUuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static long getServerTime(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		return world == null ? server.getTicks() : world.getTime();
	}

	private static Entity getTargetEntity(PlayerEntity player, double maxRange) {
		World world = player.getWorld();
		Vec3d start = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0f);
		Vec3d end = start.add(direction.multiply(maxRange));

		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		double blockDistSq = blockHit.getPos().squaredDistanceTo(start);

		Box searchBox = player.getBoundingBox().stretch(direction.multiply(maxRange)).expand(1.0);
		Entity closest = null;
		double closestDistSq = maxRange * maxRange;

		for (Entity entity : world.getOtherEntities(player, searchBox,
				e -> e instanceof LivingEntity && e.isAlive())) {
			Vec3d hitPos = entity.getBoundingBox().raycast(start, end).orElse(null);
			if (hitPos != null) {
				double distSq = start.squaredDistanceTo(hitPos);
				if (distSq < closestDistSq && distSq < blockDistSq) {
					closestDistSq = distSq;
					closest = entity;
				}
			}
		}
		return closest;
	}

	private static Vec3d getPlayerLookBlockPosition(PlayerEntity player, double maxDistance) {
		Vec3d start = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0f);
		Vec3d end = start.add(direction.multiply(maxDistance));
		BlockHitResult result = player.getWorld().raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		return result.getType() == HitResult.Type.BLOCK ? result.getPos() : null;
	}


						//===================粒子效果=======================//
	public static class ParticleRingConfig {
		public final String name;
		public final double radius;            // 三叶草半径（形状大小）
		public final double sphereRadius;      // 球面半径（环中心到球心的距离）
		public final double rotationSpeed;     // 自转速度（度/秒）
		public final ParticleEffect particleEffect;
		public final int particleCount;
		public final double speedFactor;       // 速度因子（可忽略）
		public final double sphereCenterOffsetX;   // 球心 X 轴偏移（相对于玩家腰部）
		public final double sphereCenterOffsetY;   // 球心 Y 轴偏移（正向上）
		public final double sphereCenterOffsetZ;   // 球心 Z 轴偏移（正向前）
		public final float yaw;                 // 环中心方向偏航角（度，绕 Y 轴）
		public final float pitch;               // 环中心方向俯仰角（度，绕 X 轴）
		public final String onlyForPlayer;      // 指定玩家名，区分大小写
		public final boolean onlyForSpecial;    // 是否为特殊名字玩家

		public ParticleRingConfig(String name, double radius, double sphereRadius, double rotationSpeed,
								  ParticleEffect particleEffect, int particleCount, double speedFactor,
								  double sphereCenterOffsetX, double sphereCenterOffsetY, double sphereCenterOffsetZ,
								  float yaw, float pitch, String onlyForPlayer,boolean onlyForSpecial) {
			this.name = name;
			this.radius = radius;
			this.sphereRadius = sphereRadius;
			this.rotationSpeed = rotationSpeed;
			this.particleEffect = particleEffect;
			this.particleCount = particleCount;
			this.speedFactor = speedFactor;
			this.sphereCenterOffsetX = sphereCenterOffsetX;
			this.sphereCenterOffsetY = sphereCenterOffsetY;
			this.sphereCenterOffsetZ = sphereCenterOffsetZ;
			this.yaw = yaw;
			this.pitch = pitch;
			this.onlyForPlayer = onlyForPlayer;
			this.onlyForSpecial = onlyForSpecial;
		}
	}

	private static final List<ParticleRingConfig> PARTICLE_RINGS = new ArrayList<>();

	static {
		// 主环（白色，半径0.6，腰部，不旋转）
//		PARTICLE_RINGS.add(new ParticleRingConfig("main", 0.6, 0.0, 0.0, ParticleTypes.GLOW, 32, 0.0,0.0f,0,0,0));
		// 第二环（红色，半径0.9，腰部偏上，逆时针旋转30度/秒）
		PARTICLE_RINGS.add(new ParticleRingConfig("ice", 0.8, 5, 0, ParticleTypes.GLOW, 30, 0.0, 0.0f, 0.0f, 0.0, 130,45,"Ice_in_North",false));
		PARTICLE_RINGS.add(new ParticleRingConfig("ice", 0.8, 5, 0, ParticleTypes.GLOW, 30, 0.0, 0.0f, 0.0f, 0.0, -130,45,"Ice_in_North",false));
		// 第三环（绿色，半径1.2，头部高度，顺时针45度/秒）
		PARTICLE_RINGS.add(new ParticleRingConfig("ice", 2, 2, 0.5, ParticleTypes.END_ROD, 60, 0.0, 0.0f, 0, 0.0f, 180,-10,"Ice_in_North",false));
	}
	private static void spawnCustomRingParticle(ServerWorld world, ServerPlayerEntity player, ParticleRingConfig config, long tick) {




		// 1. 球心 = 玩家腰部 + 自定义偏移（绝对坐标）
		Vec3d playerCenter = player.getPos().add(0, player.getHeight() * 0.5, 0);
		Vec3d sphereCenter = playerCenter.add(config.sphereCenterOffsetX, config.sphereCenterOffsetY, config.sphereCenterOffsetZ);

		// 2. 计算玩家水平朝向（忽略俯仰）
		Vec3d lookVec = player.getRotationVec(1.0f);
		Vec3d forward = new Vec3d(lookVec.x, 0, lookVec.z).normalize();

		// 3. 根据玩家水平朝向 + 自定义偏航/俯仰，计算环中心的方向
		// 使用四元数旋转：先绕Y轴旋转 yaw，再绕新的右轴旋转 pitch
		org.joml.Quaternionf quat = new org.joml.Quaternionf();
		// 绕Y轴旋转 yaw
		quat.rotateY((float) Math.toRadians(config.yaw));
		// 应用旋转到 forward 向量
		org.joml.Vector3f dirVec = new org.joml.Vector3f((float) forward.x, 0, (float) forward.z).normalize();
		quat.transform(dirVec);
		// 计算右轴（用于俯仰旋转）
		org.joml.Vector3f rightVec = new org.joml.Vector3f(-dirVec.z, 0, dirVec.x).normalize();
		// 绕右轴旋转 pitch
		org.joml.Quaternionf quatPitch = new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(config.pitch), rightVec.x, rightVec.y, rightVec.z);
		quatPitch.transform(dirVec);
		Vec3d direction = new Vec3d(dirVec.x, dirVec.y, dirVec.z).normalize();

		// 4. 环中心 = 球心 + 方向 * 球面半径
		Vec3d ringCenter = sphereCenter.add(direction.multiply(config.sphereRadius));

		// 5. 法线方向：从环中心指向球心
		Vec3d normal = sphereCenter.subtract(ringCenter).normalize();

		// 6. 构建局部基向量（使圆环平面垂直于法线）
		Vec3d worldUp = new Vec3d(0, 1, 0);
		Vec3d localRight = worldUp.crossProduct(normal).normalize();
		if (localRight.lengthSquared() < 1e-6) { // 法线平行于世界Y轴时
			localRight = new Vec3d(1, 0, 0);
		}
		Vec3d localUp = normal.crossProduct(localRight).normalize();

		// 7. 自转角度
		double angleRad = Math.toRadians((tick / 20.0) * config.rotationSpeed);

		// 8. 生成粒子
		for (int i = 0; i < config.particleCount; i++) {
			double offsetAngle = 2 * Math.PI * i / config.particleCount;
			double theta = offsetAngle + angleRad;
			double r = config.radius * Math.abs(Math.cos(3 * theta)); // 三叶草半径
			double xLocal = r * Math.cos(theta);
			double yLocal = r * Math.sin(theta);
			// 世界坐标 = 环中心 + xLocal*localRight + yLocal*localUp
			double x = ringCenter.x + xLocal * localRight.x + yLocal * localUp.x;
			double y = ringCenter.y + xLocal * localRight.y + yLocal * localUp.y;
			double z = ringCenter.z + xLocal * localRight.z + yLocal * localUp.z;

			world.spawnParticles(config.particleEffect, x, y, z, 1, 0, 0, 0, 0.0);
		}
	}
}
