package com.kuilunfuzhe.monvhua.features.secrecy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

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

    private SecrecyClientAudioManager() {
    }

    /**
     * 设置隐秘（不可见）状态，控制音效音量降低/恢复。
     * @param invisible    是否进入隐秘状态
     * @param fadeOutTicks 淡出tick数（当前未使用，保留用于未来扩展）
     */
    public static void setInvisible(boolean invisible, int fadeOutTicks) {
        if (invisible == active) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            active = invisible;
            return;
        }

        if (invisible) {
            storeOriginalOptions(client);
            setCategoryOptions(client, OTHER_SOUND_MULTIPLIER, HEART_SOUND_MULTIPLIER);
        } else {
            restoreOriginalOptions(client);
        }
        active = invisible;
    }

    /** 每tick调用（当前为空，保留用于未来扩展如淡入淡出） */
    public static void tick() {
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
