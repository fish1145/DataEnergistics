package com.fish_dan_.data_energistics.mixin.draconic;

import com.fish_dan_.data_energistics.integration.draconic.orbital.GuardianEncounterCleanup;

import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GuardianFightManager.class, remap = false)
public interface GuardianEncounterCleanupInvoker extends GuardianEncounterCleanup {

    @Override
    @Invoker("cleanUpAndDispose")
    void dataEnergistics$releaseErasedEncounter();
}
