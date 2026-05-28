package com.kuilunfuzhe.monvhua.features.secrecy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

import java.util.EnumMap;
import java.util.Map;

public final class SecrecyClientAudioManager {
    private static final float OTHER_SOUND_MULTIPLIER = 0.10F;
    private static final float HEART_SOUND_MULTIPLIER = 1.0F;
    private static final Map<SoundCategory, Double> ORIGINAL_OPTIONS = new EnumMap<>(SoundCategory.class);
    private static boolean active = false;

    private SecrecyClientAudioManager() {
    }

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

    public static void tick() {
    }

    public static float getVolumeMultiplier(SoundCategory category) {
            return 1.0F;
    }

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
