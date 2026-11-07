package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

/** Future read-only block snapshot, in the same scene coordinates as the recorded corpse root. */
public interface EvidenceEnvironmentLayer {
    long revision();
    Box bounds();
    default boolean occludes(net.minecraft.util.math.Vec3d from, net.minecraft.util.math.Vec3d to) { return false; }
    void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers);
}
