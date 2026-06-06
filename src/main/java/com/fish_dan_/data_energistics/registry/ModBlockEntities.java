package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataFrameworkBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Data_Energistics.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSolarPanelBlockEntity>> DATA_SOLAR_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "me_solar_panel",
            () -> BlockEntityType.Builder.of(DataSolarPanelBlockEntity::new, ModBlocks.DATA_SOLAR_PANEL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DigitalStorageDepotBlockEntity>> DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "digital_storage_depot",
            () -> BlockEntityType.Builder.of(DigitalStorageDepotBlockEntity::new, ModBlocks.DIGITAL_STORAGE_DEPOT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataExtractorBlockEntity>> DATA_EXTRACTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_extractor",
            () -> BlockEntityType.Builder.of(DataExtractorBlockEntity::new, ModBlocks.DATA_EXTRACTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataRipperReassemblerBlockEntity>> DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_reassembler",
            () -> BlockEntityType.Builder.of(DataRipperReassemblerBlockEntity::new, ModBlocks.DATA_RIPPER_REASSEMBLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataFrameworkBlockEntity>> DATA_FRAMEWORK_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_framework",
            () -> BlockEntityType.Builder.of(DataFrameworkBlockEntity::new, ModBlocks.DATA_FRAMEWORK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataDistributionTowerBlockEntity>> DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_distribution_tower",
            () -> BlockEntityType.Builder.of(DataDistributionTowerBlockEntity::new, ModBlocks.DATA_DISTRIBUTION_TOWER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataMimeticFieldBlockEntity>> DATA_MIMETIC_FIELD_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_mimetic_field",
            () -> BlockEntityType.Builder.of(DataMimeticFieldBlockEntity::new, ModBlocks.DATA_MIMETIC_FIELD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataTeleportAnchorBlockEntity>> DATA_TELEPORT_ANCHOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_teleport_anchor",
            () -> BlockEntityType.Builder.of(DataTeleportAnchorBlockEntity::new, ModBlocks.DATA_TELEPORT_ANCHOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSanctumBlockEntity>> DATA_SANCTUM_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_sanctum",
            () -> BlockEntityType.Builder.of(DataSanctumBlockEntity::new, ModBlocks.DATA_SANCTUM.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataSanctumInterfaceBlockEntity>> DATA_SANCTUM_INTERFACE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "data_sanctum_interface",
            () -> BlockEntityType.Builder.of(DataSanctumInterfaceBlockEntity::new, ModBlocks.DATA_SANCTUM_INTERFACE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdaptivePatternProviderBlockEntity>> ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "adaptive_pattern_provider",
            () -> BlockEntityType.Builder.of(AdaptivePatternProviderBlockEntity::new, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
