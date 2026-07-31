package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders synchronized Trinity ownership, dynamic-material, and fallback diagnostics in the confirmation dialog.
 */
@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {

    protected CraftConfirmScreenMixin(
                                      CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "drawFG", at = @At("RETURN"))
    private void dataEnergistics$drawPlanningMetadata(
                                                      GuiGraphics guiGraphics,
                                                      int offsetX,
                                                      int offsetY,
                                                      int mouseX,
                                                      int mouseY,
                                                      CallbackInfo ci) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        if (state.data_energistics$isTrinityOnly()) {
            guiGraphics.drawString(
                    this.font,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.only",
                            Component.translatable(state.data_energistics$quantityMode() ==
                                    CraftingQuantityMode.NET_NEW ?
                                            "gui.data_energistics.trinity_quantity.net_new" :
                                            "gui.data_energistics.trinity_quantity.final_total")),
                    8,
                    139,
                    0x55FFFF,
                    false);
        }

        if (state.data_energistics$hasDiagnostic()) {
            guiGraphics.drawString(
                    this.font,
                    state.data_energistics$diagnostic(),
                    8,
                    150,
                    0xFF7777,
                    false);
        } else if (state.data_energistics$hasDynamicMaterialWarning()) {
            guiGraphics.drawString(
                    this.font,
                    Component.translatable("gui.data_energistics.trinity_planning.dynamic_warning"),
                    8,
                    150,
                    0xFFCC55,
                    false);
        }
    }
}
