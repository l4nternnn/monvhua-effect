package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.floating.floating;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CarryManager {
	private static final double CARRIED_ENTITY_FORWARD_DISTANCE = 1.2D;
	private static final double CARRIED_PLAYER_FORWARD_DISTANCE = 1.5D;
	private static final double CARRY_Y_OFFSET = 0.8D;
	private static final double CARRY_BOX_EPSILON = 1.0E-7D;
	private static final String STAMINA_OBJECTIVE = "stamina";
	private static final float DRAG_CARRIED_PITCH = 90.0F;

	public enum CarryPoseMode {
		PRINCESS(CarryPoseSyncS2CPacket.POSE_CARRIED, "princess"),
		DRAG(CarryPoseSyncS2CPacket.POSE_CARRIED_DRAG, "drag");

		private final int carriedPacketPose;
		private final String displayName;

		CarryPoseMode(int carriedPacketPose, String displayName) {
			this.carriedPacketPose = carriedPacketPose;
			this.displayName = displayName;
		}

		public int carriedPacketPose() {
			return carriedPacketPose;
		}

		public String displayName() {
			return displayName;
		}

		public CarryPoseMode next() {
			return this == PRINCESS ? DRAG : PRINCESS;
		}
	}

	// 搬运者 -> 被搬运实体数据
	public static final Map<ServerPlayerEntity, CarriedEntityData> CARRIED_ENTITIES = new ConcurrentHashMap<>();
	// 被搬运实体 -> 搬运者（用于快速挣扎查找）
	public static final Map<Entity, ServerPlayerEntity> CARRIED_BY = new ConcurrentHashMap<>();
	// 搬运冷却：实体 -> 冷却结束时间戳
	public static final Map<Entity, Long> CARRIED_COOLDOWN = new ConcurrentHashMap<>();
	// 挣扎计数器：被搬运实体 -> 当前潜行次数
	public static final Map<Entity, Integer> STRUGGLE_COUNTER = new ConcurrentHashMap<>();
	// 经验消耗：搬运者 -> 累积刻数
	public static final Map<ServerPlayerEntity, Integer> CARRY_STAMINA_TICK_COUNTER = new ConcurrentHashMap<>();
	public static int CARRY_STAMINA_DRAIN_RATE = 1;

	private static final Set<UUID> FALL_DAMAGE_PROCESSING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, CarryPoseMode> CARRY_POSE_MODES = new ConcurrentHashMap<>();

	public static class CarriedEntityData {
		public final Entity entity;
		public final boolean originalFlying;
		public final boolean originalAllowFlying;
		public final boolean originalInvulnerable;
		public final boolean originalNoGravity;
		public final boolean originalSilent;
		public final boolean originalEntityInvulnerable;
		public final boolean originalMobAiDisabled;
		public final boolean originalServerFloating;

		public CarriedEntityData(Entity entity) {
			this(entity, false, false, false,
					entity != null && entity.hasNoGravity(),
					entity != null && entity.isSilent(),
					entity != null && entity.isInvulnerable(),
					entity instanceof MobEntity mob && mob.isAiDisabled(),
					entity instanceof ServerPlayerEntity player && floating.isFloatingServer(player.getUuid()));
		}

		public CarriedEntityData(Entity entity, boolean originalFlying, boolean originalAllowFlying, boolean originalInvulnerable) {
			this(entity, originalFlying, originalAllowFlying, originalInvulnerable,
					entity != null && entity.hasNoGravity(),
					entity != null && entity.isSilent(),
					entity != null && entity.isInvulnerable(),
					entity instanceof MobEntity mob && mob.isAiDisabled(),
					entity instanceof ServerPlayerEntity player && floating.isFloatingServer(player.getUuid()));
		}

		public CarriedEntityData(Entity entity, boolean originalFlying, boolean originalAllowFlying, boolean originalInvulnerable,
								 boolean originalNoGravity, boolean originalSilent, boolean originalEntityInvulnerable, boolean originalMobAiDisabled,
								 boolean originalServerFloating) {
			this.entity = entity;
			this.originalFlying = originalFlying;
			this.originalAllowFlying = originalAllowFlying;
			this.originalInvulnerable = originalInvulnerable;
			this.originalNoGravity = originalNoGravity;
			this.originalSilent = originalSilent;
			this.originalEntityInvulnerable = originalEntityInvulnerable;
			this.originalMobAiDisabled = originalMobAiDisabled;
			this.originalServerFloating = originalServerFloating;
		}

		public static CarriedEntityData capture(Entity entity) {
			boolean originalFlying = false;
			boolean originalAllowFlying = false;
			boolean originalInvulnerable = false;
			if (entity instanceof ServerPlayerEntity player) {
				originalFlying = player.getAbilities().flying;
				originalAllowFlying = player.getAbilities().allowFlying;
				originalInvulnerable = player.getAbilities().invulnerable;
			}
			return new CarriedEntityData(
					entity,
					originalFlying,
					originalAllowFlying,
					originalInvulnerable,
					entity != null && entity.hasNoGravity(),
					entity != null && entity.isSilent(),
					entity != null && entity.isInvulnerable(),
					entity instanceof MobEntity mob && mob.isAiDisabled(),
					entity instanceof ServerPlayerEntity player && floating.isFloatingServer(player.getUuid())
			);
		}
	}

	public static int getStruggleThreshold(ServerPlayerEntity carrier, Entity carried) {
		if (!carrier.getCommandTags().contains("qiangzhiai")) return 1;
		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			if (carriedPlayer.getCommandTags().contains("qiangzhiai")) return 1;
			if (carriedPlayer.getCommandTags().contains("strong_power")) return 100;
		}
		return 300;
	}

	public static boolean shouldDrainCarryExperience(ServerPlayerEntity carrier) {
		return CARRY_STAMINA_DRAIN_RATE > 0 && !carrier.isCreative() && carrier.getCommandTags().contains("kebao");
	}

	public static boolean hasCarryExperience(ServerPlayerEntity carrier) {
		return !shouldDrainCarryExperience(carrier) || getCarryStaminaScore(carrier) >= CARRY_STAMINA_DRAIN_RATE;
	}

	public static CarryPoseMode getCarryPoseMode(ServerPlayerEntity player) {
		return CARRY_POSE_MODES.getOrDefault(player.getUuid(), CarryPoseMode.PRINCESS);
	}

	public static CarryPoseMode setCarryPoseMode(ServerPlayerEntity player, CarryPoseMode mode) {
		CARRY_POSE_MODES.put(player.getUuid(), mode);
		CarriedEntityData data = CARRIED_ENTITIES.get(player);
		if (data != null) {
			syncCarryPose(player, data.entity, true);
		}
		return mode;
	}

	public static CarryPoseMode toggleCarryPoseMode(ServerPlayerEntity player) {
		return setCarryPoseMode(player, getCarryPoseMode(player).next());
	}

	public static void syncCarryPose(ServerPlayerEntity carrier, Entity carried, boolean active) {
		MinecraftServer server = carrier.getServer();
		if (server == null) return;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			sendCarryPose(player, carrier, carried, active);
		}
	}

	public static void syncAllCarryPosesTo(ServerPlayerEntity receiver) {
		for (Map.Entry<ServerPlayerEntity, CarriedEntityData> entry : CARRIED_ENTITIES.entrySet()) {
			sendCarryPose(receiver, entry.getKey(), entry.getValue().entity, true);
		}
	}

	private static void sendCarryPose(ServerPlayerEntity receiver, ServerPlayerEntity carrier, Entity carried, boolean active) {
		int carrierPose = active ? CarryPoseSyncS2CPacket.POSE_CARRIER : CarryPoseSyncS2CPacket.POSE_NONE;
		int carriedPose = active ? getCarryPoseMode(carrier).carriedPacketPose() : CarryPoseSyncS2CPacket.POSE_NONE;
		ServerPlayNetworking.send(receiver, new CarryPoseSyncS2CPacket(carrier.getId(), carrierPose, active ? carried.getId() : -1));
		ServerPlayNetworking.send(receiver, new CarryPoseSyncS2CPacket(carried.getId(), carriedPose, active ? carrier.getId() : -1));
	}

	public static void releaseCarried(ServerPlayerEntity carrier, Entity carried) {
		CarriedEntityData data = CARRIED_ENTITIES.get(carrier);
		syncCarryPose(carrier, carried, false);
		CARRIED_ENTITIES.remove(carrier);
		CARRIED_BY.remove(carried);
		STRUGGLE_COUNTER.remove(carried);
		CARRY_STAMINA_TICK_COUNTER.remove(carrier);
		CARRIED_COOLDOWN.put(carried, System.currentTimeMillis() + 5000);
		restoreCarriedState(carried, data);
		if (carried.isAlive()) {
			Vec3d pos = findSafeReleasePosition(carrier, carried, 0.5D);
			carried.refreshPositionAndAngles(pos.x, pos.y, pos.z, carried.getYaw(), carried.getPitch());
			if (carried instanceof ServerPlayerEntity carriedPlayer) {
				carriedPlayer.requestTeleport(pos.x, pos.y, pos.z);
			}
		}
	}

	public static void restoreCarriedState(Entity carried, CarriedEntityData data) {
		if (carried == null) {
			return;
		}
		carried.setNoGravity(data != null && data.originalNoGravity);
		carried.setSilent(data != null && data.originalSilent);
		carried.setInvulnerable(data != null && data.originalEntityInvulnerable);
		carried.setVelocity(Vec3d.ZERO);
		carried.fallDistance = 0.0F;
		if (carried instanceof MobEntity mob) {
			mob.setAiDisabled(data != null && data.originalMobAiDisabled);
			if (!mob.isAiDisabled()) {
				mob.getNavigation().stop();
			}
		}
		if (carried instanceof ServerPlayerEntity carriedPlayer) {
			if (data != null) {
				floating.setServerFloating(carriedPlayer.getUuid(), data.originalServerFloating);
				carriedPlayer.getAbilities().flying = data.originalFlying;
				carriedPlayer.getAbilities().allowFlying = data.originalAllowFlying;
				carriedPlayer.getAbilities().invulnerable = data.originalInvulnerable;
			} else if (!carriedPlayer.isCreative() && !carriedPlayer.isSpectator()) {
				floating.setServerFloating(carriedPlayer.getUuid(), false);
				carriedPlayer.getAbilities().flying = false;
				carriedPlayer.getAbilities().allowFlying = false;
				carriedPlayer.getAbilities().invulnerable = false;
			}
			carriedPlayer.sendAbilitiesUpdate();
		}
	}

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

	public static void tickCarried(ServerPlayerEntity carrier, CarriedEntityData data) {
		Entity carried = data.entity;
		if (!carried.isAlive()) {
			syncCarryPose(carrier, carried, false);
			CARRIED_ENTITIES.remove(carrier);
			CARRIED_BY.remove(carried);
			restoreCarriedState(carried, data);
			STRUGGLE_COUNTER.remove(carried);
			CARRY_STAMINA_TICK_COUNTER.remove(carrier);
			return;
		}

		Vec3d targetPos = findSafeCarryPosition(carrier, carried);


		// 计算被抱实体应该面向抱起者的水平角度
		double dx = carrier.getX() - carried.getX();
		double dz = carrier.getZ() - carried.getZ();
		float yawToCarrier = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);

		boolean dragMode = getCarryPoseMode(carrier) == CarryPoseMode.DRAG;
		float carriedYaw = dragMode ? carrier.getYaw() + 180.0F : yawToCarrier;
		float carriedPitch = dragMode ? DRAG_CARRIED_PITCH : carried.getPitch();

		if (!(carried instanceof ServerPlayerEntity)) {
			carried.setPosition(targetPos.x, targetPos.y, targetPos.z);
			carried.setYaw(carriedYaw);
			carried.setPitch(carriedPitch);
			carried.setBodyYaw(carriedYaw);
			carried.setHeadYaw(carriedYaw);
		}
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
					carrier.sendMessage(Text.literal("§c" + tag_pitch.entityDisplayName(carriedPlayer) + " 挣脱了"), false);
					carriedPlayer.sendMessage(Text.literal("§c你按潜行挣脱了怀抱"), false);
					STRUGGLE_COUNTER.remove(carried);
				} else {
					int count = STRUGGLE_COUNTER.merge(carried, 1, Integer::sum);
					carriedPlayer.sendMessage(Text.literal("§e挣扎进度: " + count + "/" + threshold), true);
					if (count >= threshold) {
						releaseCarried(carrier, carriedPlayer);
						carrier.sendMessage(Text.literal("§c" + tag_pitch.entityDisplayName(carriedPlayer) + " 挣脱了"), false);
						carriedPlayer.sendMessage(Text.literal("§c你终于挣脱了怀抱"), false);
						STRUGGLE_COUNTER.remove(carried);
					}
				}
			} else {
				if (STRUGGLE_COUNTER.containsKey(carried)) {
					STRUGGLE_COUNTER.remove(carried);
					carriedPlayer.sendMessage(Text.literal("§7挣扎中断"), false);
				}
			}
			if (CARRIED_BY.get(carriedPlayer) == carrier) {
				carriedPlayer.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
			}
		}

		// 每 20 刻消耗一次经验
		if (CARRIED_BY.get(carried) != carrier) {
			return;
		}

		if (carried instanceof LivingEntity && shouldDrainCarryExperience(carrier)) {
			int tickCount = CARRY_STAMINA_TICK_COUNTER.merge(carrier, 1, (oldVal, v) -> oldVal + 1);
			if (tickCount >= 20) {
				CARRY_STAMINA_TICK_COUNTER.put(carrier, 0);
				if (getCarryStaminaScore(carrier) >= CARRY_STAMINA_DRAIN_RATE) {
					removeCarryStaminaScore(carrier, CARRY_STAMINA_DRAIN_RATE);
				} else {
					releaseCarried(carrier, carried);
					carrier.sendMessage(Text.literal("§c体力耗尽，无法继续抱起"), false);
					if (carried instanceof ServerPlayerEntity cp) {
						cp.sendMessage(Text.literal("§c" + tag_pitch.entityDisplayName(carrier) + "体力耗尽放下了你"), false);
					}
				}
			}
		}
	}

	private static int getCarryStaminaScore(ServerPlayerEntity player) {
		Scoreboard scoreboard = player.getScoreboard();
		ScoreboardObjective objective = scoreboard.getNullableObjective(STAMINA_OBJECTIVE);
		if (objective == null) return 0;
		var score = scoreboard.getScore(player, objective);
		return score == null ? 0 : score.getScore();
	}

	private static void removeCarryStaminaScore(ServerPlayerEntity player, int amount) {
		if (amount <= 0) return;
		Scoreboard scoreboard = player.getScoreboard();
		ScoreboardObjective objective = scoreboard.getNullableObjective(STAMINA_OBJECTIVE);
		if (objective == null) return;
		int remaining = Math.max(0, getCarryStaminaScore(player) - amount);
		scoreboard.getOrCreateScore(player, objective).setScore(remaining);
	}

	public static void cleanupForDisconnect(ServerPlayerEntity player) {
		CarriedEntityData carriedData = CARRIED_ENTITIES.remove(player);
		if (carriedData != null) {
			Entity carried = carriedData.entity;
			syncCarryPose(player, carried, false);
			CARRIED_BY.remove(carried);
			restoreCarriedState(carried, carriedData);
		}
		ServerPlayerEntity theirCarrier = CARRIED_BY.remove(player);
		if (theirCarrier != null) {
			CarriedEntityData removedData = CARRIED_ENTITIES.remove(theirCarrier);
			syncCarryPose(theirCarrier, player, false);
			restoreCarriedState(player, removedData);
			CARRY_STAMINA_TICK_COUNTER.remove(theirCarrier);
		}
		CARRIED_COOLDOWN.remove(player);
		STRUGGLE_COUNTER.remove(player);
		CARRY_STAMINA_TICK_COUNTER.remove(player);
		CARRY_POSE_MODES.remove(player.getUuid());
	}

	public static void cleanupCooldowns() {
		long now = System.currentTimeMillis();
		CARRIED_COOLDOWN.values().removeIf(expiry -> expiry <= now);
	}

	public static Vec3d findSafeCarryPosition(ServerPlayerEntity carrier, Entity carried) {
		Vec3d lookVec = carrier.getRotationVec(1.0F);
		Vec3d forward = getHorizontalForward(carrier);
		Vec3d right = new Vec3d(forward.z, 0, -forward.x);
		double forwardDistance = carried instanceof ServerPlayerEntity
				? CARRIED_PLAYER_FORWARD_DISTANCE
				: CARRIED_ENTITY_FORWARD_DISTANCE;
		Vec3d base = carrier.getPos();
		Vec3d eyePos = carrier.getEyePos();
		// 主位置：跟随视线方向（含俯仰），位于眼前偏下
		Vec3d desired = eyePos.add(lookVec.multiply(forwardDistance)).subtract(0, 0.4D, 0);

		return findFirstSafePosition(carrier, carried, desired,
				desired,
				// 无俯仰回退：水平方向 + 眼部高度偏下
				eyePos.add(forward.multiply(forwardDistance)).subtract(0, 0.4D, 0),
				// 原候选位置（使用脚部坐标）
				base.add(forward.multiply(forwardDistance)).add(0, CARRY_Y_OFFSET, 0),
				base.add(forward.multiply(forwardDistance * 0.5D)).add(0, CARRY_Y_OFFSET, 0),
				base.add(forward.multiply(forwardDistance)).add(0, 1.0D, 0),
				base.add(forward.multiply(forwardDistance * 0.5D)).add(0, 1.0D, 0),
				base.add(right.multiply(0.55D)).add(0, 0.65D, 0),
				base.subtract(right.multiply(0.55D)).add(0, 0.65D, 0),
				base.add(0, CARRY_Y_OFFSET, 0),
				base.add(0, 1.2D, 0),
				base,
				carried.getPos()
		);
	}

	public static Vec3d findSafeReleasePosition(ServerPlayerEntity carrier, Entity carried, double forwardDistance) {
		Vec3d lookVec = carrier.getRotationVec(1.0F);
		Vec3d forward = getHorizontalForward(carrier);
		Vec3d right = new Vec3d(forward.z, 0, -forward.x);
		Vec3d base = carrier.getPos();
		Vec3d desired = carrier.getEyePos().add(lookVec.multiply(forwardDistance)).subtract(0, 0.5D, 0);

		return findFirstSafePosition(carrier, carried, desired,
				desired,
				base.add(forward.multiply(Math.min(forwardDistance, 0.8D))).add(0, 0.1D, 0),
				base.add(right.multiply(0.65D)).add(0, 0.1D, 0),
				base.subtract(right.multiply(0.65D)).add(0, 0.1D, 0),
				base.subtract(forward.multiply(0.5D)).add(0, 0.1D, 0),
				base.add(0, 0.1D, 0),
				base.add(0, 1.0D, 0),
				carried.getPos()
		);
	}

	private static Vec3d getHorizontalForward(ServerPlayerEntity carrier) {
		double rad = Math.toRadians(carrier.getYaw());
		return new Vec3d(-Math.sin(rad), 0, Math.cos(rad)).normalize();
	}

	private static Vec3d findFirstSafePosition(ServerPlayerEntity carrier, Entity carried, Vec3d fallback, Vec3d... candidates) {
		for (Vec3d candidate : candidates) {
			if (isSafePosition(carrier, carried, candidate)) {
				return candidate;
			}
		}
		return fallback;
	}

	private static boolean isSafePosition(ServerPlayerEntity carrier, Entity carried, Vec3d pos) {
		Box box = carried.getBoundingBox()
				.offset(pos.x - carried.getX(), pos.y - carried.getY(), pos.z - carried.getZ())
				.contract(CARRY_BOX_EPSILON);
		return carrier.getWorld().isSpaceEmpty(carried, box);
	}
}
