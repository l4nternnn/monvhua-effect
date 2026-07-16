package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

public final class PortalIrisCompat {
    private static final ThreadLocal<Deque<Boolean>> BYPASS_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean initialized;
    private static boolean available;
    private static Method isPackInUseQuickMethod;
    private static Field immediateBypassField;

    private PortalIrisCompat() {
    }

    public static boolean isShaderPackActive() {
        if (!isAvailable() || isPackInUseQuickMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isPackInUseQuickMethod.invoke(null));
        } catch (ReflectiveOperationException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to query Iris shader-pack state for portal render", e);
            return false;
        }
    }

    public static void beginPortalSurfaceRender() {
        if (!isAvailable() || immediateBypassField == null) {
            return;
        }
        try {
            Deque<Boolean> stack = BYPASS_STACK.get();
            stack.push(immediateBypassField.getBoolean(null));
            immediateBypassField.setBoolean(null, true);
        } catch (IllegalAccessException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to enable Iris portal surface shader bypass", e);
        }
    }

    public static void endPortalSurfaceRender() {
        if (!isAvailable() || immediateBypassField == null) {
            return;
        }
        Deque<Boolean> stack = BYPASS_STACK.get();
        if (stack.isEmpty()) {
            BYPASS_STACK.remove();
            return;
        }
        try {
            immediateBypassField.setBoolean(null, stack.pop());
        } catch (IllegalAccessException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Iris portal surface shader bypass", e);
        } finally {
            if (stack.isEmpty()) {
                BYPASS_STACK.remove();
            }
        }
    }

    private static boolean isAvailable() {
        if (initialized) {
            return available;
        }
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            available = false;
            return false;
        }

        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Class<?> immediateStateClass = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            isPackInUseQuickMethod = irisClass.getMethod("isPackInUseQuick");
            immediateBypassField = immediateStateClass.getField("bypass");
            immediateBypassField.setAccessible(true);
            available = true;
        } catch (ReflectiveOperationException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Iris detected, but portal Iris compatibility could not be initialized", e);
            available = false;
        }

        return available;
    }
}
