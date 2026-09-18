package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public class SwingEntityRenderer extends EntityRenderer<SwingEntity, SwingEntityRenderer.State> {
    public SwingEntityRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius = .5f; }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRender(SwingEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.structureBounds().expand(.5));
    }
    @Override public void updateRenderState(SwingEntity e, State s, float tickDelta) {
        super.updateRenderState(e, s, tickDelta);
        s.angle=e.angle(tickDelta); s.z=e.zAxis(); s.structure=e.structure(); s.pivot=e.getPos();
        s.world = e.getWorld();
    }
    @Override public void render(State s, MatrixStack m, VertexConsumerProvider v, int light) {
        SwingStructureRenderer.render(s, m, v, light);
        super.render(s,m,v,light);
    }
    public static final class State extends EntityRenderState {
        float angle;
        boolean z;
        Vec3d pivot = Vec3d.ZERO;
        net.minecraft.world.World world;
        SwingStructure structure = new SwingStructure(java.util.List.of());
    }
}
