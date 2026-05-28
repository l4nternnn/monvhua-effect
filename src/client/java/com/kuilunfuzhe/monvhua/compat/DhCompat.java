package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Field;

/**
 * Distant Horizons compatibility layer.
 * Prevents DH from rendering LOD terrain during mirror viewport re-render by temporarily
 * replacing WorldRenderEvents invokers with no-ops. This avoids the persistent LOD smear
 * caused when DH renders from the mirror camera perspective into its own framebuffers
 * and composites onto the mirror FBO.
 */
public class DhCompat {
    private static boolean dhLoaded;
    private static Object originalAfterSetup;
    private static Object originalAfterEntities;
    private static Object originalAfterTranslucent;

    public static void init() {
        dhLoaded = FabricLoader.getInstance().isModLoaded("distanthorizons");
        if (dhLoaded) {
            MonvhuaMod.LOGGER.info("[Monvhua] Distant Horizons detected, enabling mirror compatibility layer");
        }
    }

    /**
     * Replaces DH's target WorldRenderEvents invokers with no-ops.
     * Must be called before the mirror's worldRenderer.render() call.
     */
    public static void suspend() {
        if (!dhLoaded) return;
        try {
            Field invokerField = Event.class.getDeclaredField("invoker");
            invokerField.setAccessible(true);

            originalAfterSetup = invokerField.get(WorldRenderEvents.AFTER_SETUP);
            invokerField.set(WorldRenderEvents.AFTER_SETUP, (WorldRenderEvents.AfterSetup) ctx -> {});

            originalAfterEntities = invokerField.get(WorldRenderEvents.AFTER_ENTITIES);
            invokerField.set(WorldRenderEvents.AFTER_ENTITIES, (WorldRenderEvents.AfterEntities) ctx -> {});

            originalAfterTranslucent = invokerField.get(WorldRenderEvents.AFTER_TRANSLUCENT);
            invokerField.set(WorldRenderEvents.AFTER_TRANSLUCENT, (WorldRenderEvents.AfterTranslucent) ctx -> {});
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to suspend Distant Horizons for mirror render", e);
            dhLoaded = false;
        }
    }

    /**
     * Restores the original WorldRenderEvents invokers.
     * Must be called after the mirror's worldRenderer.render() call completes.
     */
    public static void resume() {
        if (!dhLoaded || originalAfterSetup == null) return;
        try {
            Field invokerField = Event.class.getDeclaredField("invoker");
            invokerField.setAccessible(true);

            invokerField.set(WorldRenderEvents.AFTER_SETUP, originalAfterSetup);
            invokerField.set(WorldRenderEvents.AFTER_ENTITIES, originalAfterEntities);
            invokerField.set(WorldRenderEvents.AFTER_TRANSLUCENT, originalAfterTranslucent);
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Distant Horizons after mirror render", e);
        }
    }
}
