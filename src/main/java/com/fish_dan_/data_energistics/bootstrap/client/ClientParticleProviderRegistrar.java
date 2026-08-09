package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.particle.RadixLossParticle;
import com.fish_dan_.data_energistics.registry.DEParticles;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

final class ClientParticleProviderRegistrar {

    private ClientParticleProviderRegistrar() {}

    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DEParticles.RADIX_LOSS.get(), RadixLossParticle.Provider::new);
    }
}
