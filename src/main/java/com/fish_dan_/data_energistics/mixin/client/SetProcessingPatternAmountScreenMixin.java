package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.screen.patternencoding.ProcessingPatternAmountContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternOutputMatchMenu;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.me.items.SetProcessingPatternAmountScreen;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/** Adds the encoded pattern's output-matching switch to the correct middle-click amount window. */
@Mixin(SetProcessingPatternAmountScreen.class)
public abstract class SetProcessingPatternAmountScreenMixin
                                                            extends AESubScreen<PatternEncodingTermMenu, PatternEncodingTermScreen<PatternEncodingTermMenu>> {

    @Unique
    private ToggleButton dataEnergistics$outputMatchButton;

    protected SetProcessingPatternAmountScreenMixin(
                                                    PatternEncodingTermScreen<PatternEncodingTermMenu> parent) {
        super(parent, "/screens/set_processing_pattern_amount.json");
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addOutputMatchButton(
                                                      PatternEncodingTermScreen<PatternEncodingTermMenu> parentScreen,
                                                      GenericStack currentStack,
                                                      Consumer<GenericStack> setter,
                                                      CallbackInfo ci) {
        ProcessingPatternAmountContext context = (ProcessingPatternAmountContext) parentScreen;
        if (!context.data_energistics$isProcessingOutputAmountTarget() ||
                !(currentStack.what() instanceof AEItemKey)) {
            return;
        }

        PatternOutputMatchMenu state = (PatternOutputMatchMenu) this.getMenu();
        this.dataEnergistics$outputMatchButton = new ToggleButton(
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                enabled -> {
                    state.data_energistics$setProcessingOutputSameItem(enabled);
                    this.dataEnergistics$outputMatchButton.setState(
                            state.data_energistics$isProcessingOutputSameItem());
                });
        this.dataEnergistics$outputMatchButton.setTooltipOn(List.of(
                Component.translatable("gui.data_energistics.processing_output_match.same_item")));
        this.dataEnergistics$outputMatchButton.setTooltipOff(List.of(
                Component.translatable("gui.data_energistics.processing_output_match.exact")));
        this.dataEnergistics$outputMatchButton.setState(state.data_energistics$isProcessingOutputSameItem());
        this.addToLeftToolbar(this.dataEnergistics$outputMatchButton);
    }
}
