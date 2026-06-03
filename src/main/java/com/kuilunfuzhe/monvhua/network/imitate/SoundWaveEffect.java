package com.kuilunfuzhe.monvhua.network.imitate;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundWaveEffect {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/SoundWave");

    public static void execute(ServerPlayerEntity caster) {
        if (!ImitateManager.canUseSoundWave(caster)) {
            int remaining = ImitateManager.getSoundWaveCooldownRemaining(caster);
            caster.sendMessage(Text.literal("§c声音震荡冷却中，请等待").append(Text.literal(String.valueOf(remaining)).formatted(Formatting.RED)).append(Text.literal("秒")), true);
            LOGGER.info("声音震荡冷却中，剩余{}秒", remaining);
            return;
        }

        int stage = ImitateManager.getStage(caster);
        ImitateConfig config = ImitateConfig.getInstance();
        double radius = config.getSoundWaveRadius(stage);
        int effectDurationSeconds = config.getSoundWaveEffectDuration(stage);
        int effectDurationTicks = effectDurationSeconds * 20;

        ServerWorld world = (ServerWorld) caster.getWorld();
        Vec3d center = caster.getPos();

        LOGGER.info("=== 声音震荡触发 ===");
        LOGGER.info("释放者: {}", caster.getName().getString());
        LOGGER.info("位置: X={}, Y={}, Z={}", center.x, center.y, center.z);
        LOGGER.info("世界: {}", world.getRegistryKey().getValue());
        LOGGER.info("半径: {}", radius);
        LOGGER.info("效果持续时间: {}秒 ({}ticks)", effectDurationSeconds, effectDurationTicks);

        LOGGER.info("检查音效注册状态...");
        LOGGER.info("SOUND_WAVE是否为null: {}", ModSounds.SOUND_WAVE == null);
        LOGGER.info("TINNITUS是否为null: {}", ModSounds.TINNITUS == null);
        LOGGER.info("SOUND_WAVE_ID: {}", ModSounds.SOUND_WAVE_ID.toString());
        LOGGER.info("TINNITUS_ID: {}", ModSounds.TINNITUS_ID.toString());

        LOGGER.info("尝试播放冲击波音效 (playSoundToPlayer)...");
        try {
            caster.playSoundToPlayer(ModSounds.SOUND_WAVE, SoundCategory.PLAYERS, 2.0f, 1.0f);
            LOGGER.info("冲击波音效播放命令已执行");
        } catch (Exception e) {
            LOGGER.error("冲击波音效播放失败: {}", e.getMessage());
            e.printStackTrace();
        }

        LOGGER.info("尝试播放冲击波音效 (world.playSound)...");
        try {
            world.playSound(null, center.x, center.y, center.z, ModSounds.SOUND_WAVE, SoundCategory.PLAYERS, 2.0f, 1.0f);
            LOGGER.info("world.playSound播放命令已执行");
        } catch (Exception e) {
            LOGGER.error("world.playSound播放失败: {}", e.getMessage());
            e.printStackTrace();
        }

        spawnCenterExplosionParticles(world, center);
        LOGGER.info("中心爆炸粒子已生成");

        int hitCount = 0;
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.equals(caster)) continue;

            double distance = target.getPos().distanceTo(center);
            LOGGER.info("检测玩家 {} 距离: {}", target.getName().getString(), distance);

            if (distance <= radius) {
                hitCount++;
                LOGGER.info("玩家 {} 在范围内，施加效果", target.getName().getString());
                applyEffects(target, effectDurationTicks);

                LOGGER.info("尝试播放耳鸣音效给玩家 {}", target.getName().getString());
                try {
                    target.playSoundToPlayer(ModSounds.TINNITUS, SoundCategory.PLAYERS, 1.5f, 1.0f);
                    LOGGER.info("耳鸣音效播放命令已执行");
                } catch (Exception e) {
                    LOGGER.error("耳鸣音效播放失败: {}", e.getMessage());
                    e.printStackTrace();
                }

                target.sendMessage(Text.literal("§c你被声音震荡命中了！"), true);
            }
        }

        LOGGER.info("命中玩家数量: {}", hitCount);
        caster.sendMessage(Text.literal("§d声音震荡已释放！"), true);

        ImitateManager.startSoundWaveCooldown(caster);
        LOGGER.info("声音震荡冷却已开始");

        double maxRadius = radius + 2;
        for (ServerPlayerEntity player : world.getPlayers()) {
            double distance = player.getPos().distanceTo(center);
            if (distance <= maxRadius + 30) {
                ServerPlayNetworking.send(player, new SoundWaveStartS2CPacket(center.x, center.y, center.z, maxRadius));
            }
        }
        LOGGER.info("粒子同步包已发送");

        LOGGER.info("=== 声音震荡执行完成 ===");
    }

    private static void spawnCenterExplosionParticles(ServerWorld world, Vec3d center) {
        for (int i = 0; i < 20; i++) {
            double angle = world.random.nextDouble() * 2 * Math.PI;
            double r = world.random.nextDouble() * 2;
            double x = center.x + r * Math.cos(angle);
            double z = center.z + r * Math.sin(angle);
            double y = center.y + 1.0 + world.random.nextDouble() * 0.5;

            world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.POOF, x, y, z, 1, 0, 0.1, 0, 0.05);
        }

        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1.0, center.z, 5, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + 1.5, center.z, 10, 0.2, 0.3, 0.2, 0.05);
    }

    private static void applyEffects(ServerPlayerEntity target, int durationTicks) {
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 2, false, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, durationTicks, 1, false, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, durationTicks, 1, false, true));
    }
}