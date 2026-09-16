package com.fish_dan_.data_energistics.integration.energy.brandonscore;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint(requiredMods = "brandonscore")
public final class BrandonsCoreEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new BrandonsCoreEnergyEndpointIntegration(new BrandonsCoreEnergyBridge()));
    }
}
