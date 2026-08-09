package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

/** Registry aliases that let worlds saved by earlier 3.0 development builds migrate to current IDs. */
public final class DELegacyRegistryAliases {

    private static final ResourceLocation LEGACY_TRINITY_ACCESS_HATCH = Data_Energistics.id("trinity_access_hatch");
    private static final ResourceLocation ME_ACCESS_HATCH = Data_Energistics.id("me_access_hatch");

    private DELegacyRegistryAliases() {}

    /**
     * Registers every persisted form of the renamed Trinity access hatch before registry events are fired.
     */
    public static void register() {
        DEBlocks.BLOCKS.addAlias(LEGACY_TRINITY_ACCESS_HATCH, ME_ACCESS_HATCH);
        DEItems.ITEMS.addAlias(LEGACY_TRINITY_ACCESS_HATCH, ME_ACCESS_HATCH);
        DEBlockEntities.BLOCK_ENTITY_TYPES.addAlias(LEGACY_TRINITY_ACCESS_HATCH, ME_ACCESS_HATCH);
        Data_Energistics.LOGGER.warn(
                "Enabled legacy Trinity access hatch registry aliases {} -> {} for block, item, and block entity " +
                        "type. Keep a world backup before its first 3.0.x load; a successful save will persist the " +
                        "current ID.",
                LEGACY_TRINITY_ACCESS_HATCH,
                ME_ACCESS_HATCH);
    }
}
