package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmCycleTooltip;
import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.math.BigInteger;
import java.util.ArrayList;
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
        return TrinityCraftConfirmCycleTooltip.append(
                dataEnergistics$formattedTooltip(entry, summary),
                entry.getWhat(),
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
        List<Component> formatted = dataEnergistics$formattedDescription(entry, summary);
        return summary.unresolvedDemand(entry.getWhat())
                .<List<Component>>map(unresolved -> {
                    var lines = new ArrayList<>(formatted);
                    lines.add(Component.translatable(
                            "gui.data_energistics.trinity_planning.cycle.unresolved_demand",
                            TrinityAmountFormatter.format(unresolved.amount())).withStyle(ChatFormatting.YELLOW));
                    return lines;
                })
                .orElse(formatted);
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
        return state.data_energistics$isPlanReady() && summary != null &&
                summary.unresolvedDemand(entry.getWhat()).isPresent() ? 0x1AFFFF00 : original;
    }

    private static List<Component> dataEnergistics$formattedDescription(
                                                                        CraftingPlanSummaryEntry entry,
                                                                        TrinityCraftingCycleSummary summary) {
        DisplayedAmounts amounts = dataEnergistics$displayedAmounts(entry, summary);
        ArrayList<Component> lines = new ArrayList<>(3);
        if (amounts.stored().signum() > 0) {
            lines.add(GuiText.FromStorage.text(TrinityAmountFormatter.format(amounts.stored())));
        }
        if (amounts.missing().signum() > 0) {
            lines.add(GuiText.Missing.text(TrinityAmountFormatter.format(amounts.missing())));
        }
        if (amounts.crafting().signum() > 0) {
            lines.add(GuiText.ToCraft.text(TrinityAmountFormatter.format(amounts.crafting())));
        }
        return lines;
    }

    private static List<Component> dataEnergistics$formattedTooltip(
                                                                    CraftingPlanSummaryEntry entry,
                                                                    TrinityCraftingCycleSummary summary) {
        DisplayedAmounts amounts = dataEnergistics$displayedAmounts(entry, summary);
        ArrayList<Component> lines = new ArrayList<>(AEKeyRendering.getTooltip(entry.getWhat()));
        if (amounts.stored().signum() > 0) {
            lines.add(GuiText.FromStorage.text(TrinityAmountFormatter.format(amounts.stored())));
        }
        if (amounts.missing().signum() > 0) {
            lines.add(GuiText.Missing.text(TrinityAmountFormatter.format(amounts.missing())));
        }
        if (amounts.crafting().signum() > 0) {
            lines.add(GuiText.ToCraft.text(TrinityAmountFormatter.format(amounts.crafting())));
        }
        return lines;
    }

    private static DisplayedAmounts dataEnergistics$displayedAmounts(
                                                                     CraftingPlanSummaryEntry entry,
                                                                     TrinityCraftingCycleSummary summary) {
        var exactPlanAmounts = summary.exactPlanAmounts(entry.getWhat());
        if (exactPlanAmounts.isPresent()) {
            var exact = exactPlanAmounts.orElseThrow();
            return new DisplayedAmounts(exact.stored(), exact.missing(), exact.crafting());
        }
        var shortage = summary.exactShortage(entry.getWhat());
        if (shortage.isPresent()) {
            var exact = shortage.orElseThrow();
            return new DisplayedAmounts(
                    exact.available(),
                    exact.missing(),
                    BigInteger.valueOf(entry.getCraftAmount()));
        }
        return new DisplayedAmounts(
                BigInteger.valueOf(entry.getStoredAmount()),
                BigInteger.valueOf(entry.getMissingAmount()),
                BigInteger.valueOf(entry.getCraftAmount()));
    }

    private record DisplayedAmounts(BigInteger stored, BigInteger missing, BigInteger crafting) {}
}
