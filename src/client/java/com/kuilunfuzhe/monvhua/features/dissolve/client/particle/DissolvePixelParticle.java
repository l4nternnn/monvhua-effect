package com.kuilunfuzhe.monvhua.features.dissolve.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

public final class DissolvePixelParticle extends SpriteBillboardParticle {
    private float initialAlpha = 1.0F;
    private float initialScale;

    private DissolvePixelParticle(ClientWorld world, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        setVelocity(velocityX, velocityY, velocityZ);
        setSprite(spriteProvider);
        this.collidesWithWorld = false;
        this.velocityMultiplier = 0.94F;
        this.gravityStrength = 0.0F;
        this.maxAge = 22;
        this.scale = 0.035F;
        this.initialScale = this.scale;
    }

    public void configure(int argb, float scale, int lifetimeTicks) {
        int alphaByte = (argb >>> 24) & 0xFF;
        int redByte = (argb >>> 16) & 0xFF;
        int greenByte = (argb >>> 8) & 0xFF;
        int blueByte = argb & 0xFF;
        this.initialAlpha = alphaByte <= 0 ? 1.0F : alphaByte / 255.0F;
        this.alpha = this.initialAlpha;
        setColor(redByte / 255.0F, greenByte / 255.0F, blueByte / 255.0F);
        this.scale = Math.max(0.001F, scale);
        this.initialScale = this.scale;
        this.maxAge = Math.max(1, lifetimeTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.dead && this.maxAge > 0) {
            float life = Math.min(1.0F, this.age / (float) this.maxAge);
            this.alpha = this.initialAlpha * (1.0F - life);
            this.scale = this.initialScale * (1.0F - life * 0.35F);
        }
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new DissolvePixelParticle(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
        }
    }
}
