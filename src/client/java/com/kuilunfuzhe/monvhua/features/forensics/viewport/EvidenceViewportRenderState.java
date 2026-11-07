package com.kuilunfuzhe.monvhua.features.forensics.viewport;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;

/** The draw operation captures pose values when submitted, not when GUI rendering is deferred. */
public record EvidenceViewportRenderState(int x1, int y1, int x2, int y2, Matrix3x2f pose,
                                      ScreenRect scissorArea, EvidenceViewportFrame frame, Scene scene)
        implements SpecialGuiElementRenderState {
    @Override public float scale() { return 1; }
    @Override public ScreenRect bounds() {
        return new ScreenRect(x1, y1, x2 - x1, y2 - y1).transformEachVertex(pose).intersection(scissorArea);
    }

    @FunctionalInterface
    public interface Scene {
        void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers);
    }
}
