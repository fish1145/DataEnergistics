package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;

import java.lang.invoke.VarHandle;
import java.util.Map;

public final class DataRipperSettings {

    public static final Setting<YesNo> ACCELERATE = new Setting<>("accelerate", YesNo.class);
    public static final Setting<YesNo> REDSTONE_CONTROL = new Setting<>("redstone_control", YesNo.class);
    private static final VarHandle AE2_SETTINGS_FIELD =
            ReflectionAccess.findStaticField(Settings.class, "SETTINGS").orElse(null);

    static {
        registerAe2PacketSetting(ACCELERATE);
        registerAe2PacketSetting(REDSTONE_CONTROL);
    }

    private DataRipperSettings() {}

    @SuppressWarnings("unchecked")
    private static void registerAe2PacketSetting(Setting<?> setting) {
        Object value = readAe2Settings();
        if (value instanceof Map<?, ?> settingsMap) {
            Map<String, Setting<?>> settings = (Map<String, Setting<?>>) settingsMap;
            settings.putIfAbsent(setting.getName(), setting);
            return;
        }
        throw new IllegalStateException("Failed to register AE2 packet setting " + setting.getName());
    }

    private static Object readAe2Settings() {
        if (AE2_SETTINGS_FIELD == null) {
            return null;
        }

        try {
            return AE2_SETTINGS_FIELD.get();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
