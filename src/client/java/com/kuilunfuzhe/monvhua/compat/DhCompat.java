package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Distant Horizons compatibility layer.
 * Prevents DH from rendering LOD terrain during mirror viewport re-render.
 */
public class DhCompat {
    private static boolean dhLoaded;
    private static boolean apiUnavailable;
    private static int suspendDepth;

    private static Object renderingEnabledConfig;
    private static Object previousApiValue;
    private static boolean capturedPreviousApiValue;
    private static boolean apiSuspended;

    private static Object originalAfterSetup;
    private static Object originalAfterEntities;
    private static Object originalAfterTranslucent;
    private static boolean eventsSuspended;

    public static void init() {
        dhLoaded = FabricLoader.getInstance().isModLoaded("distanthorizons");
        if (dhLoaded) {
            MonvhuaMod.LOGGER.info("[Monvhua] Distant Horizons detected, enabling mirror compatibility layer");
        }
    }

    /**
     * Temporarily disables DH's LOD rendering and replaces target WorldRenderEvents invokers with no-ops.
     * Must be called before the mirror's worldRenderer.render() call.
     */
    public static void suspend() {
        if (!dhLoaded) return;
        if (suspendDepth++ > 0) return;

        apiSuspended = suspendDhApiRendering();
        eventsSuspended = suspendWorldRenderEvents();
    }

    /**
     * Restores DH rendering state.
     * Must be called after the mirror's worldRenderer.render() call completes.
     */
    public static void resume() {
        if (!dhLoaded) return;
        if (suspendDepth <= 0) {
            suspendDepth = 0;
            return;
        }
        if (--suspendDepth > 0) return;

        if (eventsSuspended) {
            resumeWorldRenderEvents();
            eventsSuspended = false;
        }
        if (apiSuspended) {
            resumeDhApiRendering();
            apiSuspended = false;
        }
    }

    private static boolean suspendDhApiRendering() {
        if (apiUnavailable) return false;
        try {
            Object config = getRenderingEnabledConfig();
            if (config == null) return false;

            previousApiValue = invokeIfPresent(config, "getApiValue");
            capturedPreviousApiValue = true;
            Method setValue = findMethod(config.getClass(), "setValue", 1);
            setValue.invoke(config, Boolean.FALSE);
            return true;
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to suspend Distant Horizons rendering via API for mirror render", e);
            apiUnavailable = true;
            return false;
        }
    }

    private static void resumeDhApiRendering() {
        try {
            Object config = getRenderingEnabledConfig();
            if (config == null) return;

            if (capturedPreviousApiValue && previousApiValue != null) {
                findMethod(config.getClass(), "setValue", 1).invoke(config, previousApiValue);
            } else {
                Method clearValue = findMethod(config.getClass(), "clearValue", 0);
                clearValue.invoke(config);
            }
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Distant Horizons rendering API state after mirror render", e);
        } finally {
            previousApiValue = null;
            capturedPreviousApiValue = false;
        }
    }

    private static Object getRenderingEnabledConfig() throws Exception {
        if (renderingEnabledConfig != null) return renderingEnabledConfig;

        Class<?> delayedApi = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
        Object configs = delayedApi.getField("configs").get(null);
        if (configs == null) return null;

        Object graphics = findMethod(configs.getClass(), "graphics", 0).invoke(configs);
        if (graphics == null) return null;

        renderingEnabledConfig = findMethod(graphics.getClass(), "renderingEnabled", 0).invoke(graphics);
        return renderingEnabledConfig;
    }

    private static boolean suspendWorldRenderEvents() {
        try {
            Field invokerField = Event.class.getDeclaredField("invoker");
            invokerField.setAccessible(true);

            originalAfterSetup = invokerField.get(WorldRenderEvents.AFTER_SETUP);
            originalAfterEntities = invokerField.get(WorldRenderEvents.AFTER_ENTITIES);
            originalAfterTranslucent = invokerField.get(WorldRenderEvents.AFTER_TRANSLUCENT);

            invokerField.set(WorldRenderEvents.AFTER_SETUP, (WorldRenderEvents.AfterSetup) ctx -> {});
            invokerField.set(WorldRenderEvents.AFTER_ENTITIES, (WorldRenderEvents.AfterEntities) ctx -> {});
            invokerField.set(WorldRenderEvents.AFTER_TRANSLUCENT, (WorldRenderEvents.AfterTranslucent) ctx -> {});
            return true;
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to suspend Fabric world render events for mirror render", e);
            return false;
        }
    }

    private static void resumeWorldRenderEvents() {
        if (originalAfterSetup == null) return;
        try {
            Field invokerField = Event.class.getDeclaredField("invoker");
            invokerField.setAccessible(true);

            invokerField.set(WorldRenderEvents.AFTER_SETUP, originalAfterSetup);
            invokerField.set(WorldRenderEvents.AFTER_ENTITIES, originalAfterEntities);
            invokerField.set(WorldRenderEvents.AFTER_TRANSLUCENT, originalAfterTranslucent);
        } catch (Exception e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Fabric world render events after mirror render", e);
        } finally {
            originalAfterSetup = null;
            originalAfterEntities = null;
            originalAfterTranslucent = null;
        }
    }

    private static Object invokeIfPresent(Object target, String methodName) {
        try {
            return findMethod(target.getClass(), methodName, 0).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
