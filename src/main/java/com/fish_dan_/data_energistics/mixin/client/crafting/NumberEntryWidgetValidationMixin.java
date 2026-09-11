package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.LongAmountExpressionParser;
import com.fish_dan_.data_energistics.client.crafting.NumberEntryWidgetValidationRegistry;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Replaces the amount widget's integer-only visual validation with the parser used by the crafting screen.
 */
@Mixin(NumberEntryWidget.class)
public abstract class NumberEntryWidgetValidationMixin {

    @Shadow
    @Final
    private ConfirmableTextField textField;

    @Shadow
    @Final
    private int normalTextColor;

    @Shadow
    @Final
    private int errorTextColor;

    @Inject(method = "validate", at = @At("RETURN"))
    private void dataEnergistics$validateExpression(CallbackInfo ci) {
        if (!NumberEntryWidgetValidationRegistry.isEnabled((NumberEntryWidget) (Object) this)) {
            return;
        }
        dataEnergistics$applyExpressionValidation();
    }

    @Unique
    private void dataEnergistics$applyExpressionValidation() {
        var parsed = LongAmountExpressionParser.parse(this.textField.getValue());
        if (parsed.isPresent()) {
            this.textField.setTextColor(this.normalTextColor);
            this.textField.setTooltipMessage(List.of());
        } else {
            this.textField.setTextColor(this.errorTextColor);
            this.textField.setTooltipMessage(List.of(
                    Component.translatable("gui.data_energistics.crafting.amount.invalid_expression")));
        }
    }

}
