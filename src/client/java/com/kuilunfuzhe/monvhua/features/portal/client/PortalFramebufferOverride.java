package com.kuilunfuzhe.monvhua.features.portal.client;

import net.minecraft.client.gl.Framebuffer;

public final class PortalFramebufferOverride {
    private static final ThreadLocal<Framebuffer> OVERRIDE = new ThreadLocal<>();

    private PortalFramebufferOverride() {
    }

    public static void set(Framebuffer framebuffer) {
        OVERRIDE.set(framebuffer);
    }

    public static void clear() {
        OVERRIDE.remove();
    }

    public static Framebuffer get() {
        return OVERRIDE.get();
    }
}
