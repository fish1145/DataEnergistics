package com.fish_dan_.data_energistics.configuration.client;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.minecraft.network.chat.Component;

import dev.toma.configuration.client.WidgetAdder;
import dev.toma.configuration.client.screen.WidgetPlacerHelper;
import dev.toma.configuration.client.theme.ConfigTheme;
import dev.toma.configuration.client.theme.adapter.AbstractDisplayAdapter;
import dev.toma.configuration.client.widget.EnumWidget;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.EnumValue;

import java.lang.reflect.Field;

/** Renders stable CraftingQuantityMode enum values with localized client labels. */
final class CraftingQuantityModeDisplayAdapter extends AbstractDisplayAdapter {

    @Override
    @SuppressWarnings("unchecked")
    public void placeWidgets(
                             ConfigHolder<?> holder,
                             ConfigValue<?> value,
                             Field field,
                             ConfigTheme theme,
                             WidgetAdder widgets) {
        EnumValue<CraftingQuantityMode> enumValue = (EnumValue<CraftingQuantityMode>) value;
        LocalizedQuantityModeWidget widget = widgets.addConfigWidget((x, y, width, height, name) -> {
            int left = WidgetPlacerHelper.getLeft(x, width);
            int widgetWidth = WidgetPlacerHelper.getWidth(width);
            return new LocalizedQuantityModeWidget(left, y, widgetWidth, height, theme, enumValue);
        });
        widget.setBackgroundRenderer(theme.getButtonBackground(widget));
        createControls(
                widget,
                enumValue,
                theme,
                widgets,
                restoreDefault -> widget.setValue(restoreDefault ?
                        enumValue.getValueData().getDefaultValue() :
                        enumValue.getActiveValue()));
    }

    private static final class LocalizedQuantityModeWidget extends EnumWidget<CraftingQuantityMode> {

        private final EnumValue<CraftingQuantityMode> value;

        private LocalizedQuantityModeWidget(
                                            int x,
                                            int y,
                                            int width,
                                            int height,
                                            ConfigTheme theme,
                                            EnumValue<CraftingQuantityMode> value) {
            super(x, y, width, height, theme, value);
            this.value = value;
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            super.onClick(mouseX, mouseY);
            updateMessage();
        }

        @Override
        public void setValue(CraftingQuantityMode mode) {
            super.setValue(mode);
            updateMessage();
        }

        private void updateMessage() {
            String key = switch (this.value.get()) {
                case NET_NEW -> "gui.data_energistics.trinity_quantity.net_new";
                case FINAL_TOTAL -> "gui.data_energistics.trinity_quantity.final_total";
            };
            setMessage(Component.translatable(key));
        }
    }
}
