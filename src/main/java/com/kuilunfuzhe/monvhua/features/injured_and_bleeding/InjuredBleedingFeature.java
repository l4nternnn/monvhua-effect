package com.kuilunfuzhe.monvhua.features.injured_and_bleeding;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.config.InjuredBleedingConfig;
import com.kuilunfuzhe.monvhua.network.injured_and_bleeding.InjuredBleedingPackets;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class InjuredBleedingFeature {
    private static final double SYNC_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final double LANDING_RADIUS = 0.6D;

    private InjuredBleedingFeature() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.RequestConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> syncConfigTo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(InjuredBleedingPackets.UpdateConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> updateConfig(context.player(), packet.json())));
    }

    public static void handleDamage(LivingEntity entity, float amount) {
        if (amount > 0.0F && entity.getWorld() instanceof ServerWorld world && entity.getMaxHealth() > 0.0F && matchesConfiguredSelector(world, entity)) {
            spawnBloodEffect(world, entity, amount);
        }
    }

    private static boolean matchesConfiguredSelector(ServerWorld world, LivingEntity entity) {
        String selectorText = InjuredBleedingConfig.normalizeEntitySelector(InjuredBleedingConfig.getInstance().entitySelector);
        if (selectorText == null || selectorText.isBlank()) {
            return true;
        }
        try {
            EntitySelector selector = new EntitySelectorReader(new StringReader(selectorText), true).read();
            ServerCommandSource source = world.getServer().getCommandSource()
                    .withWorld(world)
                    .withPosition(entity.getPos())
                    .withLevel(4);
            return selector.getEntities(source).contains(entity);
        } catch (CommandSyntaxException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Ignoring invalid injured bleeding entity selector: {}", selectorText, e);
            return false;
        }
    }

    private static void spawnBloodEffect(ServerWorld world, LivingEntity entity, float amount) {
        float damagePercent = amount / entity.getMaxHealth();
        Scale scale = Scale.fromDamagePercent(damagePercent);
        Random random = new Random(world.getTime() * 31L + entity.getId() * 131L + Float.floatToIntBits(amount));
        Vec3d origin = entity.getPos();
        Vec3d emitter = attackPartPos(entity, random);
        Vec3d emitterOffset = emitter.subtract(origin);
        InjuredBleedingConfig config = InjuredBleedingConfig.getInstance();
        int sprayTicks = config.sprayTicks;
        int fadeTicks = Math.max(1, (int) Math.round(config.bloodSpotFadeSeconds * 20.0D));
        int dropCount = Math.clamp((int) Math.round(config.particlesPerSecond * config.spraySeconds), 1, InjuredBleedingPackets.MAX_DROPS);

        List<InjuredBleedingPackets.BloodDropData> drops = new ArrayList<>(dropCount);
        for (int i = 0; i < dropCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(random.nextDouble()) * LANDING_RADIUS;
            int flightTicks = randomRangeInt(random, 4, 18);
            drops.add(new InjuredBleedingPackets.BloodDropData(
                    (float) angle,
                    (float) radius,
                    random.nextFloat() * 360.0F,
                    randomSprayDelay(random, i, dropCount, config.particlesPerSecond, sprayTicks),
                    flightTicks,
                    random.nextInt()
            ));
        }

        InjuredBleedingPackets.BloodEffectS2C packet = new InjuredBleedingPackets.BloodEffectS2C(
                entity.getId(),
                origin.x,
                entity.getY(),
                origin.z,
                emitterOffset.x,
                emitterOffset.y,
                emitterOffset.z,
                scale.networkId(),
                fadeTicks,
                config.bloodSpotButterflyChancePercent,
                config.bloodButterflyLifetimeSeconds,
                drops
        );
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(entity) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static int randomSprayDelay(Random random, int index, int dropCount, int particlesPerSecond, int sprayTicks) {
        if (sprayTicks <= 1 || dropCount <= 1) return 0;
        int bucket = index / Math.max(1, Math.min(particlesPerSecond, dropCount));
        int bucketStart = Math.min(sprayTicks - 1, bucket * 20);
        int bucketEnd = Math.min(sprayTicks - 1, bucketStart + 19);
        if (bucketStart >= bucketEnd) return bucketStart;
        return bucketStart + random.nextInt(bucketEnd - bucketStart + 1);
    }

    private static Vec3d attackPartPos(LivingEntity entity, Random random) {
        double height = entity.getHeight();
        BodyPart part = BodyPart.random(random);
        double yRatio = switch (part) {
            case HEAD -> 0.88D;
            case CHEST -> 0.58D;
            case LEGS -> 0.32D;
            case FEET -> 0.08D;
        };
        double sideAngle = random.nextDouble() * Math.PI * 2.0D;
        double sideRadius = Math.min(0.25D, Math.max(0.08D, entity.getWidth() * 0.35D));
        return new Vec3d(
                entity.getX() + Math.cos(sideAngle) * sideRadius,
                entity.getY() + height * yRatio,
                entity.getZ() + Math.sin(sideAngle) * sideRadius
        );
    }

    private static int randomRangeInt(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private enum BodyPart {
        HEAD,
        CHEST,
        LEGS,
        FEET;

        private static BodyPart random(Random random) {
            BodyPart[] values = values();
            return values[random.nextInt(values.length)];
        }
    }

    public static void syncConfigTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new InjuredBleedingPackets.ConfigS2C(InjuredBleedingConfig.getInstance().toJson()));
    }

    private static void updateConfig(ServerPlayerEntity player, String json) {
        if (!player.hasPermissionLevel(2) && !player.isCreative()) {
            player.sendMessage(Text.literal("\u00a7cNo permission to update injured bleeding config"), true);
            return;
        }
        InjuredBleedingConfig config = InjuredBleedingConfig.fromJson(json);
        if (!isValidEntitySelector(config.entitySelector)) {
            player.sendMessage(Text.literal("\u00a7cInvalid injured bleeding entity selector"), true);
            syncConfigTo(player);
            return;
        }
        InjuredBleedingConfig.setInstance(config);
        InjuredBleedingPackets.ConfigS2C sync = new InjuredBleedingPackets.ConfigS2C(config.toJson());
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, sync);
        }
        player.sendMessage(Text.literal("\u00a7aInjured bleeding config updated"), true);
    }

    private static boolean isValidEntitySelector(String selectorText) {
        selectorText = InjuredBleedingConfig.normalizeEntitySelector(selectorText);
        if (selectorText == null || selectorText.isBlank()) {
            return true;
        }
        try {
            new EntitySelectorReader(new StringReader(selectorText), true).read();
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private record Scale(int networkId) {
        private static Scale fromDamagePercent(float damagePercent) {
            if (damagePercent < 0.05F) {
                return new Scale(0);
            }
            if (damagePercent < 0.15F) {
                return new Scale(1);
            }
            if (damagePercent < 0.30F) {
                return new Scale(2);
            }
            return new Scale(3);
        }
    }
}
