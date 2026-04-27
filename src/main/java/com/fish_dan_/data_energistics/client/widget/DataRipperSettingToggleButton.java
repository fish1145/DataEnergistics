package com.fish_dan_.data_energistics.client.widget;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DataRipperSettingToggleButton extends ServerSettingToggleButton<YesNo> {
    private final Icon enabledIcon;
    private final Icon disabledIcon;
    private final String titleKey;
    private final String enabledKey;
    private final String disabledKey;
    private final String blockedKey;

    public DataRipperSettingToggleButton(
            Setting<YesNo> setting,
            YesNo value,
            Icon enabledIcon,
            Icon disabledIcon,
            String titleKey,
            String enabledKey,
            String disabledKey,
            String blockedKey
    ) {
        super(setting, value);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
        this.titleKey = titleKey;
        this.enabledKey = enabledKey;
        this.disabledKey = disabledKey;
        this.blockedKey = blockedKey;
    }

    @Override
    protected Icon getIcon() {
        return switch (this.getCurrentValue()) {
            case YES -> this.enabledIcon;
            case NO -> this.disabledIcon;
            default -> Icon.INVALID;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        String stateKey = switch (this.getCurrentValue()) {
            case YES -> this.enabledKey;
            case NO -> this.disabledKey;
            default -> this.blockedKey;
        };
        return List.of(Component.translatable(this.titleKey), Component.translatable(stateKey));
    }
}
