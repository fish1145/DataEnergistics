package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.accessor.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.client.screen.AdaptivePatternProviderScreen;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdaptivePatternProviderScreen.class)
public abstract class AdaptivePatternProviderScreenMixin extends AEBaseScreen<AdaptivePatternProviderMenu> {

    @Unique
    private PatternProviderRedstoneTuningButton dataEnergistics$redstoneTuningButton;

    protected AdaptivePatternProviderScreenMixin(AdaptivePatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addRedstoneTuningButton(AdaptivePatternProviderMenu menu, Inventory playerInventory,
                                                         Component title, ScreenStyle style, CallbackInfo ci) {
        this.dataEnergistics$redstoneTuningButton = new PatternProviderRedstoneTuningButton((PatternProviderMenuAccessor) menu);
        this.addToLeftToolbar(this.dataEnergistics$redstoneTuningButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncRedstoneTuningButton(CallbackInfo ci) {
        if (this.dataEnergistics$redstoneTuningButton != null) {
            this.dataEnergistics$redstoneTuningButton.syncFromMenu();
        }
    }
}
