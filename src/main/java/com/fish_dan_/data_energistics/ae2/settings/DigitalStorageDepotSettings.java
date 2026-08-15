package com.fish_dan_.data_energistics.ae2.settings;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorAutoExportMode;

import appeng.api.config.Setting;
import appeng.api.config.Settings;

public final class DigitalStorageDepotSettings {

    public static final Setting<DataExtractorAutoExportMode> AUTO_EXPORT_MODE = new Setting<>("digital_storage_depot_auto_export_mode", DataExtractorAutoExportMode.class);

    static {
        registerAe2PacketSetting(AUTO_EXPORT_MODE);
    }

    private DigitalStorageDepotSettings() {}

    private static void registerAe2PacketSetting(Setting<?> setting) {
        Settings.SETTINGS.putIfAbsent(setting.getName(), setting);
    }
}
