package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class IrisMirrorCompat {
	private static final Object NULL_PIPELINE = new Object();
	private static final Object NO_PIPELINE_SNAPSHOT = new Object();
	private static final ThreadLocal<IrisRenderState> PREVIOUS_STATE = new ThreadLocal<>();
	private static boolean initialized;
	private static boolean available;
	private static Method getPipelineManagerMethod;
	private static Method isPackInUseQuickMethod;
	private static Field pipelineField;
	private static Object capturedRenderingState;
	private static Method getGbufferModelViewMethod;
	private static Method setGbufferModelViewMethod;
	private static Method getGbufferProjectionMethod;
	private static Method setGbufferProjectionMethod;

	private IrisMirrorCompat() {
	}

	public static void beginMirrorRender() {
		if (!isAvailable()) return;
		Object pipeline = NO_PIPELINE_SNAPSHOT;
		Matrix4f gbufferModelView = null;
		Matrix4f gbufferProjection = null;

		try {
			Object manager = getPipelineManagerMethod.invoke(null);
			pipeline = manager == null ? null : pipelineField.get(manager);
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to snapshot Iris pipeline before mirror render", e);
		}

		try {
			gbufferModelView = snapshotMatrix(getGbufferModelViewMethod);
			gbufferProjection = snapshotMatrix(getGbufferProjectionMethod);
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to snapshot Iris captured render state before mirror render", e);
		}

		PREVIOUS_STATE.set(new IrisRenderState(
				pipeline == null ? NULL_PIPELINE : pipeline,
				gbufferModelView,
				gbufferProjection
		));
	}

	public static void endMirrorRender() {
		if (!isAvailable()) return;
		IrisRenderState previous = PREVIOUS_STATE.get();
		PREVIOUS_STATE.remove();
		if (previous == null) return;

		try {
			Object manager = getPipelineManagerMethod.invoke(null);
			if (manager != null && previous.pipeline != NO_PIPELINE_SNAPSHOT) {
				pipelineField.set(manager, previous.pipeline == NULL_PIPELINE ? null : previous.pipeline);
			}
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Iris pipeline after mirror render", e);
		}

		try {
			restoreCapturedRenderingState(previous);
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to restore Iris captured render state after mirror render", e);
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
			initCapturedRenderingStateCompat();
			available = true;
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Iris detected, but mirror pipeline compatibility could not be initialized", e);
			available = false;
		}

		return available;
	}

	private static void initCapturedRenderingStateCompat() {
		try {
			Class<?> stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
			Class<?> matrixInterface = Class.forName("org.joml.Matrix4fc");
			capturedRenderingState = stateClass.getField("INSTANCE").get(null);
			getGbufferModelViewMethod = stateClass.getMethod("getGbufferModelView");
			setGbufferModelViewMethod = stateClass.getMethod("setGbufferModelView", matrixInterface);
			getGbufferProjectionMethod = stateClass.getMethod("getGbufferProjection");
			setGbufferProjectionMethod = stateClass.getMethod("setGbufferProjection", Matrix4f.class);
		} catch (ReflectiveOperationException e) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Iris captured render state compatibility could not be initialized", e);
			capturedRenderingState = null;
			getGbufferModelViewMethod = null;
			setGbufferModelViewMethod = null;
			getGbufferProjectionMethod = null;
			setGbufferProjectionMethod = null;
		}
	}

	private static Matrix4f snapshotMatrix(Method getter) throws ReflectiveOperationException {
		if (capturedRenderingState == null || getter == null) {
			return null;
		}
		Object value = getter.invoke(capturedRenderingState);
		return value instanceof Matrix4fc matrix ? new Matrix4f(matrix) : null;
	}

	private static void restoreCapturedRenderingState(IrisRenderState previous) throws ReflectiveOperationException {
		if (capturedRenderingState == null) {
			return;
		}
		if (previous.gbufferModelView != null && setGbufferModelViewMethod != null) {
			setGbufferModelViewMethod.invoke(capturedRenderingState, previous.gbufferModelView);
		}
		if (previous.gbufferProjection != null && setGbufferProjectionMethod != null) {
			setGbufferProjectionMethod.invoke(capturedRenderingState, previous.gbufferProjection);
		}
	}

	private record IrisRenderState(Object pipeline, Matrix4f gbufferModelView, Matrix4f gbufferProjection) {
	}
}
