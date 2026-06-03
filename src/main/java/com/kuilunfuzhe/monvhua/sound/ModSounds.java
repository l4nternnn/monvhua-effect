package com.kuilunfuzhe.monvhua.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModSounds {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/ModSounds");

    public static final Identifier SOUND_WAVE_ID = Identifier.of("monvhua", "sound_wave");
    public static final Identifier TINNITUS_ID = Identifier.of("monvhua", "tinnitus");

    public static SoundEvent SOUND_WAVE;
    public static SoundEvent TINNITUS;

    public static void initialize() {
        LOGGER.info("=== 开始注册音效 ===");
        LOGGER.info("SOUND_WAVE_ID: {}", SOUND_WAVE_ID.toString());
        LOGGER.info("TINNITUS_ID: {}", TINNITUS_ID.toString());

        try {
            SOUND_WAVE = Registry.register(Registries.SOUND_EVENT, SOUND_WAVE_ID, SoundEvent.of(SOUND_WAVE_ID));
            LOGGER.info("SOUND_WAVE注册成功");
        } catch (Exception e) {
            LOGGER.error("SOUND_WAVE注册失败: {}", e.getMessage());
            e.printStackTrace();
        }

        try {
            TINNITUS = Registry.register(Registries.SOUND_EVENT, TINNITUS_ID, SoundEvent.of(TINNITUS_ID));
            LOGGER.info("TINNITUS注册成功");
        } catch (Exception e) {
            LOGGER.error("TINNITUS注册失败: {}", e.getMessage());
            e.printStackTrace();
        }

        LOGGER.info("=== 音效注册完成 ===");
    }
}