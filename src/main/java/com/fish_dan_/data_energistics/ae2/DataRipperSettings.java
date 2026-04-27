package com.fish_dan_.data_energistics.ae2;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;

public final class DataRipperSettings {
    public static final Setting<YesNo> ACCELERATE = new Setting<>("accelerate", YesNo.class);
    public static final Setting<YesNo> REDSTONE_CONTROL = new Setting<>("redstone_control", YesNo.class);

    private DataRipperSettings() {
    }
}
