package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TuningForkBaseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.blockentity.decor.DollBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalUplinkBeaconBlockEntity;
import com.fish_dan_.data_energistics.blockentity.orbital.astronomy.AstronomicalMirrorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.orbital.astronomy.AstronomicalObservatoryBlockEntity;
import com.fish_dan_.data_energistics.blockentity.orbital.astronomy.InterferenceArrayCoreBlockEntity;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumReturnPortalBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityInformationExchangeDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityPatternCoreBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DEBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Data_Energistics.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSolarPanelBlockEntity>> DATA_SOLAR_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "me_solar_panel",
            () -> BlockEntityType.Builder.of(DataSolarPanelBlockEntity::new, DEBlocks.DATA_SOLAR_PANEL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DigitalStorageDepotBlockEntity>> DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "digital_storage_depot",
            () -> BlockEntityType.Builder.of(DigitalStorageDepotBlockEntity::new, DEBlocks.DIGITAL_STORAGE_DEPOT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TuningForkBaseBlockEntity>> TUNING_FORK_BASE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "tuning_fork_base",
            () -> BlockEntityType.Builder.of(TuningForkBaseBlockEntity::new, DEBlocks.TUNING_FORK_BASE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TuningForkBlockEntity>> TUNING_FORK_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "tuning_fork",
            () -> BlockEntityType.Builder.of(
                    TuningForkBlockEntity::new,
                    DEBlocks.AMETHYST_TUNING_FORK.get(),
                    DEBlocks.DATA_TUNING_FORK.get(),
                    DEBlocks.RESONANCE_TUNING_FORK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataExtractorBlockEntity>> DATA_EXTRACTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_extractor",
            () -> BlockEntityType.Builder.of(DataExtractorBlockEntity::new, DEBlocks.DATA_EXTRACTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataRipperReassemblerBlockEntity>> DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_reassembler",
            () -> BlockEntityType.Builder.of(DataRipperReassemblerBlockEntity::new, DEBlocks.DATA_RIPPER_REASSEMBLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrinityDataCoreBlockEntity>> TRINITY_DATA_CORE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "trinity_data_core",
            () -> BlockEntityType.Builder.of(TrinityDataCoreBlockEntity::new, DEBlocks.TRINITY_DATA_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataDistributionTowerBlockEntity>> DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_distribution_tower",
            () -> BlockEntityType.Builder.of(DataDistributionTowerBlockEntity::new, DEBlocks.DATA_DISTRIBUTION_TOWER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataMimeticFieldBlockEntity>> DATA_MIMETIC_FIELD_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_mimetic_field",
            () -> BlockEntityType.Builder.of(DataMimeticFieldBlockEntity::new, DEBlocks.DATA_MIMETIC_FIELD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataTeleportAnchorBlockEntity>> DATA_TELEPORT_ANCHOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_teleport_anchor",
            () -> BlockEntityType.Builder.of(DataTeleportAnchorBlockEntity::new, DEBlocks.DATA_TELEPORT_ANCHOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrbitalControlConsoleBlockEntity>> ORBITAL_CONTROL_CONSOLE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "orbital_control_console",
            () -> BlockEntityType.Builder.of(OrbitalControlConsoleBlockEntity::new, DEBlocks.ORBITAL_CONTROL_CONSOLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrbitalUplinkBeaconBlockEntity>> ORBITAL_UPLINK_BEACON_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "orbital_uplink_beacon",
            () -> BlockEntityType.Builder.of(OrbitalUplinkBeaconBlockEntity::new, DEBlocks.ORBITAL_UPLINK_BEACON.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstronomicalObservatoryBlockEntity>> ASTRONOMICAL_OBSERVATORY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "astronomical_observatory",
            () -> BlockEntityType.Builder.of(AstronomicalObservatoryBlockEntity::new, DEBlocks.ASTRONOMICAL_OBSERVATORY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterferenceArrayCoreBlockEntity>> INTERFERENCE_ARRAY_CORE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "interference_array_core",
            () -> BlockEntityType.Builder.of(InterferenceArrayCoreBlockEntity::new, DEBlocks.INTERFERENCE_ARRAY_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstronomicalMirrorBlockEntity>> ASTRONOMICAL_MIRROR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "astronomical_mirror",
            () -> BlockEntityType.Builder.of(AstronomicalMirrorBlockEntity::new, DEBlocks.ASTRONOMICAL_MIRROR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSanctumBlockEntity>> DATA_SANCTUM_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_sanctum",
            () -> BlockEntityType.Builder.of(DataSanctumBlockEntity::new, DEBlocks.DATA_SANCTUM.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSanctumInterfaceBlockEntity>> DATA_SANCTUM_INTERFACE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_sanctum_interface",
            () -> BlockEntityType.Builder.of(DataSanctumInterfaceBlockEntity::new, DEBlocks.DATA_SANCTUM_INTERFACE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSanctumReturnPortalBlockEntity>> DATA_SANCTUM_RETURN_PORTAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_sanctum_return_portal",
            () -> BlockEntityType.Builder.of(DataSanctumReturnPortalBlockEntity::new, DEBlocks.DATA_SANCTUM_RETURN_PORTAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataChargerBlockEntity>> DATA_CHARGER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_charger",
            () -> BlockEntityType.Builder.of(
                    DataChargerBlockEntity::new,
                    DEBlocks.DATA_CHARGER.get(),
                    DEBlocks.EXTENDED_DATA_CHARGER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataIntegratedChargerBlockEntity>> DATA_INTEGRATED_CHARGER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_integrated_charger",
            () -> BlockEntityType.Builder.of(
                    DataIntegratedChargerBlockEntity::new,
                    DEBlocks.DATA_INTEGRATED_CHARGER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdaptivePatternProviderBlockEntity>> ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "adaptive_pattern_provider",
            () -> BlockEntityType.Builder.of(AdaptivePatternProviderBlockEntity::new, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompositeWarehouseBlockEntity>> COMPOSITE_WAREHOUSE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "composite_warehouse",
            () -> BlockEntityType.Builder.of(
                    CompositeWarehouseBlockEntity::new,
                    DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get(),
                    DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeCompositeInputWarehouseBlockEntity>> ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "me_composite_input_warehouse",
            () -> BlockEntityType.Builder.of(
                    MeCompositeInputWarehouseBlockEntity::new,
                    DEBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeCompositeOutputWarehouseBlockEntity>> ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "me_composite_output_warehouse",
            () -> BlockEntityType.Builder.of(
                    MeCompositeOutputWarehouseBlockEntity::new,
                    DEBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MePatternBufferBlockEntity>> ME_PATTERN_BUFFER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "me_pattern_buffer",
            () -> BlockEntityType.Builder.of(
                    MePatternBufferBlockEntity::new,
                    DEBlocks.ME_PATTERN_BUFFER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrinityInformationExchangeDepotBlockEntity>> TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "trinity_information_exchange_depot",
            () -> BlockEntityType.Builder.of(
                    TrinityInformationExchangeDepotBlockEntity::new,
                    DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrinityPatternCoreBlockEntity>> TRINITY_PATTERN_CORE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "trinity_pattern_core",
            () -> BlockEntityType.Builder.of(
                    TrinityPatternCoreBlockEntity::new,
                    DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                    DEBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                    DEBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DollBlockEntity>> FISH_DAN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "fish_dan_",
            () -> BlockEntityType.Builder.of(DollBlockEntity::new, DEBlocks.FISH_DAN.get()).build(null));

    private DEBlockEntities() {}

    public static boolean isCompartmentBlockEntityType(BlockEntityType<?> type) {
        return type == COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get() ||
                type == ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY.get() ||
                type == ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get() ||
                type == ME_PATTERN_BUFFER_BLOCK_ENTITY.get() ||
                type == TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY.get();
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
