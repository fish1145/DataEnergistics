package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmMaterialPresentation;
import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import net.minecraft.network.chat.Component;

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
        if (!state.data_energistics$isPlanReady() || summary == null ||
                !(this.screen instanceof TrinityCraftConfirmPresentationState presentation)) {
            return original;
        }
        return TrinityCraftConfirmMaterialPresentation.tooltip(
                entry,
                summary,
                presentation.data_energistics$selectedCycleOrdinal(entry.getWhat()));
    }

    @ModifyReturnValue(
                       method = "getEntryDescription(Lappeng/menu/me/crafting/CraftingPlanSummaryEntry;)Ljava/util/List;",
                       at = @At("RETURN"))
    private List<Component> dataEnergistics$appendUnresolvedDescription(List<Component> original,
                                                                        CraftingPlanSummaryEntry entry) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.screen.getMenu();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (!state.data_energistics$isPlanReady() || summary == null) {
            return original;
        }
        return TrinityCraftConfirmMaterialPresentation.description(entry, summary);
    }

    @ModifyReturnValue(
                       method = "getEntryOverlayColor(Lappeng/menu/me/crafting/CraftingPlanSummaryEntry;)I",
                       at = @At("RETURN"))
    private int dataEnergistics$colorUnresolvedDemand(int original, CraftingPlanSummaryEntry entry) {
        if (original != 0) {
            return original;
        }
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.screen.getMenu();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        return state.data_energistics$isPlanReady() && summary != null ?
                TrinityCraftConfirmMaterialPresentation.overlayColor(entry, summary) : original;
    }
}
