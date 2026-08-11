package com.fish_dan_.data_energistics.mixin.client;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the native text field so the crafting amount screen can accept all 19 digits of {@link Long#MAX_VALUE}.
 */
@Mixin(NumberEntryWidget.class)
public interface NumberEntryWidgetAccessor {

    /**
     * @return AE2's owned numeric input field
     */
    @Accessor("textField")
    ConfirmableTextField dataEnergistics$textField();
}
