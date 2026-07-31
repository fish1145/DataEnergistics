package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGateway;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Server-thread-owned future and revision gate for remaining-work replanning.
 */
final class TrinityRemainingPlanCalculationImpl implements TrinityRemainingPlanCalculation {

    private final Supplier<TrinityPlanningGateway> gatewaySupplier;
    private Future<TrinityPlanningAttempt> pending;
    private long attemptedRevision = -1L;

    TrinityRemainingPlanCalculationImpl(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        this.gatewaySupplier = gatewaySupplier;
    }

    @Override
    public Result advance(Optional<TrinityCraftingGraphSnapshot> snapshot,
                          Supplier<Map<AEKey, BigInteger>> availableSupplier,
                          AEKey target,
                          BigInteger requestedAmount,
                          CraftingQuantityMode quantityMode,
                          TrinityCraftingConfig.Settings settings) {
        if (this.pending != null) {
            return pollPending();
        }
        if (snapshot.isEmpty()) {
            return new Waiting();
        }
        TrinityCraftingGraphSnapshot graph = snapshot.orElseThrow();
        if (graph.revision() == this.attemptedRevision) {
            return new Waiting();
        }

        Map<AEKey, BigInteger> available = availableSupplier.get();
        this.attemptedRevision = graph.revision();
        this.pending = this.gatewaySupplier.get().beginTrinity(() -> calculate(
                graph,
                target,
                requestedAmount,
                quantityMode,
                available,
                settings));
        return new Waiting();
    }

    private Result pollPending() {
        if (!this.pending.isDone()) {
            return new Waiting();
        }
        Future<TrinityPlanningAttempt> completed = this.pending;
        this.pending = null;
        try {
            TrinityPlanningAttempt attempt = completed.get();
            return attempt.successful() ?
                    new Ready(attempt.plan(), this.attemptedRevision) :
                    new Rejected(attempt.diagnostic(), this.attemptedRevision);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Fault(exception);
        } catch (ExecutionException | RuntimeException exception) {
            return new Fault(exception);
        }
    }

    private static TrinityPlanningAttempt calculate(TrinityCraftingGraphSnapshot graph,
                                                    AEKey target,
                                                    BigInteger requestedAmount,
                                                    CraftingQuantityMode quantityMode,
                                                    Map<AEKey, BigInteger> available,
                                                    TrinityCraftingConfig.Settings settings) {
        TrinityPlanningControl control = TrinityPlanningControl.create(
                () -> Thread.currentThread().isInterrupted(),
                System::nanoTime,
                TimeUnit.MILLISECONDS.toNanos(settings.mipTimeoutMs()));
        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                graph,
                target,
                requestedAmount,
                quantityMode,
                available,
                settings,
                control);
        return result.successful() ?
                TrinityPlanningAttempt.success(result.value()) :
                TrinityPlanningAttempt.failure(result.diagnostic());
    }

    @Override
    public void cancel() {
        Future<TrinityPlanningAttempt> calculation = this.pending;
        this.pending = null;
        this.attemptedRevision = -1L;
        if (calculation != null) {
            calculation.cancel(true);
        }
    }
}
