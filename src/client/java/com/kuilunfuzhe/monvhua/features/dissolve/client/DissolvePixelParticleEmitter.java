package com.kuilunfuzhe.monvhua.features.dissolve.client;

import com.kuilunfuzhe.monvhua.features.dissolve.DissolveParticleTypes;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveProfile;
import com.kuilunfuzhe.monvhua.features.dissolve.client.particle.DissolvePixelParticle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

final class DissolvePixelParticleEmitter {
    private DissolvePixelParticleEmitter() {
    }

    static void tick(MinecraftClient client, PlayerEntity player, DissolveClientController.ClientDissolveState state) {
        DissolveProfile profile = state.profile();
        if (!profile.pixelParticleEnabled()
                || profile.maxPixelParticlesPerTick() <= 0
                || client.world == null) {
            return;
        }

        double yaw = Math.toRadians(player.bodyYaw);
        Vec3d left = new Vec3d(-Math.cos(yaw), 0.0D, -Math.sin(yaw));

        for (int i = 0; i < profile.maxPixelParticlesPerTick(); i++) {
            DissolveTextureController.DissolvePixelEvent event = state.textureController().pollPixelEvent();
            if (event == null) {
                return;
            }

            Vec3d pos = DissolvePixelPositionMapper.toWorldPos(player, event.point());
            double randomSpeed = profile.pixelParticleRandomSpeed();
            Vec3d random = new Vec3d(
                    client.world.random.nextDouble() - 0.5D,
                    client.world.random.nextDouble() - 0.5D,
                    client.world.random.nextDouble() - 0.5D
            ).multiply(randomSpeed);
            Vec3d velocity = left.multiply(profile.pixelParticleLeftSpeed())
                    .add(0.0D, profile.pixelParticleUpSpeed(), 0.0D)
                    .add(random);

            Particle particle = client.particleManager.addParticle(
                    DissolveParticleTypes.DISSOLVE_PIXEL,
                    pos.x,
                    pos.y,
                    pos.z,
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
            if (particle instanceof DissolvePixelParticle pixelParticle) {
                int color = profile.pixelParticleUseSkinColor() ? event.argb() : profile.pixelParticleFixedColor();
                pixelParticle.configure(color, profile.pixelParticleScale(), profile.pixelParticleLifetimeTicks());
            }
        }
    }
}
