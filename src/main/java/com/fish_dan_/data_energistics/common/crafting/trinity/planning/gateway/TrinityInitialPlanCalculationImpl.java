package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.GenericStack;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Production calculation with exact post-plan CPU-capacity validation and structured outcome logging.
 */
final class TrinityInitialPlanCalculationImpl implements TrinityInitialPlanCalculation {

    private final TrinityGraphPlanner planner;

    TrinityInitialPlanCalculationImpl(TrinityGraphPlanner planner) {
        this.planner = planner;
    }

    @Override
    public TrinityPlanningAttempt calculate(TrinityInitialPlanningRequest request) {
        TrinityPlanningControl control = TrinityPlanningControl.create(
                () -> Thread.currentThread().isInterrupted(),
                System::nanoTime,
                TimeUnit.MILLISECONDS.toNanos(request.settings().mipTimeoutMs()));
        TrinityAlgorithmResult<TrinityCraftingPlan> result = this.planner.plan(
                request.graph(),
                request.target(),
                request.requestedAmount(),
                request.quantityMode(),
                request.available(),
                request.settings(),
                control);
        if (!result.successful()) {
            TrinityPlanningAttempt failedAttempt = failedAttempt(request, result.diagnostic());
            logFallback(request, failedAttempt.diagnostic());
            return failedAttempt;
        }

        TrinityCraftingPlan plan = result.value();
        if (plan.bytes() > request.maxTrinityBytes()) {
            TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.NO_ELIGIBLE_TRINITY_CPU,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.cpu_too_small",
                            plan.bytes(),
                            request.maxTrinityBytes()),
                    Map.of(
                            "planBytes", Long.toString(plan.bytes()),
                            "maxTrinityBytes", Long.toString(request.maxTrinityBytes())));
            logFallback(request, diagnostic);
            return TrinityPlanningAttempt.failure(diagnostic);
        }

        TrinityPlanningStatistics statistics = plan.statistics();
        Data_Energistics.LOGGER.info(
                "Trinity planning selected request={} target={} mode={} revision={} scc={} variants={} planningNanos={} mipNanos={} scheduleStates={}",
                request.requestId(),
                request.target(),
                request.quantityMode(),
                request.graph().revision(),
                statistics.sccCount(),
                statistics.variantCount(),
                statistics.planningNanos(),
                statistics.mipNanos(),
                statistics.scheduleStates());
        return TrinityPlanningAttempt.success(plan);
    }

    private static TrinityPlanningAttempt failedAttempt(
                                                        TrinityInitialPlanningRequest request,
                                                        TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic.inputShortage().isEmpty()) {
            return TrinityPlanningAttempt.failure(diagnostic);
        }
        try {
            return TrinityPlanningAttempt.authoritativeSimulation(TrinityDiagnosedCraftingPlan.forInputShortage(
                    new GenericStack(request.target(), request.requestedAmount().longValueExact()),
                    diagnostic));
        } catch (ArithmeticException exception) {
            return TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    Component.literal("The exact Trinity shortage exceeds an AE2 long boundary"),
                    Map.of("reason", exception.getClass().getSimpleName())));
        }
    }

    private static void logFallback(
                                    TrinityInitialPlanningRequest request,
                                    TrinityPlanningDiagnostic diagnostic) {
        Data_Energistics.LOGGER.info(
                "Trinity planning fallback request={} target={} mode={} revision={} reason={} metadata={}",
                request.requestId(),
                request.target(),
                request.quantityMode(),
                request.graph().revision(),
                diagnostic.code(),
                diagnostic.metadata());
    }
}
