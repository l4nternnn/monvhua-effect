package com.kuilunfuzhe.monvhua;

import net.minecraft.server.level.ServerPlayer;

public enum WitchRole {
    EMA("ema"),
    CERO("cero"),
    NNK("nnk"),
    MARGO("margo"),
    LEIYA("leiya"),
    MILYA("milya"),
    SHERRY("sherry"),
    YALISA("yalisa"),
    NOA("noa"),
    ANAN("anan"),
    YUKI("yuki"),
    MLL("mll"),
    COCO("coco"),
    HANNA("hanna");

    public final String id;

    WitchRole(String id) {
        this.id = id;
    }

    public static WitchRole fromPlayer(ServerPlayer player) {
        var tags = player.getTags();
        for (WitchRole role : values()) {
            if (tags.contains(role.id)) return role;
        }
        return null;
    }
}
