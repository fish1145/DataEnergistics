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
    private long retryRevision = -1L;
    private long retryAtTick = -1L;
    private int nextRetryDelay = 1;

    TrinityRemainingPlanCalculationImpl(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        this.gatewaySupplier = gatewaySupplier;
    }

    @Override
    public Result advance(Optional<TrinityCraftingGraphSnapshot> snapshot,
                          Supplier<Map<AEKey, BigInteger>> availableSupplier,
                          AEKey target,
                          BigInteger requestedAmount,
                          CraftingQuantityMode quantityMode,
                          TrinityCraftingConfig.Settings settings,
                          long currentTick) {
        if (currentTick < 0L) {
            throw new IllegalArgumentException("A Trinity remaining-plan calculation requires a non-negative tick");
        }
        if (this.pending != null) {
            return pollPending(currentTick, settings.dynamicRetryMaxTicks());
        }
        if (snapshot.isEmpty()) {
            return new Waiting();
        }
        TrinityCraftingGraphSnapshot graph = snapshot.orElseThrow();
        if (graph.revision() != this.attemptedRevision) {
            clearRetry();
        } else {
            if (this.retryRevision != graph.revision() || currentTick < this.retryAtTick) {
                return new Waiting();
            }
            this.retryRevision = -1L;
            this.retryAtTick = -1L;
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

    private Result pollPending(long currentTick, int maxRetryTicks) {
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
            scheduleRetry(this.attemptedRevision, currentTick, maxRetryTicks);
            return new Fault(exception, this.attemptedRevision);
        } catch (ExecutionException | RuntimeException exception) {
            scheduleRetry(this.attemptedRevision, currentTick, maxRetryTicks);
            return new Fault(exception, this.attemptedRevision);
        }
    }

    @Override
    public void retrySameRevision(long revision, long currentTick, int maxRetryTicks) {
        if (this.pending != null) {
            throw new IllegalStateException("A pending Trinity calculation cannot schedule a second retry");
        }
        if (revision != this.attemptedRevision) {
            throw new IllegalArgumentException("A Trinity retry must match the last attempted graph revision");
        }
        scheduleRetry(revision, currentTick, maxRetryTicks);
    }

    @Override
    public void acceptRevision(long revision) {
        if (this.pending != null) {
            throw new IllegalStateException("A pending Trinity calculation cannot be accepted");
        }
        if (revision != this.attemptedRevision) {
            throw new IllegalArgumentException("An accepted Trinity revision must match the last completed calculation");
        }
        this.attemptedRevision = -1L;
        clearRetry();
    }

    private void scheduleRetry(long revision, long currentTick, int maxRetryTicks) {
        if (revision < 0L || currentTick < 0L || maxRetryTicks <= 0) {
            throw new IllegalArgumentException("A Trinity same-revision retry requires revision, tick and retry cap");
        }
        if (this.retryRevision >= 0L) {
            throw new IllegalStateException("A Trinity same-revision retry is already scheduled");
        }
        int delay = Math.min(this.nextRetryDelay, maxRetryTicks);
        this.retryRevision = revision;
        this.retryAtTick = Math.addExact(currentTick, delay);
        this.nextRetryDelay = delay >= maxRetryTicks ? maxRetryTicks :
                (int) Math.min(maxRetryTicks, Math.multiplyExact(delay, 2L));
    }

    private void clearRetry() {
        this.retryRevision = -1L;
        this.retryAtTick = -1L;
        this.nextRetryDelay = 1;
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
        clearRetry();
        if (calculation != null) {
            calculation.cancel(true);
        }
    }
}
