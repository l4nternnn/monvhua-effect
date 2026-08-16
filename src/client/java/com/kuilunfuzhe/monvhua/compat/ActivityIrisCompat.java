package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ActivityIrisCompat {
    private static final ThreadLocal<Deque<Boolean>> BYPASS_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean initialized;
    private static boolean available;
    private static Field immediateBypassField;

    private ActivityIrisCompat() {
    }

    public static void beginBubbleRender() {
        if (!isAvailable() || immediateBypassField == null) {
            return;
        }
        try {
            Deque<Boolean> stack = BYPASS_STACK.get();
            stack.push(immediateBypassField.getBoolean(null));
            immediateBypassField.setBoolean(null, true);
        } catch (IllegalAccessException exception) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to enable Iris activity bubble shader bypass", exception);
        }
    }

    public static void endBubbleRender() {
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
        } catch (IllegalAccessException exception) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Iris activity bubble shader bypass", exception);
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
            return false;
        }
        try {
            Class<?> immediateStateClass = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            immediateBypassField = immediateStateClass.getField("bypass");
            immediateBypassField.setAccessible(true);
            available = true;
        } catch (ReflectiveOperationException exception) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Iris activity bubble compatibility could not be initialized", exception);
        }
        return available;
    }
}
