package com.fish_dan_.data_energistics.client.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import com.fish_dan_.data_energistics.blockentity.DataExtractorDropRoutingMode;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class DataExtractorDropRoutingButton extends IconButton {
    private final String titleKey;
    private final String modeKeyPrefix;
    private final Consumer<DataExtractorDropRoutingMode> onChange;
    private DataExtractorDropRoutingMode mode = DataExtractorDropRoutingMode.OFF;

    public DataExtractorDropRoutingButton(Consumer<DataExtractorDropRoutingMode> onChange) {
        this(
                "button.data_energistics.data_extractor.drop_routing",
                "button.data_energistics.data_extractor.drop_routing.",
                onChange
        );
    }

    public DataExtractorDropRoutingButton(String titleKey, String modeKeyPrefix, Consumer<DataExtractorDropRoutingMode> onChange) {
        super(btn -> {
            if (btn instanceof DataExtractorDropRoutingButton button) {
                button.onChange.accept(button.mode.next());
            }
        });
        this.titleKey = titleKey;
        this.modeKeyPrefix = modeKeyPrefix;
        this.onChange = onChange;
    }

    public void setMode(DataExtractorDropRoutingMode mode) {
        this.mode = mode == null ? DataExtractorDropRoutingMode.OFF : mode;
    }

    @Override
    protected Icon getIcon() {
        return switch (this.mode) {
            case OFF -> Icon.INVALID;
            case CONTAINER -> Icon.ACCESS_WRITE;
            case AE -> Icon.ACCESS_READ;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable(this.titleKey),
                Component.translatable(this.modeKeyPrefix + this.mode.getSerializedName())
        );
    }
}
