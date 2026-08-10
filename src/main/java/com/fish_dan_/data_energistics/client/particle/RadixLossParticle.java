package com.fish_dan_.data_energistics.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class RadixLossParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected RadixLossParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
                                double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.friction = 0.82F;
        this.gravity = -0.015F;
        this.lifetime = 18 + this.random.nextInt(10);
        this.quadSize = 0.16F + this.random.nextFloat() * 0.06F;
        this.roll = 0.0F;
        this.oRoll = this.roll;

        this.setColor(1.0F, 1.0F, 1.0F);
        this.setAlpha(0.85F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.alpha = Math.max(0.0F, 0.85F * (1.0F - (float) this.age / (float) this.lifetime));
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public RadixLossParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                                                double z, double xSpeed, double ySpeed, double zSpeed) {
            return new RadixLossParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
