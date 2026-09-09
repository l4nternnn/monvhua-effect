package com.kuilunfuzhe.monvhua.features.glitch.client;

import com.kuilunfuzhe.monvhua.features.glitch.GlitchPlane;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.List;

/** Stateless, bounded rendering; it allocates neither particles nor per-band collections. */
final class GlitchPlaneRenderer {
    private static final int MAX_BANDS_PER_FRAME = 256;
    private static final double MAX_RENDER_DISTANCE = 64.0;
    private GlitchPlaneRenderer() {}
    static void render(WorldRenderContext context, List<GlitchPlane> planes) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || planes.isEmpty()) return;
        Vec3d camera = context.camera().getPos();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        MatrixStack.Entry entry = context.matrixStack().peek(); int remaining = MAX_BANDS_PER_FRAME;
        long time = client.world.getTime();
        for (GlitchPlane plane : planes) {
            if (!plane.enabled() || remaining <= 0 || distanceSquared(plane, camera) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;
            int count = Math.min(remaining, Math.max(2, Math.round(48 * plane.density())));
            renderPlane(vertices, entry.getPositionMatrix(), camera, plane, time, count); remaining -= count;
        }
    }
    private static void renderPlane(VertexConsumer out, Matrix4f matrix, Vec3d camera, GlitchPlane p, long time, int count) {
        long bucket = Math.floorDiv(time, p.refreshTicks()); int localTick = (int) Math.floorMod(time, p.refreshTicks());
        for (int i=0;i<count;i++) {
            long h=mix(p.seed()^p.id().getMostSignificantBits()^p.id().getLeastSignificantBits()^bucket*0x9E3779B97F4A7C15L^i*0xD1B54A32D192ED03L);
            if (localTick >= 1 + (int)Math.floorMod(h >>> 48, p.refreshTicks())) continue;
            float lane=unit(h>>>8), start=unit(h>>>24), length=.06F+unit(h>>>40)*.39F;
            if ((h&7L)<2L) emitBand(out,matrix,camera,p,lane,start,length,.035F,0x050609,220,0F);
            else { emitBand(out,matrix,camera,p,lane,start,length,.023F,0xFF264A,205,-.035F); emitBand(out,matrix,camera,p,lane,start+.012F,length,.023F,0x2A5CFF,205,.035F); }
        }
    }
    private static void emitBand(VertexConsumer out, Matrix4f m, Vec3d c, GlitchPlane p, float lane, float start, float length, float thickness, int rgb, int alpha, float offset) {
        double a0,a1,b0,b1,fixed; int a,b;
        if (p.normal()==GlitchPlane.Axis.X) { a0=p.min().getY();a1=p.max().getY()+1D;b0=p.min().getZ();b1=p.max().getZ()+1D;fixed=p.min().getX()+.004D;a=1;b=2; }
        else if (p.normal()==GlitchPlane.Axis.Y) { a0=p.min().getX();a1=p.max().getX()+1D;b0=p.min().getZ();b1=p.max().getZ()+1D;fixed=p.min().getY()+.004D;a=0;b=2; }
        else { a0=p.min().getX();a1=p.max().getX()+1D;b0=p.min().getY();b1=p.max().getY()+1D;fixed=p.min().getZ()+.004D;a=0;b=1; }
        double u0=a0+(a1-a0)*start,u1=Math.min(a1,u0+(a1-a0)*length),v0=b0+(b1-b0)*lane+offset,v1=Math.min(b1,v0+Math.max(.01D,thickness));
        point(out,m,c,a,b,fixed,u0,v0,rgb,alpha); point(out,m,c,a,b,fixed,u1,v0,rgb,alpha); point(out,m,c,a,b,fixed,u1,v1,rgb,alpha); point(out,m,c,a,b,fixed,u0,v1,rgb,alpha);
    }
    private static void point(VertexConsumer v, Matrix4f m, Vec3d c, int a, int b, double fixed, double av, double bv, int rgb, int alpha) {
        double x, y, z;
        if (a == 0 && b == 1) { x = av; y = bv; z = fixed; }
        else if (a == 0) { x = av; y = fixed; z = bv; }
        else { x = fixed; y = av; z = bv; }
        v.vertex(m,(float)(x-c.x),(float)(y-c.y),(float)(z-c.z)).color((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha);
    }
    private static double distanceSquared(GlitchPlane p, Vec3d c) { double x=(p.min().getX()+p.max().getX()+1)*.5,y=(p.min().getY()+p.max().getY()+1)*.5,z=(p.min().getZ()+p.max().getZ()+1)*.5,dx=x-c.x,dy=y-c.y,dz=z-c.z; return dx*dx+dy*dy+dz*dz; }
    private static float unit(long v) { return (float)((v&0xFFFFFFL)/16777215.0); }
    private static long mix(long z) { z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31); }
}
