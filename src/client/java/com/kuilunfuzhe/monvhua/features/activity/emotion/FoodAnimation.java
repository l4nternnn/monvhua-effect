package com.kuilunfuzhe.monvhua.features.activity.emotion;

import net.minecraft.util.Identifier;

import java.util.UUID;

/** Shared food-variant selection for the world bubble and picker preview. */
public final class FoodAnimation {
    public static final long CYCLE_MILLIS = 2_400L;
    public static final double BITE_START_PHASE = 0.12D;
    public static final double BITE_INTERVAL = 0.22D;
    public static final double BITE_DURATION = 0.16D;

    private FoodAnimation() {
    }

    public static boolean isEatFood(int contentId) {
        return contentId == 18;
    }

    public static Variant variantFor(UUID playerUuid, long effectStartedAtGameTime) {
        long seed = playerUuid.getMostSignificantBits()
                ^ Long.rotateLeft(playerUuid.getLeastSignificantBits(), 29)
                ^ effectStartedAtGameTime * 0x9E3779B97F4A7C15L;
        seed ^= seed >>> 30;
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= seed >>> 27;
        return (seed & 1L) == 0L ? Variant.APPLE : Variant.BREAD;
    }

    public static Identifier textureFor(UUID playerUuid, long effectStartedAtGameTime) {
        return variantFor(playerUuid, effectStartedAtGameTime).texture();
    }

    public static int renderContentId(UUID playerUuid, long effectStartedAtGameTime) {
        return variantFor(playerUuid, effectStartedAtGameTime).renderContentId();
    }

    public static Variant variantForPreview(long timeMillis, boolean animate) {
        long cycle = animate ? timeMillis / CYCLE_MILLIS : 0L;
        return (cycle & 1L) == 0L ? Variant.APPLE : Variant.BREAD;
    }

    public enum Variant {
        APPLE(18, Identifier.ofVanilla("textures/item/apple.png"), new BiteProfile(
                new double[]{0.110, 0.110, 0.014, -0.093},
                new double[]{0.137, 0.047, 0.101, -0.030},
                new double[]{0.077, 0.086, 0.086, 0.099}
        )),
        // Reserved for the client-side bread profile; it is never sent over the network.
        BREAD(19, Identifier.ofVanilla("textures/item/bread.png"), new BiteProfile(
                new double[]{0.115, 0.115, 0.010, -0.103},
                new double[]{0.132, 0.032, 0.090, -0.043},
                new double[]{0.087, 0.100, 0.094, 0.107}
        ));

        private final int renderContentId;
        private final Identifier texture;
        private final BiteProfile biteProfile;

        Variant(int renderContentId, Identifier texture, BiteProfile biteProfile) {
            this.renderContentId = renderContentId;
            this.texture = texture;
            this.biteProfile = biteProfile;
        }

        public int renderContentId() {
            return renderContentId;
        }

        public Identifier texture() {
            return texture;
        }

        public BiteProfile biteProfile() {
            return biteProfile;
        }
    }

    public record BiteProfile(double[] centerX, double[] centerY, double[] radius) {
    }
}
