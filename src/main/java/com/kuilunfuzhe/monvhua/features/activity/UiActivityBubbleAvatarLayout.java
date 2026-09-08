package com.kuilunfuzhe.monvhua.features.activity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Normalized avatar center relative to the bubble quad; values outside 0..1 are valid. */
public record UiActivityBubbleAvatarLayout(float centerX, float centerY, float scale) {
    public static final float MIN_POSITION = -0.50F;
    public static final float MAX_POSITION = 1.50F;
    public static final float MIN_SCALE = 0.03F;
    public static final float MAX_SCALE = 0.70F;
    public static final Codec<UiActivityBubbleAvatarLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("centerX", 1.10F).forGetter(UiActivityBubbleAvatarLayout::centerX),
            Codec.FLOAT.optionalFieldOf("centerY", 0.82F).forGetter(UiActivityBubbleAvatarLayout::centerY),
            Codec.FLOAT.optionalFieldOf("scale", 0.22F).forGetter(UiActivityBubbleAvatarLayout::scale)
    ).apply(instance, UiActivityBubbleAvatarLayout::new));

    public UiActivityBubbleAvatarLayout {
        centerX = clampFinite(centerX, 1.10F, MIN_POSITION, MAX_POSITION);
        centerY = clampFinite(centerY, 0.82F, MIN_POSITION, MAX_POSITION);
        scale = clampFinite(scale, 0.22F, MIN_SCALE, MAX_SCALE);
    }

    public static UiActivityBubbleAvatarLayout defaults(int avatarId) {
        return defaults(UiActivityBubbleAvatarCatalog.key(avatarId));
    }

    public static UiActivityBubbleAvatarLayout defaults(String key) {
        UiActivityBubbleAvatarCatalog.Definition definition =
                UiActivityBubbleAvatarCatalog.definition(key);
        return definition == null
                ? new UiActivityBubbleAvatarLayout(1.10F, 0.82F, 0.22F)
                : new UiActivityBubbleAvatarLayout(definition.defaultCenterX(),
                definition.defaultCenterY(), definition.defaultScale());
    }

    public static UiActivityBubbleAvatarLayout sanitize(float centerX, float centerY, float scale, int avatarId) {
        return sanitize(centerX, centerY, scale, UiActivityBubbleAvatarCatalog.key(avatarId));
    }

    public static UiActivityBubbleAvatarLayout sanitize(float centerX, float centerY, float scale, String key) {
        UiActivityBubbleAvatarLayout fallback = defaults(key);
        return new UiActivityBubbleAvatarLayout(
                clampFinite(centerX, fallback.centerX, MIN_POSITION, MAX_POSITION),
                clampFinite(centerY, fallback.centerY, MIN_POSITION, MAX_POSITION),
                clampFinite(scale, fallback.scale, MIN_SCALE, MAX_SCALE)
        );
    }

    private static float clampFinite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
