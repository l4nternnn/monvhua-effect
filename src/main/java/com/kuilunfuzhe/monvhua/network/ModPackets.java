package com.kuilunfuzhe.monvhua.network;

import net.minecraft.util.Identifier;

public class ModPackets {
    // 观看系统（新系统）
    public static final Identifier CAMERA_UPDATE = Identifier.of("monvhua", "camera_update");
    public static final Identifier CAMERA_UNBIND = Identifier.of("monvhua", "camera_unbind");

    // 千里眼（Evil Eyes）系统
    public static final Identifier GLOBAL_CONFIG = Identifier.of("monvhua", "global_config");
    public static final Identifier OPEN_UI = Identifier.of("monvhua", "open_ui");
    public static final Identifier ENTITY_MARKED = Identifier.of("monvhua", "entity_marked");
    public static final Identifier TOGGLE_IMAGES = Identifier.of("monvhua", "toggle_images");
    public static final Identifier SELECT_VIEW = Identifier.of("monvhua", "select_view");
    public static final Identifier FORCE_EXIT_VIEW = Identifier.of("monvhua", "force_exit_view");
    public static final Identifier MARK_PARTICLE = Identifier.of("monvhua", "mark_particle");
    public static final Identifier PLAYER_STAGE = Identifier.of("monvhua", "player_stage");
    public static final Identifier ANCHOR_PARTICLE = Identifier.of("monvhua", "anchor_particle");
    public static final Identifier EXPLOSION_PARTICLE = Identifier.of("monvhua", "explosion_particle");

    // 视线诱导系统
    public static final Identifier SYNC_CONFIG = Identifier.of("monvhua", "sync_config");
    public static final Identifier ENERGY_SYNC = Identifier.of("monvhua", "energy_sync");
    public static final Identifier MARK_COUNT = Identifier.of("monvhua", "mark_count");
    public static final Identifier FOCUS_STATUS = Identifier.of("monvhua", "focus_status");
    public static final Identifier PARTICLE = Identifier.of("monvhua", "particle");
    public static final Identifier STRENGTH = Identifier.of("monvhua", "strength");

    // 原相机绑定（旧系统，已废弃但保留）
    public static final Identifier CAMERA_WATCH_BIND = Identifier.of("monvhua", "camera_watch_bind");
    public static final Identifier CAMERA_WATCH_UNBIND = Identifier.of("monvhua", "camera_watch_unbind");
}