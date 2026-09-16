package com.kuilunfuzhe.monvhua.features.swing;

import com.kuilunfuzhe.monvhua.entity.ModEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class SwingEntityRenderer extends EntityRenderer<SwingEntity, SwingEntityRenderer.State> {
    public SwingEntityRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius = .5f; }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(SwingEntity e, State s, float tickDelta) { super.updateRenderState(e, s, tickDelta); s.angle=e.angle(tickDelta); s.z=e.zAxis(); s.structure=e.structure(); }
    @Override public void render(State s, MatrixStack m, VertexConsumerProvider v, int light) {
        m.push();
        m.multiply((s.z ? RotationAxis.POSITIVE_Z : RotationAxis.POSITIVE_X).rotation(s.angle));
        var br = MinecraftClient.getInstance().getBlockRenderManager();
        for (SwingBlock b : s.structure.blocks()) {
            m.push(); m.translate(b.localPos().getX() - .5, b.localPos().getY() - .5, b.localPos().getZ() - .5);
            br.renderBlockAsEntity(b.state(), m, v, light, OverlayTexture.DEFAULT_UV); m.pop();
        }
        m.pop(); super.render(s,m,v,light);
    }
    public static final class State extends EntityRenderState { float angle; boolean z; SwingStructure structure = new SwingStructure(java.util.List.of()); }
}
