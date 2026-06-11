package com.kuilunfuzhe.monvhua.features.carryentity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
			CarryManager.cleanupForDisconnect(player);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			CarryManager.syncAllCarryPosesTo(handler.getPlayer());
		});

		// 搬运实体请求
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
				carrier.sendMessage(Text.literal("§a放下了" + target.getName().getString()), false);
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
				carrier.sendMessage(Text.literal("§c没有经验，无法抱起"), false);
				return;
			}

			boolean savedFlying = false, savedAllowFlying = false, savedInvulnerable = false;
			if (target instanceof ServerPlayerEntity carriedPlayer) {
				savedFlying = carriedPlayer.getAbilities().flying;
				savedAllowFlying = carriedPlayer.getAbilities().allowFlying;
				savedInvulnerable = carriedPlayer.getAbilities().invulnerable;
				carriedPlayer.setNoGravity(true);
				carriedPlayer.getAbilities().flying = true;
				carriedPlayer.getAbilities().allowFlying = true;
				carriedPlayer.getAbilities().invulnerable = true;
				carriedPlayer.sendAbilitiesUpdate();
				Vec3d initialPos = CarryManager.findSafeCarryPosition(carrier, carriedPlayer);
				carriedPlayer.requestTeleport(initialPos.x, initialPos.y, initialPos.z);

				if (CarryManager.CARRIED_ENTITIES.containsKey(carriedPlayer)) {
					carrier.sendMessage(Text.literal("§c§k1§r正在抱起其他存在，无法抱起"), false);
					return;
				}
				carriedPlayer.sendMessage(Text.literal("§e你被 " + carrier.getName().getString() + " 抱起来了，按潜行键挣脱"), false);
			}

			if (target instanceof MobEntity mob) {
				mob.setAiDisabled(true);
				mob.getNavigation().stop();
			}
			target.stopRiding();
			target.setNoGravity(true);
			target.setSilent(true);

			CarryManager.CARRIED_ENTITIES.put(carrier, new CarryManager.CarriedEntityData(target, savedFlying, savedAllowFlying, savedInvulnerable));
			CarryManager.CARRIED_BY.put(target, carrier);
			CarryManager.syncCarryPose(carrier, target, true);
			carrier.sendMessage(Text.literal("§a你抱起了 " + target.getName().getString()), false);
		});

		// 放下被搬运实体请求
		ServerPlayNetworking.registerGlobalReceiver(PlaceCarriedEntityPayload.ID, (payload, context) -> {
			ServerPlayerEntity carrier = context.player();
			CarryManager.CarriedEntityData data = CarryManager.CARRIED_ENTITIES.remove(carrier);
			if (data == null) return;
			Entity carried = data.entity;
			CarryManager.syncCarryPose(carrier, carried, false);
			CarryManager.CARRIED_BY.remove(carried);
			if (carried.isAlive()) {
				carried.setNoGravity(false);
				carried.setSilent(false);
				if (carried instanceof ServerPlayerEntity carriedPlayer) {
					carriedPlayer.getAbilities().flying = data.originalFlying;
					carriedPlayer.getAbilities().allowFlying = data.originalAllowFlying;
					carriedPlayer.getAbilities().invulnerable = data.originalInvulnerable;
					carriedPlayer.sendAbilitiesUpdate();
				}
				if (carried instanceof MobEntity mob) {
					mob.setAiDisabled(false);
				}
				Vec3d pos = CarryManager.findSafeReleasePosition(carrier, carried, 1.5D);
				carried.refreshPositionAndAngles(pos.x, pos.y, pos.z, carried.getYaw(), carried.getPitch());
				if (carried instanceof ServerPlayerEntity carriedPlayer) {
					carriedPlayer.requestTeleport(pos.x, pos.y, pos.z);
				}
				carrier.sendMessage(Text.literal("§a放下了抱起的实体"), false);
			} else {
				carrier.sendMessage(Text.literal("§c实体已经死亡，无法放下"), false);
			}
		});

		// carry-xp-rate command
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("carry-xp-rate")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
					.executes(ctx -> {
						int amount = IntegerArgumentType.getInteger(ctx, "amount");
						CarryManager.CARRY_XP_DRAIN_RATE = amount;
						ctx.getSource().sendMessage(Text.literal("§a搬运经验消耗率已设置为 " + amount));
						return 1;
					})
				)
				.executes(ctx -> {
					ctx.getSource().sendMessage(Text.literal("§e当前搬运经验消耗率: " + CarryManager.CARRY_XP_DRAIN_RATE));
					return 1;
				})
			);
		});
	}
}
