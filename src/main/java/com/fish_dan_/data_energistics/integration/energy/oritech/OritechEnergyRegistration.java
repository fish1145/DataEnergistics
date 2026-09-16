package com.fish_dan_.data_energistics.integration.energy.oritech;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.energy.access.VerifiedUnlimitedEnergyAccess;

@DataEnergisticsEntrypoint(requiredMods = "oritech")
public final class OritechEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new OritechEnergyEndpointIntegration(new OritechEnergyBridge(), new VerifiedUnlimitedEnergyAccess()));
    }
}
