package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import net.minecraft.network.chat.Component;

import appeng.api.networking.crafting.ICraftingPlan;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Bounded implementation that waits only for the configured Trinity budget before adopting the AE2 result.
 */
final class TrinityPlanningGatewayImpl implements TrinityPlanningGateway {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ExecutorService plannerExecutor;
    private final long trinityWaitNanos;
    private final boolean ownsExecutor;
    private final LongSupplier nanoClock;

    TrinityPlanningGatewayImpl(TrinityCraftingConfig.Settings settings) {
        this(
                createExecutor(settings),
                TimeUnit.MILLISECONDS.toNanos(settings.mipTimeoutMs()),
                true,
                System::nanoTime);
    }

    TrinityPlanningGatewayImpl(ExecutorService plannerExecutor, long trinityWaitNanos, boolean ownsExecutor) {
        this(plannerExecutor, trinityWaitNanos, ownsExecutor, System::nanoTime);
    }

    TrinityPlanningGatewayImpl(ExecutorService plannerExecutor,
                               long trinityWaitNanos,
                               boolean ownsExecutor,
                               LongSupplier nanoClock) {
        if (trinityWaitNanos <= 0L) {
            throw new IllegalArgumentException("A Trinity planning gateway requires a positive timeout");
        }
        this.plannerExecutor = plannerExecutor;
        this.trinityWaitNanos = trinityWaitNanos;
        this.ownsExecutor = ownsExecutor;
        this.nanoClock = nanoClock;
    }

    private static ExecutorService createExecutor(TrinityCraftingConfig.Settings settings) {
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "DataEnergistics-TrinityPlanner-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                settings.plannerThreads(),
                settings.plannerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.plannerQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public Future<ICraftingPlan> begin(
                                       boolean qualifiedTrinityCpu,
                                       Callable<TrinityPlanningAttempt> trinityCalculation,
                                       Supplier<Future<ICraftingPlan>> ae2Calculation) {
        long trinityStartedNanos = this.nanoClock.getAsLong();
        long trinityDeadlineNanos = PreferredPlanningFuture.saturatedAdd(
                trinityStartedNanos,
                this.trinityWaitNanos);
        Future<CompletedPlanningAttempt> trinityFuture = null;
        if (qualifiedTrinityCpu) {
            try {
                trinityFuture = this.plannerExecutor.submit(() -> new CompletedPlanningAttempt(
                        trinityCalculation.call(),
                        this.nanoClock.getAsLong()));
            } catch (RejectedExecutionException exception) {
                Data_Energistics.LOGGER.warn("Trinity planner queue rejected a calculation; falling back to AE2",
                        exception);
                trinityFuture = CompletableFuture.completedFuture(new CompletedPlanningAttempt(
                        TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                                TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                                Component.literal("Trinity planner queue is full; using the AE2 calculation"),
                                Map.of("reason", exception.getClass().getSimpleName()))),
                        trinityStartedNanos));
            }
        }

        Future<ICraftingPlan> ae2Future;
        try {
            ae2Future = ae2Calculation.get();
            if (ae2Future == null) {
                throw new IllegalStateException("AE2 planning factory returned no future");
            }
        } catch (RuntimeException exception) {
            ae2Future = CompletableFuture.failedFuture(exception);
        }

