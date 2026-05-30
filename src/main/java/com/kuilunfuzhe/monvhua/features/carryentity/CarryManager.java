package com.kuilunfuzhe.monvhua.features.carryentity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体搬运管理器。
 * 负责搬运状态的维护（抱起/放下/清理）、挣扎机制、摔落伤害分摊、
 * 被搬运实体的原始飞行状态记录（用于放下时恢复），以及断线清理。
 */
public class CarryManager {
	// 搬运者 -> 被搬运实体数据
	public static final Map<ServerPlayerEntity, CarriedEntityData> CARRIED_ENTITIES = new ConcurrentHashMap<>();
	// 被搬运实体 -> 搬运者（用于快速挣扎查找）
	public static final Map<Entity, ServerPlayerEntity> CARRIED_BY = new ConcurrentHashMap<>();
	// 搬运冷却：实体 -> 冷却结束时间戳
	public static final Map<Entity, Long> CARRIED_COOLDOWN = new ConcurrentHashMap<>();
	// 挣扎计数器：被搬运实体 -> 当前潜行次数
	public static final Map<Entity, Integer> STRUGGLE_COUNTER = new ConcurrentHashMap<>();
	// 经验消耗：搬运者 -> 累积刻数
	public static final Map<ServerPlayerEntity, Integer> CARRY_XP_TICK_COUNTER = new ConcurrentHashMap<>();
	/** 搬运经验消耗率，可通过命令 /carry-xp-rate 调整，默认为1 */
	public static int CARRY_XP_DRAIN_RATE = 1;

	/** 防重入标记：正在处理摔落伤害的搬运者UUID集合，防止无限递归 */
	private static final Set<UUID> FALL_DAMAGE_PROCESSING = ConcurrentHashMap.newKeySet();

	/**
	 * 被搬运实体的元数据记录，包含实体引用及其原始飞行/无敌状态，
	 * 用于放下时恢复这些属性。
	 */
	public static class CarriedEntityData {
		public final Entity entity;
		public final boolean originalFlying;
		public final boolean originalAllowFlying;
		public final boolean originalInvulnerable;

		public CarriedEntityData(Entity entity) {
			this(entity, false, false, false);
		}

		public CarriedEntityData(Entity entity, boolean originalFlying, boolean originalAllowFlying, boolean originalInvulnerable) {
			this.entity = entity;
			this.originalFlying = originalFlying;
			this.originalAllowFlying = originalAllowFlying;
			this.originalInvulnerable = originalInvulnerable;
		}
	}

	/**
	 * 恢复被搬运实体的原始能力状态（飞行、无敌等）。
	 */
	private static void restoreCarriedAbilities(ServerPlayerEntity carried, boolean originalFlying,
	                                             boolean originalAllowFlying, boolean originalInvulnerable) {
		carried.getAbilities().flying = originalFlying;
		carried.getAbilities().allowFlying = originalAllowFlying;
		carried.getAbilities().invulnerable = originalInvulnerable;
		carried.setNoGravity(false);
		carried.sendAbilitiesUpdate();
	}

