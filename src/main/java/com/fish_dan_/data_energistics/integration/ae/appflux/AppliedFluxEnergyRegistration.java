package com.fish_dan_.data_energistics.integration.ae.appflux;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint(requiredMods = "appflux")
public final class AppliedFluxEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new AppliedFluxEnergyEndpointIntegration());
    }
}
