package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.neoforge.registries.DeferredRegister;

/** Maps retired G-tier core IDs to the current M-tier cores before block and item registration. */
final class TrinityCoreRegistryAliases {

    private TrinityCoreRegistryAliases() {}

    /**
     * Applies the selected tier migration to a core's block or item registry on the mod-loading thread.
     * Existing M-tier IDs remain unchanged so current stacks are never remapped on a subsequent load.
     */
    static void register(DeferredRegister<?> registry) {
        String[] prefixes = { "me_digital_storage_core_", "me_digital_merged_storage_core_" };
        String[] tiers = { "1", "4", "16", "64", "256" };
        for (String prefix : prefixes) {
            for (String tier : tiers) {
                registry.addAlias(
                        Data_Energistics.id(prefix + tier + "g"),
                        Data_Energistics.id(prefix + tier + "m"));
            }
        }
    }
}
