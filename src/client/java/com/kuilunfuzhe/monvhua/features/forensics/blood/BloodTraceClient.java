package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.paint.PaintSurface;
import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import java.util.*;

public final class BloodTraceClient {
    private record Mesh(BloodPatch patch, List<float[]> spans) {}
    private static final Map<String, Mesh> CACHE = new LinkedHashMap<>();
    private static long generation = -1;
    private static String dimension = "";
    private BloodTraceClient() {}
    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { CACHE.clear(); dimension = ""; }
            else if (!dimension.isEmpty() && !dimension.equals(client.world.getRegistryKey().getValue().toString())) { CACHE.clear(); dimension = ""; }
        });
        ClientPlayNetworking.registerGlobalReceiver(BloodTracePackets.Sync.ID, (packet, context) -> context.client().execute(() -> {
            var client = context.client();
            if (client.world == null || !packet.dimension().equals(client.world.getRegistryKey().getValue().toString()) || packet.generation() < generation) return;
            if (packet.generation() > generation) {
                if (!packet.clear()) return;
                CACHE.clear(); generation = packet.generation(); dimension = packet.dimension();
            } else if (packet.clear()) CACHE.clear();
            for (String id : packet.removals()) CACHE.remove(id);
            for (var patch : packet.patches()) {
                var old = CACHE.get(patch.id());
                if (old != null && old.patch.revision() >= patch.revision()) continue;
                if (old == null && CACHE.size() >= 65536) continue;
                CACHE.put(patch.id(), mesh(patch));
            }
        }));
        WorldRenderEvents.AFTER_ENTITIES.register(BloodTraceClient::render);
    }
    private static void reset() { CACHE.clear(); generation = -1; dimension = ""; }
    private static Mesh mesh(BloodPatch p) {
        List<float[]> spans = new ArrayList<>();
        for (int y = 0; y < 64; y++) {
            int start = -1;
            for (int x = 0; x <= 64; x++) {
                boolean on = x < 64 && p.covers((x + .5) / 64.0, (y + .5) / 64.0);
                if (on && start < 0) start = x;
                if (!on && start >= 0) { spans.add(new float[]{start / 64f, y / 64f, x / 64f, (y + 1) / 64f}); start = -1; }
            }
        }
        return new Mesh(p, List.copyOf(spans));
    }
    private static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || !dimension.equals(client.world.getRegistryKey().getValue().toString()) || context.consumers() == null || context.matrixStack() == null) return;
        Vec3d camera = context.camera().getPos();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(PaintRenderLayers.paintOverlay());
        List<Mesh> visible = CACHE.values().stream().filter(m -> client.world.isChunkLoaded(m.patch.pos()) && m.patch.pos().getSquaredDistance(camera) <= 80 * 80
                && (context.frustum() == null || context.frustum().isVisible(new Box(m.patch.pos()).expand(.01))))
                .sorted(Comparator.comparingDouble(m -> m.patch.pos().getSquaredDistance(camera))).toList();
        int budget = 120000;
        for (Mesh mesh : visible) {
            var p = mesh.patch;
            Vec3d origin = PaintSurface.point(p.pos(), p.face(), 0, 0, 1, .006).subtract(camera);
            Vec3d right = p.right(), down = p.down(), normal = PaintSurface.normal(p.face());
            int light = WorldRenderer.getLightmapCoordinates(client.world, p.pos().offset(p.face()));
            for (float[] s : mesh.spans) {
                if ((budget -= 4) < 0) return;
                vertex(vertices, matrix, origin.add(right.multiply(s[0])).add(down.multiply(s[1])), normal, light, p);
                vertex(vertices, matrix, origin.add(right.multiply(s[2])).add(down.multiply(s[1])), normal, light, p);
                vertex(vertices, matrix, origin.add(right.multiply(s[2])).add(down.multiply(s[3])), normal, light, p);
                vertex(vertices, matrix, origin.add(right.multiply(s[0])).add(down.multiply(s[3])), normal, light, p);
            }
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3d point, Vec3d normal, int light, BloodPatch p) {
        int red = 95 + (int) (p.seed() & 15);
        out.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(red, 5, 12, 245)
                .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
