package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

/** Keeps optional Iris API linkage outside the activity renderer itself. */
public final class ActivityIrisPipelineCompat {
    private static boolean bubblePipelineAssigned;

    private ActivityIrisPipelineCompat() {
    }

    public static void assignBubblePipeline(RenderPipeline pipeline) {
        if (bubblePipelineAssigned) {
            return;
        }

        try {
            IrisApi iris = IrisApi.getInstance();
            iris.assignPipeline(pipeline, IrisProgram.TEXTURED);
            bubblePipelineAssigned = true;
            MonvhuaMod.LOGGER.info(
                    "[Monvhua] Iris activity bubble pipeline assigned to TEXTURED; shader pack active={}",
                    iris.isShaderPackInUse()
            );
        } catch (RuntimeException exception) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to assign the Iris activity bubble pipeline", exception);
        }
    }
}
