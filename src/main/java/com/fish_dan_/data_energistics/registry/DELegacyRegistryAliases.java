package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

/** Registry aliases that let worlds saved by earlier 3.0 development builds migrate to current IDs. */
public final class DELegacyRegistryAliases {

    private static final String[] DIGITAL_STORAGE_CELL_TIERS = {
            "1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m"
    };
    private static final ResourceLocation LEGACY_TRINITY_ACCESS_HATCH = Data_Energistics.id("trinity_access_hatch");
    private static final ResourceLocation LEGACY_ME_ACCESS_HATCH = Data_Energistics.id("me_access_hatch");
    private static final ResourceLocation TRINITY_INFORMATION_EXCHANGE_DEPOT = Data_Energistics.id("trinity_information_exchange_depot");
    private static final ResourceLocation LEGACY_DATA_DISORDER = Data_Energistics.id("data_disorder");

    private DELegacyRegistryAliases() {}

    /** Registers aliases for persisted IDs renamed during 3.0 development before registry events are fired. */
    public static void register() {
        DEBlocks.BLOCKS.addAlias(LEGACY_TRINITY_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEBlocks.BLOCKS.addAlias(LEGACY_ME_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEItems.ITEMS.addAlias(LEGACY_TRINITY_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEItems.ITEMS.addAlias(LEGACY_ME_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEBlockEntities.BLOCK_ENTITY_TYPES.addAlias(LEGACY_TRINITY_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEBlockEntities.BLOCK_ENTITY_TYPES.addAlias(LEGACY_ME_ACCESS_HATCH, TRINITY_INFORMATION_EXCHANGE_DEPOT);
        DEMobEffects.MOB_EFFECTS.addAlias(LEGACY_DATA_DISORDER, DEMobEffects.RADIX_LOSS.getId());
        DEParticles.PARTICLE_TYPES.addAlias(LEGACY_DATA_DISORDER, DEParticles.RADIX_LOSS.getId());
        registerDigitalStorageCellAliases();
        Data_Energistics.LOGGER.warn(
                "Enabled legacy Trinity information exchange depot registry aliases {} -> {} and {} -> {} for " +
                        "block, item, and block entity type. Keep a world backup before its first 3.0.x load; a " +
                        "successful save will persist the current ID.",
                LEGACY_TRINITY_ACCESS_HATCH,
                TRINITY_INFORMATION_EXCHANGE_DEPOT,
                LEGACY_ME_ACCESS_HATCH,
                TRINITY_INFORMATION_EXCHANGE_DEPOT);
        Data_Energistics.LOGGER.warn(
                "Enabled legacy Radix Loss registry aliases {} -> {} for mob effect and particle type. Keep a " +
                        "world backup before its first 3.0.x load; a successful save will persist the current ID.",
                LEGACY_DATA_DISORDER,
                DEMobEffects.RADIX_LOSS.getId());
        Data_Energistics.LOGGER.warn(
                "Enabled legacy Data Flow cell item registry aliases for {} regular and {} portable tiers. " +
                        "Keep a world backup before its first load; a successful save will persist the current " +
                        "Digital Storage Cell IDs.",
                DIGITAL_STORAGE_CELL_TIERS.length,
                DIGITAL_STORAGE_CELL_TIERS.length);
    }

    private static void registerDigitalStorageCellAliases() {
        for (String tier : DIGITAL_STORAGE_CELL_TIERS) {
            DEItems.ITEMS.addAlias(
                    Data_Energistics.id("data_flow_cell_" + tier),
                    Data_Energistics.id("digital_storage_cell_" + tier));
            DEItems.ITEMS.addAlias(
                    Data_Energistics.id("portable_data_flow_cell_" + tier),
                    Data_Energistics.id("portable_digital_storage_cell_" + tier));
        }
    }
}
