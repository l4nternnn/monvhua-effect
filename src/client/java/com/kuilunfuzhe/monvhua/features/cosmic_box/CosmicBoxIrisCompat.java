package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class CosmicBoxIrisCompat {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_PROGRAM_CLASS = "net.irisshaders.iris.api.v0.IrisProgram";

    private static boolean initialized;
    private static boolean available;
    private static Object api;
    private static Method isShaderPackInUse;
    private static Method assignPipeline;
    private static Class<? extends Enum> irisProgramClass;

    private CosmicBoxIrisCompat() {
    }

    public static void initialize() {
        loadApi();
        if (!available) {
            return;
        }

        assign(CosmicBoxRenderPipelines.COSMIC_BOX, "BASIC");
        assign(CosmicBoxRenderPipelines.COSMIC_BEAM_RAINBOW, "BASIC");
        assign(CosmicBoxRenderPipelines.COSMIC_BEAM_WHITE, "BASIC");
    }

    public static boolean isShaderPackInUse() {
        loadApi();
        if (!available) {
            return false;
        }

        try {
            return (boolean) isShaderPackInUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static void assign(RenderPipeline pipeline, String programName) {
        try {
            Enum<?> program = enumValue(programName);
            assignPipeline.invoke(api, pipeline, program);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MonvhuaMod.LOGGER.warn("[monvhua] Failed to assign Iris pipeline for {}", pipeline.getLocation(), exception);
        }
    }

    private static void loadApi() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName(IRIS_API_CLASS);
            Class<?> programClass = Class.forName(IRIS_PROGRAM_CLASS);
            api = apiClass.getMethod("getInstance").invoke(null);
            isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            assignPipeline = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
            irisProgramClass = programClass.asSubclass(Enum.class);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MonvhuaMod.LOGGER.warn("[monvhua] Iris API was present but could not be initialized.", exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> enumValue(String name) {
        return Enum.valueOf((Class) irisProgramClass, name);
    }
}
