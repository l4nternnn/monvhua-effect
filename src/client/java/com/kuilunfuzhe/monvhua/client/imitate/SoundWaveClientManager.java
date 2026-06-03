package com.kuilunfuzhe.monvhua.client.imitate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class SoundWaveClientManager {

    private static final int PARTICLES_PER_RING = 18;
    private static final int TOTAL_RINGS = 15;
    private static final double RADIUS_STEP = 0.8;
    private static final int TICKS_PER_RING = 2;

    private static final List<ActiveShockwave> activeShockwaves = new ArrayList<>();

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                activeShockwaves.clear();
                return;
            }

            List<ActiveShockwave> toRemove = new ArrayList<>();
            for (ActiveShockwave shockwave : activeShockwaves) {
                shockwave.tick(client);
                if (shockwave.isFinished()) {
                    toRemove.add(shockwave);
                }
            }
            activeShockwaves.removeAll(toRemove);
        });
    }

    public static void startShockwave(Vec3d center, double maxRadius) {
        activeShockwaves.add(new ActiveShockwave(center, maxRadius));
    }

    private static class ActiveShockwave {
        private final Vec3d center;
        private final double maxRadius;
        private int currentRing = 0;
        private int tickCounter = 0;

        ActiveShockwave(Vec3d center, double maxRadius) {
            this.center = center;
            this.maxRadius = maxRadius;
        }

        void tick(MinecraftClient client) {
            tickCounter++;

            if (tickCounter % TICKS_PER_RING == 0 && currentRing < TOTAL_RINGS) {
                double currentRadius = 1.0 + currentRing * RADIUS_STEP;

                if (currentRadius <= maxRadius) {
                    spawnRingParticles(client, currentRadius, currentRing);
                }
                currentRing++;
            }
        }

        private void spawnRingParticles(MinecraftClient client, double radius, int ringIndex) {
            ParticleManager particleManager = client.particleManager;
            double centerY = center.y + 1.0;

            for (int i = 0; i < PARTICLES_PER_RING; i++) {
                double angle = (i / (double) PARTICLES_PER_RING) * 2 * Math.PI;
                double x = center.x + radius * Math.cos(angle);
                double z = center.z + radius * Math.sin(angle);

                particleManager.addParticle(ParticleTypes.END_ROD, x, centerY, z, 0, 0, 0);

                if (ringIndex % 3 == 0) {
                    particleManager.addParticle(ParticleTypes.SONIC_BOOM, x, centerY, z, 0, 0, 0);
                }

                if (ringIndex == 0 || ringIndex == TOTAL_RINGS - 1) {
                    particleManager.addParticle(ParticleTypes.POOF, x, centerY, z, 0, 0.05, 0);
                }
            }
        }

        boolean isFinished() {
            return currentRing >= TOTAL_RINGS;
        }
    }
}