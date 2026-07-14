package com.kuilunfuzhe.monvhua.features.carryentity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.hold_hands.HoldHandsManager;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets;
import com.kuilunfuzhe.monvhua.network.openback.CarryEntityPayload;
import com.kuilunfuzhe.monvhua.network.openback.PlaceCarriedEntityPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;

import java.util.Map;

/**
 * 搬运事件注册中心。
 * 注册服务端刻搬运更新、摔落伤害共享、断线清理、网络包接收
 * （搬运请求/放下请求）、以及 /carry-xp-rate 命令。
 */
public class CarryEvents {
	/**
	 * 注册所有搬运相关的事件监听器和网络包处理器。
	 * 包括：摔落伤害分摊、服务端刻搬运更新（位置/挣扎/经验消耗/沉重感缓慢效果）、
	 * 断线清理、搬运/放下网络包、/carry-xp-rate 命令。
	 */
	public static void register() {
		// 搬运者和被搬运者之间的摔落伤害共享
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (source.isOf(DamageTypes.FALL) && entity instanceof ServerPlayerEntity carrier) {
				CarryManager.CarriedEntityData data = CarryManager.CARRIED_ENTITIES.get(carrier);
				if (data != null) {
					CarryManager.handleFallDamage(carrier, data.entity, damageTaken);
				}
			}
		});

		// 服务端刻：搬运位置更新、挣扎、经验消耗
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Map.Entry<ServerPlayerEntity, CarryManager.CarriedEntityData> entry : CarryManager.CARRIED_ENTITIES.entrySet()) {
				CarryManager.tickCarried(entry.getKey(), entry.getValue());
			}
			CarryManager.cleanupCooldowns();

			// 沉重感：持有肢体物品的 kebao 玩家获得缓慢效果
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				if (p.getCommandTags().contains("kebao") && !p.isCreative()) {
					ItemStack held = p.getMainHandStack();
					if (!held.isEmpty() && (
						BodyPartManager.isCombinedBodyItem(held) ||
						BodyPartManager.BODY_PART_DISPLAY_ITEMS.contains(held.getItem())
					)) {
						if (!p.getCommandTags().contains("qiangzhiai") && !p.getCommandTags().contains("strong_power")) {
							p.addStatusEffect(new StatusEffectInstance(
								StatusEffects.SLOWNESS, 40, 0, false, false, true
							));
						}
					}
				}
			}
		});

		// 断开连接清理
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			HoldHandsManager.cleanupForDisconnect(player);
			CarryManager.cleanupForDisconnect(player);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			CarryTransformConfig.syncTo(handler.getPlayer());
			CarryManager.syncAllCarryPosesTo(handler.getPlayer());
			HoldHandsManager.syncAllTo(handler.getPlayer());
		});

		// 搬运实体请求
		ServerPlayNetworking.registerGlobalReceiver(CarryTransformPackets.RequestConfigC2S.ID, (packet, context) -> {
			CarryTransformConfig.syncTo(context.player());
		});

		ServerPlayNetworking.registerGlobalReceiver(CarryTransformPackets.UpdateC2S.ID, (packet, context) -> {
			context.server().execute(() -> updateCarryTransform(context.player(), packet));
		});

		ServerPlayNetworking.registerGlobalReceiver(CarryEntityPayload.ID, (payload, context) -> {
			ServerPlayerEntity carrier = context.player();
			if (!carrier.isSneaking()) return;
			if (!carrier.getMainHandStack().isEmpty() || !carrier.getOffHandStack().isEmpty()) return;
			if (!carrier.isCreative() && !carrier.getCommandTags().contains("kebao")) {
				carrier.sendMessage(Text.literal("§c还无法抱起§k12§r"), false);
				return;
			}
			ServerWorld world = (ServerWorld) carrier.getWorld();
			Entity target = world.getEntityById(payload.entityId());
			if (target == null) return;

			// 检查冷却
			Long cooldownEnd = CarryManager.CARRIED_COOLDOWN.get(target);
			if (cooldownEnd != null && cooldownEnd > System.currentTimeMillis()) {
				carrier.sendMessage(Text.literal("§c哈..哈~...待会再抱吧，剩余" + ((cooldownEnd - System.currentTimeMillis()) / 1000 + 1) + "秒"), false);
				return;
			}

			CarryManager.CarriedEntityData currentData = CarryManager.CARRIED_ENTITIES.get(carrier);
			if (currentData != null && currentData.entity == target) {
				CarryManager.releaseCarried(carrier, target);
				carrier.sendMessage(Text.literal("§a放下了" + tag_pitch.entityDisplayName(target)), false);
				return;
			}

			if (CarryManager.CARRIED_ENTITIES.containsKey(carrier)) {
				carrier.sendMessage(Text.literal("§c已经抱了一个，先放下她"), false);
				return;
			}
			if (CarryManager.CARRIED_BY.containsKey(target)) {
				carrier.sendMessage(Text.literal("§c§k1234§r已经被别人抱起了"), false);
				return;
			}

			if (!(target instanceof LivingEntity)) {
				carrier.sendMessage(Text.literal("§c只能抱起活物"), false);
				return;
			}
			if (!CarryManager.hasCarryExperience(carrier)) {
				carrier.sendMessage(Text.literal("§c体力不足，无法抱起"), false);
				return;
			}

			if (target instanceof ServerPlayerEntity carriedPlayer) {
				if (CarryManager.CARRIED_ENTITIES.containsKey(carriedPlayer)) {
					carrier.sendMessage(Text.literal("§c§k1§r正在抱起其他存在，无法抱起"), false);
					return;
				}
			}

			CarryManager.CarriedEntityData carriedData = CarryManager.CarriedEntityData.capture(target);
			if (target instanceof ServerPlayerEntity carriedPlayer) {
				carriedPlayer.setNoGravity(true);
				carriedPlayer.getAbilities().flying = true;
				carriedPlayer.getAbilities().allowFlying = true;
				carriedPlayer.getAbilities().invulnerable = true;
				carriedPlayer.sendAbilitiesUpdate();
				Vec3d initialPos = CarryManager.findSafeCarryPosition(carrier, carriedPlayer);
				carriedPlayer.requestTeleport(initialPos.x, initialPos.y, initialPos.z);

				carriedPlayer.sendMessage(Text.literal("§e你被 " + tag_pitch.entityDisplayName(carrier) + " 抱起来了，按潜行键挣脱"), false);
			}

			if (target instanceof MobEntity mob) {
				mob.setAiDisabled(true);
				mob.getNavigation().stop();
			}
			target.stopRiding();
			target.setNoGravity(true);
			target.setSilent(true);

			CarryManager.CARRIED_ENTITIES.put(carrier, carriedData);
			CarryManager.CARRIED_BY.put(target, carrier);
			CarryManager.syncCarryPose(carrier, target, true);
			carrier.sendMessage(Text.literal("§a你抱起了 " + tag_pitch.entityDisplayName(target)), false);
		});

		// 放下被搬运实体请求
		ServerPlayNetworking.registerGlobalReceiver(PlaceCarriedEntityPayload.ID, (payload, context) -> {
			ServerPlayerEntity carrier = context.player();
			CarryManager.CarriedEntityData data = CarryManager.CARRIED_ENTITIES.get(carrier);
			if (data == null) return;
			Entity carried = data.entity;
			CarryManager.releaseCarried(carrier, carried);
			if (carried.isAlive()) {
				carrier.sendMessage(Text.literal("§a放下了抱起的实体"), false);
			} else {
				carrier.sendMessage(Text.literal("§c实体已经死亡，无法放下"), false);
			}
		});

		// stamina drain rate commands. carry-xp-rate is kept as a compatibility alias.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerCarryTransformCommands(dispatcher);
			dispatcher.register(CommandManager.literal("carry-stamina-rate_搬运耐力倍率")
					.requires(source -> source.hasPermissionLevel(2))
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> setCarryStaminaDrainRate(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
					.executes(ctx -> showCarryStaminaDrainRate(ctx)));
			dispatcher.register(CommandManager.literal("carry-xp-rate_搬运经验倍率")
					.requires(source -> source.hasPermissionLevel(2))
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> setCarryStaminaDrainRate(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
					.executes(ctx -> showCarryStaminaDrainRate(ctx)));
			dispatcher.register(CommandManager.literal("carry-pose_搬运姿势")
					.requires(source -> source.getEntity() instanceof ServerPlayerEntity)
					.then(CommandManager.literal("princess_公主抱")
							.executes(ctx -> setCarryPoseMode(ctx, CarryManager.CarryPoseMode.PRINCESS)))
					.then(CommandManager.literal("drag_拖拽")
							.executes(ctx -> setCarryPoseMode(ctx, CarryManager.CarryPoseMode.DRAG)))
					.then(CommandManager.literal("toggle_切换")
							.executes(ctx -> toggleCarryPoseMode(ctx)))
					.executes(ctx -> showCarryPoseMode(ctx)));
		});
	}

	private static void registerCarryTransformCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(buildCarryTransformCommand("monvhua-carry-pose-transform_搬运姿势变换", CarryTransformPackets.TARGET_POSE));
		dispatcher.register(buildCarryTransformCommand("monvhua-carry-view-transform_搬运视角变换", CarryTransformPackets.TARGET_VIEW));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> buildCarryTransformCommand(String name, int target) {
		LiteralArgumentBuilder<ServerCommandSource> root = addCarryTransformActions(
				CommandManager.literal(name),
				CarryTransformPackets.POSE_PRINCESS,
				target
		);
		root.then(addCarryTransformActions(CommandManager.literal("princess_公主抱"), CarryTransformPackets.POSE_PRINCESS, target));
		root.then(addCarryTransformActions(CommandManager.literal("drag_拖拽"), CarryTransformPackets.POSE_DRAG, target));
		return root;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> addCarryTransformActions(
			LiteralArgumentBuilder<ServerCommandSource> root,
			int poseMode,
			int target
	) {
		return root
				.then(buildCarryTransformAction("set_设置", poseMode, target, CarryTransformPackets.ACTION_SET))
				.then(buildCarryTransformAction("add_追加", poseMode, target, CarryTransformPackets.ACTION_ADD))
				.then(CommandManager.literal("reset_重置")
						.executes(ctx -> resetCarryTransform(ctx, poseMode, target)))
				.executes(ctx -> showCarryTransform(ctx, poseMode, target));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> buildCarryTransformAction(String name, int poseMode, int target, int action) {
		return CommandManager.literal(name)
				.then(CommandManager.argument("x", FloatArgumentType.floatArg())
						.then(CommandManager.argument("y", FloatArgumentType.floatArg())
								.then(CommandManager.argument("z", FloatArgumentType.floatArg())
										.then(CommandManager.argument("pitch", FloatArgumentType.floatArg())
												.then(CommandManager.argument("yaw", FloatArgumentType.floatArg())
														.then(CommandManager.argument("roll", FloatArgumentType.floatArg())
																.executes(ctx -> applyCarryTransformCommand(
																		ctx,
																		poseMode,
																		target,
																		action,
																		FloatArgumentType.getFloat(ctx, "x"),
																		FloatArgumentType.getFloat(ctx, "y"),
																		FloatArgumentType.getFloat(ctx, "z"),
																		FloatArgumentType.getFloat(ctx, "pitch"),
																		FloatArgumentType.getFloat(ctx, "yaw"),
																		FloatArgumentType.getFloat(ctx, "roll")
																))))))));
	}

	private static void updateCarryTransform(ServerPlayerEntity player, CarryTransformPackets.UpdateC2S packet) {
		if (player == null || (packet.target() != CarryTransformPackets.TARGET_POSE && packet.target() != CarryTransformPackets.TARGET_VIEW)) {
			return;
		}
		int poseMode = CarryTransformConfig.sanitizePoseMode(packet.poseMode());
		CarryTransformConfig config = CarryTransformConfig.applyRequest(packet);
		CarryTransformConfig.syncToAll(player.getServer());
		player.sendMessage(Text.literal("Carry " + CarryTransformConfig.poseModeName(poseMode) + " " + carryTransformTargetName(packet.target()) + " transform synced: " + carryTransformFormat(config, poseMode, packet.target())), true);
	}

	private static int applyCarryTransformCommand(CommandContext<ServerCommandSource> ctx, int poseMode, int target, int action, float x, float y, float z, float pitch, float yaw, float roll) {
		CarryTransformConfig config;
		if (target == CarryTransformPackets.TARGET_VIEW) {
			config = action == CarryTransformPackets.ACTION_ADD
					? CarryTransformConfig.addViewTransform(poseMode, x, y, z, pitch, yaw, roll)
					: CarryTransformConfig.setViewTransform(poseMode, x, y, z, pitch, yaw, roll);
		} else {
			config = action == CarryTransformPackets.ACTION_ADD
					? CarryTransformConfig.addPoseTransform(poseMode, x, y, z, pitch, yaw, roll)
					: CarryTransformConfig.setPoseTransform(poseMode, x, y, z, pitch, yaw, roll);
		}
		return finishCarryTransformCommand(ctx, poseMode, target, config);
	}

	private static int resetCarryTransform(CommandContext<ServerCommandSource> ctx, int poseMode, int target) {
		CarryTransformConfig config = target == CarryTransformPackets.TARGET_VIEW
				? CarryTransformConfig.resetViewTransform(poseMode)
				: CarryTransformConfig.resetPoseTransform(poseMode);
		return finishCarryTransformCommand(ctx, poseMode, target, config);
	}

	private static int showCarryTransform(CommandContext<ServerCommandSource> ctx, int poseMode, int target) {
		ctx.getSource().sendMessage(Text.literal("Carry " + CarryTransformConfig.poseModeName(poseMode) + " " + carryTransformTargetName(target) + " transform: " + carryTransformFormat(CarryTransformConfig.getInstance(), poseMode, target)));
		return 1;
	}

	private static int finishCarryTransformCommand(CommandContext<ServerCommandSource> ctx, int poseMode, int target, CarryTransformConfig config) {
		CarryTransformConfig.syncToAll(ctx.getSource().getServer());
		ctx.getSource().sendMessage(Text.literal("Carry " + CarryTransformConfig.poseModeName(poseMode) + " " + carryTransformTargetName(target) + " transform synced: " + carryTransformFormat(config, poseMode, target)));
		return 1;
	}

	private static String carryTransformTargetName(int target) {
		return target == CarryTransformPackets.TARGET_VIEW ? "view" : "pose";
	}

	private static String carryTransformFormat(CarryTransformConfig config, int poseMode, int target) {
		return target == CarryTransformPackets.TARGET_VIEW ? config.formatView(poseMode) : config.formatPose(poseMode);
	}

	private static int setCarryPoseMode(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx, CarryManager.CarryPoseMode mode) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
			return 0;
		}
		CarryManager.setCarryPoseMode(player, mode);
		ctx.getSource().sendMessage(Text.literal("Carry pose: " + mode.displayName()));
		return 1;
	}

	private static int toggleCarryPoseMode(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
			return 0;
		}
		CarryManager.CarryPoseMode mode = CarryManager.toggleCarryPoseMode(player);
		ctx.getSource().sendMessage(Text.literal("Carry pose: " + mode.displayName()));
		return 1;
	}

	private static int showCarryPoseMode(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
			return 0;
		}
		CarryManager.CarryPoseMode mode = CarryManager.getCarryPoseMode(player);
		ctx.getSource().sendMessage(Text.literal("Carry pose: " + mode.displayName() + " (/carry-pose_搬运姿势 princess_公主抱|drag_拖拽|toggle_切换)"));
		return 1;
	}

	private static int setCarryStaminaDrainRate(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx, int amount) {
		CarryManager.CARRY_STAMINA_DRAIN_RATE = amount;
		ctx.getSource().sendMessage(Text.literal("§a抱人 stamina 消耗已设置为每秒 " + amount + " 分"));
		return 1;
	}

	private static int showCarryStaminaDrainRate(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx) {
		ctx.getSource().sendMessage(Text.literal("§e当前抱人 stamina 消耗: 每秒 " + CarryManager.CARRY_STAMINA_DRAIN_RATE + " 分"));
		return 1;
	}
}
