package com.fish_dan_.data_energistics.mixin.advancedae;

import com.fish_dan_.data_energistics.accessor.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.client.screen.SingleUpgradeSlotRelocator;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.SmallAdvPatternProviderMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmallAdvPatternProviderScreen.class)
public abstract class AdvancedAeSmallPatternProviderScreenMixin extends AEBaseScreen<SmallAdvPatternProviderMenu> {

    @Unique
    private PatternProviderRedstoneTuningButton dataEnergistics$redstoneTuningButton;

    protected AdvancedAeSmallPatternProviderScreenMixin(SmallAdvPatternProviderMenu menu, Inventory playerInventory,
                                                        Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addRedstoneTuningButton(SmallAdvPatternProviderMenu menu, Inventory playerInventory,
                                                         Component title, ScreenStyle style, CallbackInfo ci) {
        this.dataEnergistics$redstoneTuningButton = new PatternProviderRedstoneTuningButton((PatternProviderMenuAccessor) menu);
        this.addToLeftToolbar(this.dataEnergistics$redstoneTuningButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncRedstoneTuningButton(CallbackInfo ci) {
        SingleUpgradeSlotRelocator.relocateIfSingle(this, 8, 67);
        if (this.dataEnergistics$redstoneTuningButton != null) {
            this.dataEnergistics$redstoneTuningButton.syncFromMenu();
        }
    }
}
