package com.fish_dan_.data_energistics.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Short, translucent exhaust with the model-space jet velocity and a fading pressure envelope. */
public final class RailSteamParticle extends TextureSheetParticle {

    private final float opacity;
    private final SpriteSet sprites;

    private RailSteamParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        float pressure = Math.clamp((float) Math.sqrt(vx * vx + vy * vy + vz * vz) / 0.164F, 0, 1);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.86F;
        this.gravity = -0.012F;
        this.lifetime = 10 + (int) (6 * pressure);
        this.quadSize = 0.08F + pressure * 0.06F;
        this.opacity = pressure * 0.65F;
        this.alpha = opacity;
        this.setColor(0.82F, 0.9F, 0.96F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = this.opacity * Math.max(0, 1 - this.age / (float) this.lifetime);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public RailSteamParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new RailSteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