	/**
	 * 根据搬运者和被搬运者的指令标签（qiangzhiai / strong_power）计算挣脱所需潜行次数阈值。
	 * @param carrier 搬运者
	 * @param carried 被搬运的实体
	 * @return 挣脱阈值（1 表示按一次即可挣脱，300 为默认普通玩家挣脱次数）
	 */
	public static int getStruggleThreshold(ServerPlayerEntity carrier, Entity carried) {
		if (!carrier.getCommandTags().contains("qiangzhiai")) return 1;
		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			if (carriedPlayer.getCommandTags().contains("qiangzhiai")) return 1;
			if (carriedPlayer.getCommandTags().contains("strong_power")) return 100;
		}
		return 300;
	}

	/**
	 * 放下被搬运实体，恢复其原始状态（重力、无敌、AI、飞行能力等），
	 * 清除挣扎计数和经验消耗记录，设置5秒搬运冷却，并将实体传送到搬运者前方。
	 * @param carrier 搬运者
	 * @param carried 被搬运的实体
	 */
	public static void releaseCarried(ServerPlayerEntity carrier, Entity carried) {
		CarriedEntityData data = CARRIED_ENTITIES.get(carrier);
		CARRIED_ENTITIES.remove(carrier);
		CARRIED_BY.remove(carried);
		carried.setNoGravity(false);
		carried.setInvulnerable(false);
		carried.setSilent(false);
		if (carried instanceof MobEntity mob) {
			mob.setAiDisabled(false);
		}
		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			restoreCarriedAbilities(carriedPlayer,
				data != null ? data.originalFlying : false,
				data != null ? data.originalAllowFlying : false,
				data != null ? data.originalInvulnerable : false);
		}
		STRUGGLE_COUNTER.remove(carried);
		CARRY_XP_TICK_COUNTER.remove(carrier);
		CARRIED_COOLDOWN.put(carried, System.currentTimeMillis() + 5000);
		Vec3d lookVec = carrier.getRotationVec(1.0f);
		Vec3d pos = carrier.getEyePos().add(lookVec.multiply(0.5));
		carried.refreshPositionAndAngles(pos.x, pos.y, pos.z, carrier.getYaw(), carrier.getPitch());
		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			carriedPlayer.networkHandler.requestTeleport(pos.x, pos.y, pos.z, carrier.getYaw(), carrier.getPitch());
		}
	}

	/**
	 * 处理搬运者摔落时与背上实体的伤害分摊。
	 * 搬运者承受20%摔倒伤害，背上的实体承受60%（用UUID集合防重入）。
	 * @param carrier 搬运者
	 * @param carried 被搬运实体
	 * @param damageTaken 原始摔落伤害量
	 * @return 是否成功处理（false 表示已在处理中，跳过）
	 */
	public static boolean handleFallDamage(ServerPlayerEntity carrier, Entity carried, float damageTaken) {
		if (FALL_DAMAGE_PROCESSING.contains(carrier.getUuid())) return false;
		FALL_DAMAGE_PROCESSING.add(carrier.getUuid());
		try {
			float carrierExtra = damageTaken * 0.2F;
			float carriedDamage = damageTaken * 0.6F;
			float newCarrierHealth = carrier.getHealth() - carrierExtra;
			if (newCarrierHealth < 0) newCarrierHealth = 0;
			carrier.setHealth(newCarrierHealth);
			if (carried.isAlive()) {
				float newCarriedHealth = carried instanceof LivingEntity ? ((LivingEntity) carried).getHealth() - carriedDamage : 0;
				if (newCarriedHealth < 0) newCarriedHealth = 0;
				if (carried instanceof LivingEntity) ((LivingEntity) carried).setHealth(newCarriedHealth);
			}
			return true;
		} finally {
			FALL_DAMAGE_PROCESSING.remove(carrier.getUuid());
		}
	}

	/**
	 * 每服务端刻更新搬运状态：将实体固定在搬运者前方、处理挣扎机制、
	 * 控制实体AI、以及每20刻消耗经验（非创造/kebao玩家）。
	 * @param carrier 搬运者
	 * @param data 被搬运实体数据
	 */
	public static void tickCarried(ServerPlayerEntity carrier, CarriedEntityData data) {
		Entity carried = data.entity;
		if (!carried.isAlive()) {
			CARRIED_ENTITIES.remove(carrier);
			CARRIED_BY.remove(carried);
			if (carried instanceof ServerPlayerEntity carriedPlayer) {
				restoreCarriedAbilities(carriedPlayer, data.originalFlying, data.originalAllowFlying, data.originalInvulnerable);
			}
			return;
		}

		float yaw = carrier.getYaw();
		double rad = Math.toRadians(yaw);
		double forwardX = -Math.sin(rad);
		double forwardZ = Math.cos(rad);
		Vec3d horizontalForward = new Vec3d(forwardX, 0, forwardZ).normalize();
		Vec3d targetPos = carrier.getPos().add(horizontalForward.multiply(1.2)).add(0, 0.5, 0);

		carried.setPosition(targetPos.x, targetPos.y, targetPos.z);
		carried.setVelocity(Vec3d.ZERO);
		carried.setNoGravity(true);
		if (carried instanceof MobEntity mob) {
			mob.setAiDisabled(true);
			mob.getNavigation().stop();
		}

		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			if (carriedPlayer.isSneaking()) {
				int threshold = getStruggleThreshold(carrier, carried);
				if (threshold <= 1) {
					releaseCarried(carrier, carriedPlayer);
					carrier.sendMessage(Text.literal("§c" + carriedPlayer.getName().getString() + " 挣脱了"), false);
					carriedPlayer.sendMessage(Text.literal("§c你按潜行挣脱了怀抱"), false);
					STRUGGLE_COUNTER.remove(carried);
				} else {
					int count = STRUGGLE_COUNTER.merge(carried, 1, Integer::sum);
					carriedPlayer.sendMessage(Text.literal("§e挣扎进度: " + count + "/" + threshold), true);
					if (count >= threshold) {
						releaseCarried(carrier, carriedPlayer);
						carrier.sendMessage(Text.literal("§c" + carriedPlayer.getName().getString() + " 挣脱了"), false);
						carriedPlayer.sendMessage(Text.literal("§c你终于挣脱了怀抱"), false);
						STRUGGLE_COUNTER.remove(carried);
					}
				}
			} else {
				if (STRUGGLE_COUNTER.containsKey(carried)) {
					STRUGGLE_COUNTER.remove(carried);
					carriedPlayer.sendMessage(Text.literal("§7挣扎中断"), false);
				}
				carriedPlayer.networkHandler.requestTeleport(
						targetPos.x, targetPos.y, targetPos.z,
						carriedPlayer.getYaw(), carriedPlayer.getPitch()
				);
			}
		}

		// 每 20 刻消耗一次经验
		if (carried instanceof LivingEntity && !carrier.isCreative() && !carrier.getCommandTags().contains("kebao")) {
			int tickCount = CARRY_XP_TICK_COUNTER.merge(carrier, 1, (oldVal, v) -> oldVal + 1);
			if (tickCount >= 20) {
				CARRY_XP_TICK_COUNTER.put(carrier, 0);
				if (carrier.experienceLevel > 0) {
					carrier.addExperienceLevels(-CARRY_XP_DRAIN_RATE);
					if (carrier.experienceLevel < 0) carrier.experienceLevel = 0;
				} else {
					releaseCarried(carrier, carried);
					carrier.sendMessage(Text.literal("§c体力耗尽，无法继续抱起"), false);
					if (carried instanceof ServerPlayerEntity cp) {
						cp.sendMessage(Text.literal("§c" + carrier.getName().getString() + "体力耗尽放下了你"), false);
					}
				}
			}
		}
	}

	/**
	 * 玩家断线时清理搬运相关状态：恢复被搬运实体的原始属性（重力、无敌、飞行能力），
	 * 同时清除该玩家在冷却、挣扎计数、经验消耗中的记录。
	 * @param player 断线的玩家
	 */
	public static void cleanupForDisconnect(ServerPlayerEntity player) {
		CarriedEntityData carriedData = CARRIED_ENTITIES.remove(player);
		if (carriedData != null) {
			Entity carried = carriedData.entity;
			CARRIED_BY.remove(carried);
			carried.setNoGravity(false);
			carried.setInvulnerable(false);
			carried.setSilent(false);
			if (carried instanceof ServerPlayerEntity carriedPlayer) {
				restoreCarriedAbilities(carriedPlayer, carriedData.originalFlying, carriedData.originalAllowFlying, carriedData.originalInvulnerable);
			}
			if (carried instanceof MobEntity mob) {
				mob.setAiDisabled(false);
			}
		}
		ServerPlayerEntity theirCarrier = CARRIED_BY.remove(player);
		if (theirCarrier != null) {
			CarriedEntityData removedData = CARRIED_ENTITIES.remove(theirCarrier);
			if (removedData != null) {
				player.getAbilities().flying = removedData.originalFlying;
				player.getAbilities().allowFlying = removedData.originalAllowFlying;
				player.getAbilities().invulnerable = removedData.originalInvulnerable;
				player.sendAbilitiesUpdate();
			}
		}
		CARRIED_COOLDOWN.remove(player);
		STRUGGLE_COUNTER.remove(player);
		CARRY_XP_TICK_COUNTER.remove(player);
	}

	/** 清除已过期的搬运冷却记录 */
	public static void cleanupCooldowns() {
		long now = System.currentTimeMillis();
		CARRIED_COOLDOWN.values().removeIf(expiry -> expiry <= now);
	}
}
