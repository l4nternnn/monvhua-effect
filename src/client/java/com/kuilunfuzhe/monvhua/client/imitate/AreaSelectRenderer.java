package com.kuilunfuzhe.monvhua.client.imitate;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

public class AreaSelectRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        Vec3d centerPoint = null;
        
        if (AreaSelectClientManager.isMarked()) {
            centerPoint = AreaSelectClientManager.getMarkedCenter();
        } else if (AreaImitateClientManager.hasAreaImitate()) {
            centerPoint = AreaImitateClientManager.getAreaCenter();
        }
        
        if (centerPoint == null) {
            return;
        }
        
        for (int i = 0; i < 10; i++) {
            double beamY = centerPoint.y + i * 0.3;
            client.particleManager.addParticle(ParticleTypes.END_ROD, 
                centerPoint.x, beamY, centerPoint.z, 0, 0, 0);
        }
    }
}