        if (!qualifiedTrinityCpu) {
            return ae2Future;
        }
        return new PreferredPlanningFuture(
                trinityFuture,
                ae2Future,
                trinityDeadlineNanos,
                this.nanoClock);
    }

    @Override
    public Future<TrinityPlanningAttempt> beginTrinity(Callable<TrinityPlanningAttempt> trinityCalculation) {
        try {
            return this.plannerExecutor.submit(trinityCalculation);
        } catch (RejectedExecutionException exception) {
            Data_Energistics.LOGGER.warn("Trinity planner queue rejected a continuation calculation", exception);
            return CompletableFuture.completedFuture(TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                    Component.literal("Trinity planner queue is full; remaining work will wait for another revision"),
                    Map.of("reason", exception.getClass().getSimpleName()))));
        }
    }

    @Override
    public void close() {
        if (this.ownsExecutor) {
            this.plannerExecutor.shutdownNow();
        }
    }

    /**
     * Future implementation that does not occupy an additional worker while the two calculations are running.
     */
    private static final class PreferredPlanningFuture implements Future<ICraftingPlan> {

        private final Future<CompletedPlanningAttempt> trinity;
        private final Future<ICraftingPlan> ae2;
        private final long trinityDeadlineNanos;
        private final LongSupplier nanoClock;

        private ICraftingPlan result;
        private boolean cancelled;

        private PreferredPlanningFuture(
                                        Future<CompletedPlanningAttempt> trinity,
                                        Future<ICraftingPlan> ae2,
                                        long trinityDeadlineNanos,
                                        LongSupplier nanoClock) {
            this.trinity = trinity;
            this.ae2 = ae2;
            this.trinityDeadlineNanos = trinityDeadlineNanos;
            this.nanoClock = nanoClock;
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (this.cancelled || this.result != null || isDone()) {
                return false;
            }
            boolean trinityCancelled = this.trinity.cancel(mayInterruptIfRunning);
            boolean ae2Cancelled = this.ae2.cancel(mayInterruptIfRunning);
            boolean cancelledAny = trinityCancelled || ae2Cancelled;
            if (cancelledAny) {
                this.cancelled = true;
            }
            return cancelledAny;
        }

        @Override
        public synchronized boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public synchronized boolean isDone() {
            if (this.cancelled || this.result != null) {
                return true;
            }
            long now = this.nanoClock.getAsLong();
            if (!this.trinity.isDone() && now < this.trinityDeadlineNanos) {
                return false;
            }
            if (this.trinity.isDone() && hasPreferredTrinityResult(this.trinity)) {
                return true;
            }
            return this.ae2.isDone();
        }

        @Override
        public ICraftingPlan get() throws InterruptedException, ExecutionException {
            try {
                return resolve(Long.MAX_VALUE);
            } catch (TimeoutException exception) {
                throw new AssertionError("An unbounded Future#get cannot time out", exception);
            }
        }

        @Override
        public ICraftingPlan get(long timeout, @NotNull TimeUnit unit)
                                                                       throws InterruptedException, ExecutionException, TimeoutException {
            if (timeout < 0L) {
                throw new IllegalArgumentException("Timeout must not be negative");
            }
            return resolve(saturatedAdd(this.nanoClock.getAsLong(), unit.toNanos(timeout)));
        }

        private ICraftingPlan resolve(long callerDeadlineNanos)
                                                                throws InterruptedException, ExecutionException, TimeoutException {
            synchronized (this) {
                if (this.cancelled) {
                    throw new CancellationException("The combined crafting calculation was cancelled");
                }
                if (this.result != null) {
                    return this.result;
                }
            }

            TrinityPlanningDiagnostic diagnostic;
            Throwable trinityFailure = null;
            try {
                CompletedPlanningAttempt completedAttempt = awaitTrinity(callerDeadlineNanos);
                TrinityPlanningAttempt attempt = completedAttempt.attempt();
                if (attempt.successful()) {
                    this.ae2.cancel(true);
                    return publish(attempt.plan());
                }
                if (attempt.authoritativeSimulation().isPresent()) {
                    this.ae2.cancel(true);
                    return publish(attempt.authoritativeSimulation().orElseThrow());
                }
                diagnostic = attempt.diagnostic();
            } catch (ExecutionException exception) {
                trinityFailure = exception.getCause();
                Data_Energistics.LOGGER.error("Trinity planning calculation failed; falling back to AE2",
                        trinityFailure);
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "Trinity planning failed internally; using the AE2 calculation");
            } catch (CancellationException exception) {
                trinityFailure = exception;
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity planning was cancelled; using the AE2 calculation");
            } catch (RuntimeException exception) {
                trinityFailure = exception;
                Data_Energistics.LOGGER.error("Trinity planning returned an invalid result; falling back to AE2",
                        exception);
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "Trinity planning returned an invalid result; using the AE2 calculation");
            }

            try {
                ICraftingPlan ae2Plan = awaitAe2(callerDeadlineNanos);
                if (ae2Plan == null) {
                    throw new ExecutionException(new IllegalStateException("AE2 planning returned no plan"));
                }
                ICraftingPlan selected = ae2Plan.simulation() ? new TrinityDiagnosedCraftingPlan(ae2Plan, diagnostic) : ae2Plan;
                return publish(selected);
            } catch (ExecutionException exception) {
                exception.addSuppressed(new TrinityPlanningFallbackException(diagnostic, trinityFailure));
                throw exception;
            }
        }

        private CompletedPlanningAttempt awaitTrinity(long callerDeadlineNanos)
                                                                                throws InterruptedException, ExecutionException, TimeoutException {
            if (this.trinity.isDone()) {
                CompletedPlanningAttempt completed = this.trinity.get();
                if (completed.completedNanos() <= this.trinityDeadlineNanos) {
                    return completed;
                }
                return timedOutAttempt();
            }

            long deadline = Math.min(this.trinityDeadlineNanos, callerDeadlineNanos);
            long remaining = deadline - this.nanoClock.getAsLong();
            if (remaining > 0L) {
                try {
                    CompletedPlanningAttempt completed = this.trinity.get(remaining, TimeUnit.NANOSECONDS);
                    if (completed.completedNanos() <= this.trinityDeadlineNanos) {
                        return completed;
                    }
                    return timedOutAttempt();
                } catch (TimeoutException timeout) {
                    if (callerDeadlineNanos < this.trinityDeadlineNanos) {
                        throw timeout;
                    }
                }
            }

            if (callerDeadlineNanos < this.trinityDeadlineNanos) {
                throw new TimeoutException("Caller timeout elapsed while awaiting Trinity planning");
            }
            this.trinity.cancel(true);
            Data_Energistics.LOGGER.warn("Trinity planning exceeded its configured wait budget; falling back to AE2");
            return timedOutAttempt();
        }

        private ICraftingPlan awaitAe2(long callerDeadlineNanos)
                                                                 throws InterruptedException, ExecutionException, TimeoutException {
            if (callerDeadlineNanos == Long.MAX_VALUE) {
                return this.ae2.get();
            }
            long remaining = callerDeadlineNanos - this.nanoClock.getAsLong();
            if (remaining <= 0L) {
                if (this.ae2.isDone()) {
                    return this.ae2.get();
                }
                throw new TimeoutException("Caller timeout elapsed while awaiting AE2 planning");
            }
            return this.ae2.get(remaining, TimeUnit.NANOSECONDS);
        }

        private synchronized ICraftingPlan publish(ICraftingPlan selected) {
            if (this.cancelled) {
                throw new CancellationException("The combined crafting calculation was cancelled");
            }
            if (this.result == null) {
                this.result = selected;
            }
            return this.result;
        }

        private boolean hasPreferredTrinityResult(Future<CompletedPlanningAttempt> future) {
            try {
                CompletedPlanningAttempt completed = future.get();
                return completed.completedNanos() <= this.trinityDeadlineNanos &&
                        (completed.attempt().successful() || completed.attempt().authoritativeSimulation().isPresent());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException | CancellationException exception) {
                return false;
            }
        }

        private CompletedPlanningAttempt timedOutAttempt() {
            return new CompletedPlanningAttempt(
                    TrinityPlanningAttempt.failure(TrinityPlanningDiagnostic.of(
                            TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                            "Trinity planning exceeded its configured budget; using the AE2 calculation")),
                    this.nanoClock.getAsLong());
        }

        private static long saturatedAdd(long left, long right) {
            if (right > 0L && left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            if (right < 0L && left < Long.MIN_VALUE - right) {
                return Long.MIN_VALUE;
            }
            return left + right;
        }
    }

    private record CompletedPlanningAttempt(TrinityPlanningAttempt attempt, long completedNanos) {}

    /**
     * Adds Trinity context without replacing the original AE2 planning exception.
     */
    private static final class TrinityPlanningFallbackException extends Exception {

        private TrinityPlanningFallbackException(
                                                 TrinityPlanningDiagnostic diagnostic,
                                                 Throwable cause) {
            super(diagnostic.code() + ": " + diagnostic.message().getString(), cause);
        }
    }
}
