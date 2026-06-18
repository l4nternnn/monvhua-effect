package com.kuilunfuzhe.monvhua.network.imitate;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SilenceEffect {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/Silence");

    public static void execute(ServerPlayerEntity caster, UUID targetUUID) {
        if (!ImitateManager.canUseSilence(caster)) {
            int remaining = ImitateManager.getSilenceCooldownRemaining(caster);
            caster.sendMessage(Text.literal("§c静音冷却中，请等待").append(Text.literal(String.valueOf(remaining)).formatted(Formatting.RED)).append(Text.literal("秒")), true);
            LOGGER.info("静音冷却中，剩余{}秒", remaining);
            return;
        }

        int stage = ImitateManager.getPlayerStage(caster);
        ImitateConfig config = ImitateConfig.getInstance();
        double radius = config.getSilenceRadius(stage);
        int duration = config.getSilenceDuration(stage);

        ServerWorld world = (ServerWorld) caster.getWorld();
        Vec3d casterPos = caster.getPos();

        LOGGER.info("=== 静音效果触发 ===");
        LOGGER.info("释放者: {}", caster.getName().getString());
        LOGGER.info("目标UUID: {}", targetUUID);
        LOGGER.info("检测半径: {}", radius);
        LOGGER.info("持续时间: {}秒", duration);

        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetUUID);
        if (target == null) {
            caster.sendMessage(Text.literal("§c目标玩家不存在"), true);
            LOGGER.info("目标玩家不存在");
            return;
        }

        double distance = casterPos.distanceTo(target.getPos());
        if (distance > radius) {
            caster.sendMessage(Text.literal("§c目标玩家超出范围"), true);
            LOGGER.info("目标玩家超出范围，距离: {}", distance);
            return;
        }

        if (target.equals(caster)) {
            caster.sendMessage(Text.literal("§c不能对自己使用静音"), true);
            LOGGER.info("不能对自己使用静音");
            return;
        }

        caster.sendMessage(Text.literal("§a魔法正在进行静音").formatted(Formatting.GREEN), true);

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false, true));

        SilenceServerManager.startSilence(target.getUuid(), caster.getUuid(), duration);

        SilenceEffectS2CPacket effectPacket = new SilenceEffectS2CPacket(target.getUuid(), duration);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, effectPacket);

        ImitateManager.startSilenceCooldown(caster);
        LOGGER.info("静音效果已应用，冷却已开始");
    }
}