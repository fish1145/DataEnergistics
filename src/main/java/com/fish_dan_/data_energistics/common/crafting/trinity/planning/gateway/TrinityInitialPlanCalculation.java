package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.PlanningCachePath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.GenericStack;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Converts one immutable initial request into an executable attempt with cooperative cancellation.
 * <p>
 * Production calculation with exact post-plan CPU-capacity validation and structured outcome logging.
 */
public final class TrinityInitialPlanCalculation {

    /**
     * @param gatewaySupplier lazy access to the server-lifetime cached planning gateway
     * @return stateless calculation composed with the production cached planner
     */
    public static TrinityInitialPlanCalculation create(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        return new TrinityInitialPlanCalculation(gatewaySupplier);
    }

    private final PlanningAlgorithm algorithm;

    TrinityInitialPlanCalculation(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        if (gatewaySupplier == null) {
            throw new IllegalArgumentException("A Trinity initial calculation requires a gateway supplier");
        }
        this.algorithm = request -> gatewaySupplier.get().calculateTrinity(input(request));
    }

    TrinityInitialPlanCalculation(TrinityGraphPlanner planner) {
        if (planner == null) {
            throw new IllegalArgumentException("A Trinity initial calculation requires a planner");
        }
        this.algorithm = request -> new TrinityPlanningComputationResult(
                planner.plan(
                        request.graph(),
                        request.target(),
                        request.requestedAmount(),
                        request.quantityMode(),
                        request.available(),
                        request.limits(),
                        TrinityPlanningControl.create(
                                () -> false,
                                System::nanoTime,
                                TimeUnit.MILLISECONDS.toNanos(request.limits().planningBudgetMs()))),
                PlanningCachePath.MISS);
    }

    TrinityInitialPlanCalculation(
                                  Function<TrinityPlanningInput, TrinityPlanningComputationResult> computation) {
        if (computation == null) {
            throw new IllegalArgumentException("A Trinity initial calculation requires a planning computation");
        }
        this.algorithm = request -> computation.apply(input(request));
    }

    /**
     * Calculates an executable plan from one immutable server-thread capture.
     *
     * @param request immutable server-thread capture
     * @return executable plan or an explicit non-executable Trinity diagnostic
     */
    public TrinityPlanningAttempt calculate(TrinityInitialPlanningRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("A Trinity initial calculation requires a request");
        }
        TrinityPlanningComputationResult computation = this.algorithm.calculate(request);
        TrinityAlgorithmResult<TrinityCraftingPlan> result = computation.result();
        if (!result.successful()) {
            TrinityPlanningAttempt failedAttempt = failedAttempt(
                    request,
                    result.diagnostic(),
                    computation.planningNanos());
            logFailure(request, failedAttempt.diagnostic(), computation.cachePath());
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
            logFailure(request, diagnostic, computation.cachePath());
            return TrinityPlanningAttempt.failure(diagnostic);
        }

        if (DataEnergisticsConfiguration.INSTANCE.developer.verboseRuntimeLogging) {
            TrinityPlanningStatistics statistics = plan.statistics();
            Data_Energistics.LOGGER.info(
                    "Trinity planning selected request={} target={} mode={} revision={} cachePath={} scc={} variants={} planningNanos={} mipNanos={} scheduleStates={}",
                    request.requestId(),
                    request.target(),
                    request.quantityMode(),
                    request.graph().revision(),
                    computation.cachePath(),
                    statistics.sccCount(),
                    statistics.variantCount(),
                    statistics.planningNanos(),
                    statistics.mipNanos(),
                    statistics.scheduleStates());
        }
        return TrinityPlanningAttempt.success(plan);
    }

    private static TrinityPlanningAttempt failedAttempt(
                                                        TrinityInitialPlanningRequest request,
                                                        TrinityPlanningDiagnostic diagnostic,
                                                        long planningNanos) {
        if (diagnostic.inputShortage().isEmpty()) {
            return TrinityPlanningAttempt.failure(diagnostic);
        }
        try {
            return TrinityPlanningAttempt.authoritativeSimulation(TrinityDiagnosedCraftingPlan.forInputShortage(
                    new GenericStack(request.target(), request.requestedAmount().longValueExact()),
                    diagnostic,
                    planningNanos));
        } catch (ArithmeticException exception) {
            return TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow"),
                    Map.of("reason", exception.getClass().getSimpleName())));
        }
    }

    private static void logFailure(
                                   TrinityInitialPlanningRequest request,
                                   TrinityPlanningDiagnostic diagnostic,
                                   PlanningCachePath cachePath) {
        if (!DataEnergisticsConfiguration.INSTANCE.developer.verboseRuntimeLogging) {
            return;
        }
        Data_Energistics.LOGGER.info(
                "Trinity planning stopped request={} target={} mode={} revision={} cachePath={} reason={} metadata={}",
                request.requestId(),
                request.target(),
                request.quantityMode(),
                request.graph().revision(),
                cachePath,
                diagnostic.code(),
                diagnostic.metadata());
    }

    private static TrinityPlanningInput input(TrinityInitialPlanningRequest request) {
        return new TrinityPlanningInput(
                request.gridScope(),
                request.graph(),
                request.target(),
                request.requestedAmount(),
                request.quantityMode(),
                request.available(),
                request.limits());
    }

    @FunctionalInterface
    private interface PlanningAlgorithm {

        TrinityPlanningComputationResult calculate(TrinityInitialPlanningRequest request) throws Exception;
    }
}
