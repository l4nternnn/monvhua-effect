package com.kuilunfuzhe.monvhua.features.cosmic_box;

import java.util.Locale;

public enum CosmicBoxBeamStyle {
    COSMIC("cosmic"),
    BEACON_RAINBOW("beacon_rainbow");

    private final String id;

    CosmicBoxBeamStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CosmicBoxBeamStyle byId(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        for (CosmicBoxBeamStyle style : values()) {
            if (style.id.equals(normalized)) {
                return style;
            }
        }
        return COSMIC;
    }
}
