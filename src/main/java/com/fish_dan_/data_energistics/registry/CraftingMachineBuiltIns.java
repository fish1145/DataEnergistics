package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;

/** Publishes Data Energistics machines through the same public declarations available to other mods. */
@DataEnergisticsEntrypoint
public final class CraftingMachineBuiltIns implements DataEnergisticsPlugin {

    public CraftingMachineBuiltIns() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.craftingMachines().registerPatternUploadWorkstation(
                PatternUploadWorkstationRegistration.blockEntity(
                        Data_Energistics.id("data_integrated_charger_pattern_upload"),
                        Data_Energistics.id("data_integrated_charger"),
                        context -> ((DataIntegratedChargerBlockEntity) context.workstation()).preparePatternUpload(
                                context)));
    }
}
