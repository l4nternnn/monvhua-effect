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
        return switch (avatarId) {
            // All avatars use the same logical height. Their source aspect ratio
            // is preserved by the renderer, but source resolution never changes
            // the default display size.
            case UiActivityBubbleAvatarCatalog.NOA -> new UiActivityBubbleAvatarLayout(1.12F, 0.82F, 0.22F);
            case UiActivityBubbleAvatarCatalog.WEIJIE -> new UiActivityBubbleAvatarLayout(1.10F, 0.82F, 0.22F);
            default -> new UiActivityBubbleAvatarLayout(1.10F, 0.82F, 0.22F);
        };
    }

    public static UiActivityBubbleAvatarLayout sanitize(float centerX, float centerY, float scale, int avatarId) {
        UiActivityBubbleAvatarLayout fallback = defaults(avatarId);
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
