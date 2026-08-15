package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmCycleTooltip;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.network.chat.Component;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Extends AE2's native material tooltip only when the active menu owns a complete current Trinity summary.
 */
@Mixin(CraftConfirmTableRenderer.class)
public abstract class CraftConfirmTableRendererMixin extends AbstractTableRenderer<CraftingPlanSummaryEntry> {

    protected CraftConfirmTableRendererMixin(AEBaseScreen<?> screen, int x, int y) {
        super(screen, x, y, 5);
    }

    @ModifyReturnValue(
                       method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingPlanSummaryEntry;)Ljava/util/List;",
                       at = @At("RETURN"))
    private List<Component> dataEnergistics$appendCycleDetails(List<Component> original,
                                                               CraftingPlanSummaryEntry entry) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.screen.getMenu();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (!state.data_energistics$isPlanReady() || summary == null) {
            return original;
        }
        return TrinityCraftConfirmCycleTooltip.append(original, entry.getWhat(), summary);
    }
}
