package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import appeng.api.client.AEKeyRendering;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/** Shared exact-amount and diagnostic presentation for native and NBT crafting confirmation tables. */
public final class TrinityCraftConfirmMaterialPresentation {

    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int UNRESOLVED_OVERLAY = 0x1AFFFF00;

    private TrinityCraftConfirmMaterialPresentation() {}

    public static List<Component> description(CraftingPlanSummaryEntry entry,
                                              @Nullable TrinityCraftingCycleSummary summary) {
        DisplayedAmounts amounts = displayedAmounts(entry, summary);
        ObjectArrayList<Component> lines = amountLines(amounts);
        if (summary != null) {
            summary.unresolvedDemand(entry.getWhat()).ifPresent(unresolved -> lines.add(Component.translatable(
                    "gui.data_energistics.trinity_planning.cycle.unresolved_demand",
                    TrinityAmountFormatter.format(unresolved.amount())).withStyle(ChatFormatting.YELLOW)));
        }
        return lines;
    }

    public static List<Component> tooltip(CraftingPlanSummaryEntry entry,
                                          @Nullable TrinityCraftingCycleSummary summary,
                                          int selectedCycleOrdinal) {
        ObjectArrayList<Component> lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(entry.getWhat()));
        lines.addAll(amountLines(displayedAmounts(entry, summary)));
        return summary == null ? lines : TrinityCraftConfirmCycleTooltip.append(
                lines,
                entry.getWhat(),
                summary,
                selectedCycleOrdinal);
    }

    public static int overlayColor(CraftingPlanSummaryEntry entry,
                                   @Nullable TrinityCraftingCycleSummary summary) {
        if (displayedAmounts(entry, summary).missing().signum() > 0) {
            return MISSING_OVERLAY;
        }
        return summary != null && summary.unresolvedDemand(entry.getWhat()).isPresent() ?
                UNRESOLVED_OVERLAY : 0;
    }

    private static ObjectArrayList<Component> amountLines(DisplayedAmounts amounts) {
        ObjectArrayList<Component> lines = new ObjectArrayList<>(3);
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

    private static DisplayedAmounts displayedAmounts(CraftingPlanSummaryEntry entry,
                                                     @Nullable TrinityCraftingCycleSummary summary) {
        if (summary != null) {
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
        }
        return new DisplayedAmounts(
                BigInteger.valueOf(entry.getStoredAmount()),
                BigInteger.valueOf(entry.getMissingAmount()),
                BigInteger.valueOf(entry.getCraftAmount()));
    }

    private record DisplayedAmounts(BigInteger stored, BigInteger missing, BigInteger crafting) {}
}
