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
    /** DH 模组是否已加载 */
    private static boolean dhLoaded;
    /** DH API 是否不可用（反射失败后标记，避免反复尝试） */
    private static boolean apiUnavailable;
    /** suspend 嵌套深度计数器，支持嵌套调用 */
    private static int suspendDepth;

    /** DH API 的 renderingEnabled 配置对象，首次获取后缓存 */
    private static Object renderingEnabledConfig;
    /** suspend 前保存的 DH API 渲染开关原始值 */
    private static Object previousApiValue;
    /** 是否已成功捕获 API 原始值 */
    private static boolean capturedPreviousApiValue;
    /** DH API 渲染是否已被暂停 */
    private static boolean apiSuspended;

    /** 被替换前的 WorldRenderEvents.AFTER_SETUP 原始 invoker */
    private static Object originalAfterSetup;
    /** 被替换前的 WorldRenderEvents.AFTER_ENTITIES 原始 invoker */
    private static Object originalAfterEntities;
    /** 被替换前的 WorldRenderEvents.AFTER_TRANSLUCENT 原始 invoker */
    private static Object originalAfterTranslucent;
    /** WorldRenderEvents 事件是否已被暂停 */
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

    /**
     * 通过 DH API 关闭渲染——反射调用 renderingEnabled.setValue(false)。
     * @return 是否成功暂停
     */
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

    /**
     * 恢复 DH API 渲染——将 renderingEnabled 设回 suspend 前的值或清除覆盖。
     */
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

    /**
     * 通过反射获取 DH API 的 renderingEnabled 配置对象。
     * 路径: DhApi.Delayed → configs → graphics() → renderingEnabled()
     * 首次获取后缓存到 renderingEnabledConfig。
     * @return renderingEnabled 配置对象，或 null
     */
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

    /**
     * 暂停 Fabric WorldRenderEvents 的三个渲染事件——将 invoker 替换为空实现。
     * 保存原始 invoker 以便后续恢复。
     * @return 是否成功暂停
     */
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

    /**
     * 恢复 Fabric WorldRenderEvents 的三个渲染事件——将 invoker 还原为原始值。
     */
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

    /**
     * 反射调用目标对象的无参方法，失败时静默返回 null。
     * @param target 目标对象
     * @param methodName 方法名
     * @return 方法返回值，或 null
     */
    private static Object invokeIfPresent(Object target, String methodName) {
        try {
            return findMethod(target.getClass(), methodName, 0).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 在类的继承链中查找指定名称和参数数量的方法，包括父类。
     * 查找到的方法会自动 setAccessible(true)。
     * @param type 起始类型
     * @param name 方法名
     * @param parameterCount 参数个数
     * @return 可访问的 Method 对象
     * @throws NoSuchMethodException 未找到匹配方法
     */
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
