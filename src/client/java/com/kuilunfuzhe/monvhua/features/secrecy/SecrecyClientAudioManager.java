package com.kuilunfuzhe.monvhua.features.secrecy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

/**
 * 隐秘状态客户端音效管理器。
 * 进入隐秘状态时将非玩家音效音量降至10%，仅保留PLAYERS类音效（心跳声）不变，
 * 退出时通过EnumMap备份恢复原始音量设置。
 */
public final class SecrecyClientAudioManager {
    /** 非玩家音效音量乘数（降至10%） */
    private static final float OTHER_SOUND_MULTIPLIER = 0.10F;
    /** 玩家类音效（心跳）音量乘数（保持100%不变） */
    private static final float HEART_SOUND_MULTIPLIER = 1.0F;
    /** 进入隐秘状态前的原始音量备份 */
    private static final Map<SoundCategory, Double> ORIGINAL_OPTIONS = new EnumMap<>(SoundCategory.class);
    /** 当前是否处于隐秘状态 */
    private static boolean active = false;
    /** 客户端是否应开启 noClip（穿墙尝试或穿墙锁定） */
    private static boolean phaseNoClip = false;
    /** 是否处于进入墙体后的穿墙锁定状态 */
    private static boolean phaseLocked = false;
    /** 穿墙锁定时服务端固定的视角 */
    private static float lockedYaw = 0.0F;
    private static float lockedPitch = 0.0F;
    /** 穿墙期间强制第一人称前的原视角，用于结束后恢复 */
    private static Perspective previousPerspective = null;

    private SecrecyClientAudioManager() {
    }

    /**
     * 设置隐秘（不可见）状态，控制音效音量降低/恢复。
     * @param invisible    是否进入隐秘状态
     * @param fadeOutTicks 淡出tick数（当前未使用，保留用于未来扩展）
     */
    public static void setInvisible(boolean invisible, int fadeOutTicks) {
        setState(invisible, false, false, 0.0F, 0.0F, fadeOutTicks);
    }

    /**
     * 设置完整隐秘状态，同步穿墙 noClip、锁定输入和锁定视角。
     */
    public static void setState(boolean invisible, boolean newPhaseNoClip, boolean newPhaseLocked, float newLockedYaw, float newLockedPitch, int fadeOutTicks) {
        if (invisible != active) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                active = invisible;
            } else if (invisible) {
                storeOriginalOptions(client);
                setCategoryOptions(client, OTHER_SOUND_MULTIPLIER, HEART_SOUND_MULTIPLIER);
                active = true;
            } else {
                restoreOriginalOptions(client);
                active = false;
            }
        }

        phaseNoClip = newPhaseNoClip;
        phaseLocked = newPhaseLocked;
        lockedYaw = newLockedYaw;
        lockedPitch = newLockedPitch;
    }

    /** 每tick调用，维护客户端 noClip、禁用侧移/跳跃并锁定视角。 */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        if (phaseNoClip) {
            client.player.noClip = false;
            client.player.fallDistance = 0.0F;
            forceFirstPersonPerspective(client);
        } else {
            restorePerspective(client);
            if (!client.player.isSpectator()) {
                client.player.noClip = false;
            }
        }

        if (phaseLocked) {
            client.player.setYaw(lockedYaw);
            client.player.setPitch(lockedPitch);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
        }
    }

    /**
     * 获取指定音效类别的音量乘数。
     * @param category 音效类别
     * @return 音量乘数（当前固定返回1.0）
     */
    public static float getVolumeMultiplier(SoundCategory category) {
            return 1.0F;
    }

    /**
     * 心跳音效是否可听到。
     * @return true表示处于隐秘状态，心跳正常播放
     */
    public static boolean isHeartAudible() {
        return active;
    }

    public static boolean isPhaseLocked() {
        return phaseLocked;
    }

    public static boolean isPhaseNoClip() {
        return phaseNoClip;
    }

    public static boolean shouldIgnorePhaseCollision(Entity entity, BlockPos pos) {
        if (!phaseNoClip || entity == null) {
            return false;
        }
        int feetY = BlockPos.ofFloored(entity.getX(), entity.getY() + 0.05D, entity.getZ()).getY();
        int headY = BlockPos.ofFloored(entity.getBoundingBox().maxX, entity.getBoundingBox().maxY, entity.getBoundingBox().maxZ).getY();
        return pos.getY() >= feetY && pos.getY() <= headY;
    }

    private static void forceFirstPersonPerspective(MinecraftClient client) {
        if (previousPerspective == null) {
            previousPerspective = client.options.getPerspective();
        }
        if (!client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    private static void restorePerspective(MinecraftClient client) {
        if (previousPerspective == null) {
            return;
        }
        client.options.setPerspective(previousPerspective);
        previousPerspective = null;
    }

    private static void storeOriginalOptions(MinecraftClient client) {
        ORIGINAL_OPTIONS.clear();
        for (SoundCategory category : SoundCategory.values()) {
            ORIGINAL_OPTIONS.put(category, client.options.getSoundVolumeOption(category).getValue());
        }
    }

    private static void setCategoryOptions(MinecraftClient client, float otherSoundMultiplier, float heartSoundMultiplier) {
        for (SoundCategory category : SoundCategory.values()) {
            if (category == SoundCategory.MASTER) {
                continue;
            }
            double original = ORIGINAL_OPTIONS.getOrDefault(category, client.options.getSoundVolumeOption(category).getValue());
            double multiplier = category == SoundCategory.PLAYERS ? heartSoundMultiplier : otherSoundMultiplier;
            client.options.getSoundVolumeOption(category).setValue(original * multiplier);
        }
    }

    private static void restoreOriginalOptions(MinecraftClient client) {
        for (Map.Entry<SoundCategory, Double> entry : ORIGINAL_OPTIONS.entrySet()) {
            client.options.getSoundVolumeOption(entry.getKey()).setValue(entry.getValue());
        }
        ORIGINAL_OPTIONS.clear();
    }
}
