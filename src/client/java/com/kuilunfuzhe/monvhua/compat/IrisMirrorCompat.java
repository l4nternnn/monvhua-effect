package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class IrisMirrorCompat {
	private static final Object NULL_PIPELINE = new Object();
	private static final ThreadLocal<Object> PREVIOUS_PIPELINE = new ThreadLocal<>();
	private static boolean initialized;
	private static boolean available;
	private static Method getPipelineManagerMethod;
	private static Method isPackInUseQuickMethod;
	private static Field pipelineField;

	private IrisMirrorCompat() {
	}

	public static void beginMirrorRender() {
		if (!isAvailable()) return;
		try {
			Object manager = getPipelineManagerMethod.invoke(null);
			Object pipeline = manager == null ? null : pipelineField.get(manager);
			PREVIOUS_PIPELINE.set(pipeline == null ? NULL_PIPELINE : pipeline);
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to snapshot Iris pipeline before mirror render", e);
		}
	}

	public static void endMirrorRender() {
		if (!isAvailable()) return;
		Object previous = PREVIOUS_PIPELINE.get();
		PREVIOUS_PIPELINE.remove();
		if (previous == null) return;

		try {
			Object manager = getPipelineManagerMethod.invoke(null);
			if (manager != null) {
				pipelineField.set(manager, previous == NULL_PIPELINE ? null : previous);
			}
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Iris pipeline after mirror render", e);
		}
	}

	public static boolean isShaderPackActive() {
		if (!isAvailable() || isPackInUseQuickMethod == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(isPackInUseQuickMethod.invoke(null));
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to query Iris shader-pack state", e);
			return false;
		}
	}

	private static boolean isAvailable() {
		if (initialized) return available;
		initialized = true;

		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			available = false;
			return false;
		}

		try {
			Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
			Class<?> managerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
			getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");
			isPackInUseQuickMethod = irisClass.getMethod("isPackInUseQuick");
			pipelineField = managerClass.getDeclaredField("pipeline");
			pipelineField.setAccessible(true);
			available = true;
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Iris detected, but mirror pipeline compatibility could not be initialized", e);
			available = false;
		}

		return available;
	}
}
