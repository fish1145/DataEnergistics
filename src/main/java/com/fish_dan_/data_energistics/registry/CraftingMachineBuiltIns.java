package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacity;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationAdapter;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspection;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspectionContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationPreparation;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;
import com.fish_dan_.data_energistics.blockentity.machine.DataAsynchronousProcessingFactoryBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;

import java.util.Optional;

/** Publishes Data Energistics machines through the same public declarations available to other mods. */
@DataEnergisticsEntrypoint
public final class CraftingMachineBuiltIns implements DataEnergisticsPlugin {

    private static final PatternUploadWorkstationAdapter DATA_INTEGRATED_CHARGER_UPLOAD = new PatternUploadWorkstationAdapter() {

        @Override
        public PatternUploadWorkstationInspection inspect(PatternUploadWorkstationInspectionContext context) {
            return ((DataIntegratedChargerBlockEntity) context.workstation()).inspectPatternUpload(context);
        }

        @Override
        public PatternUploadWorkstationPreparation prepare(PatternUploadWorkstationContext context) {
            return ((DataIntegratedChargerBlockEntity) context.workstation()).preparePatternUpload(context);
        }
    };

    public CraftingMachineBuiltIns() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.craftingMachines().registerCapacity(
                CraftingMachineCapacityRegistration.blockEntity(
                        Data_Energistics.id("asynchronous_factory_capacity"),
                        Data_Energistics.id("data_asynchronous_processing_factory"),
                        context -> Optional.of(new CraftingMachineCapacity(
                                ((DataAsynchronousProcessingFactoryBlockEntity) context.machine()).getRemainingCraftingCapacity(
                                        context.patternDetails(), context.prototype(), context.requestedCrafts())))));
        registry.craftingMachines().registerPatternUploadWorkstation(
                PatternUploadWorkstationRegistration.blockEntity(
                        Data_Energistics.id("data_integrated_charger_pattern_upload"),
                        Data_Energistics.id("data_integrated_charger"),
                        DATA_INTEGRATED_CHARGER_UPLOAD));
    }
}
