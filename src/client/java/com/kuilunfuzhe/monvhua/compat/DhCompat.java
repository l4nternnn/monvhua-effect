package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final Object PORTAL_STATE_LOCK = new Object();
    private static boolean portalBridgeUnavailable;
    private static AtomicReference<Object> portalRenderStateRef;
    private static Object portalRenderState;
    private static Object previousRenderState;
    private static Object lastMainRenderState;
    private static Object portalQuadtree;
    private static Constructor<?> dhBlockPos2DConstructor;
    private static Method portalQuadtreeTickMethod;
    private static int portalRenderDepth;

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
        eventsSuspended = false;
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
     * Installs a persistent portal-specific DH render state for one remote render pass.
     */
    public static boolean beginPortalRender(double cameraX, double cameraZ) {
        if (!PortalViewConfig.ENABLE_PORTAL_DH_LOD_BRIDGE || !dhLoaded || portalBridgeUnavailable) {
            return false;
        }

        synchronized (PORTAL_STATE_LOCK) {
            try {
                if (portalRenderDepth > 0) {
                    portalRenderDepth++;
                    return true;
                }
                if (!ensurePortalRenderState()) {
                    return false;
                }

                Object targetPos = dhBlockPos2DConstructor.newInstance(
                        (int) Math.floor(cameraX),
                        (int) Math.floor(cameraZ)
                );
                portalQuadtreeTickMethod.invoke(portalQuadtree, targetPos);

                Object currentState = portalRenderStateRef.get();
                if (currentState == portalRenderState) {
                    if (lastMainRenderState == null
                            || !portalRenderStateRef.compareAndSet(
                                    portalRenderState,
                                    lastMainRenderState
                            )) {
                        return false;
                    }
                    currentState = lastMainRenderState;
                }
                if (currentState == null) {
                    return false;
                }
                if (!portalRenderStateRef.compareAndSet(currentState, portalRenderState)) {
                    return false;
                }

                previousRenderState = currentState;
                lastMainRenderState = currentState;
                portalRenderDepth = 1;
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                disablePortalBridge("prepare portal LOD render state", exception);
                return false;
            }
        }
    }

    public static void endPortalRender() {
        synchronized (PORTAL_STATE_LOCK) {
            if (portalRenderDepth <= 0) {
                portalRenderDepth = 0;
                return;
            }
            if (--portalRenderDepth > 0) {
                return;
            }
            restorePreviousRenderState();
        }
    }

    public static void resetPortalRenderState() {
        synchronized (PORTAL_STATE_LOCK) {
            restorePreviousRenderState();
            closePortalRenderState();
        }
    }

    private static boolean ensurePortalRenderState() throws ReflectiveOperationException {
        if (portalRenderState != null && portalRenderStateRef != null) {
            return true;
        }

        Object dhLevel = getCurrentDhClientLevel();
        if (dhLevel == null) {
            return false;
        }

        Object clientLevelModule = findField(dhLevel.getClass(), "clientside").get(dhLevel);
        if (clientLevelModule == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        AtomicReference<Object> stateRef = (AtomicReference<Object>) findField(
                clientLevelModule.getClass(),
                "ClientRenderStateRef"
        ).get(clientLevelModule);
        if (stateRef == null || stateRef.get() == null) {
            return false;
        }

        Object fullDataSourceProvider = findField(
                clientLevelModule.getClass(),
                "fullDataSourceProvider"
        ).get(clientLevelModule);
        Class<?> renderStateClass = Class.forName(
                "com.seibel.distanthorizons.core.level.ClientLevelModule$ClientRenderState"
        );
        Object renderState = findConstructor(renderStateClass, 2)
                .newInstance(dhLevel, fullDataSourceProvider);
        Object quadtree = findField(renderStateClass, "quadtree").get(renderState);

        Class<?> dhBlockPos2DClass = Class.forName(
                "com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D"
        );

        portalRenderStateRef = stateRef;
        portalRenderState = renderState;
        lastMainRenderState = stateRef.get();
        portalQuadtree = quadtree;
        dhBlockPos2DConstructor = dhBlockPos2DClass.getConstructor(int.class, int.class);
        portalQuadtreeTickMethod = findMethod(quadtree.getClass(), "tryTick", 1);
        return true;
    }

    private static Object getCurrentDhClientLevel() throws ReflectiveOperationException {
        Class<?> sharedApiClass = Class.forName(
                "com.seibel.distanthorizons.core.api.internal.SharedApi"
        );
        Object dhClientWorld = findMethod(sharedApiClass, "tryGetDhClientWorld", 0)
                .invoke(null);
        if (dhClientWorld == null) {
            return null;
        }

        Class<?> singletonInjectorClass = Class.forName(
                "com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector"
        );
        Object singletonInjector = singletonInjectorClass.getField("INSTANCE").get(null);
        Class<?> minecraftClientWrapperClass = Class.forName(
                "com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper"
        );
        Object minecraftClientWrapper = findMethod(singletonInjectorClass, "get", 1)
                .invoke(singletonInjector, minecraftClientWrapperClass);
        Object currentLevelWrapper = findMethod(
                minecraftClientWrapper.getClass(),
                "getWrappedClientLevel",
                0
        ).invoke(minecraftClientWrapper);
        if (currentLevelWrapper == null) {
            return null;
        }

        return findMethod(dhClientWorld.getClass(), "getClientLevel", 1)
                .invoke(dhClientWorld, currentLevelWrapper);
    }

    private static void restorePreviousRenderState() {
        Object restoreState = previousRenderState != null
                ? previousRenderState
                : lastMainRenderState;
        if (portalRenderStateRef != null
                && portalRenderState != null
                && restoreState != null) {
            portalRenderStateRef.compareAndSet(portalRenderState, restoreState);
        }
        previousRenderState = null;
        portalRenderDepth = 0;
    }

    private static void closePortalRenderState() {
        if (portalRenderState != null) {
            try {
                findMethod(portalRenderState.getClass(), "close", 0).invoke(portalRenderState);
            } catch (ReflectiveOperationException exception) {
                MonvhuaMod.LOGGER.warn(
                        "[Monvhua] Failed to close Distant Horizons portal render state",
                        exception
                );
            }
        }
        portalRenderStateRef = null;
        portalRenderState = null;
        lastMainRenderState = null;
        portalQuadtree = null;
        dhBlockPos2DConstructor = null;
        portalQuadtreeTickMethod = null;
    }

    private static void disablePortalBridge(String stage, Exception exception) {
        restorePreviousRenderState();
        closePortalRenderState();
        portalBridgeUnavailable = true;
        MonvhuaMod.LOGGER.warn(
                "[Monvhua] Failed to {}; disabling the Distant Horizons portal bridge",
                stage,
                exception
        );
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

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Constructor<?> findConstructor(Class<?> type, int parameterCount)
            throws NoSuchMethodException {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == parameterCount) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + " constructor/" + parameterCount);
    }
}
