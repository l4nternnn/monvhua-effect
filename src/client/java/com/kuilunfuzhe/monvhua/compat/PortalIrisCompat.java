package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class PortalIrisCompat {
    private static boolean initialized;
    private static boolean available;
    private static Method isPackInUseQuickMethod;

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
            isPackInUseQuickMethod = irisClass.getMethod("isPackInUseQuick");
            available = true;
        } catch (ReflectiveOperationException e) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Iris detected, but portal Iris compatibility could not be initialized", e);
            available = false;
        }

        return available;
    }
}
