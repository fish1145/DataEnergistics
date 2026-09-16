package com.fish_dan_.data_energistics.integration.energy.mekanism;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint(requiredMods = "mekanism")
public final class MekanismEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new MekanismEnergyEndpointIntegration());
    }
}
