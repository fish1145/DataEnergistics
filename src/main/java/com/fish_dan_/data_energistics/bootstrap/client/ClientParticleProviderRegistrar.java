package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.particle.DataDisorderParticle;
import com.fish_dan_.data_energistics.registry.ModParticles;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

final class ClientParticleProviderRegistrar {

    private ClientParticleProviderRegistrar() {}

    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DATA_DISORDER.get(), DataDisorderParticle.Provider::new);
    }
}
