package com.kuilunfuzhe.monvhua.features.portal;

public final class PortalViewConfig {
    public static final int MIN_REMOTE_VIEW_RADIUS = 8;
    public static final int DEFAULT_REMOTE_VIEW_RADIUS = 12;
    public static final int MAX_REMOTE_VIEW_RADIUS = 16;
    public static final int REMOTE_VIEW_TICKET_RADIUS = 2;
    public static final int REMOTE_VIEW_TIMEOUT_TICKS = 60;
    public static final int REMOTE_INITIAL_CHUNKS_PER_TICK = 6;
    public static final int REMOTE_DIRTY_CHUNKS_PER_TICK = 3;
    public static final int REMOTE_DIRTY_DELAY_TICKS = 2;

    public static final int REMOTE_REQUEST_INTERVAL_FRAMES = 10;
    public static final double REMOTE_VIEW_CENTER_LEAD_BLOCKS = 24.0D;
    public static final int REMOTE_VIEW_RECENTER_HYSTERESIS_CHUNKS = 2;
    public static final int REMOTE_CHUNKS_PER_FRAME = 3;
    public static final int REMOTE_MAX_QUEUED_JOBS = 12;
    public static final int REMOTE_PUBLISH_CORE_RADIUS_CHUNKS = 1;
    public static final int PORTAL_FREEZE_LOG_INTERVAL_TICKS = 40;
    public static final int REMOTE_STALE_RETAIN_RADIUS_CHUNKS = 20;
    public static final int REMOTE_RENDER_THREADS = 2;
    public static final int REMOTE_BUFFER_BUILDER_COUNT = 2;
    public static final int LIVE_VIEW_UPDATE_INTERVAL_FRAMES = 1;
    public static final boolean USE_INDEPENDENT_PORTAL_RENDERER = false;
    public static final boolean INDEPENDENT_PORTAL_TERRAIN_BAND = true;
    public static final int INDEPENDENT_PORTAL_TERRAIN_COLUMNS = 48;
    public static final boolean INDEPENDENT_PORTAL_CHUNK_MESH = true;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_RADIUS_BLOCKS = 192;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_DETAIL_RADIUS_BLOCKS = 48;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_SIDE_DEPTH_BLOCKS = 5;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_TEXTURE_TILES = 1;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_NEAR_STEP_BLOCKS = 4;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_FAR_STEP_BLOCKS = 8;
    public static final int INDEPENDENT_PORTAL_CHUNK_MESH_MAX_FACES = 18000;
    public static final boolean ENABLE_PORTAL_DH_LOD_BRIDGE = false;
    public static final boolean SUSPEND_DH_DURING_PORTAL_RENDER = true;
    public static final int PORTAL_HORIZON_GRID_RADIUS = 16;
    public static final int PORTAL_HORIZON_STEP_BLOCKS = 32;
    public static final int PORTAL_HORIZON_UPDATE_INTERVAL_TICKS = 20;
    public static final int PORTAL_HORIZON_MAX_SAMPLES = 2048;
    public static final boolean PORTAL_HORIZON_FORCE_VISIBLE = false;
    public static final boolean PORTAL_HORIZON_RENDER_ENABLED = false;
    public static final double PORTAL_HORIZON_NEAR_CLIP = 2.0D;
    public static final float PORTAL_HORIZON_SCREEN_MARGIN = 0.15F;

    public static final int MAX_LIVE_RENDER_SLOTS = 12;
    public static final int MAX_PREVIEW_RENDER_SLOTS = 12;
    public static final int VANILLA_RENDER_BUDGET = 1;
    public static final int IRIS_RENDER_BUDGET = 1;
    public static final int CAPTURE_RETRY_LIMIT_FRAMES = 120;
    public static final int MIN_SURFACE_RESOLUTION = 256;
    public static final int VANILLA_MAX_SURFACE_RESOLUTION = 1536;
    public static final int IRIS_MAX_SURFACE_RESOLUTION = 1024;
    public static final int SURFACE_RESOLUTION_STEP = 64;
    public static final double SURFACE_RESOLUTION_SCALE = 1.0D;
    public static final double SURFACE_RESIZE_HYSTERESIS = 0.18D;
    public static final float PORTAL_MINIMUM_FAR_PLANE = 16384.0F;

    public static final double PORTAL_NEAR_PLANE_BIAS = 0.005D;
    public static final double MIN_PROJECTION_DEPTH = 0.01D;
    public static final double PORTAL_SURFACE_HORIZONTAL_INSET = 0.03D;
    public static final double PORTAL_SURFACE_VERTICAL_INSET = 0.05D;
    public static final double TELEPORT_EXIT_OFFSET = 0.85D;

    private PortalViewConfig() {
    }

    public static int clampRemoteRadius(int radius) {
        return Math.max(MIN_REMOTE_VIEW_RADIUS, Math.min(MAX_REMOTE_VIEW_RADIUS, radius));
    }
}
