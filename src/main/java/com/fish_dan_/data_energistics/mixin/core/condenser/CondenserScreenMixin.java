package com.fish_dan_.data_energistics.mixin.core.condenser;

import com.fish_dan_.data_energistics.accessor.condenser.CondenserMenuAccessor;
import com.fish_dan_.data_energistics.ae2.settings.CondenserOutputMode;
import com.fish_dan_.data_energistics.client.widget.CondenserOutputModeButton;
import com.fish_dan_.data_energistics.mixin.core.accessor.ae2.WidgetContainerAccessor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.CondenserScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.CondenserMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CondenserScreen.class)
public abstract class CondenserScreenMixin extends AEBaseScreen<CondenserMenu> {

    @Unique
    private CondenserOutputModeButton dataEnergistics$condenserModeButton;

    protected CondenserScreenMixin(CondenserMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(
            method = "<init>(Lappeng/menu/implementations/CondenserMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;Lappeng/client/gui/style/ScreenStyle;)V",
            at = @At("RETURN"))
    private void dataEnergistics$replaceModeButton(CondenserMenu menu, Inventory playerInventory, Component title,
                                                   ScreenStyle style, CallbackInfo ci) {
        this.dataEnergistics$condenserModeButton = new CondenserOutputModeButton((CondenserMenuAccessor) menu);
        ((WidgetContainerAccessor) this.widgets)
                .dataEnergistics$getWidgets()
                .put("mode", this.dataEnergistics$condenserModeButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncModeButton(CallbackInfo ci) {
        if (this.dataEnergistics$condenserModeButton == null) {
            return;
        }

        var mode = CondenserOutputMode.fromOrdinal(((CondenserMenuAccessor) this.menu).dataEnergistics$getCondenserOutputMode());
        this.dataEnergistics$condenserModeButton.setMode(mode);
    }
}
