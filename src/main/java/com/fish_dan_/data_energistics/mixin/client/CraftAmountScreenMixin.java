package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftAmountMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds one explicit NET_NEW / FINAL_TOTAL selector below AE2's amount dialog.
 */
@Mixin(CraftAmountScreen.class)
public abstract class CraftAmountScreenMixin extends AEBaseScreen<CraftAmountMenu> {

    @Unique
    private Button dataEnergistics$quantityModeButton;

    protected CraftAmountScreenMixin(
                                     CraftAmountMenu menu,
                                     Inventory playerInventory,
                                     Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void dataEnergistics$ensureQuantityModeButton(CallbackInfo ci) {
        if (this.dataEnergistics$quantityModeButton != null &&
                this.children().contains(this.dataEnergistics$quantityModeButton)) {
            return;
        }
        TrinityCraftAmountMenuState state = (TrinityCraftAmountMenuState) this.menu;
        this.dataEnergistics$quantityModeButton = Button.builder(
                dataEnergistics$quantityModeLabel(state.data_energistics$quantityMode()),
                button -> {
                    CraftingQuantityMode next = state.data_energistics$quantityMode().next();
                    state.data_energistics$setQuantityMode(next);
                    button.setMessage(dataEnergistics$quantityModeLabel(next));
                })
                .bounds(this.leftPos + 20, this.topPos + this.imageHeight + 4, 138, 20)
                .build();
        this.addRenderableWidget(this.dataEnergistics$quantityModeButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncQuantityModeButton(CallbackInfo ci) {
        TrinityCraftAmountMenuState state = (TrinityCraftAmountMenuState) this.menu;
        this.dataEnergistics$quantityModeButton.setMessage(
                dataEnergistics$quantityModeLabel(state.data_energistics$quantityMode()));
    }

    @Unique
    private static Component dataEnergistics$quantityModeLabel(CraftingQuantityMode mode) {
        String modeKey = switch (mode) {
            case NET_NEW -> "gui.data_energistics.trinity_quantity.net_new";
            case FINAL_TOTAL -> "gui.data_energistics.trinity_quantity.final_total";
        };
        return Component.translatable(
                "gui.data_energistics.trinity_quantity.mode",
                Component.translatable(modeKey));
    }
}
