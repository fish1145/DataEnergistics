package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGateway;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Owns the bounded asynchronous calculation for replacing a compact plan's remaining work.
 * <p>
 * Server-thread-owned future and revision gate for remaining-work replanning.
 */
public final class TrinityRemainingPlanCalculation {

    /**
     * Result of one server-thread advancement.
     */
    public sealed interface Result permits Waiting, Ready, Rejected, Fault {}

    /**
     * No result is ready, or the current catalog revision has already been attempted.
     */
    public record Waiting() implements Result {}

    /**
     * @param plan     validated replacement plan
     * @param revision graph revision used by the calculation
     */
    public record Ready(TrinityCraftingPlan plan, long revision) implements Result {}

    /**
     * @param diagnostic structured planning rejection
     * @param revision   graph revision that was rejected
     */
    public record Rejected(TrinityPlanningDiagnostic diagnostic, long revision) implements Result {}

    /**
     * @param cause asynchronous calculation failure
     */
    public record Fault(Throwable cause, long revision) implements Result {}

    /**
     * Creates a calculation owner using the shared bounded planning gateway.
     *
     * @param gatewaySupplier lazy access to the server-lifetime bounded planning entry point
     * @return empty remaining-work calculation
     */
    public static TrinityRemainingPlanCalculation create(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        return new TrinityRemainingPlanCalculation(gatewaySupplier);
    }

    private final Supplier<TrinityPlanningGateway> gatewaySupplier;
    private Future<TrinityPlanningAttempt> pending;
    private long attemptedRevision = -1L;
    private long retryRevision = -1L;
    private long retryAtTick = -1L;
    private int nextRetryDelay = 1;

    TrinityRemainingPlanCalculation(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        this.gatewaySupplier = gatewaySupplier;
    }

    /**
     * Polls a pending calculation or starts one for a newer immutable graph revision.
     *
     * @param snapshot          current immutable graph, if one has been published
     * @param gridScope         owning Grid publication scope
     * @param availableSupplier server-thread capture of CPU and network material
     * @param target            remaining requested key
     * @param requestedAmount   remaining delivery amount
     * @param quantityMode      retained quantity semantics
     * @param settings          immutable planner budgets
     * @param currentTick       current server tick used by bounded same-revision retries
     * @return current advancement result
     */
    public Result advance(Optional<TrinityCraftingGraphSnapshot> snapshot,
                          long gridScope,
                          Supplier<Map<AEKey, BigInteger>> availableSupplier,
                          AEKey target,
                          BigInteger requestedAmount,
                          CraftingQuantityMode quantityMode,
                          TrinityCrafting settings,
                          long currentTick) {
        if (gridScope <= 0L || currentTick < 0L) {
            throw new IllegalArgumentException(
                    "A Trinity remaining-plan calculation requires a positive Grid scope and non-negative tick");
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
        TrinityPlanningGateway gateway = this.gatewaySupplier.get();
        this.pending = gateway.beginTrinity(gridScope, graph.revision(), () -> calculate(
                gateway,
                gridScope,
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
            if (!attempt.successful() && attempt.diagnostic().code().transientPlanningFailure()) {
                scheduleRetry(this.attemptedRevision, currentTick, maxRetryTicks);
            }
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

    /**
     * Reopens the revision that produced a valid plan after its server-thread material reservation lost a race.
     * Recalculation is delayed with exponential backoff so a changing inventory cannot create a planner busy loop.
     *
     * @param revision      revision returned with the unapplied ready plan
     * @param currentTick   current server tick
     * @param maxRetryTicks inclusive retry-delay cap
     */
    public void retrySameRevision(long revision, long currentTick, int maxRetryTicks) {
        if (this.pending != null) {
            throw new IllegalStateException("A pending Trinity calculation cannot schedule a second retry");
        }
        if (revision != this.attemptedRevision) {
            throw new IllegalArgumentException("A Trinity retry must match the last attempted graph revision");
        }
        scheduleRetry(revision, currentTick, maxRetryTicks);
    }

    /**
     * Completes one successful replacement-plan episode and releases its revision gate for future independent stale
     * pattern events.
     *
     * @param revision revision of the replacement plan installed by the CPU
     */
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

    private static TrinityPlanningAttempt calculate(TrinityPlanningGateway gateway,
                                                    long gridScope,
                                                    TrinityCraftingGraphSnapshot graph,
                                                    AEKey target,
                                                    BigInteger requestedAmount,
                                                    CraftingQuantityMode quantityMode,
                                                    Map<AEKey, BigInteger> available,
                                                    TrinityCrafting settings) {
        try {
            TrinityPlanningComputationResult computation = gateway.calculateTrinity(new TrinityPlanningInput(
                    gridScope,
                    graph,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    settings));
            if (DataEnergisticsConfiguration.isVerboseRuntimeLoggingEnabled()) {
                Data_Energistics.LOGGER.info(
                        "Trinity remaining planning completed target={} mode={} revision={} cachePath={} outcome={}",
                        target,
                        quantityMode,
                        graph.revision(),
                        computation.cachePath(),
                        computation.result().successful() ?
                                "SELECTED" : computation.result().diagnostic().code());
            }
            return computation.result().successful() ?
                    TrinityPlanningAttempt.success(computation.result().value()) :
                    TrinityPlanningAttempt.failure(computation.result().diagnostic());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Trinity remaining planning was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Trinity remaining planning failed", exception.getCause());
        }
    }

    /**
     * Cooperatively cancels pending background work and clears its revision gate.
     */
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
