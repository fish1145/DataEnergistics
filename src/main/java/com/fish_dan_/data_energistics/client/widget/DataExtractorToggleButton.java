package com.fish_dan_.data_energistics.client.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ToggleButton;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class DataExtractorToggleButton extends ToggleButton {
    private final String titleKey;
    private final String enabledKey;
    private final String disabledKey;
    private boolean state;

    public DataExtractorToggleButton(
            Icon enabledIcon,
            Icon disabledIcon,
            String titleKey,
            String enabledKey,
            String disabledKey,
            Consumer<Boolean> onChange
    ) {
        super(enabledIcon, disabledIcon, onChange::accept);
        this.titleKey = titleKey;
        this.enabledKey = enabledKey;
        this.disabledKey = disabledKey;
    }

    @Override
    public void setState(boolean isOn) {
        super.setState(isOn);
        this.state = isOn;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable(this.titleKey),
                Component.translatable(this.state ? this.enabledKey : this.disabledKey)
        );
    }
}
