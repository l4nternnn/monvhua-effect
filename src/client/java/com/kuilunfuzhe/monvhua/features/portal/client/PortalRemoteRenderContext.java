package com.kuilunfuzhe.monvhua.features.portal.client;

public final class PortalRemoteRenderContext {
    private static final ThreadLocal<Boolean> PORTAL_PASS = ThreadLocal.withInitial(() -> false);

    private PortalRemoteRenderContext() {
    }

    public static void beginPortalPass() {
        PORTAL_PASS.set(true);
    }

    public static void endPortalPass() {
        PORTAL_PASS.set(false);
    }

    public static boolean isPortalPass() {
        return PORTAL_PASS.get();
    }

    public static void markRemoteRendererAlive() {
    }

    public static void clearRemoteRendererAlive() {
        PORTAL_PASS.remove();
    }
}
