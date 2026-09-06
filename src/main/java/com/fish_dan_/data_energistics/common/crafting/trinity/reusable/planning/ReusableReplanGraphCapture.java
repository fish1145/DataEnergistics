package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Per-CPU server-thread owner of one request-local reusable graph capture. The supplier is invoked only when
 * beginning an attempt, so current CPU item-state candidates are captured once per attempt. No completion callback
 * mutates this owner, and no live world, provider or inventory supplier is retained for background execution.
 */
public final class ReusableReplanGraphCapture {

    public sealed interface Result permits Waiting, Ready, Rejected, Fault {}

    /** The current capture is incomplete; no unexpanded or partially captured graph is available. */
    public record Waiting() implements Result {}

    /** Complete captured value retained until installation/cancellation or a publication identity change. */
    public record Ready(TrinityCraftingGraphSnapshot snapshot, long publicationScope) implements Result {}

    /** Original bounded-capture diagnostic, retained during same-publication retry cooldown. */
    public record Rejected(TrinityPlanningDiagnostic diagnostic, long retryAtTick) implements Result {}

    /** Original capture failure, retained during same-publication retry cooldown. */
    public record Fault(Throwable cause, long retryAtTick) implements Result {}

    private static final Waiting WAITING = new Waiting();
    private long publicationScope;
    private long revision = -1;
    private long generation;
    private int nextRetryDelay = 1;
    private @Nullable CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> pending;
    private @Nullable Result retained;

    /**
     * Non-blocking server-tick advancement. Success is cached; failures retry after 1, 2, 4, ... ticks capped
     * by maxRetryTicks. The current scope/revision is checked before an old future is observed. The supplier
     * must return the complete result of ReusableInputGraphCaptureAccess, never a raw fallback graph.
     */
    public Result advance(long publicationScope, long revision, long currentTick, int maxRetryTicks,
                          Supplier<CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>>> beginCapture) {
        if (publicationScope <= 0 || revision < 0 || currentTick < 0 || maxRetryTicks <= 0) {
            throw new IllegalArgumentException("Reusable replan capture requires a positive scope/retry cap and non-negative revision/tick");
        }
        if (this.publicationScope != publicationScope || this.revision != revision) {
            cancel();
            this.publicationScope = publicationScope;
            this.revision = revision;
        }
        Result previous = this.retained;
        if (previous instanceof Ready || previous instanceof Rejected rejected && currentTick < rejected.retryAtTick() ||
                previous instanceof Fault fault && currentTick < fault.retryAtTick()) {
            return previous;
        }
        if (this.pending == null) {
            long attemptGeneration = this.generation;
            CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> started;
            try {
                started = beginCapture.get();
            } catch (RuntimeException exception) {
                return this.generation == attemptGeneration ? retainFault(exception, currentTick, maxRetryTicks) : WAITING;
            }
            if (this.generation != attemptGeneration) {
                started.cancel(false);
                return WAITING;
            }
            this.pending = started;
            this.retained = null;
        }
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> capture = this.pending;
        if (!capture.isDone()) {
            return WAITING;
        }
        this.pending = null;
        TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> result;
        try {
            result = capture.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            return retainFault(cause == null ? exception : cause, currentTick, maxRetryTicks);
        } catch (CancellationException exception) {
            return retainFault(exception, currentTick, maxRetryTicks);
        }
        if (!result.successful()) {
            return retainRejection(result.diagnostic(), currentTick, maxRetryTicks);
        }
        TrinityCraftingGraphSnapshot snapshot = result.value();
        if (snapshot.revision() != revision) {
            return retainRejection(new TrinityPlanningDiagnostic(TrinityPlanningDiagnosticCode.STALE_GRAPH,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.stale_graph"),
                    Map.of("expected_revision", Long.toString(revision), "captured_revision", Long.toString(snapshot.revision()))),
                    currentTick, maxRetryTicks);
        }
        this.nextRetryDelay = 1;
        Ready ready = new Ready(snapshot, publicationScope);
        this.retained = ready;
        return ready;
    }

    /**
     * Clears pending capture, retained success/diagnostic, and retry state after cancel, close, or plan installation.
     */
    public void cancel() {
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> previous = this.pending;
        this.pending = null;
        this.retained = null;
        this.publicationScope = 0;
        this.revision = -1;
        this.nextRetryDelay = 1;
        this.generation++;
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private Result retainFault(Throwable cause, long currentTick, int maxRetryTicks) {
        Fault fault = new Fault(cause, retryTick(currentTick, maxRetryTicks));
        this.retained = fault;
        return fault;
    }

    private Result retainRejection(TrinityPlanningDiagnostic diagnostic, long currentTick, int maxRetryTicks) {
        Rejected rejected = new Rejected(diagnostic, retryTick(currentTick, maxRetryTicks));
        this.retained = rejected;
        return rejected;
    }

    private long retryTick(long currentTick, int maximum) {
        int delay = Math.min(this.nextRetryDelay, maximum);
        long retryAt = Math.addExact(currentTick, delay);
        this.nextRetryDelay = (int) Math.min(maximum, delay * 2L);
        return retryAt;
    }
}
