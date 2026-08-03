package com.fish_dan_.data_energistics.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

public class ConfigHolder {

    public ConfigHolder() {
        // TODO: dev-mode config overrides (e.g. if (!FMLLoader.isProduction()) { ... })
    }

    public static void init(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, FlatteningTntConfig.SPEC, "data_energistics-tnt.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, DataExtractorConfig.SPEC, "data_energistics-data_extractor.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, SolarPanelConfig.SPEC, "data_energistics-solar_panel.toml");
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                TrinityCraftingConfig.SPEC,
                "data_energistics-trinity_crafting.toml");
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                TrinityDispatchConfig.SPEC,
                "data_energistics-trinity_dispatch.toml");
    }
}
