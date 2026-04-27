package com.fish_dan_.data_energistics.ae2;

import appeng.api.config.Settings;
import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import java.lang.reflect.Field;
import java.util.Map;

public final class DataRipperSettings {
    public static final Setting<YesNo> ACCELERATE = new Setting<>("accelerate", YesNo.class);
    public static final Setting<YesNo> REDSTONE_CONTROL = new Setting<>("redstone_control", YesNo.class);

    static {
        registerAe2PacketSetting(ACCELERATE);
        registerAe2PacketSetting(REDSTONE_CONTROL);
    }

    private DataRipperSettings() {
    }

    @SuppressWarnings("unchecked")
    private static void registerAe2PacketSetting(Setting<?> setting) {
        try {
            Field settingsField = Settings.class.getDeclaredField("SETTINGS");
            settingsField.setAccessible(true);

            Map<String, Setting<?>> settings = (Map<String, Setting<?>>) settingsField.get(null);
            settings.putIfAbsent(setting.getName(), setting);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register AE2 packet setting " + setting.getName(), e);
        }
    }
}
