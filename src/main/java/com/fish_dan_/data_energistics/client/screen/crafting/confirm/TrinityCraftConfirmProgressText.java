package com.fish_dan_.data_energistics.client.screen.crafting.confirm;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.client.util.TrinityDurationFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressMeasure;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import appeng.menu.me.crafting.CraftingPlanSummary;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Formats truthful phase-local planning progress for the compact confirmation status row. */
final class TrinityCraftConfirmProgressText {

    private static final String PREFIX = "gui.data_energistics.craft_confirm.";

    private TrinityCraftConfirmProgressText() {}

    static Component status(TrinityCraftConfirmMenuState state,
                            @Nullable CraftingPlanSummary plan,
                            boolean hasTrinityCpu) {
        if (!hasTrinityCpu) {
            return Component.translatable(PREFIX + "status.trinity_cpu_unavailable");
        }
        if (plan != null) {
            if (!state.data_energistics$isPlanReady()) {
                return Component.translatable(PREFIX + "status.synchronizing");
            }
            TrinityPlanningProgressSnapshot progress = state.data_energistics$planningProgress();
            if (progress != null && progress.phase() == TrinityPlanningProgressPhase.DELEGATED_TO_AE2) {
                return Component.translatable(PREFIX + "status.ae2_delegated");
            }
            if (state.data_energistics$isAe2FallbackEstimate()) {
                return Component.translatable(PREFIX + "status.ae2_fallback");
            }
            if (state.data_energistics$hasDiagnostic()) {
                return Component.translatable(PREFIX + "status.diagnostic");
            }
            if (plan.isSimulation()) {
                return Component.translatable(PREFIX + "status.partial");
            }
            return Component.translatable(PREFIX + "status.ready");
        }

        TrinityPlanningProgressSnapshot progress = state.data_energistics$planningProgress();
        if (progress == null) {
            return Component.translatable(PREFIX + "status.waiting");
        }
        Component phase = Component.translatable(
                PREFIX + "progress.phase." + progress.phase().name().toLowerCase(Locale.ROOT));
        if (progress.measure() == TrinityPlanningProgressMeasure.EXACT) {
            int percent = (int) ((long) progress.completedUnits() * 100L / progress.totalUnits());
            return Component.translatable(
                    PREFIX + "progress.exact",
                    phase,
                    percent,
                    TrinityAmountFormatter.format(progress.completedUnits()),
                    TrinityAmountFormatter.format(progress.totalUnits()));
        }
        if (progress.measure() == TrinityPlanningProgressMeasure.COUNTER) {
            return Component.translatable(
                    PREFIX + "progress.counter",
                    phase,
                    TrinityAmountFormatter.format(progress.completedUnits()),
                    TrinityAmountFormatter.format(progress.totalUnits()),
                    TrinityAmountFormatter.format(progress.solverPasses()));
        }
        if (progress.routeStates() > 0 || progress.solverPasses() > 0) {
            return Component.translatable(
                    PREFIX + "progress.observed",
                    phase,
                    TrinityAmountFormatter.format(progress.completedUnits()),
                    TrinityAmountFormatter.format(progress.solverPasses()));
        }
        return phase;
    }

    static Component tooltip(TrinityCraftConfirmMenuState state) {
        TrinityPlanningProgressSnapshot progress = state.data_energistics$planningProgress();
        if (progress == null) {
            return Component.translatable(PREFIX + "status.waiting");
        }
        return Component.translatable(
                PREFIX + "progress.tooltip",
                TrinityAmountFormatter.format(progress.routeStates()),
                TrinityAmountFormatter.format(progress.routeStateLimit()),
                TrinityAmountFormatter.format(progress.jointStates()),
                TrinityAmountFormatter.format(progress.solverPasses()),
                TrinityAmountFormatter.format(progress.solverModels()),
                TrinityDurationFormatter.formatNanos(progress.solverNanos()));
    }
}
